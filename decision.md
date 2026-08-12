# GateForge — Architecture & Decision Record

> **Purpose of this document:** A single place to understand what GateForge is, what was built, why each choice was made, what was traded off, what is broken or missing, and how to verify the system works.

---

## 1. What Is GateForge?

**GateForge** is a **learning-focused, lightweight API Gateway** written in Java with Spring Boot. It sits in front of backend microservices and handles cross-cutting concerns before traffic reaches those services.

It is **not production-ready**. The goal is to make gateway mechanics visible — the same problems solved by Nginx, Kong, Envoy, or Spring Cloud Gateway — without hiding logic inside heavy frameworks.

| Attribute | Value |
|-----------|-------|
| Language | Java 21 (see note in §8 — README says 17) |
| Framework | Spring Boot 4.1.0 (`spring-boot-starter-webmvc` only) |
| Build | Maven (`gateforge/` module) |
| Default port | 8080 |
| Auth library | `jjwt` 0.12.6 (HS256) |
| HTTP client | Apache HttpClient 5 via `RestClient` |

### Configured backends (from `application.yml`)

| Route ID | Path prefix | Target |
|----------|-------------|--------|
| user-service | `/api/users` | `http://localhost:9001` |
| order-service | `/api/orders` | `http://localhost:9002` |
| payment-service | `/api/payments` | `http://localhost:9003` |

---

## 2. Request Flow

Every HTTP request passes through a **Servlet Filter chain** before reaching any controller. Filters run *before* Spring MVC dispatch, which is the correct layer for gateway-style short-circuiting.

```
Client
  │
  ▼
LoggingFilter        (@Order 1)  — log method, path, status, latency (always runs)
  │
  ▼
JwtAuthFilter        (@Order 2)  — reject if no valid Bearer JWT (except /health)
  │
  ▼
RateLimitFilter      (@Order 3)  — reject if client exceeds 5 req / 10 sec (except /health)
  │
  ▼
Controller layer
  ├─ /health          → HealthController (direct response)
  ├─ /login           → AuthController (issues JWT — currently blocked; see §7)
  └─ /api/**          → ProxyController (forwards to backend)
  │
  ▼
Backend service (e.g. user-service on :9001)
```

### Why Servlet Filters instead of Spring Security or Interceptors?

| Option | Why not chosen |
|--------|----------------|
| **Spring Security** | Hides JWT validation, filter ordering, and failure responses behind auto-configuration. Defeats the learning goal. |
| **HandlerInterceptor** | Runs *after* `DispatcherServlet` routing. Auth and rate limiting should reject requests before controller matching and proxy work. |
| **Servlet Filters** ✅ | Run earliest in the pipeline; can short-circuit; map directly to how real gateways implement middleware. |

**Tradeoff:** You own all security edge cases (exempt paths, header parsing, error responses). Spring Security would handle many of these for free.

---

## 3. Component Decisions

### 3.1 Reverse Proxy — `ProxyController`

**What:** Catches all `/api/**` requests, resolves a backend route, forwards via `RestClient`, and relays status + headers + body back to the client.

**Why:** This is the core gateway responsibility — a single entry point that hides backend topology from clients.

**How routing works:**
- Routes are declared in `application.yml` under `gateforge.routes`.
- `RouteResolver` uses **longest-prefix-match**: among routes whose `path-prefix` matches the request path, the longest prefix wins.
- Target URL is built as `targetUrl + requestPath` (path is preserved, not stripped).

**Tradeoffs:**

| Decision | Benefit | Cost |
|----------|---------|------|
| Catch-all `@RequestMapping("/api/**")` | Simple, one controller | No per-route middleware (e.g. different auth per route) |
| `RestClient` + Apache HttpClient | Connect timeout (2s) and response timeout (5s) | More setup than default JDK client |
| Relay `byte[]` body | Works for any content type | No streaming; large payloads buffered in memory |
| Config-driven routes | Add backends without code changes | No hot reload; restart required |

**Known gaps (not yet implemented):**
- Incoming **request headers** are not forwarded to the backend (including `Authorization`, `Content-Type`, etc.).
- **Request body** is not forwarded — `POST`/`PUT`/`PATCH` through the gateway will not work correctly.
- **Query strings** are lost — `getRequestURI()` excludes `?key=value`; use `getRequestURI() + "?" + getQueryString()` when building the target URL.
- No path rewriting (prefix is kept as-is on the backend URL).

