package com.tuowei.erp.db;

import org.assertj.core.api.Assertions;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

class RuntimeMenuRouteAlignmentMigrationTest {

    private static final long ERP_ADMIN_ROLE_ID = 3002L;
    private static JdbcTemplate jdbcTemplate;

    @BeforeAll
    static void migrateSchema() throws Exception {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:runtime_menu_route_alignment;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");

        Path migrationDir = H2MigrationTestSupport.copyCompatibleMigrations(
                RuntimeMenuRouteAlignmentMigrationTest.class,
                "runtime-menu-route-alignment-migrations");
        Flyway.configure()
                .dataSource(dataSource)
                .locations("filesystem:" + migrationDir.toAbsolutePath().toString().replace('\\', '/'))
                .load()
                .migrate();

        jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @Test
    void activeVisibleMenusHaveValidParentsAndUniquePaths() {
        Long invalidParentCount = jdbcTemplate.queryForObject("""
                select count(*)
                from sys_menu child
                left join sys_menu parent on parent.id = child.parent_id
                where child.deleted_flag = 0
                  and child.status = 'ACTIVE'
                  and child.visible_flag = 1
                  and child.parent_id <> 0
                  and (parent.id is null
                    or parent.deleted_flag <> 0
                    or parent.status <> 'ACTIVE'
                    or parent.visible_flag <> 1)
                """, Long.class);
        List<String> duplicatePaths = jdbcTemplate.queryForList("""
                select path
                from sys_menu
                where deleted_flag = 0
                  and status = 'ACTIVE'
                  and visible_flag = 1
                  and path is not null
                group by path
                having count(*) > 1
                """, String.class);

        Assertions.assertThat(invalidParentCount).isZero();
        Assertions.assertThat(duplicatePaths).isEmpty();
    }

    @Test
    void correctedMenusMatchTheFrontendHierarchy() {
        Map<String, Long> expectedParents = Map.of(
                "INVENTORY_REPLENISHMENT", 5009L,
                "PURCHASE_ORDER_UNAPPROVE", 5136L,
                "QC_INSPECTION", 5423L
        );

        expectedParents.forEach((menuCode, parentId) -> {
            Long actual = jdbcTemplate.queryForObject(
                    "select parent_id from sys_menu where menu_code = ? and deleted_flag = 0",
                    Long.class,
                    menuCode);
            Assertions.assertThat(actual).as(menuCode).isEqualTo(parentId);
        });

        Map<String, Object> qcCatalog = jdbcTemplate.queryForMap("""
                select menu_type, path, component, visible_flag, status
                from sys_menu
                where menu_code = 'QC' and deleted_flag = 0
                """);
        Assertions.assertThat(qcCatalog)
                .containsEntry("menu_type", "CATALOG")
                .containsEntry("path", "/qc")
                .containsEntry("component", "Layout")
                .containsEntry("visible_flag", 1)
                .containsEntry("status", "ACTIVE");
    }

    @Test
    void aggregateOnlyLegacyRoutesAreHiddenWithoutDroppingPermissions() {
        List<String> aggregateOnlyCodes = List.of(
                "INVENTORY_RESERVATION",
                "REPORT_PURCHASE_ORDER",
                "REPORT_SALES_ORDER",
                "REPORT_INVENTORY_BALANCE",
                "REPORT_FINANCE_SETTLEMENT",
                "REPORT_INVENTORY_TRANSACTION",
                "IMPORT_CENTER",
                "INITIAL_IMPORT"
        );
        String placeholders = String.join(",", java.util.Collections.nCopies(aggregateOnlyCodes.size(), "?"));

        Long hiddenCount = jdbcTemplate.queryForObject("""
                select count(*)
                from sys_menu
                where deleted_flag = 0
                  and status = 'ACTIVE'
                  and visible_flag = 0
                  and menu_code in (%s)
                """.formatted(placeholders), Long.class, aggregateOnlyCodes.toArray());
        Long preservedErpAdminBindings = jdbcTemplate.queryForObject("""
                select count(*)
                from sys_role_menu rm
                join sys_menu m on m.id = rm.menu_id
                where rm.role_id = ?
                  and m.menu_code in (%s)
                """.formatted(placeholders), Long.class,
                parameterArray(ERP_ADMIN_ROLE_ID, aggregateOnlyCodes));
        String reportCatalogPath = jdbcTemplate.queryForObject(
                "select path from sys_menu where menu_code = 'REPORT' and deleted_flag = 0",
                String.class);

        Assertions.assertThat(hiddenCount).isEqualTo((long) aggregateOnlyCodes.size());
        Assertions.assertThat(preservedErpAdminBindings).isEqualTo((long) aggregateOnlyCodes.size());
        Assertions.assertThat(reportCatalogPath).isNull();
    }

    @Test
    void visibleMenuComponentsExistAndReservationPermissionsReachErpAdmin() {
        List<Map<String, Object>> components = jdbcTemplate.queryForList("""
                select menu_code, component
                from sys_menu
                where deleted_flag = 0
                  and status = 'ACTIVE'
                  and visible_flag = 1
                  and menu_type = 'MENU'
                  and component is not null
                order by id
                """);
        Path viewRoot = Path.of("..", "frontend", "src", "views");
        for (Map<String, Object> row : components) {
            String component = row.get("component").toString();
            Assertions.assertThat(Files.isRegularFile(viewRoot.resolve(component + ".vue")))
                    .as("component for %s: %s", row.get("menu_code"), component)
                    .isTrue();
        }

        Long permissionCount = jdbcTemplate.queryForObject("""
                select count(*)
                from sys_role_menu rm
                join sys_menu m on m.id = rm.menu_id
                where rm.role_id = ?
                  and m.menu_code in (
                    'INVENTORY_RESERVATION',
                    'INVENTORY_RESERVATION_CHECK',
                    'INVENTORY_RESERVATION_RELEASE'
                  )
                """, Long.class, ERP_ADMIN_ROLE_ID);
        Assertions.assertThat(permissionCount).isEqualTo(3L);
    }

    private static Object[] parameterArray(long first, List<String> rest) {
        Object[] parameters = new Object[rest.size() + 1];
        parameters[0] = first;
        for (int index = 0; index < rest.size(); index++) {
            parameters[index + 1] = rest.get(index);
        }
        return parameters;
    }
}
