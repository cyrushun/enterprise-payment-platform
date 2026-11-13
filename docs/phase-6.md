# Phase 6 — Frontend Application and Full-Stack Integration

This is the single source of truth for Phase 6. It records milestones in chronological order (6.0 through 6.5), the API contracts, security model, verification commands and deployment integration. Update this document and the root `README.md` whenever a Phase 6 implementation or status changes. Do not create competing Phase 6 runbooks.

## Objective

Deliver a modern fintech web experience with two independently navigable workspaces:

- **Customer Portal:** account overview, balances, fund transfers, transaction history and notifications.
- **Operations Portal:** transfer oversight, customer/account lookup, reconciliation, audit logs, outbox/notification monitoring and dependency health.

The frontend is a presentation/client layer. Spring Boot remains authoritative for identity, authorization, ownership checks, transfer validation, idempotency, ledger posting, reconciliation and operational actions. Existing Phase 0–5 backend behavior and Flyway migrations V1–V7 must be preserved.

## Technology baseline

| Area | Technology |
|---|---|
| Framework | Next.js 15 App Router |
| Language | TypeScript, strict mode |
| UI styling | Tailwind CSS 3 |
| Icons | lucide-react |
| Server state | TanStack Query |
| API transport | Fetch API, same-origin `/api` proxy |
| Frontend tests | Vitest and Playwright Chromium |
| Runtime | Node.js 22 LTS |
| Container | Multi-stage Node Dockerfile, standalone Next output |

## Milestones and status

| Milestone | Scope | Status |
|---|---|---|
| 6.0 | Frontend foundation, responsive portal shells, route/navigation structure, API client/contracts and project documentation | User-verified complete |
| 6.1 | Authentication, encrypted HttpOnly session, BFF token handling, refresh rotation, RBAC route gates and logout | User-verified complete |
| 6.2 | Customer account listing, balances, transfer initiation/history, activity and notifications | User-verified complete |
| 6.3 | Operations dashboard, customer/account lookup, transfer investigation, reconciliation, audit, outbox, notification monitoring and controlled Kafka DLT replay | User-verified complete |
| 6.4 | Unit/utility tests, Playwright E2E, role/session security, API integration, accessibility and responsive verification | User-verified complete |
| 6.5 | Docker Compose, Nginx reverse proxy, frontend/API image publishing, staging/production deployment and rollback integration | User-verified complete |

The user has reported successful verification of Phases 6.0–6.5. Phase 6.5 deployment verification is complete according to the user. The commands and checks in this guide remain as repeatable regression procedures for future changes; they should not be interpreted as outstanding phase work.

**Overall Phase 6 status: 6.0–6.5 user-verified complete.** All frontend implementation, quality/integration testing, and deployment/CI/CD milestones are complete. This guide retains the original implementation detail and repeatable regression/deployment procedures.

## Phase 6.0 delivered structure

```text
frontend/
├── src/app/
│   ├── components/             # Shared portal shell and integration placeholders
│   ├── customer/dashboard/     # Customer dashboard shell
│   ├── operations/dashboard/   # Operations dashboard shell
│   ├── globals.css
│   ├── layout.tsx
│   ├── page.tsx                # Portal selector
│   └── providers.tsx           # TanStack Query provider
├── src/lib/api/                # Typed fetch client and API endpoint constants
├── src/lib/navigation/         # Customer and operations navigation definitions
├── src/types/                  # Backend DTO contracts
├── tests/                      # Initial navigation tests
├── Dockerfile                  # Standalone production image foundation
├── next.config.ts              # Same-origin API rewrite
└── package.json
```

The Phase 6.0 baseline provided portal shells and explicit API integration placeholders. Phase 6.1 adds secure sign-in/session handling and role-aware route protection. Phase 6.2 replaces customer placeholders with authenticated API-backed account, balance, transfer, history and notification views. Operations navigation is enabled for authorized OPERATIONS and ADMIN users in Phase 6.3; customer users remain redirected away from the operations workspace.

## Phase 6.1 implementation details

