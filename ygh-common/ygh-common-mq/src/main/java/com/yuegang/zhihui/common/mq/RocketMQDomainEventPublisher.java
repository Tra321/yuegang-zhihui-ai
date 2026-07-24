package com.yuegang.zhihui.common.mq;

import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.common.message.Message;

import java.nio.charset.StandardCharsets;

/** 领域事件发布的 RocketMQ 物理驱动实现类。 */
public class RocketMQDomainEventPublisher implements DomainEventPublisher{
    private final DefaultMQProducer producer; // 真正的 RocketMQ 客户端
    private final String topic; // 事件发布的默认 Topic

    public RocketMQDomainEventPublisher(String group, String nameserver, String topic) { //构造函数开始
        this.topic = topic; // 绑定 Topic
        try { // 客户端
            producer = new DefaultMQProducer(group); // 初始化生产组
            producer.setNamesrvAddr(nameserver); // 指定 Nacos/NameServer 地址
            producer.setRetryTimesWhenSendFailed(2); // 失败时客户端默认重试2次
            producer.setSendMsgTimeout(3000); // 设置发送消息超时时间为3秒
            producer.start(); // 调用底层 Netty 开始建立连接
        } catch (Exception e) {
            throw new IllegalArgumentException("RocketMQ producer start failed", e); // 抛出状态异常
        }
    }
    @Override
    public void publish(String eventId, String aggregateId, String eventType, String payload) { // 发布逻辑
        try { // 构造消息
            // 填充 Topic 、Tag（既类型）、key(既时间ID） 和 Body
            Message m = new Message(topic, eventType, eventId, payload.getBytes(StandardCharsets.UTF_8));
            // 向 RocketMQ 消息属性中注入业务元数据，方便控制台搜索和消息过滤
            m.putUserProperty("eventId", eventId); // 存入 ID
            m.putUserProperty("aggregateId", aggregateId); // 存入聚合ID
            m.putUserProperty("eventType", eventType); // 传入类型
            m.putUserProperty("schemaVersion", "1"); // 传入契约版本
            SendResult result = producer.send(m); //执行同步发送，等待服务确认
            if (result.getSendStatus() != SendStatus.SEND_OK) // 如果确认状态部署 SEND_OK
                throw new IllegalArgumentException("RocketMQ sent status " + result.getSendStatus()); //报错
        } catch (Exception e) {
            throw new MessageInfrastructureException("Rocket event publish failed", e); // 抛出基础设施异常
        }
    }

    public void close() { // 实现 AutoCloseable 接口
        producer.shutdown(); // 优雅关闭 RocketMQ 客户端，释放物理资源
    }
}
