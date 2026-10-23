package com.tuowei.erp.workflow;

import com.tuowei.erp.workflow.service.WorkflowRecordCommandService;
import com.tuowei.erp.workflow.service.WorkflowService;
import com.tuowei.erp.workflow.service.WorkflowTaskTransitionService;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:workflow_transition_context;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false"
})
@ActiveProfiles("test")
class WorkflowTaskTransitionSpringContextTest {

    @Autowired
    private WorkflowService workflowService;
    @Autowired
    private WorkflowTaskTransitionService taskTransitionService;
    @Autowired
    private WorkflowRecordCommandService recordCommandService;

    @Test
    void workflowCommandCollaboratorsLoadAsTransactionalSpringBeans() {
        assertThat(workflowService).isNotNull();
        assertThat(taskTransitionService).isNotNull();
        assertThat(recordCommandService).isNotNull();
        assertThat(AopUtils.isAopProxy(workflowService)).isTrue();
        assertThat(AopUtils.isAopProxy(taskTransitionService)).isTrue();
    }
}
