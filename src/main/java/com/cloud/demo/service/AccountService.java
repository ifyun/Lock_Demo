package com.cloud.demo.service;

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
    public int transferPessimistic(int fromId, int toId, BigDecimal value) {
        Account from, to;

        try {
            // 先锁 id 较大的那行，避免死锁
            if (fromId > toId) {
                from = accountMapper.selectByIdB(fromId);
                to = accountMapper.selectByIdB(toId);
            } else {
                to = accountMapper.selectByIdB(toId);
                from = accountMapper.selectByIdB(fromId);
            }
        } catch (Exception e) {
            log.error(e.getMessage());
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return 0;
        }

        if (!isDepositEnough.test(from.getDeposit(), value)) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return -1;
        }

        from.setDeposit(from.getDeposit().subtract(value));
        to.setDeposit(to.getDeposit().add(value));

        accountMapper.updateDepositB(from);
        accountMapper.updateDepositB(to);

        return  1;
    }

    /**
     * 转账操作，乐观锁
     *
     * @param fromId 扣款账户
     * @param toId   收款账户
     * @param value  金额
     */
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public int transferOptimistic(int fromId, int toId, BigDecimal value) {
        Account from = accountMapper.selectById(fromId),
                to = accountMapper.selectById(toId);

        if (!isDepositEnough.test(from.getDeposit(), value)) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return -1;
        }

        from.setDeposit(from.getDeposit().subtract(value));
        to.setDeposit(to.getDeposit().add(value));

        int r1, r2;

        // 先锁 id 较大的那行，避免死锁
        if (from.getId() > to.getId()) {
            r1 = accountMapper.updateDeposit(from);
            r2 = accountMapper.updateDeposit(to);
        } else {
            r2 = accountMapper.updateDeposit(to);
            r1 = accountMapper.updateDeposit(from);
        }

        if (r1 < 1 || r2 < 1) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            TransactionAspectSupport.currentTransactionStatus().flush();
            return 0;
        } else {
            return 1;
        }
    }
}
