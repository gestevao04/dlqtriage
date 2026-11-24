# Spec 006 — MCP server
Status: Draft | Depends on: 004, 005

## Context
Expose the triage through the Model Context Protocol so it can be operated
from an MCP client (for example Claude): query failures, read diagnoses and
approve or reject actions. This is the author's first MCP server and the
centerpiece of the public demo.

## User story
As an on-call developer, I want to talk to an MCP client to list failures,
understand diagnoses and approve reprocessing without opening the database.

## Acceptance criteria
- [ ] The MCP server starts with the application (transport decided in the
  spec work; ASSUMPTION: HTTP/SSE with a static token).
- [ ] Read tools: `list_failures` (filters: status, class, period),
  `get_failure`, `get_diagnosis`, `get_run_trace_summary`.
- [ ] Restricted write tools: `approve_action` and `reject_action` (require
  `approval_id` and a justification; they reuse the spec 004 use cases).
- [ ] No MCP tool reprocesses outside the approval flow.
- [ ] Every MCP call is audited (who, when, what).
- [ ] Inputs are schema-validated; errors return a structured message.
- [ ] A contract test exercises every tool with a test MCP client.
- [ ] Docs explain how to connect a local MCP client.
- [ ] `docs/DEMO.md` is complete and the end-to-end demo has been run and
  recorded (failure -> diagnosis -> approval via MCP -> republish).

## Out of scope
OAuth, multi-user, discard tools.

## Technical notes and dependencies
- Prefer the Spring AI MCP server starter if it fits the stack; otherwise
  the official Kotlin/Java SDK (needs dependency approval).
- Read tools truncate payloads and mask sensitive fields.

## Test plan
- Unit: input validation and result mapping.
- Integration: test MCP client against the app in Testcontainers.
- Manual: connect a real MCP client and approve a pending action.

## DoD
Criteria ticked; recorded end-to-end demo linked from README; tests and lint
green.