- `POST /api/session/login` validates same-origin requests, calls the existing backend login endpoint, calls `/api/v1/auth/me` to obtain the verified identity and roles, and sets the encrypted session cookie.
- `GET /api/session/me` returns only the current user profile and roles; it never returns JWT values.
- `POST /api/session/logout` revokes the refresh token through the backend and expires the cookie.
- `app/api/backend/[...path]` is the authenticated BFF proxy. It adds the bearer token server-side, refreshes near-expiry sessions, retries an upstream 401 with rotated tokens, and forwards only selected response headers.
- Customer routes require the CUSTOMER role. Operations routes require OPERATIONS or ADMIN. The backend independently authorizes every request.
- `GET /api/v1/auth/me` has an additive `roles` array sourced from the signed JWT claim. Existing response fields remain unchanged.

## Phase 6.2 implementation details

- `GET /api/v1/accounts` returns accounts for the authenticated customer's `customer_id` claim. A customer-supplied `customerId` query parameter is ignored for customer sessions, preventing cross-customer discovery. OPERATIONS/ADMIN callers must specify `customerId`.
- The customer portal loads account list and balances from this endpoint, and loads history through `GET /api/v1/accounts/{accountId}/transfers`.
- Transfer submission uses the existing `POST /api/v1/transfers` contract, including `Idempotency-Key`, `sourceAccountId`, `destinationAccountId`, `amount`, `currency` and `description`. The UI retains an idempotency key when a submission fails so the user can safely retry the same submission; a successful submission receives a new key.
- Notifications use `GET /api/v1/notifications?accountId={accountId}&page=0&size=20`. The controller now verifies customer ownership via `OwnershipService`; OPERATIONS/ADMIN retain privileged access.
- The customer overview displays the selected account balance returned by the API; it does not aggregate balances or perform foreign-exchange conversion in the browser.
- Customer account-list, transfer, activity and notification screens are API-backed. No fabricated balances or transactions are used.

## Phase 6.3 — Operations & Administration Portal

The operations portal is now connected to the existing privileged backend APIs. The portal remains a client/presentation layer; backend role checks and resource authorization are authoritative.

| Screen | Capability | Backend contract |
|---|---|---|
| Operations overview | Recent reconciliation runs, outbox queue snapshot and sanitized dependency health | `GET /api/v1/reconciliation/runs`, `GET /api/v1/outbox/events`, server-side BFF `GET /api/operations/health` → `GET /actuator/health` |
| Customer lookup | Retrieve one customer by UUID and list that customer's accounts | `GET /api/v1/customers/{customerId}`, `GET /api/v1/accounts?customerId={id}` |
| Transfer investigation | Retrieve a transfer by exact reference or UUID | `GET /api/v1/transfers/reference/{reference}`, `GET /api/v1/transfers/{id}` |
| Reconciliation | View runs, trigger a read-only reconciliation, inspect persisted findings | `GET /api/v1/reconciliation/runs`, `POST /api/v1/reconciliation/run`, `GET /api/v1/reconciliation/runs/{runId}/discrepancies` |
| Audit trail | Paginated audit records | `GET /api/v1/audit-logs` |
| Outbox operations | Paginated event delivery state, inspect payload/error, replay eligible DEAD event | `GET /api/v1/outbox/events`, `POST /api/v1/outbox/events/{id}/replay` |
| Notification monitoring | Paginated notifications for a supplied account UUID | `GET /api/v1/notifications?accountId={id}` |
| Kafka DLT replay | Manually republish a reviewed event key/payload to configured transfer topic | `POST /api/v1/operations/kafka-dlt/replay` |

### Backend contract limitations (intentionally surfaced in the UI)

- There is no customer search/list endpoint. The screen supports exact customer UUID lookup only; it does not fabricate search results.
- There is no all-transfers search/list endpoint. Transfer investigation supports exact transfer UUID or reference lookup only.
- Notification monitoring requires an account UUID. Use customer lookup to obtain an account ID before querying notifications.
- The overview's outbox metric is based on the currently fetched page, not a global aggregate. It is labelled accordingly and must not be treated as a full queue count.
- Dependency health is fetched by a role-checked Next.js server route and reduced to top-level/component status only. Actuator detail payloads are not returned to browser JavaScript.
- The frontend does not implement automatic remediation, balance adjustment, customer status mutation, or arbitrary transfer reversal.
- Reconciliation is read-only. It reports discrepancies and persists findings; it does not repair balances.
- Outbox replay is presented only for `DEAD` events; the backend remains responsible for final eligibility checks.
- Kafka DLT replay requires explicit operator review and confirmation. The UI does not claim to browse Kafka DLT contents; the operator must supply the event key and payload obtained from the approved operational process.

