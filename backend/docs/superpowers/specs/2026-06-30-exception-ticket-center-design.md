# Exception Ticket Center Design

## Goal

Build an exception handling center that turns discovered business issues into trackable work items with an owner, state, due time, resolution, and audit trail.

## Scope

The first version supports manual or caller-driven ticket creation. It does not implement automatic rule generation from every dashboard alert yet.

Supported workflow:

- Create an exception ticket with category, priority, title, source document, assignee, and due time.
- Query tickets by keyword, category, priority, status, assignee, source number, and overdue flag.
- Assign or reassign a ticket.
- Start processing a ticket.
- Resolve a ticket with a resolution note.
- Close a resolved ticket.
- View ticket events.

## Backend Design

Add a new domain package:

- `com.tuowei.erp.issue`

Add tables:

- `biz_exception_ticket`
- `biz_exception_ticket_event`

Add endpoints:

- `GET /api/exception-tickets`
- `POST /api/exception-tickets`
- `POST /api/exception-tickets/{id}/assign`
- `POST /api/exception-tickets/{id}/start`
- `POST /api/exception-tickets/{id}/resolve`
- `POST /api/exception-tickets/{id}/close`

Permissions:

- `exception-ticket:view` for list and detail-style data.
- `exception-ticket:manage` for create and state transitions.

All queries must filter by `companyId` and `accountBookId`. State changes use optimistic locking via `version` and write an event row.

## Ticket State Model

Allowed states:

- `OPEN`
- `PROCESSING`
- `RESOLVED`
- `CLOSED`

Allowed transitions:

- `OPEN -> PROCESSING`
- `OPEN -> RESOLVED`
- `PROCESSING -> RESOLVED`
- `RESOLVED -> CLOSED`

Assigning does not change status. Closed tickets are immutable for the first version.
Repeated start on `PROCESSING` and repeated resolve on `RESOLVED` are treated as idempotent actions and still record an event.

## Frontend Design

Add route:

- `/exception-tickets`
- Title: `异常处理`
- Permission: `exception-ticket:view`

The page is a dense operational workbench:

- Filter bar for keyword, status, priority, category, assignee, source number, and overdue-only.
- Summary cards for open, processing, resolved, overdue, high-priority.
- Ticket table with status, priority, category, source, assignee, due time, and operations.
- Create dialog.
- Process dialog for assign, start, resolve, and close.

The UI should use existing Element Plus operational patterns, not a decorative dashboard layout.

## Error Handling

- Blank title is rejected.
- Invalid state transitions return `IllegalArgumentException`.
- Missing tickets return `IllegalArgumentException`.
- Closed tickets reject further state changes.

## Tests

Backend tests:

- Controller permission checks and request binding.
- Service create/list with tenant filters.
- Service state transitions and event creation.
- Invalid transition rejection.

Frontend verification:

- `npm run type-check`
- `npm run build`
