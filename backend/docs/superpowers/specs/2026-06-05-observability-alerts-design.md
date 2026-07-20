# Observability Alerts Design

## Goal

Add a minimum alerting bridge for the existing ERP business-health summary: expose business-health check counts as Prometheus metrics, provide Prometheus alert rule templates, and make preproduction evidence verify the rule template and metric names.

## Scope

This change stays inside the ERP backend repository. It does not deploy Prometheus, Alertmanager, Grafana, notification channels, or environment-specific scrape credentials. Those belong to infrastructure.

## Current State

- Production profile exposes `/actuator/prometheus`.
- `/actuator/prometheus` is authenticated because only `/actuator/health` is anonymous.
- `/api/system/observability/business-health` returns four tenant-scoped checks:
  - `READINESS_UNPASSED_P0_P1`
  - `IMPORT_FAILED_RECENT`
  - `NEGATIVE_INVENTORY_BALANCE`
  - `OPEN_PERIOD_COUNT`
- No ERP-specific Micrometer metrics exist yet.

## Design

### Business Health Metrics

Add a `MeterBinder` under `system/observability` that registers gauges using `ObservabilityBusinessHealthService.current()` as the data source.

Metrics:

- `erp_business_health_overall_status`: `0` for `UP`, `1` for `WARN`.
- `erp_business_health_check_count{check="READINESS_UNPASSED_P0_P1"}`: current check count.
- `erp_business_health_check_count{check="IMPORT_FAILED_RECENT"}`: current check count.
- `erp_business_health_check_count{check="NEGATIVE_INVENTORY_BALANCE"}`: current check count.
- `erp_business_health_check_count{check="OPEN_PERIOD_COUNT"}`: current open period count.
- `erp_business_health_check_status{check="<code>"}`: `0` for `UP`, `1` for `WARN`.

The first scrape for these gauges will execute the same tenant-scoped aggregation used by the API. This is a minimum bridge, not a high-cardinality metrics system. No company or account-book labels are added because those would leak tenant identifiers and expand label cardinality.

### Alert Rule Template

Add `docs/monitoring/prometheus-alert-rules.yml` with rules that can be copied into infrastructure:

- `ErpReadinessP0P1Unpassed`
- `ErpRecentImportFailures`
- `ErpNegativeInventoryBalance`
- `ErpNoOpenAccountingPeriod`
- `ErpBusinessHealthWarn`

Rules use the new metric names only. Severity is `critical` for readiness and no open period, `warning` for failed imports and negative inventory.

### Acceptance Coverage

Update production docs and readiness checklist to require:

- `/actuator/prometheus` response contains `erp_business_health_overall_status`.
- `/actuator/prometheus` response contains `erp_business_health_check_count`.
- Alert rule template exists and names the five minimum rules.

Preproduction scripts continue to fetch `/actuator/prometheus` and `/api/system/observability/business-health`; they do not validate live Prometheus rule loading because infrastructure is outside this repository.

## Error Handling

Gauge scraping uses the business-health service directly. If the database is unavailable or the authenticated request cannot resolve a current user, Spring/Micrometer will surface scrape failure through the existing request path. No silent fallback is introduced because fake zero metrics would hide real outages.

## Tests

- Unit test the meter binder with a mocked business-health service and `SimpleMeterRegistry`.
- Configuration test the alert rule template for required metric names and alert names.
- Existing controller tests continue to verify the API permission boundary.
- Existing script/doc tests are extended to cover the new metric names and rule template.

## Out of Scope

- Alertmanager receiver config.
- Grafana dashboards.
- Prometheus scrape job deployment.
- Anonymous metrics endpoint exposure.
- Per-tenant Prometheus labels.
