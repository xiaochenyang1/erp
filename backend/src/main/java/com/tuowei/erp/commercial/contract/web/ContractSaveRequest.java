package com.tuowei.erp.commercial.contract.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

public record ContractSaveRequest(
        @NotBlank(message = "contractType不能为空") String contractType,
        Long customerId,
        Long supplierId,
        @NotBlank(message = "contractName不能为空") String contractName,
        @NotNull(message = "signedDate不能为空") LocalDate signedDate,
        @NotNull(message = "effectiveFrom不能为空") LocalDate effectiveFrom,
        LocalDate effectiveTo,
        String remark,
        @NotEmpty(message = "合同明细不能为空") @Valid List<ContractLineRequest> lines
) {
    @AssertTrue(message = "合同类型仅支持 SALES 或 PURCHASE，且必须填写对应客户或供应商")
    public boolean isPartyValid() {
        if (contractType == null) return true;
        String type = contractType.trim().toUpperCase();
        return ("SALES".equals(type) && customerId != null && supplierId == null)
                || ("PURCHASE".equals(type) && supplierId != null && customerId == null);
    }

    @AssertTrue(message = "effectiveTo不能早于effectiveFrom")
    public boolean isDateRangeValid() {
        return effectiveFrom == null || effectiveTo == null || !effectiveTo.isBefore(effectiveFrom);
    }
}
