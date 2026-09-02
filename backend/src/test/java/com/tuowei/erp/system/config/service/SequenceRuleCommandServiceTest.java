package com.tuowei.erp.system.config.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuowei.erp.common.exception.BusinessConflictException;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.system.config.mapper.SequenceCounterMapper;
import com.tuowei.erp.system.config.mapper.SequenceRuleMapper;
import com.tuowei.erp.system.config.model.SequenceRuleEntity;
import com.tuowei.erp.system.config.web.SequenceRuleCreateRequest;
import com.tuowei.erp.system.config.web.SequenceRuleResponse;
import com.tuowei.erp.system.config.web.SequenceRuleUpdateRequest;
import com.tuowei.erp.system.log.service.SystemLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SequenceRuleCommandServiceTest {

    private static final AuditMetadata AUDIT = new AuditMetadata(
            1001L, 101L, 202L, LocalDateTime.of(2026, 8, 21, 19, 30)
    );
    private static final Long RULE_ID = 7001L;

    @Mock private SequenceRuleMapper sequenceRuleMapper;
    @Mock private SequenceCounterMapper sequenceCounterMapper;
    @Mock private AuditMetadataFactory auditMetadataFactory;
    @Mock private SystemLogService systemLogService;
    @Mock private SequenceRuleQueryService sequenceRuleQueryService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        lenient().when(auditMetadataFactory.current()).thenReturn(AUDIT);
    }

    @Test
    void createValidatesDefinitionBuildsTenantAuditedRuleAndRecordsSnapshotAudit() throws Exception {
        when(sequenceRuleMapper.insert(any(SequenceRuleEntity.class))).thenAnswer(invocation -> {
            SequenceRuleEntity entity = invocation.getArgument(0);
            entity.setId(RULE_ID);
            return 1;
        });
        when(sequenceRuleQueryService.toResponse(any(SequenceRuleEntity.class)))
                .thenReturn(response("ACTIVE", 12L));

        SequenceRuleResponse actual = service().create(new SequenceRuleCreateRequest(
                "SALES_ORDER", " SO- ", "yyyyMMdd", 5, 12L
        ));

        assertThat(actual.id()).isEqualTo(RULE_ID);
        ArgumentCaptor<SequenceRuleEntity> entityCaptor = ArgumentCaptor.forClass(SequenceRuleEntity.class);
        verify(sequenceRuleMapper).insert(entityCaptor.capture());
        SequenceRuleEntity inserted = entityCaptor.getValue();
        assertThat(inserted.getCompanyId()).isEqualTo(AUDIT.companyId());
        assertThat(inserted.getAccountBookId()).isEqualTo(AUDIT.accountBookId());
        assertThat(inserted.getBizType()).isEqualTo("SALES_ORDER");
        assertThat(inserted.getPrefix()).isEqualTo(" SO- ");
        assertThat(inserted.getDatePattern()).isEqualTo("yyyyMMdd");
        assertThat(inserted.getSeqLength()).isEqualTo(5);
        assertThat(inserted.getCurrentValue()).isEqualTo(12L);
        assertThat(inserted.getStatus()).isEqualTo("ACTIVE");
        assertThat(inserted.getCreatedBy()).isEqualTo(AUDIT.userId());
        assertThat(inserted.getCreatedTime()).isEqualTo(AUDIT.now());
        assertThat(inserted.getUpdatedBy()).isEqualTo(AUDIT.userId());
        assertThat(inserted.getUpdatedTime()).isEqualTo(AUDIT.now());
        assertThat(inserted.getVersion()).isZero();

        ArgumentCaptor<String> snapshotCaptor = ArgumentCaptor.forClass(String.class);
        verify(systemLogService).recordAudit(
                org.mockito.ArgumentMatchers.eq("CONFIG"),
                org.mockito.ArgumentMatchers.eq("SEQUENCE_RULE"),
                org.mockito.ArgumentMatchers.eq(RULE_ID),
                org.mockito.ArgumentMatchers.eq("SALES_ORDER"),
                org.mockito.ArgumentMatchers.eq("CREATE"),
                org.mockito.ArgumentMatchers.eq(AUDIT.userId()),
                org.mockito.ArgumentMatchers.isNull(),
                snapshotCaptor.capture(),
                org.mockito.ArgumentMatchers.eq("创建编号规则"),
                org.mockito.ArgumentMatchers.eq(AUDIT.now())
        );
        assertThat(snapshotCaptor.getValue())
                .contains("\"bizType\":\"SALES_ORDER\"")
                .contains("\"currentValue\":12")
                .contains("\"status\":\"ACTIVE\"");
    }

    @Test
    void createDefaultsNullCurrentValueToZeroAndRejectsInvalidDefinitionsBeforeInsert() {
        when(sequenceRuleMapper.insert(any(SequenceRuleEntity.class))).thenReturn(1);
        when(sequenceRuleQueryService.toResponse(any(SequenceRuleEntity.class)))
                .thenReturn(response("ACTIVE", 0L));

        service().create(new SequenceRuleCreateRequest(
                "SALES_ORDER", "SO-", "yyyyMMdd", 3, null
        ));
        ArgumentCaptor<SequenceRuleEntity> captor = ArgumentCaptor.forClass(SequenceRuleEntity.class);
        verify(sequenceRuleMapper).insert(captor.capture());
        assertThat(captor.getValue().getCurrentValue()).isZero();

        assertThatThrownBy(() -> service().create(new SequenceRuleCreateRequest(
                "BAD", "B-", "yyyy-MM-dd-#", 3, 0L
        ))).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("datePattern不是有效的日期格式");
        assertThatThrownBy(() -> service().create(new SequenceRuleCreateRequest(
                "BAD", "B-", "yyyyMMdd", 2, 100L
        ))).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("seqLength不能小于当前流水位数");
    }

    @Test
    void updateProtectsGeneratedCountersAndUsesTenantScopedMaximum() {
        SequenceRuleEntity entity = rule(12L);
        when(sequenceRuleQueryService.requireSequenceRule(RULE_ID)).thenReturn(entity);
        when(sequenceCounterMapper.selectMaxCurrentValue(
                AUDIT.companyId(), AUDIT.accountBookId(), "SALES_ORDER"
        )).thenReturn(123L);

        assertThatThrownBy(() -> service().update(RULE_ID, new SequenceRuleUpdateRequest(
                "SO-", "yyyyMMdd", 3, 122L
        ))).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("currentValue不能小于已产生的最大流水");
        verify(sequenceRuleMapper, never()).updateById(any(SequenceRuleEntity.class));

        assertThatThrownBy(() -> service().update(RULE_ID, new SequenceRuleUpdateRequest(
                "SO-", "yyyyMM", 5, 123L
        ))).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("已产生编号的规则不能修改日期格式");

        assertThatThrownBy(() -> service().update(RULE_ID, new SequenceRuleUpdateRequest(
                "SO-", "yyyyMMdd", 2, 123L
        ))).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("seqLength不能小于已产生的最大流水位数");
    }

    @Test
    void updateAuditsChangeAndStatusCommandsRecordExpectedActions() {
        SequenceRuleEntity entity = rule(12L);
        when(sequenceRuleQueryService.requireSequenceRule(RULE_ID)).thenReturn(entity);
        when(sequenceCounterMapper.selectMaxCurrentValue(any(), any(), any())).thenReturn(null);
        when(sequenceRuleMapper.updateById(entity)).thenReturn(1);
        when(sequenceRuleQueryService.toResponse(entity)).thenAnswer(invocation -> response(entity.getStatus(), entity.getCurrentValue()));

        service().update(RULE_ID, new SequenceRuleUpdateRequest("SO2-", "yyyyMMdd", 6, 13L));
        service().disable(RULE_ID);
        service().enable(RULE_ID);

        ArgumentCaptor<String> actionCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> snapshotCaptor = ArgumentCaptor.forClass(String.class);
        verify(systemLogService, org.mockito.Mockito.times(3)).recordAudit(
                org.mockito.ArgumentMatchers.eq("CONFIG"),
                org.mockito.ArgumentMatchers.eq("SEQUENCE_RULE"),
                org.mockito.ArgumentMatchers.eq(RULE_ID),
                org.mockito.ArgumentMatchers.eq("SALES_ORDER"),
                actionCaptor.capture(),
                org.mockito.ArgumentMatchers.eq(AUDIT.userId()),
                org.mockito.ArgumentMatchers.isNull(),
                snapshotCaptor.capture(),
                any(String.class),
                org.mockito.ArgumentMatchers.eq(AUDIT.now())
        );
        assertThat(actionCaptor.getAllValues()).containsExactly("UPDATE", "DISABLE", "ENABLE");
        assertThat(snapshotCaptor.getAllValues().get(0)).contains("\"before\"").contains("\"after\"");
        assertThat(snapshotCaptor.getAllValues().get(1)).contains("\"status\":\"ACTIVE\"").contains("\"status\":\"DISABLED\"");
    }

    @Test
    void updateStopsOnOptimisticConflictBeforeAuditOrResponse() {
        SequenceRuleEntity entity = rule(12L);
        when(sequenceRuleQueryService.requireSequenceRule(RULE_ID)).thenReturn(entity);
        when(sequenceCounterMapper.selectMaxCurrentValue(any(), any(), any())).thenReturn(null);
        when(sequenceRuleMapper.updateById(entity)).thenReturn(0);

        assertThatThrownBy(() -> service().update(RULE_ID, new SequenceRuleUpdateRequest(
                "SO2-", "yyyyMMdd", 5, 13L
        ))).isInstanceOf(BusinessConflictException.class)
                .hasMessage("编号规则已被其他操作修改，请刷新后重试");
        verify(systemLogService, never()).recordAudit(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        verify(sequenceRuleQueryService, never()).toResponse(entity);
    }

    @Test
    void commandLookupFailureDoesNotTouchWriteOrAuditCollaborators() {
        when(sequenceRuleQueryService.requireSequenceRule(RULE_ID))
                .thenThrow(new IllegalArgumentException("编号规则不存在"));

        assertThatThrownBy(() -> service().disable(RULE_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("编号规则不存在");

        verifyNoInteractions(sequenceRuleMapper, sequenceCounterMapper, systemLogService);
    }

    private SequenceRuleCommandService service() {
        return new SequenceRuleCommandService(
                sequenceRuleMapper, sequenceCounterMapper, auditMetadataFactory,
                systemLogService, objectMapper, sequenceRuleQueryService
        );
    }

    private SequenceRuleEntity rule(long currentValue) {
        SequenceRuleEntity entity = new SequenceRuleEntity();
        entity.setId(RULE_ID);
        entity.setCompanyId(AUDIT.companyId());
        entity.setAccountBookId(AUDIT.accountBookId());
        entity.setBizType("SALES_ORDER");
        entity.setPrefix("SO-");
        entity.setDatePattern("yyyyMMdd");
        entity.setSeqLength(5);
        entity.setCurrentValue(currentValue);
        entity.setStatus("ACTIVE");
        entity.setVersion(0);
        return entity;
    }

    private SequenceRuleResponse response(String status, Long currentValue) {
        return new SequenceRuleResponse(
                RULE_ID, AUDIT.companyId(), AUDIT.accountBookId(), "SALES_ORDER",
                "SO-", "yyyyMMdd", 5, currentValue, status
        );
    }
}
