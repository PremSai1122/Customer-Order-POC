# Customer & Order Microservices POC

Built from the onboarding transcript + operations-model diagram. Java 17,
Spring Boot 3.5.4, Spring Cloud 2025.0.0 (Northfields).

## The services and why each exists

| Service | Port | Role |
|---|---|---|
| `discovery-server` | 8761 | Eureka registry - everything else registers here so nobody hardcodes host:port |
| `api-gateway` | 8080 | Single entry point, routes by path, checks auth on every request before it's proxied |
| `customer-service` | 8081 | Atomic service - owns the `customer` table only |
| `order-service` | 8082 | Atomic service - owns the `order` table only |
| `composite-service` | 8083 | Orchestrator - has no table of its own, calls the two atomic services and stitches results |
| Keycloak (Docker) | 8180 | Real OIDC identity provider - one of two ways to get a token the gateway accepts |

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

## Auth: two ways to get a token

`api-gateway`'s `LoggingAndAuthFilter` rejects every proxied request
(`/api/composite/**`, `/api/customers/**`, `/api/orders/**`) with a 401
unless it carries `Authorization: Bearer <token>` for a token it can
validate. It accepts either of two kinds, checked in this order:

1. **Local login** - `POST /auth/login` with `{"username", "password"}`.
   Verified against `app_user` in `auth_db` (bcrypt-hashed passwords via
   Spring Security's `PasswordEncoder`), issues a self-signed HS256 JWT.
   Register a user first: `POST /auth/register` with the same body shape.
2. **Keycloak OIDC** - a real identity provider running in Docker (see
   below). Get a token directly from Keycloak; the gateway verifies it
   against Keycloak's published JWKS (RS256) without ever talking to
   Keycloak to issue tokens itself.

Direct calls to `customer-service`/`order-service` on their own ports
(8081/8082) skip the gateway entirely, so they need no token - useful for
testing one service in isolation, but also means the gateway is the only
place auth is actually enforced.

### Running Keycloak

```bash
docker compose up -d
```

This starts Keycloak from [scripts/keycloak/realm-import.json](scripts/keycloak/realm-import.json),
which declares the `microservices-poc` realm, a public client
(`gateway-client`, direct-access-grants enabled), and a demo user
(`demo` / `demo123`). Admin console: `http://localhost:8180` (`admin`/`admin`).

```bash
# Get a Keycloak token for the demo user
curl -X POST http://localhost:8180/realms/microservices-poc/protocol/openid-connect/token \
  -d "client_id=gateway-client" -d "grant_type=password" \
  -d "username=demo" -d "password=demo123" -d "scope=openid"
```

Note: `start-dev --import-realm` resets the realm to match that JSON file
on every container start (by design, for a reproducible demo state) - if
you create extra users/clients by hand in the admin console and want them
to survive a restart, drop `--import-realm` from `docker-compose.yml`
after the first run.

## Run order (each in its own terminal)

```bash
# 1. Postgres running locally, with customer_db, order_db, and auth_db
#    already created (CREATE DATABASE customer_db; etc. in pgAdmin4/psql -
#    Postgres doesn't auto-create these on connect the way MySQL can).
#    auth_db also needs the app_user table:
#      CREATE TABLE app_user (
#        id BIGSERIAL PRIMARY KEY,
#        username VARCHAR(255) NOT NULL UNIQUE,
#        password_hash VARCHAR(255) NOT NULL,
#        created_date TIMESTAMP NOT NULL DEFAULT now()
#      );
#    Check each service's application.yml matches your actual Postgres
#    username/password - "postgres"/"postgres" is a placeholder; Homebrew
#    and Postgres.app installs on Mac often use your system username with
#    no password instead.

# 2. Keycloak (optional if you're only testing local login)
docker compose up -d

# 3. The 5 Spring Boot services
mvn spring-boot:run -pl discovery-server
# wait for it to come up, check http://localhost:8761

mvn spring-boot:run -pl customer-service
mvn spring-boot:run -pl order-service
mvn spring-boot:run -pl composite-service
mvn spring-boot:run -pl api-gateway
```

Watch the Eureka dashboard (http://localhost:8761) - you should see all 4
app instances (gateway, customer, order, composite) register within ~5s,
all listed under `hostname: localhost` (see note below on why that matters).

The Eureka heartbeat and cache intervals are deliberately shortened from
their cluster-sized defaults (30s renew / 90s expiry / 30s registry cache),
which is what makes registration show up in seconds rather than half a
minute, and makes a service you kill disappear in ~10s rather than ~90s.
Those defaults exist to keep heartbeat traffic down in large deployments
and are worth restoring if this ever runs as more than a local POC.

### Or run the built jars

`mvn clean install` now repackages each module into an executable jar, so
the services can also be started without Maven in the loop - handy when you
want all five running from one terminal:

```bash
mvn clean install -DskipTests
java -jar discovery-server/target/discovery-server-1.0.0.jar
java -jar customer-service/target/customer-service-1.0.0.jar
java -jar order-service/target/order-service-1.0.0.jar
java -jar composite-service/target/composite-service-1.0.0.jar
java -jar api-gateway/target/api-gateway-1.0.0.jar
```

Any setting can be overridden per run, which is the easiest way to bring up
a second isolated stack beside a running one:

```bash
java -jar customer-service/target/customer-service-1.0.0.jar \
  --server.port=9081 \
  --eureka.client.service-url.defaultZone=http://localhost:9761/eureka/
```

## Try it

```bash
# Add a customer (direct to the service, bypassing the gateway - no token needed)
curl -X POST http://localhost:8081/api/customers \
  -H "Content-Type: application/json" \
  -d '{"name":"Asha Rao","email":"asha@example.com","phone":"9999900000"}'

# Register + log in through the gateway to get a token
curl -X POST http://localhost:8080/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"raj","password":"S3cure!Pass"}'

TOKEN=$(curl -s -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"raj","password":"S3cure!Pass"}' | python3 -c "import json,sys;print(json.load(sys.stdin)['token'])")

# Place an order THROUGH the gateway -> composite -> customer + order
curl -X POST http://localhost:8080/api/composite/orders \
  -H "Content-Type: application/json" -H "Authorization: Bearer $TOKEN" \
  -d '{"customerId":1,"productId":"P-100","quantity":2}'

# Combined view: customer + all their orders, one call
curl http://localhost:8080/api/composite/customers/1/orders \
  -H "Authorization: Bearer $TOKEN"

# Filter customers by name and/or creation date (direct to the service)
curl "http://localhost:8081/api/customers?name=raj"
curl "http://localhost:8081/api/customers?date=2026-09-12"
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
