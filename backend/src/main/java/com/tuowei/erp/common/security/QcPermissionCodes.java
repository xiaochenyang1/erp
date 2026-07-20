package com.tuowei.erp.common.security;

public interface QcPermissionCodes {

    String QC_INSPECTION_VIEW = "qc:inspection:view";
    String QC_INSPECTION_CREATE = "qc:inspection:create";
    String QC_INSPECTION_UPDATE = "qc:inspection:update";
    String QC_INSPECTION_SUBMIT = "qc:inspection:submit";
    String QC_INSPECTION_JUDGE = "qc:inspection:judge";
    String QC_INSPECTION_CANCEL = "qc:inspection:cancel";

    String HAS_QC_INSPECTION_VIEW = "hasAuthority('" + QC_INSPECTION_VIEW + "')";
    String HAS_QC_INSPECTION_CREATE = "hasAuthority('" + QC_INSPECTION_CREATE + "')";
    String HAS_QC_INSPECTION_UPDATE = "hasAuthority('" + QC_INSPECTION_UPDATE + "')";
    String HAS_QC_INSPECTION_SUBMIT = "hasAuthority('" + QC_INSPECTION_SUBMIT + "')";
    String HAS_QC_INSPECTION_JUDGE = "hasAuthority('" + QC_INSPECTION_JUDGE + "')";
    String HAS_QC_INSPECTION_CANCEL = "hasAuthority('" + QC_INSPECTION_CANCEL + "')";
}
