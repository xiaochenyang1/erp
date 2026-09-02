package com.tuowei.erp.common.security;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.tuowei.erp.production.order.model.ProductionOrderEntity;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.util.Locale;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductionDataScopeServiceTest {

    private static final CurrentUser CURRENT_USER = new CurrentUser(
            9001L,
            1001L,
            2001L,
            11L,
            12L,
            "scope_user",
            "Scope User"
    );

    private final ProductionDataScopeService service = new ProductionDataScopeService();

    @BeforeAll
    static void initTableInfo() {
        if (TableInfoHelper.getTableInfo(ProductionOrderEntity.class) != null) {
            return;
        }
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(
                new MybatisConfiguration(), ProductionOrderEntity.class.getName());
        assistant.setCurrentNamespace(ProductionOrderEntity.class.getName());
        TableInfoHelper.initTableInfo(assistant, ProductionOrderEntity.class);
    }

    @Test
    void allScopeLeavesQueryUnchanged() {
        LambdaQueryWrapper<ProductionOrderEntity> wrapper =
                new LambdaQueryWrapper<>(ProductionOrderEntity.class);

        LambdaQueryWrapper<ProductionOrderEntity> scoped = service.applyProductionOrderScope(
                wrapper, CURRENT_USER, DataScopeSnapshot.all(), Set.of(), Set.of());

        assertThat(scoped).isSameAs(wrapper);
        assertThat(wrapper.getSqlSegment()).isEmpty();
    }

    @Test
    void emptyScopeRejectsAllRows() {
        LambdaQueryWrapper<ProductionOrderEntity> wrapper = service.applyProductionOrderScope(
                new LambdaQueryWrapper<>(ProductionOrderEntity.class),
                CURRENT_USER,
                DataScopeSnapshot.none(),
                Set.of(),
                Set.of()
        );

        assertThat(wrapper.getSqlSegment()).contains("1 = 0");
    }

    @Test
    void selfDepartmentAndPostScopesFilterVisibleCreators() {
        assertCreatorScope(
                new DataScopeSnapshot(false, false, false, true, Set.of()),
                Set.of(), Set.of(), Set.of(CURRENT_USER.userId()));
        assertCreatorScope(
                new DataScopeSnapshot(false, true, false, false, Set.of()),
                Set.of(21L, 22L), Set.of(), Set.of(21L, 22L));
        assertCreatorScope(
                new DataScopeSnapshot(false, false, true, false, Set.of()),
                Set.of(), Set.of(31L, 32L), Set.of(31L, 32L));
    }

    @Test
    void warehouseScopeRequiresBothProductionWarehouses() {
        LambdaQueryWrapper<ProductionOrderEntity> wrapper = service.applyProductionOrderScope(
                new LambdaQueryWrapper<>(ProductionOrderEntity.class),
                CURRENT_USER,
                new DataScopeSnapshot(false, false, false, false, Set.of(41L, 42L)),
                Set.of(),
                Set.of()
        );

        assertThat(wrapper.getSqlSegment().toLowerCase(Locale.ROOT))
                .contains("material_warehouse_id", "finished_warehouse_id")
                .doesNotContain("created_by", "or");
        assertThat(wrapper.getParamNameValuePairs().values()).contains(41L, 42L);
    }

    @Test
    void combinedScopeAllowsVisibleCreatorOrBothWarehouses() {
        LambdaQueryWrapper<ProductionOrderEntity> wrapper = service.applyProductionOrderScope(
                new LambdaQueryWrapper<>(ProductionOrderEntity.class),
                CURRENT_USER,
                new DataScopeSnapshot(false, false, false, true, Set.of(41L, 42L)),
                Set.of(),
                Set.of()
        );

        assertThat(wrapper.getSqlSegment().toLowerCase(Locale.ROOT))
                .contains("created_by", "material_warehouse_id", "finished_warehouse_id", "or");
        assertThat(wrapper.getParamNameValuePairs().values())
                .contains(CURRENT_USER.userId(), 41L, 42L);
    }

    @Test
    void viewAssertionAllowsCreatorOrganizationOrBothWarehouses() {
        ProductionOrderEntity creatorOrder = order(41L, 42L, CURRENT_USER.userId());
        ProductionOrderEntity organizationOrder = order(41L, 42L, 9999L);
        ProductionOrderEntity warehouseOrder = order(41L, 42L, 9999L);

        assertThatCode(() -> service.assertCanViewProductionOrder(
                creatorOrder,
                CURRENT_USER,
                new DataScopeSnapshot(false, false, false, true, Set.of()),
                null,
                null
        )).doesNotThrowAnyException();
        assertThatCode(() -> service.assertCanViewProductionOrder(
                organizationOrder,
                CURRENT_USER,
                new DataScopeSnapshot(false, true, false, false, Set.of()),
                CURRENT_USER.deptId(),
                null
        )).doesNotThrowAnyException();
        assertThatCode(() -> service.assertCanViewProductionOrder(
                organizationOrder,
                CURRENT_USER,
                new DataScopeSnapshot(false, false, true, false, Set.of()),
                null,
                CURRENT_USER.postId()
        )).doesNotThrowAnyException();
        assertThatCode(() -> service.assertCanViewProductionOrder(
                warehouseOrder,
                CURRENT_USER,
                new DataScopeSnapshot(false, false, false, false, Set.of(41L, 42L)),
                null,
                null
        )).doesNotThrowAnyException();
    }

    @Test
    void viewAssertionRejectsWhenOnlyOneWarehouseIsVisible() {
        assertDenied(() -> service.assertCanViewProductionOrder(
                order(41L, 42L, 9999L),
                CURRENT_USER,
                new DataScopeSnapshot(false, false, false, false, Set.of(41L)),
                null,
                null
        ));
    }

    @Test
    void tenantProtectionRunsBeforeAllScopeAcceptance() {
        ProductionOrderEntity wrongCompany = order(41L, 42L, CURRENT_USER.userId());
        wrongCompany.setCompanyId(9999L);
        ProductionOrderEntity wrongAccountBook = order(41L, 42L, CURRENT_USER.userId());
        wrongAccountBook.setAccountBookId(9999L);

        assertDenied(() -> service.assertCanViewProductionOrder(
                wrongCompany, CURRENT_USER, DataScopeSnapshot.all(), null, null));
        assertDenied(() -> service.assertCanViewProductionOrder(
                wrongAccountBook, CURRENT_USER, DataScopeSnapshot.all(), null, null));
    }

    private void assertCreatorScope(
            DataScopeSnapshot snapshot,
            Set<Long> deptUserIds,
            Set<Long> postUserIds,
            Set<Long> expectedCreatorIds
    ) {
        LambdaQueryWrapper<ProductionOrderEntity> wrapper = service.applyProductionOrderScope(
                new LambdaQueryWrapper<>(ProductionOrderEntity.class),
                CURRENT_USER,
                snapshot,
                deptUserIds,
                postUserIds
        );

        assertThat(wrapper.getSqlSegment().toLowerCase(Locale.ROOT))
                .contains("created_by")
                .doesNotContain("material_warehouse_id", "finished_warehouse_id");
        assertThat(wrapper.getParamNameValuePairs().values()).containsAll(expectedCreatorIds);
    }

    private static ProductionOrderEntity order(
            Long materialWarehouseId,
            Long finishedWarehouseId,
            Long createdBy
    ) {
        ProductionOrderEntity entity = new ProductionOrderEntity();
        entity.setCompanyId(CURRENT_USER.companyId());
        entity.setAccountBookId(CURRENT_USER.accountBookId());
        entity.setMaterialWarehouseId(materialWarehouseId);
        entity.setFinishedWarehouseId(finishedWarehouseId);
        entity.setCreatedBy(createdBy);
        return entity;
    }

    private static void assertDenied(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOf(AccessDeniedException.class);
    }
}
