# Exception SLA Policy Design

## Goal

Move exception ticket due-time and overdue escalation rules out of hard-coded Java methods and into tenant-scoped SLA policies that operations users can review and adjust.

## Scope

- Add tenant-scoped SLA policies by exception category and priority.
- Bootstrap default policies for each company and account book when the policy page is opened.
- Use policies when exception rules create tickets.
- Use policies when overdue tickets are escalated.
- Add backend APIs and a frontend management page.

Out of scope:

- Calendars, holidays, work shifts, or pause/resume timers.
- Multi-step escalation chains beyond one configured target priority.
- Role-based reassignment during escalation.
- External notification channels.

## Backend Design

Create `biz_exception_sla_policy` with:

- `company_id`, `account_book_id`
- `category`
- `priority`
- `due_hours`
- `escalation_enabled`
- `escalate_to_priority`
- `enabled`
- audit fields, optimistic version, and soft delete flag

The unique business key is `(company_id, account_book_id, category, priority)`.

`ExceptionSlaPolicyService` owns policy behavior:

- `list(query)` loads current tenant policies and bootstraps default rows if none exist.
- `update(id, request)` validates due hours, priority values, enabled flags, and updates audit fields.
- `resolveDueTime(category, priority, createdAt, audit)` finds an enabled exact policy first, then falls back to `GENERAL + priority`, then to built-in defaults.
- `resolveEscalation(ticket, audit)` finds an enabled policy and returns whether escalation is enabled and the target priority. Exact category policy wins over `GENERAL`.

Default policies:

- `GENERAL + LOW`: due in `168` hours, escalates to `MEDIUM`.
- `GENERAL + MEDIUM`: due in `72` hours, escalates to `HIGH`.
- `GENERAL + HIGH`: due in `24` hours, escalates to `URGENT`.
- `GENERAL + URGENT`: due in `4` hours, escalation enabled but target remains `URGENT`.
- `LOW_STOCK + HIGH`: due in `24` hours, escalates to `URGENT`.
- `PAYMENT_OVERDUE + MEDIUM`: due in `72` hours, escalates to `HIGH`.
- `PAYMENT_OVERDUE + HIGH`: due in `24` hours, escalates to `URGENT`.
- `SYSTEM_ERROR + MEDIUM`: due in `72` hours, escalates to `HIGH`.

## Integration

`ExceptionRuleService` stops using its private `dueHours()` method. When creating a ticket request, it asks `ExceptionSlaPolicyService.resolveDueTime(...)` with the rule category, rule priority, scan time, and audit metadata.

`ExceptionTicketService` stops using its private `nextPriority(...)` method. During `escalateOverdueTickets(...)`, it asks `ExceptionSlaPolicyService.resolveEscalation(...)`. Tickets with disabled escalation policies are skipped and remain eligible if the policy is re-enabled later. Tickets that escalate still write a single `ESCALATE` event, preserving the existing one-time escalation guard.

Manual ticket creation remains compatible: callers may still pass `dueTime` explicitly. SLA defaulting is applied only to tickets created by exception rules in this feature.

## API Design

Add endpoints under `/api/exception-sla-policies`:

- `GET /api/exception-sla-policies`
- `PUT /api/exception-sla-policies/{id}`

Permissions:

- `exception-sla-policy:view`
- `exception-sla-policy:manage`

The controller follows the same response envelope and page-query conventions as exception rules and tickets.

## Frontend Design

Add `/exception-sla-policies` as a dense operational page:

- Filter by category, priority, and enabled state.
- Summary cards for enabled policies, disabled policies, escalation-enabled policies, average due hours, and urgent policies.
- Table columns: category, priority, due hours, escalation state, target priority, enabled state, updated time.
- Edit dialog for due hours, escalation enabled, target priority, enabled state, and remark.

The page uses the existing Element Plus console style. It should sit beside exception tickets and exception rules, not inside a marketing-style dashboard.

## Testing

Backend tests cover:

- Default policy bootstrap per tenant.
- Policy update validation and audit fields.
- Due-time resolution exact match and `GENERAL` fallback.
- Rule-created tickets use SLA due hours.
- Overdue escalation uses configured target priority.
- Disabled escalation policy skips escalation.
- Controller permission checks and request binding.
- Permission facade includes SLA policy permission codes.
- Tenant table coverage includes the new table.
- Flyway migration smoke test passes for H2 and MySQL.

Frontend checks cover:

- `npm run type-check`
- `npm run build`
