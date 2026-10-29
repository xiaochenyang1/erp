package com.tuowei.erp.common.security;

public interface ContractPermissionCodes {

    String CONTRACT_VIEW = "contract:view";
    String CONTRACT_MANAGE = "contract:manage";
    String CONTRACT_APPROVE = "contract:approve";

    String HAS_CONTRACT_VIEW = "hasAuthority('" + CONTRACT_VIEW + "')";
    String HAS_CONTRACT_MANAGE = "hasAuthority('" + CONTRACT_MANAGE + "')";
    String HAS_CONTRACT_APPROVE = "hasAuthority('" + CONTRACT_APPROVE + "')";
}
