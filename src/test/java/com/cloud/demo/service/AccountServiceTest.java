package com.cloud.demo.service;

import com.cloud.demo.mapper.AccountMapper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import javax.annotation.Resource;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;

import static java.lang.System.currentTimeMillis;

@Slf4j
@SpringBootTest
@RunWith(SpringRunner.class)
class AccountServiceTest {

    // 并发数
    private static final int COUNT = 1000;

    @Resource
    AccountMapper accountMapper;

    @Resource
    AccountService accountService;

    private CountDownLatch latch = new CountDownLatch(COUNT);
    private List<Thread> transferThreads = new ArrayList<>();

    @BeforeEach
    void setUp() {
        transferThreads.clear();
    }

    /**
     * 测试悲观锁
     */
    @Test
    void transferByPessimisticLock() throws Throwable {
        for (int i = 1; i <= COUNT; i++) {
            transferThreads.add(new Transfer(i, true));
        }
        for (Thread t : transferThreads) {
            t.start();
        }
        latch.await();

        BigDecimal a = accountMapper.selectByIdB(1).getDeposit(),
                b = accountMapper.selectByIdB(2).getDeposit();

        Assertions.assertEquals(a.add(b), BigDecimal.valueOf(2000).setScale(2, RoundingMode.HALF_UP));
    }

    /**
     * 测试乐观锁
     */
    @Test
    void transferByOptimisticLock() throws Throwable {
        for (int i = 1; i <= COUNT; i++) {
            transferThreads.add(new Transfer(i, false));
        }
        for (Thread t : transferThreads) {
            t.start();
        }
        latch.await();

        BigDecimal a = accountMapper.selectById(1).getDeposit(),
                b = accountMapper.selectById(2).getDeposit();

        Assertions.assertEquals(a.add(b), BigDecimal.valueOf(2000).setScale(2, RoundingMode.HALF_UP));
    }

    class Transfer extends Thread {
        int index;
        boolean isPessimistic;
        int[] id = {1, 2};

        Transfer(int i, boolean b) {
            index = i;
            isPessimistic = b;
            if (index % 2 == 0) {
                id[0] = 2;
                id[1] = 1;
            }
        }

        @Override
        public void run() {
            BigDecimal value = BigDecimal.valueOf(
                    new Random(currentTimeMillis()).nextFloat() * 100
            ).setScale(2, RoundingMode.HALF_UP);
            int result;
            if (isPessimistic) {
                result = accountService.transferPessimistic(id[0], id[1], value);
            } else {
               result= accountService.transferOptimistic(id[0], id[1], value);
            }
            if (result == 1) {
                log.info(String.format("Transfer %f from %d to %d success", value, id[0],id[1]));
            }
            latch.countDown();
        }
    }
}