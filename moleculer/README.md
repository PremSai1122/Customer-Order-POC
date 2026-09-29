# Moleculer POC (Node.js)

The Node.js counterpart of the Spring Boot services in this repo, built exactly to [`BUILD_SPEC.md`](BUILD_SPEC.md) — read that file for the full code, the traps that were hit once already, and the reasoning. This file is the shorter, practical summary: how to run it, and confirmation that it works.

## Services

| Folder | Port | Eureka name | Owns |
|---|---|---|---|
| `UserProfileServiceNode/` | 3001 | `USER-PROFILE-SERVICE` | `user_profile` table, Postgres `userprofile_db` |
| `ConsumerServiceNode/` | 3002 | `CONSUMER-SERVICE` | Nothing — looks up the other two in Eureka and calls them with axios |

They're separate npm projects with no shared library (unlike the deleted earlier scaffold under this same folder, which had a `node-utils` package — this build doesn't). The existing Java `customer-service` (8081, Eureka `CUSTOMER-SERVICE`) is called by `ConsumerServiceNode` too, but isn't part of this POC.

## Prerequisites

- Node.js 18+, Postgres running locally with an empty `userprofile_db` (`createdb -U postgres userprofile_db`)
- The real Eureka server: from the repo root, `mvn spring-boot:run -pl discovery-server`
- No Redis, no Docker — nodes talk over Moleculer's built-in TCP transporter, and Eureka is only for discovery, not messaging
- `moleculer/tools/mock-eureka` and `moleculer/tools/fake-customer-service.js` exist for testing without the real Eureka/Java service, but were not needed here — see "How this was tested" below

## Run order

1. Postgres, `userprofile_db` created
2. Eureka: `mvn spring-boot:run -pl discovery-server` (repo root)
3. Java `customer-service`: `mvn spring-boot:run -pl customer-service` (repo root) — or `node moleculer/tools/fake-customer-service.js`
4. `cd moleculer/UserProfileServiceNode && npm install && node src/UserProfileServiceNode/index.js` — wait for `Registered with Eureka as USER-PROFILE-SERVICE`
5. `cd moleculer/ConsumerServiceNode && npm install && node src/ConsumerServiceNode/index.js` — wait for `Registered with Eureka as CONSUMER-SERVICE`

Use `node`, not `npm start`, when stopping with Ctrl+C matters (see `BUILD_SPEC.md` trap 15). Ctrl+C should print `De-registered from Eureka` in each terminal before the process exits.

## How this was tested (2026-09-29)

All 18 acceptance checks from `BUILD_SPEC.md` section 12 were run against **real** infrastructure — the repo's own `discovery-server` and `customer-service` (not the `tools/` mocks) — and **all 18 passed**, including the two checks that are supposed to fail with a 500 (duplicate `userId`, and "not found" — see Known weaknesses below).

Two things worth knowing about that run:

- **Checks 10-12 failed on the first try**, with `"USER-PROFILE-SERVICE is not available in Eureka"`, then passed after waiting. This is `BUILD_SPEC.md` trap 6 in practice: the Consumer's local copy of the registry only refreshes every ~30 seconds, so a Consumer that's just started (even after the target service registered) can't see it until the next refresh.
- **Check 15** (the Java service receiving the same `X-Correlation-Id`) could only be confirmed from the Consumer's own log, not the Java service's, because the real `customer-service` doesn't log incoming headers the way `tools/fake-customer-service.js` does. The header was sent correctly; it just isn't visible server-side with the real service.

Infrastructure started for the test (Eureka, `customer-service`) was stopped again afterward. `userprofile_db` was left in place.

## Known weaknesses (deliberate — see `BUILD_SPEC.md` §13)

Not found and duplicate-create both return a generic 500 instead of 404/409. The Consumer hides the real downstream status/message on failure. No auth between services. The DB password sits in `config.json`. `sync()` is used instead of migrations. Do not "fix" these without being asked — they're intentional gaps in the reference, not bugs.

## Concepts, mapped to Spring Boot

See `BUILD_SPEC.md` section 2 for the full table (`ServiceBroker` ↔ `@SpringBootApplication`, `*.service.js` action ↔ `@GetMapping`, `*.handler.js` ↔ service layer, `errorHandler` ↔ `@ControllerAdvice`, Sequelize model ↔ JPA entity, axios ↔ Feign/RestTemplate, `config.json` ↔ `application.yml`).

## Ports summary

Eureka 8761 · Spring services 8080-8083 · `UserProfileServiceNode` 3001 · `ConsumerServiceNode` 3002