---

### 3.2 Configuration-Driven Routing

**Files:** `GatewayProperties`, `RouteConfig`, `RouteResolver`, `application.yml`

**What:** `@ConfigurationProperties(prefix = "gateforge")` binds YAML routes into Java objects at startup.

**Why:** Mirrors how real gateways (Kong routes, Envoy clusters, Spring Cloud Gateway routes) externalize routing from code.

**Tradeoff:** No validation at startup for duplicate prefixes, unreachable URLs, or invalid target URLs. A typo in YAML fails silently or at first request.

---

### 3.3 JWT Authentication

**Files:** `JwtUtil`, `JwtAuthFilter`, `AuthController`

**What:**
- `JwtUtil` signs and verifies HS256 tokens (1-hour expiry).
- `JwtAuthFilter` requires `Authorization: Bearer <token>` on all paths except `/health`.
- Valid tokens store `username` as a request attribute for downstream use (rate limiting).
- `/login?username=<name>` is intended to mint a dev token.

**Why hand-rolled JWT:**
- Makes token structure, signing, and validation explicit.
- Good for interviews and understanding what API gateways actually check.

**Tradeoffs:**

| Decision | Benefit | Cost |
|----------|---------|------|
| Hardcoded secret in `JwtUtil` | Zero external dependencies for learning | Not safe for production; no key rotation |
| HS256 symmetric key | Simple | All gateway instances must share the same secret |
| `/login` as a GET with query param | Easy to curl in demos | Not a real login flow; no password, no OAuth |
| Only `/health` exempt from auth | Clean LB probe path | **`/login` is also blocked — see §7 (bug)** |

---

### 3.4 Rate Limiting

**Files:** `RateLimiter`, `RateLimitFilter`

**What:** Fixed-window rate limiter — **5 requests per 10 seconds per client**.

**Client key:** Authenticated `username` (from JWT) if present; otherwise client IP (`getRemoteAddr()`).

**Why after auth:** Rate limit authenticated identities, not anonymous noise that auth already rejected.

**Algorithm:** Fixed window with `ConcurrentHashMap<String, Window>` and per-window `synchronized` block.

**Tradeoffs:**

| Decision | Benefit | Cost |
|----------|---------|------|
| In-memory counters | Simple, fast, no Redis | Not shared across multiple gateway replicas |
| Fixed window | Easy to implement and test | Boundary burst: 5 at end of window + 5 at start of next = 10 in ~1 second |
| Hardcoded limits (5/10s) | No config complexity | Cannot tune per environment without code change |
| Per-username when authed | Fair for logged-in users | Same user from multiple IPs shares one bucket |

**Production alternatives:** Redis sliding window, token bucket (Guava RateLimiter), or a dedicated service (Envoy local rate limit / global rate limit).

---

### 3.5 Logging Middleware

**File:** `LoggingFilter`

**What:** Logs `METHOD path -> status (duration ms)` for every request, including rejected ones.

**Why `@Order(1)` (first):** Auth and rate-limit rejections should still appear in logs for observability and debugging.

**Tradeoff:** Plain SLF4J text logs — no structured JSON, trace IDs, or correlation with downstream services.

---

### 3.6 Health Check

**File:** `HealthController`

**What:** `GET /health` returns `{ status, service, timestamp }`.

**Why exempt from auth + rate limit:** Load balancers and Kubernetes probes hit this frequently without tokens.

**Tradeoff:** Does not check backend service health — only reports that GateForge itself is up (liveness, not readiness).

---

### 3.7 HTTP Client Timeouts

**File:** `HttpClientConfig`

**What:** `RestClient` backed by Apache HttpClient with 2s connect timeout and 5s response timeout.

**Why:** Without timeouts, a dead backend hangs gateway threads indefinitely.

**Tradeoff:** `ProxyController` maps `ResourceAccessException` to **504 Gateway Timeout** with a plain-text message — no retry, no circuit breaker.

---

## 4. Build & Test Strategy

### Unit tests (present)

