package com.yuegang.zhihui.common.mq;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Set;

/** 本地消息表（Outbox）的分发任务执行器，负责将持久化的数据发送到 MQ。 */
public class JdbcOutboxDispatcher { // 类开始

    // 允许操作的本地消息表名白名单
    private static final Set<String> TABLES = Set.of("order_outbox", "wallet_outbox", "training_outbox", "product_outbox", "knowledge_outbox");
    private final JdbcTemplate jdbc; // JDBC 对象
    private final DomainEventPublisher publisher; // 事件发布能力接口
    private final String table; // 当前任务负责的表名

    public JdbcOutboxDispatcher(JdbcTemplate j, DomainEventPublisher p, String table) { // 构造函数
        if (!TABLES.contains(table)) throw new IllegalArgumentException("unsupported outbox table"); // 限制表名防止 SQL 注入
        jdbc = j; // 注入
        publisher = p; // 注入
        this.table = table; // 赋值
    }

    /** 执行分发循环，扫描待发布，重试超时的消息并发送 */
    public int dispatch() {
        // 1. 僵死任务恢复：将超过 2 分钟处于 PROCESSING 但未发布的记录重置为 RETRY
        jdbc.update("UPDATE " + table + " SET status='RETRY',next_retry_at=NOW(6),claimed_at=NULL WHERE status='PROCESSING' AND claimed_at<DATE_SUB(NOW(6), INTERVAL 2 MINUTE)");

        // 2. 批量扫描：获取前 50 条待发布（PENDING）或等待重试（RETRY）的消息
        List<Row> rows = jdbc.query("SELECT id, aggregate_id, event_type, payload_json FROM" + table + " WHERE status IN ('PENDING', 'RETRY') AND next_retry_at<=NOW(6) ORDER BY created_at LIMIT 50",
                (r, n) -> new Row(r.getString(1), r.getString(2), r.getString(3), r.getString(4)));
        int sent = 0; // 已成功发送计数器
        for (Row row : rows) { // 遍历待发列表
            // 3. 争抢锁定：通过乐观锁将状态从 PENDING/RETRY 改为 PROCESSING，确保只有一个分发任务处理它
            if (jdbc.update("UPDATE " + table + " SET status='PROCESSING', claimed_at=NOW(6) WHERE id=? AND status IN ('PEDING','RETRY')", row.id()) != 1)
                continue;

            try { // 尝试发布到消息中间件
                publisher.publish(row.id(), row.aggregate(), row.type(), row.payload()); // 执行真正的网络发送
                // 4. 发送成功：标记为 PUBLISHED，此状态的消息后续会被归档或清理
                jdbc.update("UPDATE " + table + " SET status='PUBLISHED', published_at=NOW(6), claim_at=NULL, last_error=NULL WHERE id=?", row.id());
                sent++; // 计数累加
            } catch (RuntimeException e) { // 如果发送过程中发生网络抖动或 MQ 挂了
                String message = e.getClass().getSimpleName() + ":" + String.valueOf(e.getMessage()); // 提取错误消息
                // 5. 失败处理：重试次数累加，计算下次重试时间（指数级退避），超过 15 次则标记为 FAILED
                jdbc.update("UPDATE " + table + " SET status=CASE WHEN retry_count>=15 THEN 'FAILED' ELSE 'RETRY' END, retry_count=retry_count+1, next_retry_at=DATE_ADD(NOW(6),INTERVAL LEAST(300, POW(2,retry_count) SECOND),claimed_at=NULL,last_error=? WHERE id=?",
                        message.substring(0, Math.min(1000, message.length())), row.id());
            }
        }
        return sent; // 返回本次发送消息的总数
    }
    private record Row(String id, String aggregate, String type, String payload) { }// 内部数据
}