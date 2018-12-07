package com.leyou.order.config;

import com.github.wxpay.sdk.WXPayConfig;
import lombok.Data;

import java.io.InputStream;

@Data
public class WXPayConfigImpl implements WXPayConfig {

    private String appID; // 公众账号ID

    private String mchID; // 商户号

    private String key; // 生成签名的密钥

    private int httpConnectTimeoutMs; // 连接超时时间

    private int httpReadTimeoutMs;// 读取超时时间

    private String tradeType; // 交易类型
    private String spbillCreateIp;// 本地ip
    private String notifyUrl;// 回调地址

    @Override
    public InputStream getCertStream() {
        return null;
    }
}
