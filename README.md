# Customer & Order Microservices POC

Built from the onboarding transcript + operations-model diagram. Java 17,
Spring Boot 3.5.4, Spring Cloud 2025.0.0 (Northfields).

## The services and why each exists

| Service | Port | Role |
|---|---|---|
| `discovery-server` | 8761 | Eureka registry - everything else registers here so nobody hardcodes host:port |
| `api-gateway` | 8080 | Single entry point - routes `/api/composite/**` only, and requires a valid JWT on every proxied request |
| `customer-service` | 8081 | Atomic service - owns the `customer` table only |
| `order-service` | 8082 | Atomic service - owns the `order` table only |
| `composite-service` | 8083 | Orchestrator - has no table of its own, calls the two atomic services and stitches results |

This is the "core services + composite services" split from the operations
model diagram: composite-service is your `MS-3`-style layer, customer/order
are the `MS-1`/`MS-2` atomic layer, api-gateway is the edge server.

## Why composite-service has no database

This is the one thing that trips people up coming from a monolith: in the
"place order" flow, the composite controller does NOT reach into the order
database and also read the customer database in one query. It makes an
HTTP call to customer-service, then an HTTP call to order-service, and
combines the two responses in Java. Each service's database is private -
that's what makes them independently deployable. `Order.customerId` is a
plain `Long`, not a JPA `@ManyToOne` to a `Customer` entity, because that
entity lives in a different service/JVM/database entirely.

## Where the circuit breaker actually lives

The transcript says "in any atomic service it's fine to add resilience,"
but the piece that actually needs protecting is composite-service's calls
OUT to the atomic services - if customer-service is slow or down, you don't
want that hanging the composite service too. That's why Resilience4j is
wired into `CustomerClient`/`OrderClient` (the Feign interfaces) via
`fallback` classes, not into customer-service or order-service themselves.
Open the circuit breaker config in `composite-service/application.yml` to
see the thresholds; the `CustomerClientFallback`/`OrderClientFallback`
classes are what get called when it trips.

## Auth: one local JWT, no identity provider

The gateway uses Spring Security's reactive resource server. One symmetric
HS256 secret both signs and verifies tokens, so there is no identity
provider to run and the gateway has no database of its own.

`POST /auth/login` is the only way to get a token, and the only path that
does not need one. Credentials are checked against the `jwt.users` map in
`api-gateway/src/main/resources/application.yml`:

```yaml
jwt:
  secret: "poc-only-shared-secret-change-me-before-any-real-use-32bytes+"
  expiration-ms: 3600000   # 1 hour
  users:
    raj: "S3cure!Pass"
    demo: "demo123"
```

Add a user by adding a line to that map and restarting the gateway - there
is no registration endpoint, because there is nowhere to persist a new user
to. The secret must stay at least 32 characters; HS256 needs a 256-bit key,
and the gateway fails fast at startup rather than signing with a weak one.

Everything except `/auth/**` requires `Authorization: Bearer <token>`.
Anything else gets a 401 with a `WWW-Authenticate: Bearer` header.

### Only composite is exposed

The gateway routes `/api/composite/**` to `lb://composite-service` and
nothing else. `customer-service` and `order-service` are not routed, so
they are reachable only through composite-service - the orchestration layer
can't be bypassed from outside. A call to `/api/customers` through the
gateway is a 404, by design.

Their own ports (8081/8082) are still open and need no token, which is
useful for testing one service in isolation but means the gateway is the
only place auth is actually enforced.

## Run order (each in its own terminal)

```bash
# 1. Postgres running locally, with customer_db and order_db already
#    created (CREATE DATABASE customer_db; etc. in pgAdmin4/psql -
#    Postgres doesn't auto-create these on connect the way MySQL can).
#    The gateway needs no database: its users live in the jwt.users map
#    in api-gateway/src/main/resources/application.yml.
#    Check each service's application.yml matches your actual Postgres
#    username/password - "postgres"/"postgres" is a placeholder; Homebrew
#    and Postgres.app installs on Mac often use your system username with
#    no password instead.

# 2. The 5 Spring Boot services
mvn spring-boot:run -pl discovery-server
# wait for it to come up, check http://localhost:8761

mvn spring-boot:run -pl customer-service
mvn spring-boot:run -pl order-service
mvn spring-boot:run -pl composite-service
mvn spring-boot:run -pl api-gateway
```

