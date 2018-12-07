package com.leyou.common.advice;

import com.leyou.common.exception.LyException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
@Slf4j
public class BasicExceptionHandler {
    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleException(Exception e){
        //记录异常信息
        log.error(e.getMessage(),e);
        //判断是否为自定义异常
        if(e instanceof LyException){
            LyException eThis = (LyException) e;
            int code = eThis.getStatus()== null ? eThis.getStatusCode() : eThis.getStatus().value();
            return ResponseEntity.status(code).body(e.getMessage());
        }
        //其他情况，返回500
        return ResponseEntity.status(500).body("未知错误！");
    }
}