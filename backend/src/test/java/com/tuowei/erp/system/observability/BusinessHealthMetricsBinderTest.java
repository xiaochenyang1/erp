package com.tuowei.erp.system.observability;

import com.tuowei.erp.system.observability.metrics.BusinessHealthMetricsBinder;
import com.tuowei.erp.system.observability.service.ObservabilityBusinessHealthService;
import com.tuowei.erp.system.observability.web.BusinessHealthCheckResponse;
import com.tuowei.erp.system.observability.web.BusinessHealthResponse;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BusinessHealthMetricsBinderTest {

    private final ObservabilityBusinessHealthService businessHealthService = mock(ObservabilityBusinessHealthService.class);

    @Test
    void exposesOverallStatusAndCheckCountGauges() {
        when(businessHealthService.current()).thenReturn(new BusinessHealthResponse(
                "WARN",
                LocalDateTime.parse("2026-06-05T08:00:00"),
                List.of(
                        new BusinessHealthCheckResponse(
                                "READINESS_UNPASSED_P0_P1",
                                "未通过 P0/P1 验收项",
                                "UP",
                                0,
                                0,
                                "未发现异常"
                        ),
                        new BusinessHealthCheckResponse(
                                "NEGATIVE_INVENTORY_BALANCE",
                                "负库存余额",
                                "WARN",
                                3,
                                0,
                                "存在负库存余额"
                        ),
                        new BusinessHealthCheckResponse(
                                "OPEN_PERIOD_COUNT",
                                "开放会计期间数量",
                                "WARN",
                                0,
                                1,
                                "当前账套没有开放会计期间"
                        )
                )
        ));
        SimpleMeterRegistry registry = new SimpleMeterRegistry();

        new BusinessHealthMetricsBinder(businessHealthService).bindTo(registry);

        assertThat(registry.get("erp_business_health_overall_status").gauge().value()).isEqualTo(1.0);
        assertThat(registry.get("erp_business_health_check_count")
                .tag("check", "NEGATIVE_INVENTORY_BALANCE")
                .gauge()
                .value()).isEqualTo(3.0);
        assertThat(registry.get("erp_business_health_check_status")
                .tag("check", "OPEN_PERIOD_COUNT")
                .gauge()
                .value()).isEqualTo(1.0);
    }
}
