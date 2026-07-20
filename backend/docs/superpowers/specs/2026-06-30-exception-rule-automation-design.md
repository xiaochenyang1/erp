# Exception Rule Automation Design

## Goal

Turn exception rules from manual scans into an automated operational loop: enabled rules are scanned on schedule, generated tickets notify the assigned user, and overdue tickets are escalated once.

## Scope

- Add a per-rule scan interval and next scan time.
- Run a backend scheduler that scans due enabled rules across existing rule rows.
- Reuse the exception ticket center and system notification tables.
- Notify assigned users when tickets are created or assigned.
- Notify relevant users when tickets start, resolve, close, or escalate.
- Escalate overdue open or processing tickets by raising priority one level and writing an `ESCALATE` ticket event.

Out of scope:

- Cron expressions, holiday calendars, or per-rule time windows.
- External channels such as SMS, email, DingTalk, or WeCom.
- Role-based dispatch when no assignee is configured.
- A dedicated scan history table.

## Backend Design

`biz_exception_rule` gains:

- `schedule_interval_minutes`: integer, default `60`, allowed range `5..10080`.
- `next_scan_time`: nullable timestamp. Null means the rule is due.

Manual scans and scheduled scans both update the existing scan summary fields and set `next_scan_time = scannedAt + schedule_interval_minutes`.

`ExceptionRuleService` keeps user-triggered methods unchanged and adds scheduled execution support:

- `scanDueRules()` selects all enabled, non-deleted due rules.
- Each due rule is scanned with a synthetic system audit context using the rule company and account book.
- Existing rule-hit upsert and active-ticket de-duplication remain the source of truth.

`ExceptionRuleScheduler` runs with Spring scheduling when `erp.exception-rule.scheduler.enabled=true`. It calls:

- `ExceptionRuleService.scanDueRules()`
- `ExceptionTicketService.escalateOverdueTickets(now)`

The scheduler catches and logs failures so one failed cycle does not stop future cycles.

## Notification Design

`NotificationService` gets a generic business notification method so exception tickets can reuse `sys_notification` and `sys_notification_recipient` without coupling notification storage to workflow-only models.

`ExceptionTicketService` emits notifications after durable ticket changes:

- `EXCEPTION_TICKET_CREATED`: sent to `assigneeUserId` when present.
- `EXCEPTION_TICKET_ASSIGNED`: sent to the new assignee.
- `EXCEPTION_TICKET_STARTED`, `EXCEPTION_TICKET_RESOLVED`, `EXCEPTION_TICKET_CLOSED`: sent to distinct non-system users among assignee and creator.
- `EXCEPTION_TICKET_ESCALATED`: sent to distinct non-system users among assignee and creator.

Ticket target URL is `/exception-tickets?keyword=<ticketNo>`.

## Overdue Escalation

Escalation scans active tickets where:

- `status in ('OPEN', 'PROCESSING')`
- `due_time <= now`
- no existing `ESCALATE` event exists for the ticket

Priority moves one step:

- `LOW -> MEDIUM`
- `MEDIUM -> HIGH`
- `HIGH -> URGENT`
- `URGENT -> URGENT`

Escalation writes a ticket event even if the priority was already `URGENT`, so the ticket is not escalated repeatedly.

## Frontend Design

The existing `/exception-rules` page adds schedule controls to the rule configuration dialog and rule table:

- Scan interval in minutes.
- Next scan time.
- Last scan result remains visible.

The UI stays dense and operational, matching the Element Plus console style already used in this project.

## Testing

Backend tests cover:

- Updating a rule scan interval validates range and returns schedule fields.
- Scanning a due rule updates `nextScanTime`.
- Scheduled scan works without a logged-in user by using synthetic audit metadata.
- Creating and assigning exception tickets creates notifications.
- Overdue ticket escalation raises priority once and creates one `ESCALATE` event.
- Scheduler is disabled under the test profile.

Frontend checks cover:

- `npm run type-check`
- `npm run build`
