package com.cloud.demo.service;

import com.cloud.demo.annotation.Retry;
import com.cloud.demo.exceptiom.RetryException;
import com.cloud.demo.mapper.AccountMapper;
import com.cloud.demo.model.Account;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.function.BiPredicate;

@Slf4j
@Service
public class AccountService {

    public enum Result{
        SUCCESS,
        DEPOSIT_NOT_ENOUGH,
        FAILED,
    }

    @Resource
    private AccountMapper accountMapper;

    private BiPredicate<BigDecimal, BigDecimal> isDepositEnough = (deposit, value) -> deposit.compareTo(value) > 0;

    /**
     * 转账操作，悲观锁
     *
     * @param fromId 扣款账户
     * @param toId   收款账户
     * @param value  金额
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Result transferPessimistic(int fromId, int toId, BigDecimal value) {
        Account from, to;

        try {
            // 先锁 id 较大的那行，避免死锁
            if (fromId > toId) {
                from = accountMapper.selectByIdForUpdate(fromId);
                to = accountMapper.selectByIdForUpdate(toId);
            } else {
                to = accountMapper.selectByIdForUpdate(toId);
                from = accountMapper.selectByIdForUpdate(fromId);
            }
        } catch (Exception e) {
            log.error(e.getMessage());
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return Result.FAILED;
        }

        if (!isDepositEnough.test(from.getDeposit(), value)) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            log.info(String.format("Account %d is not enough.", fromId));
            return Result.DEPOSIT_NOT_ENOUGH;
        }

        from.setDeposit(from.getDeposit().subtract(value));
        to.setDeposit(to.getDeposit().add(value));

        accountMapper.updateDeposit(from);
        accountMapper.updateDeposit(to);

        return Result.SUCCESS;
    }

    /**
     * 转账操作，乐观锁
     *  @param fromId 扣款账户
     * @param toId   收款账户
     * @param value  金额
     */
    @Retry
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public Result transferOptimistic(int fromId, int toId, BigDecimal value) {
        Account from = accountMapper.selectById(fromId),
                to = accountMapper.selectById(toId);

        if (!isDepositEnough.test(from.getDeposit(), value)) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return Result.DEPOSIT_NOT_ENOUGH;
        }

        from.setDeposit(from.getDeposit().subtract(value));
        to.setDeposit(to.getDeposit().add(value));

        int r1, r2;

        // 先锁 id 较大的那行，避免死锁
        if (from.getId() > to.getId()) {
            r1 = accountMapper.updateDepositWithVersion(from);
            r2 = accountMapper.updateDepositWithVersion(to);
        } else {
            r2 = accountMapper.updateDepositWithVersion(to);
            r1 = accountMapper.updateDepositWithVersion(from);
        }

        if (r1 < 1 || r2 < 1) {
            // 失败，抛出重试异常，执行重试
            throw new RetryException("Transfer failed, retry.");
        } else {
            return Result.SUCCESS;
        }
    }
}