### Security hardening

`GET /api/v1/customers/{customerId}` now has method-level `OPERATIONS`/`ADMIN` authorization, in addition to the operations route guard. This closes the gap where the exact-ID customer lookup was previously reachable by any authenticated role. Other privileged APIs continue to rely on the existing Spring Security role matchers. UI visibility is not an authorization boundary.

## Phase 6.4 — Frontend quality and integration test foundation

Added automated checks:

- Vitest utility tests for UUID validation, MYR formatting and the strict `DEAD`-only outbox replay affordance.
- Existing navigation and encrypted-session tests remain part of the regression suite.
- Playwright Chromium E2E tests cover anonymous route protection, optional authenticated role checks for OPERATIONS and CUSTOMER users, mobile navigation visibility, and token non-exposure.
- Authenticated E2E tests assert that JWT values are not exposed in localStorage/sessionStorage or readable document cookies.

### Phase 6.4 local verification (Windows PowerShell)

```powershell
cd frontend
npm install
npx playwright install chromium
npm run typecheck
npm run lint
npm test
npm run build
npm run dev
```

In a second terminal:

```powershell
cd frontend
npm run test:e2e
```

To run authenticated role tests, set the following environment variables in the terminal before running Playwright. Use dedicated non-production test identities; never put passwords in committed files or CI logs.

```powershell
$env:E2E_OPERATIONS_EMAIL = "operations-test@example.test"
$env:E2E_OPERATIONS_PASSWORD = "<test-password>"
$env:E2E_CUSTOMER_EMAIL = "customer-test@example.test"
$env:E2E_CUSTOMER_PASSWORD = "<test-password>"
npm run test:e2e
```

The authenticated tests are skipped when the corresponding credentials are not configured. The application must be running with a reachable Spring Boot API and a valid local `SESSION_SECRET`. E2E tests use the real login flow and should run against a dedicated test database. Do not run reconciliation or replay actions against production data as part of browser tests.

### Verification status

**User-verified complete.** Phases 6.3 and 6.4 have been verified successfully. Keep the commands above as repeatable regression procedures. For future changes, confirm the API role guard returns 403 for a CUSTOMER token on `GET /api/v1/customers/{customerId}`, and that OPERATIONS/ADMIN can perform the lookup. Also regression-test reconciliation, outbox replay and Kafka DLT replay against a disposable local environment. For responsive/accessibility review, test keyboard-only navigation, visible focus states, labelled form controls, readable status/error announcements, 390px mobile viewport navigation, horizontal table scrolling, and browser zoom at 200%.

## Phase 6.5 — Frontend production deployment and CI/CD integration

Phase 6.5 packages the Next.js customer/operations portals as a non-root standalone Node.js image and deploys it alongside the Spring Boot API. The existing API image, PostgreSQL, Kafka, Redis, observability stack, and deployment scripts remain the foundation.

### Runtime topology

- `frontend`: Next.js standalone server, internal port 3000; no public host port.
- `frontend-proxy`: Nginx edge proxy, host loopback port `${FRONTEND_HTTP_PORT:-8088}`; forwards browser traffic to Next.js.
- The Next.js BFF calls `http://payment-api:8080` over the Compose network. Browser clients do not call the internal API service directly.
- TLS must terminate at a trusted public load balancer/reverse proxy in front of the loopback-bound Nginx listener. Do not expose the plain HTTP listener publicly. Configure the external proxy to forward `X-Forwarded-Proto: https`.
- Nginx applies baseline security headers and a conservative login endpoint rate limit. Application authentication, role checks, ownership checks and rate limits remain enforced by Spring Boot/Next.js as appropriate.

### Required deployment secrets

Add a unique `FRONTEND_SESSION_SECRET` (at least 32 characters) to the deployment VM's `.env`.

**PowerShell compatibility note:** Do not use `[Security.Cryptography.RandomNumberGenerator]::GetBytes(48)` on Windows PowerShell/.NET versions where that static method is unavailable. Use the instance-based API below; it works with older Windows PowerShell/.NET versions:

