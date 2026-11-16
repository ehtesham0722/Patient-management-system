# Project Changelog & Architecture Decision Records (ADR)

This file tracks major milestones, architectural decisions, solved issues, and upcoming roadmap items.

---

## 📅 Roadmap & Milestones

- [x] **Milestone 1: Patient Service Foundation**
  - [x] CRUD REST APIs for Patient entity.
  - [x] Database seed scripts (`data.sql`).
  - [x] OpenAPI 3 / Swagger UI integration.
  - [x] Multi-stage Dockerization (`Dockerfile`).
  - [x] MySQL 8 Docker setup on internal network.
- [x] **Milestone 2: Billing Service & gRPC Communication**
  - [x] Protobuf schema definition (`billing.proto`).
  - [x] Protobuf & gRPC Java code generation with Maven.
  - [x] Spring Boot 3 gRPC server implementation (`BillingGrpcService`).
  - [x] Server reflection enabled for testing via Postman / `grpcurl`.
- [x] **Milestone 3: Structural Decoupling & Multi-Module Organization**
  - [x] Moved `patient-service` into dedicated sibling subfolder alongside `billing-service`.
  - [x] Created root multi-module aggregator `pom.xml`.
  - [x] Created root `docker-compose.yml` orchestrating all containers.
  - [x] Standardized module directory and artifact naming to lowercase.
  - [x] Updated IntelliJ IDEA project model and Docker run configurations.
- [x] **Milestone 4: Event Streaming & Inter-Service Integration**
  - [x] Deployed Apache Kafka (`apache/kafka:latest`) in KRaft mode via Docker Compose.
  - [x] Added `spring-kafka` dependency and `KafkaProducer` skeleton in `patient-service`.
  - [x] Defined Kafka event contract `patient_event.proto` (`PatientEvent`).
  - [x] Created `analytic-service` as an independent consumer of `patient-events` via `@KafkaListener`.
  - [x] Created `BillingServiceGrpcClient` in `patient-service` using blocking stubs.
- [x] **Milestone 5: API Gateway & Centralized Routing**
  - [x] Built `api-gateway` on Spring Cloud Gateway Server WebFlux (Port 4004).
  - [x] Configured route prefix stripping (`/api/patients` -> `patient-service:4000/patients`).
  - [x] Exposed OpenAPI proxy route (`/api-docs/patients`).
  - [x] Integrated into root `pom.xml` and `docker-compose.yml`.
- [x] **Milestone 6: Authentication & Security Service**
  - [x] Created standalone `auth-service` (Port 4005).
  - [x] Configured Spring Security, BCrypt password hashing, and JJWT 0.12.6.
  - [x] Built login endpoint (`POST /login`) issuing signed HMAC-SHA256 tokens.
  - [x] Standardized database from PostgreSQL to MySQL with seed data (`data.sql`).

---

## 🛠 Key Architecture Decisions & Solved Issues

### ADR 001: Standardizing on Spring Boot 3.4.0 & Java 17
* **Date**: November 2026
* **Decision**: Standardized microservices on **Spring Boot 3.4.0** and **Java 17** for compatibility with `grpc-spring-boot-starter:3.1.0.RELEASE`.

### ADR 002: MySQL Port Mapping (`3307:3306`)
* **Date**: October 2026
* **Decision**: Bound MySQL container port `3306` to host port `3307` to prevent conflict with local Windows host `mysqld` service.

### ADR 003: Protobuf Directory Convention
* **Date**: November 2026
* **Decision**: Relocated `.proto` files to standard `src/main/proto/` and added `build-helper-maven-plugin` to register generated sources in IntelliJ.

### ADR 004: Hibernate UUID Column Definition
* **Date**: October 2026
* **Decision**: Annotated `id` field in entities with `@JdbcTypeCode(SqlTypes.VARCHAR)` and updated SQL schemas to use `VARCHAR(36) PRIMARY KEY`.

### ADR 005: Billing-Service Dockerfile Artifact Case Sensitivity
* **Date**: November 2026
* **Decision**: Standardized artifactId to lowercase `billing-service` and Dockerfile to copy `/app/target/billing-service-0.0.1-SNAPSHOT.jar`.

### ADR 006: Microservices Structural Decoupling into Multi-Module Root
* **Date**: November 2026
* **Decision**: Decoupled `patient-service` and `billing-service` into siblings under a root aggregator POM and unified `docker-compose.yml`.

### ADR 007: Configuring Protobuf and gRPC Client in Patient-Service
* **Date**: November 2026
* **Decision**: Used `grpc-client-spring-boot-starter` in `patient-service` to call `billing-service` without starting an unwanted local gRPC server.

### ADR 008: Kafka Docker Image Resolution (apache/kafka:latest)
* **Date**: November 2026
* **Decision**: Replaced deprecated `bitnami/kafka:latest` with official `apache/kafka:latest` in native KRaft mode.

### ADR 009: Protobuf Build Lifecycle & Stale Module Cleanup
* **Date**: November 2026
* **Decision**: Bound `protobuf-maven-plugin` to `generate-sources` and cleaned stale `.idea/` module references.

### ADR 010: Analytic-Service Kafka Consumer Integration
* **Date**: November 2026
* **Context**: System required an asynchronous consumer for patient lifecycle events.
* **Decision**: Created `analytic-service` on port `4002`, consuming from topic `patient-events` in consumer group `analytics-group` with Protobuf deserialization.

### ADR 011: Spring Cloud Gateway Server WebFlux Configuration & Path Predicate
* **Date**: November 2026
* **Context**: In Spring Cloud Gateway 5.x (`spring-cloud-starter-gateway-server-webflux`), routes defined under legacy `spring.cloud.gateway.routes` were not loaded, causing all requests to return `404 Not Found`. Furthermore, `Path=/api/patients/**` did not match root collection path `/api/patients`.
* **Decision**:
  1. Configured routes under `spring.cloud.gateway.server.webflux.routes`.
  2. Declared predicates with both root and wildcard paths: `Path=/api/patients, /api/patients/**`.
  3. Corrected service URI to `http://patient-service:4000`.

### ADR 012: Auth-Service Migration from PostgreSQL to MySQL
* **Date**: November 2026
* **Context**: `auth-service` initially had PostgreSQL dependencies and SQL syntax (`UUID PRIMARY KEY`, `"users"`), crashing with `ClassNotFoundException: com.mysql.cj.jdbc.Driver` and SQL syntax errors on MySQL.
* **Decision**:
  1. Replaced `org.postgresql:postgresql` with `com.mysql:mysql-connector-j` in `auth-service/pom.xml`.
  2. Updated `data.sql` to MySQL syntax (`VARCHAR(36) PRIMARY KEY`, standard table identifiers).
  3. Standardized entity annotation to `@Table(name = "users")`.

### ADR 013: JWT Key Decoding & Property Resolution Resilience
* **Date**: November 2026
* **Context**: `JwtUtil` constructor had `@Value("${{jwt.secret}")` with an extra `{`, injecting the literal string `"${{jwt.secret}"` and throwing `IllegalArgumentException: Illegal base64 character 24` (`$` character). Additionally, non-base64 secrets caused decoding failures.
* **Decision**:
  1. Fixed property placeholder to `@Value("${jwt.secret:${JWT_SECRET:...}}")`.
  2. Made `JwtUtil` resilient: attempts Base64 decode first, falling back to UTF-8 bytes for raw string secrets.
  3. Added default 256-bit secret in `application.properties`.
