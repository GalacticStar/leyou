package com.leyou.order.config;

import com.leyou.order.utils.PayHelper;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PayConfiguration {

    @Bean
    @ConfigurationProperties(prefix = "ly.pay")
    public WXPayConfigImpl payConfig(){
        return new WXPayConfigImpl();
    }

    @Bean
    public PayHelper payHelper(WXPayConfigImpl payConfig){
        return new PayHelper(payConfig);
    }
}
