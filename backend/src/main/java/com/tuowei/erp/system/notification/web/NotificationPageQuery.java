package com.tuowei.erp.system.notification.web;

public class NotificationPageQuery {

    private Integer pageNo = 1;
    private Integer pageSize = 20;
    private Boolean unreadOnly;
    private String category;
    private String notificationType;

    public Integer getPageNo() { return pageNo; }
    public void setPageNo(Integer pageNo) { this.pageNo = pageNo; }
    public Integer getPageSize() { return pageSize; }
    public void setPageSize(Integer pageSize) { this.pageSize = pageSize; }
    public Boolean getUnreadOnly() { return unreadOnly; }
    public void setUnreadOnly(Boolean unreadOnly) { this.unreadOnly = unreadOnly; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getNotificationType() { return notificationType; }
    public void setNotificationType(String notificationType) { this.notificationType = notificationType; }
}
