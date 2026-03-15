# TeamAI: Monolith → Microservices Migration Context

> **This document provides context for any AI agent working on this project.**
> The original monolith is at `A:\TeamAI\TeamAI-backend\` and this distributed version was created from it.

---

## ⚠️ Rules for AI Agent (MANDATORY)

1. **Do what the user asks** — nothing more, nothing less.
2. **Always read and understand the `resource` folder** (`A:\Distributed TeamAI\resource\`) before starting any work. This is the project's knowledge base.
3. **Always check the equivalent file in the monolith** (`A:\TeamAI\TeamAI-backend\`) **before** editing any file in this distributed project. Cross-reference dependencies, configurations, and code from the monolith to ensure nothing is missed.
4. **Do not add, remove, or modify anything** without first verifying against the monolith source. If the monolith has it, bring it over completely. If it doesn't, confirm with the user before adding.

---

## What is this project?

This is the **distributed (microservices) version** of TeamAI — originally a monolithic Spring Boot application. The monolith is being split into **3 core services + 4 infrastructure modules** following the tutor's architecture plan.

## Source Monolith

- **Location**: `A:\TeamAI\TeamAI-backend\`
- **Original Group**: `com.ai.project.team_ai`
- **Spring Boot**: 4.0.1 → Now 4.0.3
- **Java**: 21
- **Full plan file**: `A:\TeamAI\resources\implementation_plan.md`

---

## Architecture Overview

```
Client → API Gateway (:8080) → Routes to services via Eureka

3 Core Services:
  ├── Account Service    (:9010) — Auth + Billing + Users
  ├── Workspace Service  (:9020) — Projects + Files + K8s
  └── Intelligence Service (:9030) — AI + Chat

Infrastructure:
  ├── API Gateway         (:8080) — Spring Cloud Gateway, routes to services
  ├── Config Service      (:8888) — Centralized YAML config (Config Server)
  ├── Discovery Service   (:8761) — Eureka Server
  └── Common Lib          (jar)   — Shared JWT filter, Feign interceptor, DTOs
```

### Startup Order
Config Server → Eureka → Account → Workspace → Intelligence → Gateway

---

## Module Details

### Infrastructure

| Module | Artifact | Key Dependencies | Notes |
|---|---|---|---|
| **api-gateway** | `api-gateway` | Reactive Gateway, Eureka Client, Config Client, Actuator | Routes requests, single entry point |
| **config-service** | `config-service` | **Config Server** (NOT client), Eureka Client, Actuator | Serves config to all services. No Config Client (it IS the server) |
| **discovery-service** | `discovery-service` | **Eureka Server**, Actuator | No Config Client (chicken-and-egg: can't fetch config before Eureka exists). Self-contained config |
| **common-lib** | `common-lib` | Lombok, Spring Security, OpenFeign | **Not a runnable app** — shared library. Remove `@SpringBootApplication` and `spring-boot-maven-plugin` |

### Core Services

| Module | Artifact | Key Dependencies | Entities (from monolith) |
|---|---|---|---|
| **account-service** | `account-service` | Web, JPA, PostgreSQL, Security, Eureka Client, Config Client, Actuator, Validation, Lombok, OpenAI | `User`, `Plan`, `Subscription` |
| **workspace-service** | `workspace-service` | Web, JPA, PostgreSQL, Security, Redis, Eureka Client, Config Client, OpenFeign, Kafka, Actuator, Validation, Lombok | `Project`, `ProjectFile`, `ProjectMember` |
| **intelligence-service** | `intelligence-service` | Web, JPA, PostgreSQL, Security, Eureka Client, Config Client, Kafka, Actuator, Validation, Lombok, OpenAI | `ChatSession`, `ChatMessage`, `ChatEvent`, `UsageLog` |

### Dependencies to add manually to pom.xml (not on Spring Initializr)

| Dependency | Services that need it |
|---|---|
| `stripe-java` | Account Service |
| `jjwt-api`, `jjwt-impl`, `jjwt-jackson` | Common Lib (JWT auth) |
| `mapstruct` + `mapstruct-processor` | Account, Workspace, Intelligence |
| `minio` + `okhttp` | Workspace Service |
| `fabric8 kubernetes-client` | Workspace Service |
| OpenFeign | Intelligence Service (tutor adding later) |

---

## Inter-Service Communication

| From | To | Via | Purpose |
|---|---|---|---|
| Intelligence | Workspace | Feign + JWT | Fetch file trees + content |
| Intelligence | Account | Feign + JWT | Verify daily token limits |
| Workspace | Account | Feign + JWT | Check maxProjects limit |
| Intelligence | Workspace | Kafka (`file-updates`) | Publish file update events |

### Common Lib provides:
- `JwtAuthFilter` — every service authenticates JWT locally
- `FeignClientInterceptor` — propagates JWT in service-to-service calls
- Shared DTOs — standardized request/response objects
- **All Enums** — shared across all services to maintain consistency

---

## Key Design Decisions

1. **Account Service has OpenAI** — because `Plan` entity has `maxTokensPerDay` and `unlimitedAi` fields; needs to understand token counting for billing
2. **Account is the foundation** — no Feign clients, other services call it
3. **Eureka Server has NO Config Client** — avoids circular dependency (Config Server registers with Eureka, so Eureka can't depend on Config Server at startup)
4. **Kafka topic**: `file-updates` — Intelligence publishes, Workspace subscribes (use Spring for Apache Kafka, NOT Kafka Streams)
5. **Config**: All services use YAML configuration format

---

## Migration Task

Code is being copied from `A:\TeamAI\TeamAI-backend\src\` and restructured into the appropriate service modules. Each service will have its own `src/main/java/com/distributed/teamai/<servicename>/` package structure.
