package com.tuowei.erp.finance.receivable;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.finance.receivable.mapper.ReceivableMapper;
import com.tuowei.erp.finance.receivable.model.ReceivableEntity;
import com.tuowei.erp.finance.receivable.service.ReceivableQueryService;
import com.tuowei.erp.finance.receivable.web.ReceivablePageQuery;
import com.tuowei.erp.finance.receivable.web.ReceivableResponse;
import com.tuowei.erp.finance.settlement.service.FinanceSettlementScopeSupport;
import com.tuowei.erp.masterdata.customer.mapper.CustomerMapper;
import com.tuowei.erp.masterdata.customer.model.CustomerEntity;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReceivableQueryServiceTest {

    private static final AuditMetadata AUDIT = new AuditMetadata(
            9001L,
            7L,
            9L,
            LocalDateTime.of(2026, 7, 23, 10, 0)
    );
    private static final LocalDateTime CREATED_TIME = LocalDateTime.of(2026, 5, 18, 9, 0);
    private static final LocalDateTime UPDATED_TIME = LocalDateTime.of(2026, 5, 19, 10, 30);

    @Mock
    private ReceivableMapper receivableMapper;

    @Mock
    private CustomerMapper customerMapper;

    @Mock
    private FinanceSettlementScopeSupport financeSettlementScopeSupport;

    @Mock
    private AuditMetadataFactory auditMetadataFactory;

    private ReceivableQueryService service;

    @BeforeAll
    static void initTableInfo() {
        initTableInfo(ReceivableEntity.class);
        initTableInfo(CustomerEntity.class);
    }

    @BeforeEach
    void setUp() {
        service = new ReceivableQueryService(
                receivableMapper,
                customerMapper,
                financeSettlementScopeSupport,
                auditMetadataFactory
        );
    }

    @Test
    void listBatchLoadsTenantScopedNamesAndPreservesFinanceContract() {
        ReceivableEntity first = receivable(1001L, 5001L, "AR-2026-001");
        ReceivableEntity second = receivable(1002L, 5002L, "AR-2026-002");
        stubScopedList(List.of(first, second));
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(customerMapper.selectList(any())).thenReturn(List.of(
                customer(5001L, AUDIT.companyId(), AUDIT.accountBookId(), "当前账套客户"),
                customer(5002L, AUDIT.companyId(), 99L, "其他账套客户")
        ));

        ReceivablePageQuery query = new ReceivablePageQuery();
        query.setReceivableNo("  AR-2026  ");
        PageResponse<ReceivableResponse> response = service.list(query);

        assertThat(response.records()).hasSize(2);
        assertThat(response.records().get(0).customerName()).isEqualTo("当前账套客户");
        assertThat(response.records().get(1).customerName()).isNull();
        assertThat(response.records().get(0).status()).isEqualTo("PARTIALLY_SETTLED");
        assertThat(response.records().get(0).createdTime()).isEqualTo(CREATED_TIME);
        assertThat(response.records().get(0).updatedTime()).isEqualTo(UPDATED_TIME);

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaQueryWrapper<ReceivableEntity>> receivableQueryCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(receivableMapper).selectPage(any(), receivableQueryCaptor.capture());
        assertThat(receivableQueryCaptor.getValue().getSqlSegment().toLowerCase(Locale.ROOT))
                .contains("receivable_no")
                .contains("like");
        assertThat(receivableQueryCaptor.getValue().getParamNameValuePairs().values())
                .contains("%AR-2026%")
                .doesNotContain("%  AR-2026  %");

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaQueryWrapper<CustomerEntity>> customerQueryCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(customerMapper, times(1)).selectList(customerQueryCaptor.capture());
        assertMasterdataScope(customerQueryCaptor.getValue());
    }

    @Test
    void detailLoadsCustomerNameOnlyAfterScopeCheck() {
        ReceivableEntity entity = receivable(1003L, 5003L, "AR-2026-003");
        when(receivableMapper.selectById(1003L)).thenReturn(entity);
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(customerMapper.selectList(any())).thenReturn(List.of(
                customer(5003L, AUDIT.companyId(), AUDIT.accountBookId(), "详情客户")
        ));

        ReceivableResponse response = service.detail(1003L);

        verify(financeSettlementScopeSupport).assertCanViewReceivable(entity);
        assertThat(response.customerName()).isEqualTo("详情客户");
        assertThat(response.status()).isEqualTo("PARTIALLY_SETTLED");
        assertThat(response.createdTime()).isEqualTo(CREATED_TIME);
        assertThat(response.updatedTime()).isEqualTo(UPDATED_TIME);

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaQueryWrapper<CustomerEntity>> customerQueryCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(customerMapper).selectList(customerQueryCaptor.capture());
        assertMasterdataScope(customerQueryCaptor.getValue());
    }

    @Test
    void deniedDetailDoesNotLoadCustomerName() {
        ReceivableEntity entity = receivable(1004L, 5004L, "AR-2026-004");
        when(receivableMapper.selectById(1004L)).thenReturn(entity);
        doThrow(new AccessDeniedException("denied"))
                .when(financeSettlementScopeSupport).assertCanViewReceivable(entity);

        assertThatThrownBy(() -> service.detail(1004L)).isInstanceOf(AccessDeniedException.class);

        verify(customerMapper, never()).selectList(any());
        verify(auditMetadataFactory, never()).current();
    }

    @Test
    void blankReceivableNumberDoesNotAddLikeCondition() {
        stubScopedList(List.of());
        ReceivablePageQuery query = new ReceivablePageQuery();
        query.setReceivableNo("   ");

        service.list(query);

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaQueryWrapper<ReceivableEntity>> queryCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(receivableMapper).selectPage(any(), queryCaptor.capture());
        assertThat(queryCaptor.getValue().getSqlSegment().toLowerCase(Locale.ROOT))
                .doesNotContain("receivable_no");
        verify(customerMapper, never()).selectList(any());
        verify(auditMetadataFactory, never()).current();
    }

    private void stubScopedList(List<ReceivableEntity> records) {
        when(financeSettlementScopeSupport.applyReceivableScope(any()))
                .thenAnswer(invocation -> invocation.getArgument(0, LambdaQueryWrapper.class));
        when(receivableMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            Page<ReceivableEntity> page = invocation.getArgument(0);
            page.setRecords(records);
            page.setTotal(records.size());
            return page;
        });
    }

    private void assertMasterdataScope(LambdaQueryWrapper<CustomerEntity> wrapper) {
        assertThat(wrapper.getSqlSegment().toLowerCase(Locale.ROOT))
                .contains("company_id")
                .contains("account_book_id")
                .contains("deleted_flag")
                .contains("id");
        assertThat(wrapper.getParamNameValuePairs().values())
                .contains(AUDIT.companyId(), AUDIT.accountBookId(), 0);
    }

    private ReceivableEntity receivable(Long id, Long customerId, String receivableNo) {
        ReceivableEntity entity = new ReceivableEntity();
        entity.setId(id);
        entity.setCompanyId(AUDIT.companyId());
        entity.setAccountBookId(AUDIT.accountBookId());
        entity.setReceivableNo(receivableNo);
        entity.setCustomerId(customerId);
        entity.setBizDate(LocalDate.of(2026, 5, 18));
        entity.setSourceType("SALES_DELIVERY");
        entity.setSourceId(8001L);
        entity.setSourceNo("SD-2026-001");
        entity.setDirection("INCREASE");
        entity.setOriginalAmount(new BigDecimal("100.00"));
        entity.setSettledAmount(new BigDecimal("20.00"));
        entity.setStatus("PARTIALLY_SETTLED");
        entity.setDeletedFlag(0);
        entity.setCreatedTime(CREATED_TIME);
        entity.setUpdatedTime(UPDATED_TIME);
        return entity;
    }

    private CustomerEntity customer(Long id, Long companyId, Long accountBookId, String name) {
        CustomerEntity entity = new CustomerEntity();
        entity.setId(id);
        entity.setCompanyId(companyId);
        entity.setAccountBookId(accountBookId);
        entity.setCustomerName(name);
        entity.setDeletedFlag(0);
        return entity;
    }

    private static void initTableInfo(Class<?> entityClass) {
        if (TableInfoHelper.getTableInfo(entityClass) != null) {
            return;
        }
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), entityClass.getName());
        assistant.setCurrentNamespace(entityClass.getName());
        TableInfoHelper.initTableInfo(assistant, entityClass);
    }
}
