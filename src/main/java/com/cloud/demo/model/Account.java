package com.cloud.demo.model;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class Account {
    private int id;
    private BigDecimal deposit;
    private int version;
}
