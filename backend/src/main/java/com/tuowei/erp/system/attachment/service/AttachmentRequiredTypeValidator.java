package com.tuowei.erp.system.attachment.service;

import com.tuowei.erp.common.config.AttachmentProperties;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.TreeSet;

/**
 * 拒绝启动时配置了未挂闸门的附件强制留痕业务类型。
 *
 * {@code erp.attachment.required-business-types} 里出现 {@link AttachmentBusinessType#GATED}
 * 之外的值时（拼写错误、或某个单据还没接闸门），过账/提交并不会被拦截。
 * 与其让运维以为管控已生效，不如直接启动失败。
 *
 * 用 {@link InitializingBean} 而不是 {@code ApplicationRunner}：后者在上下文刷新完成、
 * Tomcat 已开始接收请求之后才执行，那段窗口里系统对外表现为「已强制留痕」，
 * 实际并没有拦截。校验必须在 Web 服务器绑定之前完成。
 */
@Component
public class AttachmentRequiredTypeValidator implements InitializingBean {

    private final AttachmentProperties attachmentProperties;

    public AttachmentRequiredTypeValidator(AttachmentProperties attachmentProperties) {
        this.attachmentProperties = attachmentProperties;
    }

    @Override
    public void afterPropertiesSet() {
        Set<String> ungated = new TreeSet<>(attachmentProperties.requiredBusinessTypeSet());
        ungated.removeAll(AttachmentBusinessType.GATED);
        if (ungated.isEmpty()) {
            return;
        }
        throw new IllegalStateException(
                "erp.attachment.required-business-types 配置了未挂附件闸门的业务类型 " + ungated
                        + "，这些类型不会被拦截；允许的类型为 " + new TreeSet<>(AttachmentBusinessType.GATED)
        );
    }
}
