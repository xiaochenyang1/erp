package com.tuowei.erp.masterdata.supplier.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.masterdata.supplier.mapper.SupplierMapper;
import com.tuowei.erp.masterdata.supplier.model.SupplierEntity;
import com.tuowei.erp.masterdata.supplier.web.SupplierPageQuery;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.ByteArrayOutputStream;
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
class SupplierQueryServiceTest {

    private static final AuditMetadata AUDIT = new AuditMetadata(
            7301L,
            8301L,
            9301L,
            LocalDateTime.of(2026, 8, 20, 17, 0)
    );
    private static final Long SUPPLIER_ID = 301L;

    private final SupplierMapper supplierMapper = mock(SupplierMapper.class);
    private final AuditMetadataFactory auditMetadataFactory = mock(AuditMetadataFactory.class);

    @BeforeAll
    static void initTableInfo() {
        if (TableInfoHelper.getTableInfo(SupplierEntity.class) != null) {
            return;
        }
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(
                new MybatisConfiguration(),
                SupplierEntity.class.getName()
        );
        assistant.setCurrentNamespace(SupplierEntity.class.getName());
        TableInfoHelper.initTableInfo(assistant, SupplierEntity.class);
    }

    @Test
    void listNormalizesFiltersClampsPaginationScopesTenantAndMapsResponse() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(supplierMapper.selectPage(any(Page.class), any())).thenAnswer(invocation -> {
            Page<SupplierEntity> page = invocation.getArgument(0);
            page.setTotal(1L);
            page.setRecords(List.of(supplier(AUDIT.accountBookId())));
            return page;
        });
        SupplierPageQuery query = new SupplierPageQuery();
        query.setPageNo(0);
        query.setPageSize(999);
        query.setKeyword("  S-001  ");
        query.setStatus(" active ");
        query.setSettlementMethod(" MONTHLY ");

        var response = service().list(query);

        assertThat(response.pageNo()).isEqualTo(1L);
        assertThat(response.pageSize()).isEqualTo(200L);
        assertThat(response.total()).isEqualTo(1L);
        assertThat(response.records()).singleElement().satisfies(record -> {
            assertThat(record.id()).isEqualTo(SUPPLIER_ID);
            assertThat(record.supplierCode()).isEqualTo("S-001");
            assertThat(record.supplierName()).isEqualTo("华东供应商");
            assertThat(record.creditPeriod()).isEqualTo(30);
        });

        ArgumentCaptor<Page<SupplierEntity>> pageCaptor = ArgumentCaptor.forClass(Page.class);
        ArgumentCaptor<LambdaQueryWrapper<SupplierEntity>> wrapperCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(supplierMapper).selectPage(pageCaptor.capture(), wrapperCaptor.capture());
        assertThat(pageCaptor.getValue().getCurrent()).isEqualTo(1L);
        assertThat(pageCaptor.getValue().getSize()).isEqualTo(200L);
        assertThat(wrapperCaptor.getValue().getSqlSegment().toLowerCase(Locale.ROOT))
                .contains("company_id")
                .contains("account_book_id")
                .contains("deleted_flag")
                .contains("supplier_code")
                .contains("supplier_name")
                .contains("contact_name")
                .contains("status")
                .contains("settlement_method")
                .contains("order by supplier_code asc");
        assertThat(wrapperCaptor.getValue().getParamNameValuePairs().values())
                .contains(
                        AUDIT.companyId(),
                        AUDIT.accountBookId(),
                        "%S-001%",
                        "ACTIVE",
                        "MONTHLY"
                );
    }

    @Test
    void listNullQueryUsesDefaultPagination() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(supplierMapper.selectPage(any(Page.class), any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service().list(null);

        ArgumentCaptor<Page<SupplierEntity>> pageCaptor = ArgumentCaptor.forClass(Page.class);
        verify(supplierMapper).selectPage(pageCaptor.capture(), any());
        assertThat(pageCaptor.getValue().getCurrent()).isEqualTo(1L);
        assertThat(pageCaptor.getValue().getSize()).isEqualTo(20L);
    }

    @Test
    void getByIdRejectsSupplierFromAnotherAccountBook() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(supplierMapper.selectById(SUPPLIER_ID)).thenReturn(supplier(9999L));

        assertThatThrownBy(() -> service().getById(SUPPLIER_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("供应商不存在");
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
            SupplierPageQuery query = new SupplierPageQuery();
            query.setKeyword("  S-001  ");
            query.setStatus(" active ");
            query.setSettlementMethod(" MONTHLY ");
            var export = service().exportSuppliers(query);

            SecurityContext streamingContext = SecurityContextHolder.createEmptyContext();
            streamingContext.setAuthentication(streamingAuthentication);
            SecurityContextHolder.setContext(streamingContext);
            when(supplierMapper.selectList(any())).thenAnswer(invocation -> {
                assertThat(SecurityContextHolder.getContext().getAuthentication())
                        .isSameAs(capturedAuthentication);
                return List.of(supplier(AUDIT.accountBookId()));
            });
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

            export.writeTo(outputStream);

            assertThat(SecurityContextHolder.getContext().getAuthentication())
                    .isSameAs(streamingAuthentication);
            assertThat(outputStream.toString(StandardCharsets.UTF_8))
                    .startsWith("\uFEFFsupplierCode,supplierName,contactName,contactPhone,email,settlementMethod,creditPeriod,address,status,remark\r\n")
                    .contains("S-001,华东供应商,赵经理,13900000001,supplier@example.com,MONTHLY,30,苏州,ACTIVE,query test\r\n");

            ArgumentCaptor<LambdaQueryWrapper<SupplierEntity>> wrapperCaptor =
                    ArgumentCaptor.forClass(LambdaQueryWrapper.class);
            verify(supplierMapper).selectList(wrapperCaptor.capture());
            assertThat(wrapperCaptor.getValue().getSqlSegment().toLowerCase(Locale.ROOT))
                    .contains(
                            "company_id",
                            "account_book_id",
                            "deleted_flag",
                            "supplier_code",
                            "supplier_name",
                            "contact_name",
                            "status",
                            "settlement_method",
                            "order by supplier_code asc"
                    );
            assertThat(wrapperCaptor.getValue().getParamNameValuePairs().values())
                    .contains(
                            AUDIT.companyId(),
                            AUDIT.accountBookId(),
                            "%S-001%",
                            "ACTIVE",
                            "MONTHLY"
                    );
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private SupplierQueryService service() {
        return new SupplierQueryService(supplierMapper, auditMetadataFactory);
    }

    private SupplierEntity supplier(Long accountBookId) {
        SupplierEntity entity = new SupplierEntity();
        entity.setId(SUPPLIER_ID);
        entity.setCompanyId(AUDIT.companyId());
        entity.setAccountBookId(accountBookId);
        entity.setSupplierCode("S-001");
        entity.setSupplierName("华东供应商");
        entity.setContactName("赵经理");
        entity.setContactPhone("13900000001");
        entity.setEmail("supplier@example.com");
        entity.setSettlementMethod("MONTHLY");
        entity.setCreditPeriod(30);
        entity.setAddress("苏州");
        entity.setStatus("ACTIVE");
        entity.setDeletedFlag(0);
        entity.setRemark("query test");
        return entity;
    }
}
