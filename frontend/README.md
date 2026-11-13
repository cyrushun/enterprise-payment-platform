# Enterprise Payment Platform Frontend

Next.js 15 App Router, TypeScript, Tailwind CSS, TanStack Query and a server-side BFF for the Enterprise Payment Platform.

## Phase status

| Phase | Scope | Status |
|---|---|---|
| 6.0 | Frontend foundation and portal shells | User-verified complete |
| 6.1 | Authentication, encrypted BFF session, refresh rotation and RBAC | User-verified complete |
| 6.2 | Customer banking portal | User-verified complete |
| 6.3 | Operations and administration portal | User-verified complete |
| 6.4 | Frontend quality and integration testing | User-verified complete |
| 6.5 | Docker, Nginx and CI/CD deployment integration | User-verified complete |

Update this status table and the root `README.md` whenever frontend phase scope or verification status changes. The canonical implementation guide is [`../docs/phase-6.md`](../docs/phase-6.md).

**Overall status: Phases 6.0–6.5 are user-verified complete**, including frontend deployment and CI/CD. The commands below remain available for repeatable regression checks.

## Local setup

```powershell
Copy-Item .env.example .env.local
# .env.example has a public local-only sample. Replace it before shared/staging/production use.
npm install
npm run dev
```

Open `http://localhost:3000`. The Spring Boot API should be running at the `API_PROXY_TARGET` URL (default `http://127.0.0.1:8080`).

## Security model

- Browser code calls the same-origin Next.js BFF; it never receives bearer or refresh tokens.
- Login stores backend tokens inside an AES-256-GCM encrypted, HttpOnly, SameSite=Lax cookie. The cookie is Secure when `NODE_ENV=production`.
- `SESSION_SECRET` is mandatory, server-only and at least 32 characters. The committed `.env.example` value is a public local-development sample only; replace it with a unique secret for every shared/staging/production environment. Do not commit `.env.local` or expose it as `NEXT_PUBLIC_*`.
- The BFF refreshes tokens server-side and revokes refresh tokens on logout.
- Frontend role checks are UX gates. Spring Security and ownership checks remain authoritative.

## Implemented routes

- `/login` — backend authentication through the session route.
- `/customer/dashboard` — account summary, transfer activity and transfer form.
- `/customer/accounts` — customer-owned account list and balances.
- `/customer/transfers` — transfer workflow.
- `/customer/activity` — account transfer history.
- `/customer/notifications` — account notifications.
- `/operations` — protected Operations/Admin workspace, including dashboard, customer lookup, transfer investigation, reconciliation, audit, outbox, notifications and controlled Kafka DLT replay.

## Verification

```powershell
npm run typecheck
npm run lint
npm test
npm run build
```

The Phase 6.0–6.5 implementation and manual verification checklist are maintained in [`../docs/phase-6.md`](../docs/phase-6.md).


## Phase 6.3–6.4 Operations and quality verification

The Operations Portal is available to OPERATIONS and ADMIN roles at `/operations`. It provides exact-ID customer lookup, transfer lookup by reference/UUID, reconciliation run/discrepancy review, audit browsing, outbox inspection and eligible DEAD-event replay, account notification inspection, and manually controlled Kafka DLT replay. The backend remains authoritative for role checks and replay eligibility.

The backend currently has no customer search endpoint or all-transfers search endpoint; the UI intentionally exposes only the exact lookups supported by the API. Customer lookup is protected with method-level OPERATIONS/ADMIN authorization.

Run checks from this directory:

```powershell
npm install
npx playwright install chromium
npm run typecheck
npm run lint
npm test
npm run build
npm run dev
```

In another terminal run `npm run test:e2e`. Authenticated E2E role checks are enabled by setting `E2E_OPERATIONS_EMAIL`, `E2E_OPERATIONS_PASSWORD`, `E2E_CUSTOMER_EMAIL`, and `E2E_CUSTOMER_PASSWORD` in the process environment. Use dedicated local test users and a disposable test database. See `../docs/phase-6.md` for test scope, backend contract limitations, and operational safety notes.


## Phase 6.5 production container

The production image uses the Next.js standalone output and runs as an unprivileged user. Compose supplies `API_PROXY_TARGET=http://payment-api:8080` and injects the server-only `SESSION_SECRET` from `FRONTEND_SESSION_SECRET`. Do not expose the frontend container directly; use the Nginx edge service and terminate TLS at the external trusted proxy/load balancer. See [`../docs/phase-6.md`](../docs/phase-6.md#phase-65--frontend-production-deployment-and-cicd-integration) for local deployment, CI/CD, health checks, secret setup and rollback.
