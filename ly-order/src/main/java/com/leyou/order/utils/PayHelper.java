package com.leyou.order.utils;

import com.github.wxpay.sdk.WXPay;
import com.github.wxpay.sdk.WXPayConstants;
import com.github.wxpay.sdk.WXPayUtil;
import com.leyou.common.exception.LyException;
import com.leyou.order.config.WXPayConfigImpl;
import com.leyou.order.enums.OrderStatusEnum;
import com.leyou.order.enums.PayState;
import com.leyou.order.enums.PayStatusEnum;
import com.leyou.order.mapper.OrderMapper;
import com.leyou.order.mapper.OrderStatusMapper;
import com.leyou.order.mapper.PayLogMapper;
import com.leyou.order.pojo.Order;
import com.leyou.order.pojo.OrderStatus;
import com.leyou.order.pojo.PayLog;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static com.github.wxpay.sdk.WXPayConstants.FAIL;
import static com.github.wxpay.sdk.WXPayConstants.SignType;

@Slf4j
public class PayHelper {

    private WXPay wxpay;

    private WXPayConfigImpl payConfig;

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private OrderStatusMapper statusMapper;

    @Autowired
    private PayLogMapper payLogMapper;

    public PayHelper(WXPayConfigImpl payConfig) {
        this.wxpay = new WXPay(payConfig, SignType.HMACSHA256);
        this.payConfig = payConfig;
    }

    public String createPayUrl(Long orderId, Long totalFee, String body){

        // 准备请求参数
        HashMap<String, String> data = new HashMap<String, String>();
        data.put("body", body);
        data.put("out_trade_no", orderId.toString());
        data.put("total_fee", totalFee.toString());
        data.put("spbill_create_ip", payConfig.getSpbillCreateIp());
        data.put("notify_url", payConfig.getNotifyUrl());
        data.put("trade_type", payConfig.getTradeType());

        try {
            // 调用统一下单API
            Map<String, String> result = wxpay.unifiedOrder(data);

            // 校验通信标示
            if (FAIL.equals(result.get("return_code"))) {
                log.error("【微信下单】下单通信失败，订单号:{}, 原因：{}",
                        orderId, result.get("return_msg"));
                throw new LyException(HttpStatus.INTERNAL_SERVER_ERROR, "下单失败！");
            }

            // 校验业务标示
            if (FAIL.equals(result.get("result_code"))) {
                log.error("【微信下单】下单失败，订单号:{}, 错误码：{}， 错误原因：{}",
                        orderId, result.get("err_code"), result.get("err_code_des"));
                throw new LyException(HttpStatus.INTERNAL_SERVER_ERROR, "下单失败！");
            }
            // 验证签名
            isSignatureValid(result);

            String payUrl = result.get("code_url");
            return payUrl;
        } catch (Exception e) {
            log.error("【微信下单】下单失败，订单号:{}", orderId, e);
            throw new LyException(HttpStatus.INTERNAL_SERVER_ERROR, "下单失败！");
        }
    }

    private void isSignatureValid(Map<String, String> result){
        try {
            // 下单成功，验证签名
            boolean boo1 = WXPayUtil.isSignatureValid(result, payConfig.getKey(), SignType.MD5);
            boolean boo2 = WXPayUtil.isSignatureValid(result, payConfig.getKey(), SignType.HMACSHA256);
            if (!boo1 && !boo2) {
                // 验证失败
                log.error("【微信下单】签名验证失败");
                throw new LyException(HttpStatus.INTERNAL_SERVER_ERROR, "签名验证失败！");
            }
        } catch (Exception e){
            // 验证失败
            log.error("【微信下单】签名验证失败");
            throw new LyException(HttpStatus.INTERNAL_SERVER_ERROR, "签名验证失败！");
        }
    }

    public void handleNotify(Map<String,String> msg){
        // 1、校验签名
        isSignatureValid(msg);
        // 2、校验金额
        // 2.1.解析数据
        String totalFee = msg.get("total_fee");
        String outTradeNo = msg.get("out_trade_no");
        String transactionId = msg.get("transaction_id");
        String bankType = msg.get("bank_type");
        if (StringUtils.isBlank(outTradeNo) || StringUtils.isBlank(totalFee)
                || StringUtils.isBlank(transactionId) || StringUtils.isBlank(bankType)) {
            log.error("【微信支付回调】支付回调返回数据不正确");
            throw new LyException(HttpStatus.INTERNAL_SERVER_ERROR, "数据不正确");
        }
        // 2.2.查询订单
        Order order = orderMapper.selectByPrimaryKey(Long.valueOf(outTradeNo));
        // 2.3.校验金额，此处因为我们支付的都是1，所以写死了，应该与订单中的对比
        if (1L != Long.valueOf(totalFee)) {
            log.error("【微信支付回调】支付回调返回数据不正确");
            throw new LyException(HttpStatus.INTERNAL_SERVER_ERROR, "数据不正确");
        }

        // 判断支付状态
        OrderStatus status = statusMapper.selectByPrimaryKey(order.getOrderId());
        if (status.getStatus() != OrderStatusEnum.INIT.value()) {
            // 如果不是未支付状态，则都认为支付成功！
            return;
        }

        // 3、修改支付日志状态
        PayLog payLog = payLogMapper.selectByPrimaryKey(order.getOrderId());
        // 只有未支付订单才需要修改
        if (payLog.getStatus() == PayStatusEnum.NOT_PAY.value()) {
            payLog.setOrderId(order.getOrderId());
            payLog.setStatus(PayStatusEnum.SUCCESS.value());
            payLog.setTransactionId(transactionId);
            payLog.setBankType(bankType);
            payLog.setPayTime(new Date());
            payLogMapper.updateByPrimaryKeySelective(payLog);
        }

        // 4、修改订单状态
        OrderStatus orderStatus = new OrderStatus();
        orderStatus.setOrderId(order.getOrderId());
        orderStatus.setPaymentTime(new Date());
        orderStatus.setStatus(OrderStatusEnum.PAY_UP.value());
        statusMapper.updateByPrimaryKeySelective(orderStatus);
    }

    public PayState queryPayState(Long orderId) {
        Map<String, String> data = new HashMap<>();
        // 订单号
        data.put("out_trade_no", orderId.toString());
        try {
            Map<String, String> result = wxpay.orderQuery(data);
            // 链接失败
            if (result == null || WXPayConstants.FAIL.equals(result.get("return_code"))) {
                // 未查询到结果或链接失败，认为是未付款
                log.info("【支付状态查询】链接微信服务失败，订单编号：{}", orderId);
                return PayState.NOT_PAY;
            }
            // 查询失败
            if (WXPayConstants.FAIL.equals(result.get("result_code"))) {
                log.error("【支付状态查询】查询微信订单支付状态失败，错误码：{}，错误信息：{}",
                        result.get("err_code"), result.get("err_code_des"));
                return PayState.NOT_PAY;
            }

            // 校验签名
            isSignatureValid(result);


            String state = result.get("trade_state");
            if ("SUCCESS".equals(state)) {
                // 修改支付状态等信息
                handleNotify(result);

                // success，则认为付款成功
                return PayState.SUCCESS;
            } else if (StringUtils.equals("USERPAYING", state) || StringUtils.equals("NOTPAY", state)) {
                // 未付款或正在付款，都认为是未付款
                return PayState.NOT_PAY;
            } else {
                // 其它状态认为是付款失败
                return PayState.FAIL;
            }
        } catch (Exception e) {
            log.error("查询订单状态异常", e);
            return PayState.NOT_PAY;
        }
    }

}
