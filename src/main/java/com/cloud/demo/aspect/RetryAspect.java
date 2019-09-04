package com.cloud.demo.aspect;

import com.cloud.demo.annotation.Retry;
import com.cloud.demo.exceptiom.RetryException;
import com.cloud.demo.service.AccountService;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

@Slf4j
@Aspect
@Component
public class RetryAspect {

    @Pointcut("@annotation(com.cloud.demo.annotation.Retry)")
    public void retryPointcut() {

    }

    @Around("retryPointcut() && @annotation(retry)")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Object tryAgain(ProceedingJoinPoint joinPoint, Retry retry) throws Throwable {
        int count = 0;
        do {
            count++;
            try {
                return joinPoint.proceed();
            } catch (RetryException e) {
                if (count > retry.value()) {
                    log.error("Retry failed!");
                    TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
                    return AccountService.Result.FAILED;
                }
            }
        } while (true);
    }
}
