package com.tuowei.erp.issue;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.issue.mapper.ExceptionTicketMapper;
import com.tuowei.erp.issue.model.ExceptionTicketEntity;
import com.tuowei.erp.issue.service.ExceptionTicketNumberService;
import com.tuowei.erp.system.config.mapper.SequenceCounterMapper;
import com.tuowei.erp.system.config.mapper.SequenceRuleMapper;
import com.tuowei.erp.system.config.model.SequenceCounterEntity;
import com.tuowei.erp.system.config.model.SequenceRuleEntity;
import com.tuowei.erp.system.config.service.SequenceNumberGenerator;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExceptionTicketNumberServiceTest {

    private static final AuditMetadata AUDIT = new AuditMetadata(
            7001L,
            71L,
            9L,
            LocalDateTime.of(2026, 6, 30, 10, 0)
    );

    @Mock
    private SequenceNumberGenerator sequenceNumberGenerator;
    @Mock
    private SequenceRuleMapper sequenceRuleMapper;
    @Mock
    private SequenceCounterMapper sequenceCounterMapper;
    @Mock
    private ExceptionTicketMapper exceptionTicketMapper;

    @BeforeAll
    static void initTableInfo() {
        initTableInfo(SequenceRuleEntity.class);
        initTableInfo(SequenceCounterEntity.class);
        initTableInfo(ExceptionTicketEntity.class);
    }

    @Test
    void provisionsMissingRuleForCurrentTenantAndUsesExplicitAudit() {
        when(sequenceRuleMapper.selectOne(any())).thenReturn(null);
        when(exceptionTicketMapper.selectList(any())).thenReturn(List.of());
        when(sequenceNumberGenerator.nextNumber(
                eq("EXCEPTION_TICKET"), eq("异常工单"), eq(AUDIT.now().toLocalDate()), eq(AUDIT)))
                .thenReturn("ET-20260630-0001");

        String number = service().nextTicketNo(AUDIT);

        assertThat(number).isEqualTo("ET-20260630-0001");
        ArgumentCaptor<SequenceRuleEntity> ruleCaptor = ArgumentCaptor.forClass(SequenceRuleEntity.class);
        verify(sequenceRuleMapper).insert(ruleCaptor.capture());
        SequenceRuleEntity rule = ruleCaptor.getValue();
        assertThat(rule.getCompanyId()).isEqualTo(AUDIT.companyId());
        assertThat(rule.getAccountBookId()).isEqualTo(AUDIT.accountBookId());
        assertThat(rule.getBizType()).isEqualTo("EXCEPTION_TICKET");
        assertThat(rule.getPrefix()).isEqualTo("ET-");
        assertThat(rule.getDatePattern()).isEqualTo("yyyyMMdd-");
        assertThat(rule.getSeqLength()).isEqualTo(4);
        assertThat(rule.getStatus()).isEqualTo("ACTIVE");
        verify(sequenceNumberGenerator).nextNumber(
                "EXCEPTION_TICKET", "异常工单", AUDIT.now().toLocalDate(), AUDIT);
    }

    @Test
    void duplicateRuleInsertReloadsConcurrentRuleInsteadOfReenablingIt() {
        SequenceRuleEntity concurrentRule = rule("ACTIVE");
        when(sequenceRuleMapper.selectOne(any())).thenReturn(null, concurrentRule);
        when(sequenceRuleMapper.insert(any(SequenceRuleEntity.class)))
                .thenThrow(new DuplicateKeyException("concurrent insert"));
        when(exceptionTicketMapper.selectList(any())).thenReturn(List.of());
        when(sequenceNumberGenerator.nextNumber(any(), any(), any(), eq(AUDIT)))
                .thenReturn("ET-20260630-0001");

        assertThat(service().nextTicketNo(AUDIT)).isEqualTo("ET-20260630-0001");
        verify(sequenceRuleMapper, org.mockito.Mockito.times(2)).selectOne(any());
    }

    @Test
    void disabledRuleIsNotAutomaticallyReenabled() {
        when(sequenceRuleMapper.selectOne(any())).thenReturn(rule("DISABLED"));

        assertThatThrownBy(() -> service().nextTicketNo(AUDIT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("异常工单编号规则已停用");

        verify(sequenceRuleMapper, never()).insert(any(SequenceRuleEntity.class));
        verify(sequenceNumberGenerator, never()).nextNumber(any(), any(), any(), any());
    }

    @Test
    void raisesExistingCounterToHistoricalMaximumForSameTenantAndPeriod() {
        SequenceRuleEntity rule = rule("ACTIVE");
        SequenceCounterEntity counter = counter(3L);
        when(sequenceRuleMapper.selectOne(any())).thenReturn(rule);
        when(exceptionTicketMapper.selectList(any())).thenReturn(List.of(
                ticket("ET-20260630-0007"),
                ticket("ET-20260630-0012"),
                ticket("ET-20260629-0099"),
                ticket("ET-20260630-0012-invalid")
        ));
        when(sequenceCounterMapper.selectForUpdate(
                AUDIT.companyId(), AUDIT.accountBookId(), "EXCEPTION_TICKET", "20260630-"))
                .thenReturn(counter);
        when(sequenceCounterMapper.updateById(counter)).thenReturn(1);
        when(sequenceNumberGenerator.nextNumber(any(), any(), any(), eq(AUDIT)))
                .thenReturn("ET-20260630-0013");

        assertThat(service().nextTicketNo(AUDIT)).isEqualTo("ET-20260630-0013");

        assertThat(counter.getCurrentValue()).isEqualTo(12L);
        verify(sequenceCounterMapper).updateById(counter);
        verify(sequenceCounterMapper, never()).insert(any(SequenceCounterEntity.class));
    }

    @Test
    void createsCounterAtHistoricalMaximumWhenPeriodCounterIsMissing() {
        SequenceRuleEntity rule = rule("ACTIVE");
        when(sequenceRuleMapper.selectOne(any())).thenReturn(rule);
        when(exceptionTicketMapper.selectList(any())).thenReturn(List.of(ticket("ET-20260630-0017")));
        when(sequenceCounterMapper.selectForUpdate(
                AUDIT.companyId(), AUDIT.accountBookId(), "EXCEPTION_TICKET", "20260630-"))
                .thenReturn(null);
        when(sequenceCounterMapper.insert(any(SequenceCounterEntity.class))).thenReturn(1);
        when(sequenceNumberGenerator.nextNumber(any(), any(), any(), eq(AUDIT)))
                .thenReturn("ET-20260630-0018");

        assertThat(service().nextTicketNo(AUDIT)).isEqualTo("ET-20260630-0018");

        ArgumentCaptor<SequenceCounterEntity> counterCaptor = ArgumentCaptor.forClass(SequenceCounterEntity.class);
        verify(sequenceCounterMapper).insert(counterCaptor.capture());
        SequenceCounterEntity inserted = counterCaptor.getValue();
        assertThat(inserted.getCompanyId()).isEqualTo(AUDIT.companyId());
        assertThat(inserted.getAccountBookId()).isEqualTo(AUDIT.accountBookId());
        assertThat(inserted.getPeriodKey()).isEqualTo("20260630-");
        assertThat(inserted.getCurrentValue()).isEqualTo(17L);
    }

    @Test
    void retriesCounterInsertAfterConcurrentDuplicateKey() {
        SequenceRuleEntity rule = rule("ACTIVE");
        SequenceCounterEntity winner = counter(17L);
        when(sequenceRuleMapper.selectOne(any())).thenReturn(rule);
        when(exceptionTicketMapper.selectList(any())).thenReturn(List.of(ticket("ET-20260630-0017")));
        when(sequenceCounterMapper.selectForUpdate(
                AUDIT.companyId(), AUDIT.accountBookId(), "EXCEPTION_TICKET", "20260630-"))
                .thenReturn(null, winner);
        when(sequenceCounterMapper.insert(any(SequenceCounterEntity.class)))
                .thenThrow(new DuplicateKeyException("concurrent insert"));
        when(sequenceNumberGenerator.nextNumber(any(), any(), any(), eq(AUDIT)))
                .thenReturn("ET-20260630-0018");

        assertThat(service().nextTicketNo(AUDIT)).isEqualTo("ET-20260630-0018");
        verify(sequenceCounterMapper, org.mockito.Mockito.times(2)).selectForUpdate(
                AUDIT.companyId(), AUDIT.accountBookId(), "EXCEPTION_TICKET", "20260630-");
        verify(sequenceCounterMapper).insert(any(SequenceCounterEntity.class));
    }

    @Test
    void rejectsHistoricalMaximumAtConfiguredSequenceWidthLimit() {
        SequenceRuleEntity rule = rule("ACTIVE");
        when(sequenceRuleMapper.selectOne(any())).thenReturn(rule);
        when(exceptionTicketMapper.selectList(any())).thenReturn(List.of(ticket("ET-20260630-9999")));

        assertThatThrownBy(() -> service().nextTicketNo(AUDIT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("异常工单编号流水已达到当前规则上限，请调整日期或编号规则");

        verify(sequenceCounterMapper, never()).selectForUpdate(any(), any(), any(), any());
        verify(sequenceNumberGenerator, never()).nextNumber(any(), any(), any(), any());
    }

    private ExceptionTicketNumberService service() {
        return new ExceptionTicketNumberService(
                sequenceNumberGenerator,
                sequenceRuleMapper,
                sequenceCounterMapper,
                exceptionTicketMapper
        );
    }

    private static SequenceRuleEntity rule(String status) {
        SequenceRuleEntity rule = new SequenceRuleEntity();
        rule.setCompanyId(AUDIT.companyId());
        rule.setAccountBookId(AUDIT.accountBookId());
        rule.setBizType("EXCEPTION_TICKET");
        rule.setPrefix("ET-");
        rule.setDatePattern("yyyyMMdd-");
        rule.setSeqLength(4);
        rule.setCurrentValue(0L);
        rule.setStatus(status);
        rule.setVersion(0);
        return rule;
    }

    private static SequenceCounterEntity counter(long currentValue) {
        SequenceCounterEntity counter = new SequenceCounterEntity();
        counter.setCompanyId(AUDIT.companyId());
        counter.setAccountBookId(AUDIT.accountBookId());
        counter.setBizType("EXCEPTION_TICKET");
        counter.setPeriodKey("20260630-");
        counter.setCurrentValue(currentValue);
        counter.setVersion(0);
        return counter;
    }

    private static ExceptionTicketEntity ticket(String ticketNo) {
        ExceptionTicketEntity ticket = new ExceptionTicketEntity();
        ticket.setCompanyId(AUDIT.companyId());
        ticket.setAccountBookId(AUDIT.accountBookId());
        ticket.setTicketNo(ticketNo);
        return ticket;
    }

    private static void initTableInfo(Class<?> entityClass) {
        if (TableInfoHelper.getTableInfo(entityClass) != null) {
            return;
        }
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(
                new MybatisConfiguration(), entityClass.getName());
        assistant.setCurrentNamespace(entityClass.getName());
        TableInfoHelper.initTableInfo(assistant, entityClass);
    }
}
