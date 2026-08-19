package com.tuowei.erp.inventory.stock;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.tuowei.erp.common.security.CurrentUser;
import com.tuowei.erp.common.security.CurrentUserContext;
import com.tuowei.erp.common.security.DataScopeService;
import com.tuowei.erp.common.security.DataScopeSnapshot;
import com.tuowei.erp.common.security.ErpPrincipal;
import com.tuowei.erp.inventory.stock.mapper.InventoryTransactionMapper;
import com.tuowei.erp.inventory.stock.model.InventoryTransactionEntity;
import com.tuowei.erp.inventory.stock.service.InventoryDocumentLinkResolver;
import com.tuowei.erp.inventory.stock.service.InventoryLotGenealogyService;
import com.tuowei.erp.inventory.stock.service.LotGenealogyCounterpartyResolver;
import com.tuowei.erp.inventory.stock.service.LotGenealogyDisplayResolver;
import com.tuowei.erp.inventory.stock.web.CounterpartyRef;
import com.tuowei.erp.inventory.stock.web.InventoryLotGenealogyQuery;
import com.tuowei.erp.inventory.stock.web.InventoryLotGenealogyResponse;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InventoryLotGenealogyServiceTest {

    private static final CurrentUser USER = new CurrentUser(
            9401L, 101L, 202L, 11L, 12L, "genealogy_user", "谱系用户");
    private static final DataScopeSnapshot ALL_SCOPE = DataScopeSnapshot.all();

    @Mock
    private InventoryTransactionMapper transactionMapper;
    @Mock
    private CurrentUserContext currentUserContext;
    @Mock
    private DataScopeService dataScopeService;
    @Mock
    private LotGenealogyCounterpartyResolver counterpartyResolver;
    @Mock
    private LotGenealogyDisplayResolver displayResolver;

    private InventoryLotGenealogyService service;

    @BeforeAll
    static void initTableInfo() {
        if (TableInfoHelper.getTableInfo(InventoryTransactionEntity.class) == null) {
            MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
            TableInfoHelper.initTableInfo(assistant, InventoryTransactionEntity.class);
        }
    }

    @BeforeEach
    void setUp() {
        service = new InventoryLotGenealogyService(
                transactionMapper,
                currentUserContext,
                dataScopeService,
                new InventoryDocumentLinkResolver(),
                counterpartyResolver,
                displayResolver
        );
        when(currentUserContext.requireCurrentUser()).thenReturn(USER);
        when(currentUserContext.requirePrincipal()).thenReturn(principal(ALL_SCOPE));
        when(dataScopeService.applyInventoryTransactionScope(any(), eq(ALL_SCOPE)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(displayResolver.products(anyCollection(), eq(USER.companyId()), eq(USER.accountBookId())))
                .thenReturn(Map.of(7001L, new LotGenealogyDisplayResolver.ProductDisplay("P-7001", "成品甲")));
        when(displayResolver.warehouseNames(anyCollection(), eq(USER.companyId()), eq(USER.accountBookId())))
                .thenReturn(Map.of(1L, "主仓"));
        when(counterpartyResolver.resolve(anyCollection(), anyCollection(), eq(USER.companyId()), eq(USER.accountBookId())))
                .thenReturn(LotGenealogyCounterpartyResolver.CounterpartyIndex.empty());
    }

    @Test
    void rejectsMissingRequiredFields() {
        assertThatThrownBy(() -> service.genealogy(new InventoryLotGenealogyQuery()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("批次谱系必须指定商品");

        InventoryLotGenealogyQuery query = query(7001L, " ");
        assertThatThrownBy(() -> service.genealogy(query))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("批次谱系必须指定批次号");
    }

    @Test
    void resolvesUpstreamPurchaseAndSupplier() {
        when(transactionMapper.selectList(any()))
                .thenReturn(List.of(txn(7001L, "LOT-1", "PURCHASE_RECEIPT", "PR-1", "IN")))
                .thenReturn(List.of());
        when(counterpartyResolver.resolve(anyCollection(), anyCollection(), eq(USER.companyId()), eq(USER.accountBookId())))
                .thenReturn(new LotGenealogyCounterpartyResolver.CounterpartyIndex(
                        Map.of("PR-1", new CounterpartyRef("SUPPLIER", 501L, "S-501", "上游供应商", "PO-1")),
                        Map.of()));

        InventoryLotGenealogyQuery query = query(7001L, " LOT-1 ");
        query.setDirection("UPSTREAM");
        InventoryLotGenealogyResponse response = service.genealogy(query);

        assertThat(response.root().lotNo()).isEqualTo("LOT-1");
        assertThat(response.upstream().links()).hasSize(1);
        assertThat(response.upstream().links().get(0).terminalReason()).isEqualTo("PURCHASED");
        assertThat(response.upstream().links().get(0).counterparty().name()).isEqualTo("上游供应商");
        assertThat(response.downstream()).isNull();
    }

    @Test
    void crossesProductionBoundaryInBatchedLevels() {
        when(transactionMapper.selectList(any()))
                .thenReturn(List.of(txn(7001L, "LOT-F", "PRODUCTION_COMPLETION", "MO-1", "IN")))
                .thenReturn(List.of(
                        txn(8001L, "LOT-A", "PRODUCTION_ISSUE", "MO-1", "OUT"),
                        txn(8002L, "LOT-B", "PRODUCTION_ISSUE", "MO-1", "OUT")))
                .thenReturn(List.of(
                        txn(8001L, "LOT-A", "PURCHASE_RECEIPT", "PR-A", "IN"),
                        txn(8002L, "LOT-B", "PURCHASE_RECEIPT", "PR-B", "IN")));

        InventoryLotGenealogyQuery query = query(7001L, "LOT-F");
        query.setDirection("UPSTREAM");
        InventoryLotGenealogyResponse response = service.genealogy(query);

        var completion = response.upstream().links().get(0);
        assertThat(completion.bizType()).isEqualTo("PRODUCTION_COMPLETION");
        assertThat(completion.node()).isNotNull();
        assertThat(completion.node().links()).hasSize(2);
        assertThat(completion.node().links()).allSatisfy(link -> {
            assertThat(link.bizType()).isEqualTo("PRODUCTION_ISSUE");
            assertThat(link.node()).isNotNull();
        });
        assertThat(completion.node().links()).flatExtracting(link -> link.node().links())
                .extracting(link -> link.terminalReason())
                .containsOnly("PURCHASED");
        verify(transactionMapper, times(3)).selectList(any());
        verify(dataScopeService, times(3)).applyInventoryTransactionScope(any(), eq(ALL_SCOPE));
    }

    @Test
    void keepsNonLotControlledProductionMaterialAsTerminalChild() {
        when(transactionMapper.selectList(any()))
                .thenReturn(List.of(txn(7001L, "LOT-F", "PRODUCTION_COMPLETION", "MO-1", "IN")))
                .thenReturn(List.of(txn(8001L, null, "PRODUCTION_ISSUE", "MO-1", "OUT")));

        InventoryLotGenealogyQuery query = query(7001L, "LOT-F");
        query.setDirection("UPSTREAM");
        var completion = service.genealogy(query).upstream().links().get(0);

        assertThat(completion.node()).isNotNull();
        assertThat(completion.node().links()).singleElement().satisfies(material -> {
            assertThat(material.node()).isNotNull();
            assertThat(material.node().productId()).isEqualTo(8001L);
            assertThat(material.node().lotNo()).isNull();
            assertThat(material.terminalReason()).isEqualTo("MATERIAL_NOT_LOT_CONTROLLED");
        });
    }

    @Test
    void resolvesDownstreamCustomer() {
        when(transactionMapper.selectList(any()))
                .thenReturn(List.of(txn(7001L, "LOT-1", "SALES_DELIVERY", "SD-1", "OUT")))
                .thenReturn(List.of());
        when(counterpartyResolver.resolve(anyCollection(), anyCollection(), eq(USER.companyId()), eq(USER.accountBookId())))
                .thenReturn(new LotGenealogyCounterpartyResolver.CounterpartyIndex(
                        Map.of(),
                        Map.of("SD-1", new CounterpartyRef("CUSTOMER", 601L, "C-601", "下游客户", "SO-1"))));

        InventoryLotGenealogyQuery query = query(7001L, "LOT-1");
        query.setDirection("DOWNSTREAM");
        var link = service.genealogy(query).downstream().links().get(0);

        assertThat(link.terminalReason()).isEqualTo("SOLD");
        assertThat(link.counterparty().name()).isEqualTo("下游客户");
    }

    @Test
    void clampsDepthAndReportsScopeLimited() {
        when(currentUserContext.requirePrincipal()).thenReturn(
                principal(new DataScopeSnapshot(false, false, false, false, Set.of(1L))));
        when(transactionMapper.selectList(any())).thenReturn(List.of());
        InventoryLotGenealogyQuery query = query(7001L, "LOT-1");
        query.setMaxDepth(99);

        var limits = service.genealogy(query).limits();

        assertThat(limits.maxDepth()).isEqualTo(10);
        assertThat(limits.scopeLimited()).isTrue();
    }

    @Test
    void keepsBoundaryNodeWhenDepthLimitStopsFurtherExpansion() {
        when(transactionMapper.selectList(any()))
                .thenReturn(List.of(txn(7001L, "LOT-F", "PRODUCTION_COMPLETION", "MO-1", "IN")))
                .thenReturn(List.of(txn(8001L, "LOT-A", "PRODUCTION_ISSUE", "MO-1", "OUT")));

        InventoryLotGenealogyQuery query = query(7001L, "LOT-F");
        query.setDirection("UPSTREAM");
        query.setMaxDepth(1);
        var completion = service.genealogy(query).upstream().links().get(0);

        assertThat(completion.node()).isNotNull();
        assertThat(completion.node().links()).singleElement().satisfies(material -> {
            assertThat(material.node()).isNotNull();
            assertThat(material.node().depth()).isEqualTo(1);
            assertThat(material.terminalReason()).isEqualTo("MAX_DEPTH");
        });
    }

    @Test
    void reportsPerLevelNodeLimitAcrossTheWholeLevel() {
        List<InventoryTransactionEntity> materials = IntStream.range(0, 201)
                .mapToObj(index -> txn(8000L + index, "LOT-" + index, "PRODUCTION_ISSUE", "MO-1", "OUT"))
                .toList();
        when(transactionMapper.selectList(any()))
                .thenReturn(List.of(txn(7001L, "LOT-F", "PRODUCTION_COMPLETION", "MO-1", "IN")))
                .thenReturn(materials)
                .thenReturn(List.of());

        InventoryLotGenealogyQuery query = query(7001L, "LOT-F");
        query.setDirection("UPSTREAM");
        var response = service.genealogy(query);

        assertThat(response.upstream().links().get(0).node().links()).hasSize(200);
        assertThat(response.limits().truncated()).isTrue();
        assertThat(response.limits().truncationReasons()).contains("NODE_LIMIT_PER_LEVEL");
    }

    @Test
    void reportsTotalNodeLimitPerDirection() {
        List<InventoryTransactionEntity> firstMaterials = IntStream.range(0, 200)
                .mapToObj(index -> txn(8000L + index, "L1-" + index, "PRODUCTION_ISSUE", "MO-0", "OUT"))
                .toList();
        List<InventoryTransactionEntity> firstCompletions = IntStream.range(0, 200)
                .mapToObj(index -> txn(8000L + index, "L1-" + index,
                        "PRODUCTION_COMPLETION", "MO-1-" + index, "IN"))
                .toList();
        List<InventoryTransactionEntity> secondMaterials = IntStream.range(0, 200)
                .mapToObj(index -> txn(9000L + index, "L2-" + index,
                        "PRODUCTION_ISSUE", "MO-1-" + index, "OUT"))
                .toList();
        List<InventoryTransactionEntity> secondCompletions = IntStream.range(0, 200)
                .mapToObj(index -> txn(9000L + index, "L2-" + index,
                        "PRODUCTION_COMPLETION", "MO-2-" + index, "IN"))
                .toList();
        List<InventoryTransactionEntity> thirdMaterials = IntStream.range(0, 200)
                .mapToObj(index -> txn(10000L + index, "L3-" + index,
                        "PRODUCTION_ISSUE", "MO-2-" + index, "OUT"))
                .toList();
        when(transactionMapper.selectList(any()))
                .thenReturn(List.of(txn(7001L, "LOT-F", "PRODUCTION_COMPLETION", "MO-0", "IN")))
                .thenReturn(firstMaterials)
                .thenReturn(firstCompletions)
                .thenReturn(secondMaterials)
                .thenReturn(secondCompletions)
                .thenReturn(thirdMaterials)
                .thenReturn(List.of());

        InventoryLotGenealogyQuery query = query(7001L, "LOT-F");
        query.setDirection("UPSTREAM");
        var response = service.genealogy(query);

        assertThat(response.limits().truncated()).isTrue();
        assertThat(response.limits().truncationReasons())
                .contains("NODE_LIMIT_TOTAL")
                .doesNotContain("NODE_LIMIT_PER_LEVEL");
    }

    private static ErpPrincipal principal(DataScopeSnapshot snapshot) {
        return new ErpPrincipal(USER.userId(), USER.companyId(), USER.accountBookId(),
                USER.deptId(), USER.postId(), USER.username(), USER.realName(), "N/A", Set.of(), snapshot);
    }

    private static InventoryLotGenealogyQuery query(Long productId, String lotNo) {
        InventoryLotGenealogyQuery query = new InventoryLotGenealogyQuery();
        query.setProductId(productId);
        query.setLotNo(lotNo);
        return query;
    }

    private static InventoryTransactionEntity txn(
            Long productId,
            String lotNo,
            String bizType,
            String bizNo,
            String direction
    ) {
        InventoryTransactionEntity entity = new InventoryTransactionEntity();
        entity.setId((long) (bizNo.hashCode() & 0xffff));
        entity.setCompanyId(USER.companyId());
        entity.setAccountBookId(USER.accountBookId());
        entity.setWarehouseId(1L);
        entity.setProductId(productId);
        entity.setLotNo(lotNo);
        entity.setBizType(bizType);
        entity.setBizNo(bizNo);
        entity.setDirection(direction);
        entity.setQty(new BigDecimal("10"));
        entity.setOccurredTime(LocalDateTime.of(2026, 8, 1, 10, 0));
        entity.setProductionDate(LocalDate.of(2026, 7, 1));
        entity.setExpiryDate(LocalDate.of(2027, 7, 1));
        return entity;
    }
}