| Test | Covers |
|------|--------|
| `JwtUtilTest` | Token generation, validation, invalid token rejection |
| `RouteResolverTest` | Matching path, unmatched path |
| `RateLimiterTest` | Limit boundary, independent client buckets |
| `GateforgeApplicationTests` | Spring context loads |

### What's not tested

- Filter chain integration (auth → rate limit → proxy)
- `/login` and `/health` HTTP endpoints
- Proxy forwarding behavior (headers, body, query string)
- Longest-prefix-match when two prefixes overlap
- 504 timeout path when backend is down

**Tradeoff:** Fast, focused unit tests for core algorithms; no Testcontainers or mock backend for end-to-end proxy tests.

---

## 5. Evolution Timeline (Git History)

| Commit | Milestone |
|--------|-----------|
| Milestone 1 | Spring Boot skeleton |
| Proxy + HttpClient config | Basic reverse proxy |
| JWT | Auth filter + token utility + `/login` |
| Logging | Request logging filter |
| Rate limiter | Fixed-window per-client throttling |
| Health check | `/health` endpoint |
| Config-driven routing | YAML routes + `RouteResolver` |
| Request timeout | Apache HttpClient timeouts + 504 handling |
| Tests | JWT, routing, rate limiter unit tests |

This shows an intentional **incremental build**: proxy first, then middleware layers, then configurability, then resilience (timeout), then tests.

---

## 6. Intentional Limitations (By Design)

These are documented in README and are acceptable for a learning project:

1. In-memory rate limiting (no Redis)
2. Fixed-window algorithm (boundary burst)
3. No circuit breaker / retry on backend calls
4. No TLS termination
5. Hardcoded JWT secret
6. No service discovery — backends are static URLs in YAML

---

## 7. Issues Found — Status After Aug 2026 Fix

### ✅ Fixed: `/login` blocked by JWT filter

**Was:** `/login` returned 401. **Now:** `/login`, `/health`, and `/health/ready` are public paths via `PublicPaths.java`.

### ✅ Fixed: Proxy did not forward headers, body, or query string

**Was:** Only method + path forwarded. **Now:** `ProxyController` copies client headers (minus hop-by-hop), request body, and query string.

### ✅ Fixed: README vs pom.xml Java version mismatch

**Now:** Both document Java 21.

### ✅ Fixed: Hardcoded rate limit and JWT secret

**Now:** Configurable in `application.yml`; JWT secret overridable via `JWT_SECRET` env var.

### ✅ Added: Integration tests and readiness probe

- 18 automated tests (10 new integration tests)
- `/health/ready` pings all configured backends

---

## 8. Recommendations

### Must-do (to match README behavior)

1. **Exempt `/login` from `JwtAuthFilter`** — unblocks the documented quick-start.
2. **Forward query string in `ProxyController`** — one-line fix using `getQueryString()`.

### Should-do (next learning milestones)

3. **Forward request headers and body** in the proxy — required for real API gateway behavior.
4. **Make rate limit configurable** in `application.yml` (`max-requests`, `window-seconds`).
5. **Add integration test** with `@SpringBootTest` + `MockRestServiceServer` or WireMock as fake backend.
6. **Add longest-prefix-match test** — e.g. `/api/users/admin` vs `/api/users`.
7. **Readiness probe** — optional `/health/ready` that pings configured backends.

### Nice-to-have (production path)

8. Externalize JWT secret via environment variable.
9. Redis-backed rate limiter for multi-instance deployment.
10. Sliding window or token bucket algorithm.
11. Structured JSON logging with request ID.
12. Circuit breaker (Resilience4j) on backend calls.

---

## 9. How to Verify It Is Working

### Step 1 — Run unit tests

```powershell
cd gateforge
.\mvnw.cmd test
```

**Expected:** All tests pass (JwtUtil, RouteResolver, RateLimiter, context load).

### Step 2 — Start the gateway

```powershell
cd gateforge
.\mvnw.cmd spring-boot:run
```

**Expected:** Log line `Started GateforgeApplication` on port 8080.

### Step 3 — Health check (no auth required)

```powershell
Invoke-RestMethod -Uri "http://localhost:8080/health"
```

**Expected:**
```json
{"status":"UP","service":"GateForge","timestamp":"..."}
```

### Step 4 — Get a JWT (after fixing `/login` exemption)

```powershell
$token = Invoke-RestMethod -Uri "http://localhost:8080/login?username=rahul"
$token
```