```powershell
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$bytes = New-Object byte[] 48
$rng.GetBytes($bytes)
$secret = [Convert]::ToBase64String($bytes)
$rng.Dispose()
$secret
```

Copy the generated output to `FRONTEND_SESSION_SECRET` in the VM `.env`; do not commit a real deployment secret, put it in a `NEXT_PUBLIC_*` variable, or print it in CI logs. Keep it stable across deployments. Rotating it invalidates all existing encrypted browser sessions. `scripts/prepare-linux-deployment.sh` validates presence and minimum length without displaying the value. The fixed value shown in the checked-in `.env.example` is only a public local-development sample and must be replaced for every shared/staging/production environment.

### Local container deployment (Windows PowerShell)

1. Copy `.env.example` to `.env` and configure all existing backend/observability values plus a unique `FRONTEND_SESSION_SECRET`.
2. Ensure the JWT key files are available under `.secrets/` as required by the existing API deployment profile.
3. Validate the Compose model and start the full deployment profile:

```powershell
docker compose --env-file .env --profile deployment config --quiet
docker compose --env-file .env --profile deployment up -d --build
docker compose --env-file .env --profile deployment ps
```

4. Verify API readiness and frontend edge health:

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health/readiness
Invoke-WebRequest http://localhost:8088/healthz
.\scripts\verify-frontend-deployment.ps1
```

5. Open `http://localhost:8088`. For production, use HTTPS at the external TLS terminator; do not expose port 8088 directly to the internet.

To stop the application profile without deleting persistent data:

```powershell
docker compose --env-file .env --profile deployment down
```

### CI and image publishing

- The CI workflow runs Maven verification, frontend TypeScript check, lint, Vitest and Next.js production build, then builds both API and frontend container images.
- The publish workflow publishes API and frontend images to GHCR with commit-based tags, scans both images, and records immutable digest references.
- Staging deployment transfers Compose, Nginx and operational scripts to the VM, authenticates to GHCR, then deploys both immutable images.
- Production deployment is manually approved through the `Deploy Production` workflow and requires both the API image digest and frontend image digest from the staged release, plus the matching full source commit SHA.
- Configure the existing staging/production GitHub Environments and SSH/GHCR secrets described in `docs/phase-5.md`. Also configure `FRONTEND_SESSION_SECRET` on each VM in its protected `.env`; the value is environment-specific and is not sent through GitHub Actions.

### Health checks and rollback

- Frontend container health: `GET /api/health` inside the Compose network.
- Nginx edge health: `GET /healthz` on the loopback listener.
- API health: existing `/actuator/health/readiness`.
- `scripts/deploy-linux-vm.sh` records previous API and frontend image references and verifies both API readiness and frontend edge health. If either readiness check fails, it attempts to restore both prior images.
- `scripts/rollback-linux-vm.sh` restores the previously recorded API and frontend image pair. Database rollback is not automatic; use the existing backup/restore and migration recovery procedures.

### Phase 6.5 verification boundaries

**User-verified complete.** The user has confirmed Phase 6.5 deployment verification. The local Compose, browser smoke-test, health-check, staging and rollback instructions above are retained for repeatable operational checks after future changes. Confirm that the external HTTPS proxy preserves `Host`, `X-Forwarded-Proto`, and forwarded client IP headers whenever the deployment topology changes.

## Verified backend contract and frontend implications

The Phase 5.4 backend source was inspected before defining frontend endpoint constants. Phase 6.2 adds an account-list endpoint and an ownership guard for notifications. Current controller mappings include:

| Capability | Existing API | Notes |
|---|---|---|
| Register | `POST /api/v1/auth/register` | Public; assigns CUSTOMER role |
| Login | `POST /api/v1/auth/login` | Returns access and refresh tokens in JSON |
| Refresh | `POST /api/v1/auth/refresh` | Refresh token supplied in JSON body; rotation is implemented |
| Logout | `POST /api/v1/auth/logout` | Refresh token supplied in JSON body |
| Current identity | `GET /api/v1/auth/me` | Requires bearer access token; response now includes the JWT roles claim as `roles` (additive field) |
| Account list | `GET /api/v1/accounts` | Phase 6.2 additive endpoint; CUSTOMER token is always scoped to its own `customer_id` claim. OPERATIONS/ADMIN must provide `customerId` for lookup. |
| Account details | `GET /api/v1/accounts/{accountId}` | Requires account ownership or privileged role |
| Account balance | `GET /api/v1/accounts/{accountId}/balance` | Requires account ownership or privileged role |
| Create account | `POST /api/v1/accounts` | Requires customer ownership or privileged role |
| Create transfer | `POST /api/v1/transfers` | Requires `Idempotency-Key`; request uses `sourceAccountId`, `destinationAccountId`, `amount`, `currency`, `description` |
| Transfer details | `GET /api/v1/transfers/{id}` | Ownership enforced for customer |
| Transfer by reference | `GET /api/v1/transfers/reference/{reference}` | Ownership enforced for customer |
| Account transfer history | `GET /api/v1/accounts/{accountId}/transfers` | Pageable; not `/transactions` |
| Audit logs | `GET /api/v1/audit-logs` | OPERATIONS/ADMIN |
| Reconciliation runs | `GET /api/v1/reconciliation/runs` | OPERATIONS/ADMIN |
| Reconciliation discrepancies | `GET /api/v1/reconciliation/runs/{runId}/discrepancies` | OPERATIONS/ADMIN |
| Run reconciliation | `POST /api/v1/reconciliation/run` | OPERATIONS/ADMIN; no request body |
| Outbox events | `GET /api/v1/outbox/events` | OPERATIONS/ADMIN; pageable |
| Replay outbox event | `POST /api/v1/outbox/events/{id}/replay` | OPERATIONS/ADMIN; backend permits eligible DEAD rows only |
| Notifications | `GET /api/v1/notifications?accountId={id}` | Requires account ID; pageable; Phase 6.2 adds customer account-ownership enforcement, while OPERATIONS/ADMIN may inspect accounts |
| Kafka DLT replay | `POST /api/v1/operations/kafka-dlt/replay` | OPERATIONS/ADMIN; controlled eventKey/payload body |

### Backend API limitations and frontend implications

- There is no customer-list/search endpoint in the inspected baseline. Phase 6.3 therefore supports exact customer UUID lookup only. The customer account-list gap has been addressed in Phase 6.2 with `GET /api/v1/accounts`.
- The frontend API DTOs must continue to match Java records. In particular, transfer responses use `id`, `reference`, `sourceAccountId`, `destinationAccountId`, `amount`, `currency`, `status`, and `createdAt`.
- The existing transfer request requires an `Idempotency-Key` header and uses `sourceAccountId`/`destinationAccountId`; never use the older illustrative `fromAccountId`/`toAccountId` API-spec example.

## Authentication and session security constraints

The existing backend returns both access and refresh tokens in JSON and does not set an HttpOnly cookie. Phase 6.1 implements a Next.js BFF: login calls the backend server-to-server, then stores the tokens in an AES-256-GCM authenticated-encryption cookie marked HttpOnly, SameSite=Lax, path=/ and Secure in production. The browser never receives either token in JavaScript or browser storage. The session cookie is bounded to the backend refresh-token lifetime (7 days in the current baseline). The BFF refreshes access tokens shortly before expiry and retries an upstream 401 after token rotation. A per-Node-process single-flight guard coalesces concurrent refresh attempts for the same refresh token to avoid refresh-family reuse detection during parallel page queries. Mutating BFF requests enforce same-origin Origin checks. Logout revokes the refresh token at the backend and clears the cookie.

`SESSION_SECRET` must be server-only, at least 32 characters, and configured in `frontend/.env.local` for local development. For the deployment container, supply the same kind of secret through `FRONTEND_SESSION_SECRET` in the protected VM `.env`. Never use a `NEXT_PUBLIC_` prefix. The literal value committed in `.env.example` files is a public development sample only; replace it before use in any shared, staging or production environment. Changing the secret invalidates all existing browser sessions. The refresh single-flight guard is process-local; a horizontally scaled/multi-instance Next.js deployment must add a shared session store/refresh lock or otherwise guarantee single-instance refresh ownership before enabling multiple replicas. Cookie encryption protects confidentiality/integrity but does not replace TLS, secure host configuration, backend RBAC or resource ownership enforcement. The UI role checks are navigation/UX gates only; Spring Security remains authoritative.