Watch the Eureka dashboard (http://localhost:8761) - you should see all 4
app instances (gateway, customer, order, composite) register within ~30s,
all listed under `hostname: localhost` (see note below on why that matters).

## Try it

```bash
# Add a customer (direct to the service, bypassing the gateway - no token needed)
curl -X POST http://localhost:8081/api/customers \
  -H "Content-Type: application/json" \
  -d '{"name":"Asha Rao","email":"asha@example.com","phone":"9999900000"}'

# Log in through the gateway to get a token. "raj" is one of the users in
# the gateway's jwt.users map - there is no registration endpoint.
TOKEN=$(curl -s -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"raj","password":"S3cure!Pass"}' | python3 -c "import json,sys;print(json.load(sys.stdin)['token'])")

# Place an order THROUGH the gateway -> composite -> customer + order
# productId is a plain numeric id (Long), not a "P100"-style code
curl -X POST http://localhost:8080/api/composite/orders \
  -H "Content-Type: application/json" -H "Authorization: Bearer $TOKEN" \
  -d '{"customerId":1,"productId":100,"quantity":2}'

# Combined view: customer + all their orders, one call
curl http://localhost:8080/api/composite/customers/1/orders \
  -H "Authorization: Bearer $TOKEN"

# The atomic services are NOT routed through the gateway - only
# /api/composite/** is. This returns 404, by design:
curl -o /dev/null -w "%{http_code}\n" http://localhost:8080/api/customers \
  -H "Authorization: Bearer $TOKEN"

# Filter customers by name and/or creation date (direct to the service)
curl "http://localhost:8081/api/customers?name=raj"
curl "http://localhost:8081/api/customers?date=2026-09-12"

# Customer listing is active-only by default; includeInactive=true also
# returns soft-deleted (inactive) customers
curl "http://localhost:8081/api/customers?includeInactive=true"

# Orders: customerId is optional - omit it for every order, or narrow by
# customerId and, optionally, productId
curl "http://localhost:8082/api/orders"
curl "http://localhost:8082/api/orders?customerId=1"
curl "http://localhost:8082/api/orders?customerId=1&productId=100"
```

A ready-to-import Postman collection covering every endpoint (including
the token-fetching requests, which auto-save the token into a collection
variable) is at [microservices-poc.postman_collection.json](microservices-poc.postman_collection.json).

Kill `order-service` and repeat the place-order call - after a handful of
failed attempts the circuit breaker should trip and you'll get the fallback
response instantly instead of a timeout. That's the behavior to point to
in your demo.

## A note on Eureka and `localhost`

Every service's `eureka.instance` config uses `hostname: localhost` rather
than `prefer-ip-address: true`. That's deliberate: on a machine with
several network interfaces (Docker, a VPN, etc.), Java's own hostname
resolution can return a different LAN IP on different runs, which causes
services started at different times to register under different addresses
- and then composite-service's Feign calls to the other two fail with "No
route to host" even though everything is actually running fine locally.
Since every service in this POC always runs on the same machine, pinning
to `localhost` sidesteps that entirely. If you ever split these across
real hosts, switch back to `prefer-ip-address: true` (or set a real
hostname) since `localhost` obviously won't resolve across machines.

## What's deliberately NOT in this POC yet (per the transcript, "later")

- **Config Server** - all config is in each service's `application.yml` for
  now. Next step: pull these into a `config-server` module backed by a git
  repo, each service adds `spring-cloud-config-client` and drops the
  duplicated eureka/db blocks.
- **Node.js/Moleculer services** - mentioned as a separate track that
  starts after the Java side is signed off, registering onto the same
  Eureka.
- **Monitoring/centralized logging dashboards** - each service just logs
  to its own console/file today; no Prometheus/Grafana or ELK-style
  aggregation yet.
