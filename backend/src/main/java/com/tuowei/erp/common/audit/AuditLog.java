package com.tuowei.erp.common.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作审计注解
 * 标记需要记录审计日志的方法
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditLog {

    /**
     * 业务模块
     */
    String module();

    /**
     * 操作类型
     */
    OperationType operation();

    /**
     * 操作描述（支持SpEL表达式）
     * 例如: "删除用户: #{#username}"
     */
    String description() default "";

    /**
     * 是否记录参数
     */
    boolean logParams() default true;

    /**
     * 是否记录返回值
     */
    boolean logResult() default false;

    enum OperationType {
        CREATE("新增"),
        UPDATE("修改"),
        DELETE("删除"),
        QUERY("查询"),
        EXPORT("导出"),
        IMPORT("导入"),
        APPROVE("审批"),
        REJECT("驳回"),
        LOGIN("登录"),
        LOGOUT("登出"),
        OTHER("其他");

        private final String label;

        OperationType(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }
}
