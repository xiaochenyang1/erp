package com.tuowei.erp.masterdata.customer.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.masterdata.customer.mapper.CustomerMapper;
import com.tuowei.erp.masterdata.customer.model.CustomerEntity;
import com.tuowei.erp.masterdata.customer.web.CustomerPageQuery;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings({"unchecked", "rawtypes"})
class CustomerQueryServiceTest {

    private static final AuditMetadata AUDIT = new AuditMetadata(
            7101L,
            8101L,
            9101L,
            LocalDateTime.of(2026, 8, 20, 16, 0)
    );
    private static final Long CUSTOMER_ID = 101L;

    private final CustomerMapper customerMapper = mock(CustomerMapper.class);
    private final AuditMetadataFactory auditMetadataFactory = mock(AuditMetadataFactory.class);

    @BeforeAll
    static void initTableInfo() {
        if (TableInfoHelper.getTableInfo(CustomerEntity.class) != null) {
            return;
        }
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(
                new MybatisConfiguration(),
                CustomerEntity.class.getName()
        );
        assistant.setCurrentNamespace(CustomerEntity.class.getName());
        TableInfoHelper.initTableInfo(assistant, CustomerEntity.class);
    }

    @Test
    void listNormalizesFiltersClampsPaginationScopesTenantAndMapsResponse() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(customerMapper.selectPage(any(Page.class), any())).thenAnswer(invocation -> {
            Page<CustomerEntity> page = invocation.getArgument(0);
            page.setTotal(1L);
            page.setRecords(List.of(customer(AUDIT.accountBookId())));
            return page;
        });
        CustomerPageQuery query = new CustomerPageQuery();
        query.setPageNo(0);
        query.setPageSize(999);
        query.setKeyword("  C-001  ");
        query.setType(" COMPANY ");
        query.setStatus(" active ");
        query.setSettlementMethod(" MONTHLY ");

        var response = service().list(query);

        assertThat(response.pageNo()).isEqualTo(1L);
        assertThat(response.pageSize()).isEqualTo(200L);
        assertThat(response.total()).isEqualTo(1L);
        assertThat(response.records()).singleElement().satisfies(record -> {
            assertThat(record.id()).isEqualTo(CUSTOMER_ID);
            assertThat(record.customerCode()).isEqualTo("C-001");
            assertThat(record.customerName()).isEqualTo("华东客户");
            assertThat(record.creditLimit()).isEqualByComparingTo("10000.00");
            assertThat(record.creditPeriod()).isEqualTo(30);
        });

