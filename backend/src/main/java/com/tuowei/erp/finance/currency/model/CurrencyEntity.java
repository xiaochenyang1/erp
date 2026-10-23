package com.tuowei.erp.finance.currency.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("md_currency")
public class CurrencyEntity {
    @TableId(type = IdType.ASSIGN_ID) private Long id;
    private String currencyCode;
    private String currencyName;
    private String currencySymbol;
    private Integer decimalPlaces;
    private String status;
    private Integer deletedFlag;
    public Long getId(){return id;} public void setId(Long v){id=v;}
    public String getCurrencyCode(){return currencyCode;} public void setCurrencyCode(String v){currencyCode=v;}
    public String getCurrencyName(){return currencyName;} public void setCurrencyName(String v){currencyName=v;}
    public String getCurrencySymbol(){return currencySymbol;} public void setCurrencySymbol(String v){currencySymbol=v;}
    public Integer getDecimalPlaces(){return decimalPlaces;} public void setDecimalPlaces(Integer v){decimalPlaces=v;}
    public String getStatus(){return status;} public void setStatus(String v){status=v;}
    public Integer getDeletedFlag(){return deletedFlag;} public void setDeletedFlag(Integer v){deletedFlag=v;}
}
