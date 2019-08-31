package com.cloud.demo.model;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class Account implements Serializable {
    private int id;
    private String name;
    private BigDecimal deposit;
    private int version;
}
