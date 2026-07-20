package com.tuowei.erp.testsupport;

import org.springframework.security.test.context.support.WithSecurityContext;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@WithSecurityContext(factory = WithErpUserSecurityContextFactory.class)
public @interface WithErpUser {

    long userId() default 1L;

    long companyId() default 1L;

    long accountBookId() default 1L;

    long deptId() default 1L;

    long postId() default 1L;

    String username() default "admin";

    String realName() default "系统管理员";

    String[] authorities() default {};

    boolean allScope() default true;

    boolean deptScoped() default false;

    boolean postScoped() default false;

    boolean selfScoped() default false;

    long[] warehouseIds() default {};
}