        ArgumentCaptor<Page<CustomerEntity>> pageCaptor = ArgumentCaptor.forClass(Page.class);
        ArgumentCaptor<LambdaQueryWrapper<CustomerEntity>> wrapperCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(customerMapper).selectPage(pageCaptor.capture(), wrapperCaptor.capture());
        assertThat(pageCaptor.getValue().getCurrent()).isEqualTo(1L);
        assertThat(pageCaptor.getValue().getSize()).isEqualTo(200L);
        assertThat(wrapperCaptor.getValue().getSqlSegment().toLowerCase(Locale.ROOT))
                .contains("company_id")
                .contains("account_book_id")
                .contains("deleted_flag")
                .contains("customer_code")
                .contains("customer_name")
                .contains("contact_name")
                .contains("customer_type")
                .contains("status")
                .contains("settlement_method")
                .contains("order by customer_code asc");
        assertThat(wrapperCaptor.getValue().getParamNameValuePairs().values())
                .contains(
                        AUDIT.companyId(),
                        AUDIT.accountBookId(),
                        "%C-001%",
                        "COMPANY",
                        "ACTIVE",
                        "MONTHLY"
                );
    }

    @Test
    void listNullQueryUsesDefaultPagination() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(customerMapper.selectPage(any(Page.class), any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service().list(null);

        ArgumentCaptor<Page<CustomerEntity>> pageCaptor = ArgumentCaptor.forClass(Page.class);
        verify(customerMapper).selectPage(pageCaptor.capture(), any());
        assertThat(pageCaptor.getValue().getCurrent()).isEqualTo(1L);
        assertThat(pageCaptor.getValue().getSize()).isEqualTo(20L);
    }

    @Test
    void getByIdRejectsCustomerFromAnotherAccountBook() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(customerMapper.selectById(CUSTOMER_ID)).thenReturn(customer(9999L));

        assertThatThrownBy(() -> service().getById(CUSTOMER_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("客户不存在");
    }

    @Test
    void exportRestoresAuthenticationAndWritesTenantFilteredCsv() throws Exception {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        Authentication capturedAuthentication = mock(Authentication.class);
        Authentication streamingAuthentication = mock(Authentication.class);
        SecurityContext requestContext = SecurityContextHolder.createEmptyContext();
        requestContext.setAuthentication(capturedAuthentication);
        SecurityContextHolder.setContext(requestContext);
        try {
            CustomerPageQuery query = new CustomerPageQuery();
            query.setKeyword("  C-001  ");
            query.setType(" COMPANY ");
            query.setStatus(" active ");
            query.setSettlementMethod(" MONTHLY ");
            var export = service().exportCustomers(query);

            SecurityContext streamingContext = SecurityContextHolder.createEmptyContext();
            streamingContext.setAuthentication(streamingAuthentication);
            SecurityContextHolder.setContext(streamingContext);
            when(customerMapper.selectList(any())).thenAnswer(invocation -> {
                assertThat(SecurityContextHolder.getContext().getAuthentication())
                        .isSameAs(capturedAuthentication);
                return List.of(customer(AUDIT.accountBookId()));
            });
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

            export.writeTo(outputStream);

            assertThat(SecurityContextHolder.getContext().getAuthentication())
                    .isSameAs(streamingAuthentication);
            assertThat(outputStream.toString(StandardCharsets.UTF_8))
                    .startsWith("\uFEFFcustomerCode,customerName,customerType,contactName,contactPhone,email,settlementMethod,creditLimit,creditPeriod,address,status,remark\r\n")
                    .contains("C-001,华东客户,COMPANY,王经理,13800000001,customer@example.com,MONTHLY,10000.00,30,上海,ACTIVE,query test\r\n");

            ArgumentCaptor<LambdaQueryWrapper<CustomerEntity>> wrapperCaptor =
                    ArgumentCaptor.forClass(LambdaQueryWrapper.class);
            verify(customerMapper).selectList(wrapperCaptor.capture());
            assertThat(wrapperCaptor.getValue().getSqlSegment().toLowerCase(Locale.ROOT))
                    .contains(
                            "company_id",
                            "account_book_id",
                            "deleted_flag",
                            "customer_code",
                            "customer_name",
                            "contact_name",
                            "customer_type",
                            "status",
                            "settlement_method",
                            "order by customer_code asc"
                    );
            assertThat(wrapperCaptor.getValue().getParamNameValuePairs().values())
                    .contains(
                            AUDIT.companyId(),
                            AUDIT.accountBookId(),
                            "%C-001%",
                            "COMPANY",
                            "ACTIVE",
                            "MONTHLY"
                    );
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private CustomerQueryService service() {
        return new CustomerQueryService(customerMapper, auditMetadataFactory);
    }

    private CustomerEntity customer(Long accountBookId) {
        CustomerEntity entity = new CustomerEntity();
        entity.setId(CUSTOMER_ID);
        entity.setCompanyId(AUDIT.companyId());
        entity.setAccountBookId(accountBookId);
        entity.setCustomerCode("C-001");
        entity.setCustomerName("华东客户");
        entity.setCustomerType("COMPANY");
        entity.setContactName("王经理");
        entity.setContactPhone("13800000001");
        entity.setEmail("customer@example.com");
        entity.setSettlementMethod("MONTHLY");
        entity.setCreditLimit(new BigDecimal("10000.00"));
        entity.setCreditPeriod(30);
        entity.setAddress("上海");
        entity.setStatus("ACTIVE");
        entity.setDeletedFlag(0);
        entity.setRemark("query test");
        return entity;
    }
}
