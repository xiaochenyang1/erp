package com.tuowei.erp.finance.currency.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDate;

@TableName("md_exchange_rate")
public class ExchangeRateEntity {
    @TableId(type = IdType.ASSIGN_ID) private Long id;
    private Long companyId; private Long accountBookId; private String fromCurrencyCode; private String toCurrencyCode;
    private BigDecimal rate; private LocalDate effectiveFrom; private LocalDate effectiveTo; private String status;
    public Long getId(){return id;} public void setId(Long v){id=v;} public Long getCompanyId(){return companyId;} public void setCompanyId(Long v){companyId=v;}
    public Long getAccountBookId(){return accountBookId;} public void setAccountBookId(Long v){accountBookId=v;} public String getFromCurrencyCode(){return fromCurrencyCode;} public void setFromCurrencyCode(String v){fromCurrencyCode=v;}
    public String getToCurrencyCode(){return toCurrencyCode;} public void setToCurrencyCode(String v){toCurrencyCode=v;} public BigDecimal getRate(){return rate;} public void setRate(BigDecimal v){rate=v;}
    public LocalDate getEffectiveFrom(){return effectiveFrom;} public void setEffectiveFrom(LocalDate v){effectiveFrom=v;} public LocalDate getEffectiveTo(){return effectiveTo;} public void setEffectiveTo(LocalDate v){effectiveTo=v;}
    public String getStatus(){return status;} public void setStatus(String v){status=v;}
}