The backend grants public self-registration only the CUSTOMER role. OPERATIONS and ADMIN identities must be provisioned through the existing controlled process; never add a frontend role selector that grants privileges.

### Phase 6.1–6.2 regression verification (previously user-verified; rerun after frontend changes)

```powershell
cd frontend
Copy-Item .env.example .env.local
# Replace the public sample SESSION_SECRET with a unique secret for this environment.
npm install
npm run typecheck
npm run lint
npm test
npm run build
npm run dev
```

Manual checks: sign in with a CUSTOMER test identity; verify customer routes work; verify an OPERATIONS identity is redirected away from customer routes and can open the operations overview; verify a CUSTOMER cannot open operations routes; sign out and confirm protected routes redirect to login; confirm browser storage contains no JWT; confirm the session cookie is HttpOnly; expire/revoke a refresh token and confirm the BFF clears the session after refresh fails.

### Postman coverage added in Phase 6

The Phase 1–5 Postman collections remain the canonical regression suites for authentication, account details and balances, transfers, transfer history, notifications, reconciliation, audit, outbox and Kafka DLT operations. The two Phase 6 collections are deliberately limited to API coverage that was not present in those earlier collections; they do not repeat login, identity, transfer, notification or operations workflows.

| Collection | Additive requests | Required token |
|---|---|---|
| `Enterprise-Payment-Platform-Phase6.postman_collection.json` | `GET /api/v1/accounts` for the authenticated CUSTOMER and `GET /api/v1/accounts?customerId={otherCustomerId}` to prove the CUSTOMER cannot widen their account scope. | CUSTOMER |
| `Enterprise-Payment-Platform-Phase6.3-Operations.postman_collection.json` | `GET /api/v1/accounts?customerId={customerId}` for privileged account lookup and `GET /api/v1/customers/{customerId}` using a CUSTOMER token, expecting HTTP 403. | OPERATIONS/ADMIN for the first request; CUSTOMER for the second |

Before running the customer collection, execute the Phase 4 CUSTOMER login request and copy its `accessToken` into the Phase 6 collection variable `accessToken`. Set `customerId` to the corresponding customer UUID and `otherCustomerId` to a different existing customer UUID.

Before running the operations collection, copy the Phase 4 OPERATIONS or ADMIN token into `operationsAccessToken`, and copy the Phase 4 CUSTOMER token into `customerAccessToken`. Set `customerId` to an existing customer UUID. The first request uses the privileged token; the second request overrides it with the CUSTOMER token and must return HTTP 403. These focused collections do not perform login/logout or mutate financial data.

The Phase 6.5 deployment integration is documented below. The user has verified all Phase 6 milestones, 6.0–6.5.

## Local development

Prerequisites: Node.js 22 LTS, npm 10+, and (for API integration) the Spring Boot API running on port 8080.

```powershell
cd frontend
Copy-Item .env.example .env.local
# .env.example contains a public local-only sample. Replace SESSION_SECRET with a unique secret before using a shared environment.
npm install
npm run dev
```

Open `http://localhost:3000`. The Next.js BFF forwards authenticated requests to `API_PROXY_TARGET` (default `http://127.0.0.1:8080`) and keeps access/refresh tokens server-side in the encrypted HttpOnly session cookie.

## Phase 6.0 verification (user marked complete)

```powershell
cd frontend
npm run typecheck
npm run lint
npm test
npm run build
```

The user has reported Phase 6.0 verification complete. Keep these commands as repeatable regression checks when changing the frontend.

## Cross-phase security and implementation boundaries

## Non-goals and safety boundaries

- This is an educational simulated banking application, not a real-money payment service.
- Do not move business rules, balances, ledger logic, transfer validation or reconciliation into the browser.
- Do not expose JWT private keys, database credentials or service credentials to frontend build arguments or `NEXT_PUBLIC_*` variables.
- Do not log tokens, passwords, full account data or sensitive payloads.
- Do not alter applied Flyway migrations V1–V7. Any future backend API additions require a new append-only migration only if schema changes are needed, plus tests and API documentation.
- Do not describe the operations preview dashboard as live data until its Phase 6.3 API integrations are complete.
