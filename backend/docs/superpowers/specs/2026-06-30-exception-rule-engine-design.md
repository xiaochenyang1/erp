# Exception Rule Engine Design

## Goal

Build a rule-driven exception detection layer that scans existing ERP data, records rule hits, and creates exception tickets automatically without duplicating tickets for the same active business issue.

## Scope

The first version supports built-in operational rules, manual scanning, rule enablement/configuration, hit records, and automatic ticket creation.

Supported rule types:

- `LOW_STOCK`: uses existing inventory alert rules and current stock quantities.
- `RECEIVABLE_OVERDUE`: finds unsettled receivables whose business date is older than the configured day threshold.
- `PAYABLE_OVERDUE`: finds unsettled payables whose business date is older than the configured day threshold.
- `OPERATION_FAILURE`: finds failed operation logs inside the configured minute window.

Out of scope for this version:

- A custom expression language or user-authored SQL.
- Cross-tenant scanning without an authenticated tenant context.
- Closing hits automatically after the underlying issue disappears.

## Backend Design

Add a new package:

- `com.tuowei.erp.issue.rule`

Add tables:

- `biz_exception_rule`
- `biz_exception_rule_hit`

Add endpoints:

- `GET /api/exception-rules`
- `PUT /api/exception-rules/{id}`
- `POST /api/exception-rules/{id}/enable`
- `POST /api/exception-rules/{id}/disable`
- `POST /api/exception-rules/{id}/scan`
- `POST /api/exception-rules/scan-all`
- `GET /api/exception-rules/hits`

Permissions:

- `exception-rule:view` for listing rules and hits.
- `exception-rule:manage` for editing rules and enabling/disabling them.
- `exception-rule:execute` for manual scans.

All rule and hit queries are tenant/account-book scoped. Rule scans run under the current authenticated audit context and reuse the existing `ExceptionTicketService` to create tickets.

## Rule Model

Each rule has:

- `ruleCode`: stable built-in code.
- `ruleName`: display name.
- `ruleType`: scanner selector.
- `category`: exception ticket category.
- `priority`: ticket priority.
- `thresholdValue`: numeric threshold.
- `thresholdUnit`: `QTY`, `DAYS`, `MINUTES`, or `COUNT`.
- `enabled`: scan switch.
- `assigneeUserId`: optional default ticket owner.
- Last scan metadata: time, status, hit count, ticket-created count, and error message.

Seeded rules:

- `LOW_STOCK_DEFAULT`
- `RECEIVABLE_OVERDUE_DEFAULT`
- `PAYABLE_OVERDUE_DEFAULT`
- `OPERATION_FAILURE_DEFAULT`

## Hit And Ticket Flow

Each scanner returns normalized findings with:

- source type, source id, source number, source route
- title and description
- trigger value and threshold value
- hit key

During scan:

1. Load the enabled rule by id or all enabled rules.
2. Scan the matching source table or service.
3. For each finding, upsert a hit row by `rule_id + hit_key`.
4. Look for an active existing ticket with the same `source_type + source_id` and status in `OPEN`, `PROCESSING`, or `RESOLVED`.
5. Create a new exception ticket only when no active ticket exists.
6. Link the hit to the existing or created ticket.
7. Update rule last-scan metadata.

This keeps repeated scans idempotent while still refreshing hit timestamps and counters.

## Frontend Design

Add route:

- `/exception-rules`
- Title: `异常规则`
- Permission: `exception-rule:view`

The page is a compact operations workbench:

- Filter bar for keyword, rule type, and enabled state.
- Rule table showing type, threshold, priority, owner, last scan, and scan result.
- Inline actions for scan, enable/disable, and configuration edit.
- Hit table showing latest hits, source, trigger value, threshold, generated ticket, and timestamps.

The UI follows the existing Element Plus ERP console style. It should prioritize dense scanning and quick actions, not decorative dashboards.

## Error Handling

- Disabled rules cannot be scanned individually.
- Unsupported rule types fail with a clear validation error.
- Rule updates validate priority and threshold unit.
- A scan catches rule-level exceptions, records `FAILED` metadata, and returns a failed scan result instead of hiding the error.
- Ticket deduplication treats `OPEN`, `PROCESSING`, and `RESOLVED` as active statuses.

## Tests

Backend tests:

- Controller permission checks, query binding, update binding, and scan endpoint delegation.
- Service rule listing with tenant filters.
- Service update/enable/disable behavior.
- Service scan creates hits and tickets.
- Service scan deduplicates tickets for existing active issues.
- Service rejects scanning disabled rules.

Frontend verification:

- `npm run type-check`
- `npm run build`
