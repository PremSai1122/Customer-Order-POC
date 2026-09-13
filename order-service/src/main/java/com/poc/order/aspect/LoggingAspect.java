package com.poc.order.aspect;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * Cross-cutting logging around the service layer, mirroring
 * customer-service's LoggingAspect so both atomic services have the same
 * AOP logging coverage.
 */
@Aspect
@Component
public class LoggingAspect {

    private static final Logger log = LoggerFactory.getLogger(LoggingAspect.class);

    @Before("execution(* com.poc.order.service.*.*(..))")
    public void logBefore(JoinPoint joinPoint) {
        log.info("Entering: {} with args={}",
                joinPoint.getSignature().toShortString(),
                Arrays.toString(joinPoint.getArgs()));
    }

    @Around("execution(* com.poc.order.service.*.*(..))")
    public Object logAround(ProceedingJoinPoint joinPoint) throws Throwable {
        long start = System.currentTimeMillis();
        Object result = joinPoint.proceed();
        long duration = System.currentTimeMillis() - start;
        log.info("Exiting: {} took {} ms", joinPoint.getSignature().toShortString(), duration);
        return result;
    }

    @AfterThrowing(pointcut = "execution(* com.poc.order.service.*.*(..))", throwing = "ex")
    public void logException(JoinPoint joinPoint, Throwable ex) {
        log.error("Exception in {}: {}", joinPoint.getSignature().toShortString(), ex.getMessage());
    }
}