**Expected:** A JWT string. **Currently fails with 401 until §7 fix is applied.**

**Workaround without code change:** Generate a token in a unit test or a small main method using `JwtUtil.generateToken("rahul")`.

### Step 5 — Call a proxied route (requires running backend)

Start a mock backend on port 9001 (example with Python):

```powershell
# In a separate terminal — simple echo server is enough for a smoke test
python -m http.server 9001
```

Then:

```powershell
$headers = @{ Authorization = "Bearer $token" }
Invoke-WebRequest -Uri "http://localhost:8080/api/users/5" -Headers $headers -UseBasicParsing
```

**Expected without backend:** **504 Gateway Timeout** (backend unreachable — proves proxy + timeout work).

**Expected with backend:** Response relayed from port 9001.

### Step 6 — Verify auth rejection

```powershell
try {
  Invoke-WebRequest -Uri "http://localhost:8080/api/users/5" -UseBasicParsing
} catch {
  $_.Exception.Response.StatusCode.value__  # should be 401
}
```

### Step 7 — Verify rate limiting

Send 6+ authenticated requests within 10 seconds for the same user:

```powershell
1..6 | ForEach-Object {
  try {
    $r = Invoke-WebRequest -Uri "http://localhost:8080/api/users/1" -Headers $headers -UseBasicParsing
    "Request $_`: $($r.StatusCode)"
  } catch {
    "Request $_`: $($_.Exception.Response.StatusCode.value__)"
  }
}
```

**Expected:** First 5 succeed (or 504 if no backend); 6th returns **429 Too Many Requests**.

### Step 8 — Verify logging

Check console output for lines like:

```
INFO ... LoggingFilter : GET /api/users/5 -> 401 (12 ms)
INFO ... LoggingFilter : GET /health -> 200 (3 ms)
```

---

## 10. Project Layout

```
gateforge/
├── pom.xml                          # Maven deps, Java 21, Spring Boot 4.1.0
├── src/main/java/com/gateforge/
│   ├── GateforgeApplication.java    # Entry point
│   ├── auth/
│   │   ├── AuthController.java      # GET /login — issues JWT
│   │   ├── JwtAuthFilter.java       # Bearer token validation
│   │   └── JwtUtil.java             # Sign / verify HS256 tokens
│   ├── config/
│   │   └── HttpClientConfig.java    # RestClient + timeouts
│   ├── health/
│   │   └── HealthController.java    # GET /health
│   ├── logging/
│   │   └── LoggingFilter.java       # Request logging (@Order 1)
│   ├── proxy/
│   │   └── ProxyController.java     # /api/** reverse proxy
│   ├── ratelimit/
│   │   ├── RateLimitFilter.java     # 429 enforcement (@Order 3)
│   │   └── RateLimiter.java         # Fixed-window counter
│   └── routing/
│       ├── GatewayProperties.java   # Binds gateforge.* from YAML
│       ├── RouteConfig.java         # id, pathPrefix, targetUrl
│       └── RouteResolver.java       # Longest-prefix-match
├── src/main/resources/
│   ├── application.yml              # Port + route definitions
│   └── application.properties       # spring.application.name
└── src/test/java/com/gateforge/     # Unit tests
README.md                            # User-facing quick start
decision.md                          # This document
```

---

## 11. Summary

GateForge successfully demonstrates the **shape of an API gateway**: filter-chain middleware (logging → auth → rate limit) followed by config-driven reverse proxy with backend timeouts. The codebase is small, readable, and well-suited for learning and interviews.

The most important gap between **documented behavior** and **actual behavior** is that `/login` cannot be reached without already having a JWT. Fixing that one exemption, then improving proxy header/body/query forwarding, would make the project fully exercisable end-to-end with mock backends.

**Current verified state (Aug 2026):**
- ✅ 18 unit/integration tests pass (~43s build)
- ✅ App starts on :8080
- ✅ `/health` returns 200
- ✅ `/login` returns 200 with JWT (131 chars)
- ✅ Unauthenticated `/api/**` returns 401
- ✅ Unreachable backend returns 504 (~92ms with connect timeout)
- ✅ Rate limit returns 429 after configured max
- ✅ Proxy forwards headers, body, and query string (WireMock verified)
