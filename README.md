 🥈 GateForge – Lightweight API Gateway

Java | Spring Boot | Reverse Proxy | JWT | Rate Limiting | Middleware

GateForge is a learning-focused API Gateway built from scratch to understand
the internals of how real gateways (Nginx, Spring Cloud Gateway, Kong, Envoy)
work under the hood — without hiding logic behind heavy frameworks.

This is **not production-ready**. It is intentionally simplified to expose
every mechanism clearly for study and interview preparation.

## Features

- **Reverse Proxy** — forwards incoming requests to configured backend services
- **JWT Authentication** — validates Bearer tokens before requests reach any backend
- **Logging Middleware** — logs method, path, status code, and latency per request
- **Rate Limiting** — fixed-window, per-client request throttling
- **Health Endpoint** — `/health` for liveness checks, bypasses auth and rate limiting
- **Configuration-Driven Routing** — routes defined in `application.yml`, not hardcoded

## Architecture

A request flows through GateForge as a chain of Servlet Filters, executed in
this order, before ever reaching the proxy controller:

Client Request
│
▼
LoggingFilter (@Order 1) → logs every request, even rejected ones
│
▼
JwtAuthFilter (@Order 2) → rejects requests without a valid JWT
│
▼
RateLimitFilter (@Order 3) → rejects requests exceeding the rate limit
│
▼
ProxyController → resolves route, forwards to backend, relays response
│
▼
Backend Service (e.g. user-service on :9001)


`/health` is exempted from JWT auth and rate limiting, since load balancers
and orchestration tools (Kubernetes, ALBs) hit it frequently without tokens.

## Why Servlet Filters, not Spring Security or Interceptors?

Filters run **before** Spring's `DispatcherServlet` routes to any controller,
making them the correct layer for cross-cutting gateway concerns (auth,
logging, rate limiting) that should short-circuit a request before it costs
any real work. This project deliberately avoids Spring Security to keep the
JWT validation logic visible and hand-written rather than hidden in a
framework's configuration.

## How Routing Works

Routes are declared in `application.yml`:

```yaml
gateforge:
  routes:
    - id: user-service
      path-prefix: /api/users
      target-url: http://localhost:9001
```

`RouteResolver` matches incoming request paths against configured prefixes
using **longest-prefix-match**, so more specific routes take priority over
broader ones. Adding a new backend service requires only a new entry in
`application.yml` — no Java code changes.

## Adding a New Route (Onboarding Guide)

1. Open `src/main/resources/application.yml`
2. Add a new entry under `gateforge.routes`:
```yaml
   - id: notification-service
     path-prefix: /api/notifications
     target-url: http://localhost:9004
```
3. Restart GateForge. No other changes needed.

## Known Limitations (Intentional, for Learning)

- Rate limiter is **in-memory** — state is per-instance, not shared across
  multiple GateForge replicas. Production fix: centralize counters in Redis.
- Rate limiting uses **Fixed Window** algorithm — has a boundary-burst edge
  case. Production fix: Sliding Window or Token Bucket algorithm.
- No circuit breaker / retry / timeout handling on backend calls yet.
- No HTTPS/TLS termination.
- Single JWT secret hardcoded — production would use a secrets manager and
  key rotation.

## Running Locally

```bash
./mvnw spring-boot:run
```

Get a test token:
```bash
curl "http://localhost:8080/login?username=rahul"
```

Call a proxied route:
```bash
curl -H "Authorization: Bearer <token>" http://localhost:8080/api/users/5
```

Check health:
```bash
curl http://localhost:8080/health
```

## Tech Stack

- Java 17
- Spring Boot (Web only — routing, filters, auth all hand-built)
- `jjwt` for JWT generation/validation
- Maven
