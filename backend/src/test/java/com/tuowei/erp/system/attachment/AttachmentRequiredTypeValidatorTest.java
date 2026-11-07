package com.tuowei.erp.system.attachment;

import com.tuowei.erp.common.config.AttachmentProperties;
import com.tuowei.erp.system.attachment.service.AttachmentRequiredTypeValidator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.ApplicationRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 附件强制留痕配置校验器：配了没挂闸门的业务类型必须启动失败，
 * 而不是静默不生效。
 */
class AttachmentRequiredTypeValidatorTest {

    @Test
    void acceptsRequiredTypesThatAllHaveGates() {
        AttachmentRequiredTypeValidator validator = validatorFor("EXPENSE,MANUAL_VOUCHER,PURCHASE_ORDER");

        assertThatCode(() -> validator.afterPropertiesSet()).doesNotThrowAnyException();
    }

    @Test
    void rejectsRequiredTypeThatHasNoGate() {
        AttachmentRequiredTypeValidator validator = validatorFor("EXPENSE,PAYMENT");

        assertThatThrownBy(() -> validator.afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PAYMENT");
    }

    @Test
    void rejectsMisspelledRequiredType() {
        AttachmentRequiredTypeValidator validator = validatorFor("EXPENSE,SALES_ORDR");

        assertThatThrownBy(() -> validator.afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SALES_ORDR");
    }

    @Test
    void errorMessageListsEveryAllowedBusinessTypeSoOperatorsCanFixTheConfig() {
        AttachmentRequiredTypeValidator validator = validatorFor("RECEIPT");

        assertThatThrownBy(() -> validator.afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .satisfies(error -> assertThat(error.getMessage())
                        .contains("EXPENSE")
                        .contains("MANUAL_VOUCHER")
                        .contains("PRODUCTION_ORDER")
                        .contains("erp.attachment.required-business-types"));
    }

    @Test
    void normalizesLowercaseAndWhitespaceBeforeValidating() {
        AttachmentRequiredTypeValidator validator = validatorFor("  expense , manual_voucher  ");

        assertThatCode(() -> validator.afterPropertiesSet()).doesNotThrowAnyException();
    }

    @Test
    void reportsEveryUngatedTypeAtOnceInsteadOfFailingOnTheFirst() {
        AttachmentRequiredTypeValidator validator = validatorFor("PAYMENT,EXPENSE,RECEIPT");

        assertThatThrownBy(() -> validator.afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .satisfies(error -> assertThat(error.getMessage())
                        .contains("PAYMENT")
                        .contains("RECEIPT"));
    }

    /**
     * 必须在上下文刷新期失败，而不是等 Web 服务器已监听后才抛错。
     * 用 ApplicationRunner 实现时，Tomcat 会先接收一段时间的请求，
     * 期间系统对外表现为「已强制留痕」，实际并没有拦截。
     */
    @Test
    void failsDuringContextRefreshRatherThanAfterTheWebServerAcceptsTraffic() {
        assertThat(InitializingBean.class.isAssignableFrom(AttachmentRequiredTypeValidator.class))
                .as("配置校验必须在 Bean 初始化期完成，避免应用先开始服务再退出")
                .isTrue();
        assertThat(ApplicationRunner.class.isAssignableFrom(AttachmentRequiredTypeValidator.class))
                .as("不应再依赖 ApplicationRunner，它在 Tomcat 启动之后才执行")
                .isFalse();
    }

    private AttachmentRequiredTypeValidator validatorFor(String requiredBusinessTypes) {
        return new AttachmentRequiredTypeValidator(
                new AttachmentProperties("./data/attachments", 1024L, requiredBusinessTypes, 1)
        );
    }
}
