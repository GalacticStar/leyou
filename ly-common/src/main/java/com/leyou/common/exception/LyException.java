package com.leyou.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter //实体类的getter、setter方法等
public class LyException extends RuntimeException {
    //响应状态对象
    private HttpStatus status;
    //响应状态码
    private int statusCode;

    public LyException(HttpStatus status,String message) {//根据springMVC里的HttpStatus状态对象
        super(message);
        this.status = status;
    }

    public LyException(int statusCode,String message) {//若没有HttpStatus，使用自定义的code对象
        super(message);
        this.statusCode = statusCode;
    }
}
