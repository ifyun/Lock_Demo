package com.cloud.demo.mapper;

import com.cloud.demo.model.Account;
import org.springframework.stereotype.Component;

@Component
public interface AccountMapper {
    Account selectById(int id);
    int updateDeposit(Account account);

    Account selectByIdB(int id);
    int updateDepositB(Account account);
}
