package com.yuegang.zhihui.common.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Objects;

public final class InternalServiceSignature { // 定义签名类
    private final SecretKeySpec key; // 秘钥字段声明
    private final Clock clock; // 时钟字段声明
    private final Duration skew; // 偏差字段声明
    public InternalServiceSignature(byte[]s, Clock c, Duration d) {
        if(s==null||s.length<32) throw new IllegalArgumentException("service secret too short"); // 长度校验
        key=new SecretKeySpec(Arrays.copyOf(s,s.length),"HmacSHA256"); // 秘钥初始化
        clock= Objects.requireNonNull(c); // 时钟初始化
        skew=Objects.requireNonNull(d); // 偏差初始化
    }
    public String sign(Metadata m) {return HexFormat.of().formatHex(mac(canonical(m)));}// 执行签名逻辑
    public boolean verify(Metadata m, String sig){
        if(sig==null||!sig.matches("[0-9a-f]{64}")||Duration.between(m.timestamp(),clock.instant()).abs().compareTo(skew)>0) return false; // 格式及时间校验
        byte[] a=HexFormat.of().parseHex(sig), e=mac(canonical(m)); // 转换字节
        try{return MessageDigest.isEqual(a,e);} // 比对
        finally {Arrays.fill(a,(byte) 0); Arrays.fill(e,(byte) 0);}}// 擦除敏感内存

        private byte[] canonical(Metadata m) {
            return String.join("\n", m.service(), m.method(), m.path(), Long.toString(m.timestamp().toEpochMilli())).getBytes(StandardCharsets.UTF_8);}  // 转换字节流

        private byte[] mac(byte[] b) { // HMAC运算
            try{Mac m= Mac.getInstance("HmacSHA256");m.init(key);return m.doFinal(b);} // 执行
            catch (GeneralSecurityException e){throw new IllegalStateException(e);} // 异常处理
            finally {Arrays.fill(b,(byte) 0);}} //内存安全

        public record Metadata(String service, String method, String path, java.time.Instant timestamp) {
            public Metadata { // 数据校验 no usages
                if(service==null||!service.matches("[a-z][a-z0-9-]{2,63}")) throw new IllegalArgumentException("unsafe service"); // 校验服务名
                if(method==null||!method.matches("[A-Z]{3,10}")) throw new IllegalArgumentException("unsafe method");
                if(path==null||!path.startsWith("/")) throw new IllegalArgumentException("unsafe path"); // 校验路径
                Objects.requireNonNull(timestamp); // 校验时间戳
        }
    }
}
