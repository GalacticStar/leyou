package com.leyou.order.enums;

public enum PayStatusEnum {

    NOT_PAY(1, "未支付"),
    SUCCESS(2, "支付成功"),
    REFUND(3, "已退款"),
    PAY_ERROR(4, "支付错误"),
    CLOSED(5, "已关闭");

    private Integer code;
    private String msg;

    PayStatusEnum(Integer code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    public Integer value(){
        return this.code;
    }

    public String msg(){
        return msg;
    }
}