package com.tuowei.erp.purchase.requisition.web;
public class PurchaseRequisitionPageQuery {
    private Long pageNo=1L; private Long pageSize=20L; private String status; private String keyword;
    public Long getPageNo(){return pageNo;} public void setPageNo(Long pageNo){this.pageNo=pageNo;}
    public Long getPageSize(){return pageSize;} public void setPageSize(Long pageSize){this.pageSize=pageSize;}
    public String getStatus(){return status;} public void setStatus(String status){this.status=status;}
    public String getKeyword(){return keyword;} public void setKeyword(String keyword){this.keyword=keyword;}
}
