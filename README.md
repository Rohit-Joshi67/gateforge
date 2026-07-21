# 🥈 GateForge – Lightweight API Gateway

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
