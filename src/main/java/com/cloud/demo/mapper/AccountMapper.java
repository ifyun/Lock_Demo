package com.cloud.demo.mapper;

import com.cloud.demo.model.Account;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public interface AccountMapper {
    Account selectById(int id);
    Account selectByIdForUpdate(int id);
    int updateDepositWithVersion(Account account);
    void updateDeposit(Account account);
    BigDecimal getTotalDeposit();
}
