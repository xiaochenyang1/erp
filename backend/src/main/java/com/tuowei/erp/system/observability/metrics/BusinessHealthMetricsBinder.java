package com.tuowei.erp.system.observability.metrics;

import com.tuowei.erp.system.observability.service.ObservabilityBusinessHealthService;
import com.tuowei.erp.system.observability.web.BusinessHealthCheckResponse;
import com.tuowei.erp.system.observability.web.BusinessHealthResponse;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class BusinessHealthMetricsBinder implements MeterBinder {

    private static final String STATUS_UP = "UP";
    private static final List<String> CHECK_CODES = List.of(
            "READINESS_UNPASSED_P0_P1",
            "IMPORT_FAILED_RECENT",
            "NEGATIVE_INVENTORY_BALANCE",
            "OPEN_PERIOD_COUNT"
    );

    private final ObservabilityBusinessHealthService businessHealthService;

    public BusinessHealthMetricsBinder(ObservabilityBusinessHealthService businessHealthService) {
        this.businessHealthService = businessHealthService;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        Gauge.builder("erp_business_health_overall_status", businessHealthService,
                        service -> statusValue(service.current().overallStatus()))
                .description("ERP business health overall status, 0 means UP and 1 means WARN")
                .register(registry);

        for (String checkCode : CHECK_CODES) {
            Gauge.builder("erp_business_health_check_count", businessHealthService,
                            service -> checkCount(service.current(), checkCode))
                    .tag("check", checkCode)
                    .description("ERP business health check count")
                    .register(registry);
            Gauge.builder("erp_business_health_check_status", businessHealthService,
                            service -> checkStatus(service.current(), checkCode))
                    .tag("check", checkCode)
                    .description("ERP business health check status, 0 means UP and 1 means WARN")
                    .register(registry);
        }
    }

    private static double checkCount(BusinessHealthResponse response, String checkCode) {
        return response.checks().stream()
                .filter(check -> checkCode.equals(check.code()))
                .findFirst()
                .map(BusinessHealthCheckResponse::count)
                .map(Long::doubleValue)
                .orElse(0.0);
    }

    private static double checkStatus(BusinessHealthResponse response, String checkCode) {
        return response.checks().stream()
                .filter(check -> checkCode.equals(check.code()))
                .findFirst()
                .map(BusinessHealthCheckResponse::status)
                .map(BusinessHealthMetricsBinder::statusValue)
                .orElse(0.0);
    }

    private static double statusValue(String status) {
        return STATUS_UP.equals(status) ? 0.0 : 1.0;
    }
}
