package com.tuowei.erp.inventory.stock.web;

public record CounterpartyRef(
        String type,
        Long id,
        String code,
        String name,
        String documentNo
) {
}
