package com.leyou.user.config;

import org.apache.commons.lang3.StringUtils;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class DataSourceAop {

    @Before("execution(* com.leyou.user.service.*.*(..))")
    public void before(JoinPoint jp) {
        String methodName = jp.getSignature().getName();

        if (StringUtils.startsWithAny(methodName, "get", "select", "find","check")) {
            DBContextHolder.slave();
        }else {
            DBContextHolder.master();
        }
    }
}