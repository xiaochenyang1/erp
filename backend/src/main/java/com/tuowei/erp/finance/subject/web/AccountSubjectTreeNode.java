package com.tuowei.erp.finance.subject.web;

import java.util.ArrayList;
import java.util.List;

public class AccountSubjectTreeNode {

    private final Long id;
    private final String subjectCode;
    private final String subjectName;
    private final Long parentId;
    private final String subjectType;
    private final String balanceDirection;
    private final String status;
    private final String remark;
    private final List<AccountSubjectTreeNode> children = new ArrayList<>();

    public AccountSubjectTreeNode(
            Long id,
            String subjectCode,
            String subjectName,
            Long parentId,
            String subjectType,
            String balanceDirection,
            String status,
            String remark
    ) {
        this.id = id;
        this.subjectCode = subjectCode;
        this.subjectName = subjectName;
        this.parentId = parentId;
        this.subjectType = subjectType;
        this.balanceDirection = balanceDirection;
        this.status = status;
        this.remark = remark;
    }

    public Long getId() { return id; }
    public String getSubjectCode() { return subjectCode; }
    public String getSubjectName() { return subjectName; }
    public Long getParentId() { return parentId; }
    public String getSubjectType() { return subjectType; }
    public String getBalanceDirection() { return balanceDirection; }
    public String getStatus() { return status; }
    public String getRemark() { return remark; }
    public List<AccountSubjectTreeNode> getChildren() { return children; }
}
