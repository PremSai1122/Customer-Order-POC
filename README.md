# Customer & Order Microservices POC

Built from the onboarding transcript + operations-model diagram. Java 17,
Spring Boot 3.5.4, Spring Cloud 2025.0.0 (Northfields).

## The 5 services and why each exists

| Service | Port | Role |
|---|---|---|
| `discovery-server` | 8761 | Eureka registry - everything else registers here so nobody hardcodes host:port |
| `api-gateway` | 8080 | Single entry point, routes by path, holds the pre/post filter (auth/logging) |
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

## Run order (each in its own terminal)

```bash
# 1. Postgres running locally, with customer_db and order_db already created
#    (CREATE DATABASE customer_db; / CREATE DATABASE order_db; in pgAdmin4 -
#    Postgres doesn't auto-create these on connect the way MySQL can).
#    Check customer-service/order-service application.yml match your actual
#    Postgres username/password - "postgres"/"postgres" is a placeholder,
#    Homebrew and Postgres.app installs on Mac often use your system
#    username with no password instead.
mvn spring-boot:run -pl discovery-server
# wait for it to come up, check http://localhost:8761

mvn spring-boot:run -pl customer-service
mvn spring-boot:run -pl order-service
mvn spring-boot:run -pl composite-service
mvn spring-boot:run -pl api-gateway
```

Watch the Eureka dashboard (http://localhost:8761) - you should see all 4
app instances (gateway, customer, order, composite) register within ~30s.

## Try it

```bash
# Add a customer (direct to the service, bypassing the gateway)
curl -X POST http://localhost:8081/api/customers \
  -H "Content-Type: application/json" \
  -d '{"name":"Asha Rao","email":"asha@example.com","phone":"9999900000"}'

# Place an order THROUGH the gateway -> composite -> customer + order
curl -X POST http://localhost:8080/api/composite/orders \
  -H "Content-Type: application/json" \
  -d '{"customerId":1,"productId":"P-100","quantity":2}'

# Combined view: customer + all their orders, one call
curl http://localhost:8080/api/composite/customers/1/orders
```

Kill `order-service` and repeat the place-order call - after a handful of
failed attempts the circuit breaker should trip and you'll get the fallback
response instantly instead of a timeout. That's the behavior to point to
in your demo.

## What's deliberately NOT in this POC yet (per the transcript, "later")

- **Config Server** - all config is in each service's `application.yml` for
  now. Next step: pull these into a `config-server` module backed by a git
  repo, each service adds `spring-cloud-config-client` and drops the
  duplicated eureka/db blocks.
- **Node.js/Moleculer services** - mentioned as a separate track that
  starts after the Java side is signed off, registering onto the same
  Eureka.
- **Real auth** - the gateway filter (`LoggingAndAuthFilter`) has the hook
  point commented in; wire up a real JWT/OAuth check there when asked.

## A build note

I couldn't run `mvn compile` in this sandbox (no access to Maven Central
here), so this hasn't been compiled in this environment - but the code,
dependency versions, and annotations are all valid for the Boot
3.5.4 / Cloud 2025.0.0 combo. Run `mvn clean install` from the root once
you've pulled it onto your machine and fix anything Maven flags (should
just be config-heavy: DB credentials, ports if they clash with something
else you're running).
