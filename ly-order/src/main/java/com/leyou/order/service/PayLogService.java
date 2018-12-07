package com.leyou.order.service;

import com.leyou.auth.pojo.UserInfo;
import com.leyou.order.enums.PayStatusEnum;
import com.leyou.order.interceptor.LoginInterceptor;
import com.leyou.order.mapper.PayLogMapper;
import com.leyou.order.pojo.PayLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;

@Service
public class PayLogService {
    @Autowired
    private PayLogMapper logMapper;

    public void createPayLog(Long orderId, Long actualPay) {
        //创建支付日志，先删除以前的
        logMapper.deleteByPrimaryKey(orderId);
        //再重新创建
        PayLog payLog = new PayLog();
        payLog.setOrderId(orderId);
        payLog.setPayType(1);
        payLog.setStatus(PayStatusEnum.NOT_PAY.value());
        payLog.setCreateTime(new Date());
        payLog.setTotalFee(actualPay);
        //用户信息
        UserInfo userInfo = LoginInterceptor.getUserInfo();
        payLog.setUserId(userInfo.getId());
        logMapper.insertSelective(payLog);
    }
}