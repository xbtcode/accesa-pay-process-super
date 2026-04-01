# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

SEPA Instant Payment system for retail POS terminals. A customer scans a QR code at checkout, enters their IBAN, and the backend submits a SEPA pain.001 to a (mock) bank. Four services compose the system:

| Service | Stack | Port |
|---|---|---|
| payment-backend | Spring Boot 3.2, Java 17, MongoDB, Maven | 8080 |
| bank-mock | Spring Boot 3.2, Java 17, Maven | 8081 |
| retailer-app | React 19, TypeScript, Vite | 5173 |
| customer-app | React 19, TypeScript, Vite | 5174 |

## Build & Run Commands

### Full stack (Docker)
```
docker-compose up --build
```

### Local development
```bash
# 1. MongoDB
docker-compose up mongo

# 2. Backend services (each in its own terminal)
cd bank-mock && mvn spring-boot:run
cd payment-backend && mvn spring-boot:run

# 3. Frontend apps (each in its own terminal)
cd retailer-app && npm run dev
cd customer-app && npm run dev
```

### Build / Lint / Test
```bash
# Java services
cd payment-backend && mvn clean package    # build + tests
cd payment-backend && mvn test             # tests only
cd payment-backend && mvn test -Dtest=ClassName#methodName  # single test
cd bank-mock && mvn clean package

# Frontend apps
cd retailer-app && npm run build   # tsc + vite build
cd retailer-app && npm run lint    # eslint
cd customer-app && npm run build
cd customer-app && npm run lint
```

## Project Memory (`memory/` folder)

This project uses an in-repo `memory/` folder for persistent knowledge. **Always** follow these rules:

- **Before starting architecture research or planning**, read the relevant files in `memory/architecture/`, `memory/research/`, and `memory/planning/` to understand existing context and decisions.
- **When producing new architecture docs, research findings, or implementation plans**, write them into the appropriate subfolder:
  - `memory/architecture/` — system design, component relationships, data models, integration patterns
  - `memory/research/` — investigation results, technology evaluations, spike findings
  - `memory/planning/` — implementation plans, task breakdowns, migration strategies
  - `memory/reviews/` — code review findings from the reviewer agent
  - `memory/status/` — agent completion status files (each agent writes `<agent-name>-latest.md`)
- **Update existing memory files** when decisions change rather than creating duplicates.
- **File naming convention:** `YYYY-MM-DD-<descriptive-slug>.md`
- These files are the source of truth for cross-session context. Do not rely solely on conversation history.

## Team Leader Protocol

This project uses a **team leader** pattern. The main Claude instance coordinates work but NEVER directly edits source code or test files.

### Rules for the Team Leader (main instance)
1. **Never edit source files directly.** All code and test changes go through the `implementer` agent.
2. **Coordinate by dispatching agents** from `.claude/agents/` for specific tasks.
3. **Monitor results** by reading `memory/status/<agent-name>-latest.md` after each agent completes.
4. **Present summaries** to the user after reviewing agent outputs.
5. **Get user approval** before re-dispatching agents for fixes or proceeding to the next phase.

### Team Members (agents)

| Agent | Role | Model | Writes to |
|---|---|---|---|
| `architect` | Architecture decisions, API contracts, data models | opus | `memory/architecture/` |
| `researcher` | Technology evaluation, spikes, investigations | sonnet | `memory/research/` |
| `planner` | Task breakdowns, implementation checklists | sonnet | `memory/planning/` |
| `implementer` | Source code and tests (the ONLY agent that edits code) | sonnet | source files + `memory/planning/` (checklist updates) |
| `reviewer` | Code review for quality, security, correctness | opus | `memory/reviews/` |

### Agent Dispatch Order for Features
1. **Architect** (if new patterns/components needed)
2. **Planner** (always) — present plan to user for approval
3. **Implementer** (always) — single attempt, then report status
4. **Reviewer** (always) — present findings to user
5. **Implementer** again (if reviewer found CRITICAL issues, with user approval)

### Quality Gates
A feature is not complete until:
- A plan exists in `memory/planning/` with all checklist items marked `[x]`
- The reviewer found no CRITICAL issues (or they were fixed and re-reviewed)
- Build and tests pass

### Available Skills
- `/research-topic <topic>` — dispatch researcher to investigate a topic
- `/implement-feature <description>` — full feature pipeline (architect → plan → implement → review)
- `/review-code [scope]` — dispatch reviewer for code review

### Status File Format
All agents write a status file to `memory/status/<agent-name>-latest.md` on completion:
```
# Status: <agent-name>
Date: YYYY-MM-DD HH:MM
Task: <brief description>
Result: SUCCESS | PARTIAL | FAILED
Output: memory/<subfolder>/<filename>.md
Notes: <blockers or follow-ups>
```

## Architecture

### Payment Flow (state machine)
`INITIATED → QR_GENERATED → CUSTOMER_OPENED → CUSTOMER_CONFIRMED → PROCESSING → APPROVED | REJECTED`

Transactions can also move to `CANCELLED` (by merchant), `EXPIRED` (TTL-based, default 5 min), or `FAILED` (bank communication error). Allowed transitions are enforced in `TransactionStatus.canTransitionTo()`.

### Backend structure (payment-backend)
- **Controllers**: `MerchantTransactionController` (retailer-facing, secured by API key) and `CustomerPaymentController` (customer-facing, public via transaction ref)
- **Security**: `ApiKeyAuthFilter` guards `/api/v1/transactions/**` routes. API key is passed via `X-API-Key` header and resolved to a merchant via MongoDB. Customer endpoints (`/api/v1/payment/**`) are unauthenticated.
- **Service layer**: `TransactionService` orchestrates the full payment lifecycle. `BankIntegrationService` handles the REST call to bank-mock.
- **Domain**: Lombok `@Builder` data classes stored in MongoDB. `PaymentTransaction` is the aggregate root with embedded `CreditorInfo`, `DebtorInfo`, `BankIntegrationInfo`, and `StatusHistoryEntry` list.
- **Scheduler**: `ExpirationScheduler` periodically marks stale transactions as `EXPIRED`.

### Bank Mock
Simulates SEPA Instant with configurable delay (200-800ms). Rejection rules: IBAN ending `9999` → INSUFFICIENT_FUNDS, `8888` → ACCOUNT_BLOCKED, amount > 15000 → AMOUNT_EXCEEDS_LIMIT.

### Frontend apps
- **retailer-app**: Single-page flow — enter amount → show QR code → poll for result. Uses `qrcode.react` for QR generation and axios for API calls.
- **customer-app**: Route-based (`react-router-dom`) — `/pay/:ref` loads payment details → customer enters IBAN → confirms → sees result. Includes client-side IBAN validation.

### Test API Key
`sk_test_lidl_001` — pass as `X-API-Key` header for merchant endpoints.

### MongoDB Init
`mongo-init/init-mongo.js` seeds the database with test merchant data on first container start.
