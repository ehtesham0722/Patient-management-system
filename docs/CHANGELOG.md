# Project Changelog & Architecture Decision Records (ADR)

This file tracks major milestones, architectural decisions, solved issues, and upcoming roadmap items. Keep this document updated as new features and services are developed.

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
- [ ] **Milestone 4: Event Streaming & Inter-Service Integration (In Progress)**
  - [x] Deployed Apache Kafka (`apache/kafka:latest`) in KRaft mode via Docker Compose and IntelliJ.
  - [x] Added `spring-kafka` dependency and created `KafkaProducer` skeleton in `patient-service`.
  - [x] Defined Kafka event contract [`patient_event.proto`](file:///C:/Users/Ehtesham%20Ahmed/Desktop/ecomers/Patient-management-system/patient-service/src/main/proto/patient_event.proto) (`PatientEvent`).
  - [x] Configured Protobuf compilation (`protobuf-maven-plugin`) and source roots in `patient-service`.
  - [x] Created `BillingServiceGrpcClient` in `patient-service` using blocking stubs.
  - [ ] Automatically publish `PatientEvent` to Kafka on patient creation/update/deletion.
  - [ ] Automatically trigger `CreateBillingAccount` via gRPC when a new patient is created via `POST /patients`.
  - [ ] Implement error handling, circuit breaking, and dead-letter queues.
- [ ] **Milestone 5: API Gateway & Security**
  - [ ] Spring Cloud Gateway or reverse proxy for unified entry point.
  - [ ] JWT / OAuth2 authentication and authorization.
  - [ ] Centralized logging and distributed tracing (OpenTelemetry / Zipkin).

---

## 🛠 Key Architecture Decisions & Solved Issues

### ADR 001: Standardizing on Spring Boot 3.4.0 & Java 17
* **Date**: November 2026
* **Context**: `patient-service` was running on Spring Boot 3.4.0 and Java 17, while `billing-service` initially specified Java 21 and Spring Boot 4.1.2.
* **Decision**: Standardized all microservices on **Spring Boot 3.4.0** and **Java 17**.
* **Reasoning**:
  * Prevents compilation errors on JDK 17 environments.
  * `net.devh:grpc-spring-boot-starter:3.1.0.RELEASE` is designed for Spring Boot 3 (Spring 6), preventing `AbstractMethodError` runtime crashes.

### ADR 002: MySQL Port Mapping (`3307:3306`)
* **Date**: October 2026
* **Context**: Native Windows host already occupied port `3306` with a local `mysqld` service, preventing Docker port binding.
* **Decision**: Bound the container port `3306` to host port `3307`. Containers inside the Docker `internal` bridge network continue to connect directly via `patient-service-db:3306`.

### ADR 003: Protobuf Directory Convention
* **Date**: November 2026
* **Context**: `billing.proto` was originally placed in `Billing-service/src/proto/`, which `protobuf-maven-plugin` ignores by default.
* **Decision**: Relocated `.proto` files to standard `src/main/proto/` and added `build-helper-maven-plugin` to guarantee IntelliJ IDEA auto-registers generated sources under `target/generated-sources/protobuf`.

### ADR 004: Hibernate UUID Column Definition
* **Date**: October 2026
* **Context**: MySQL threw `Data too long for column 'id'` during Hibernate schema update when reading standard UUID strings.
* **Decision**: Annotated `id` field in `Patient.java` with `@JdbcTypeCode(SqlTypes.VARCHAR)` and updated `data.sql` to use `VARCHAR(36) PRIMARY KEY`.

### ADR 005: Billing-Service Dockerfile Artifact Case Sensitivity
* **Date**: November 2026
* **Context**: Docker build failed in the runner stage trying to copy `billing-service-0.0.1-SNAPSHOT.jar` because Maven generated `Billing-service-0.0.1-SNAPSHOT.jar` matching `<artifactId>Billing-service</artifactId>`.
* **Decision**: Standardized artifactId to lowercase `billing-service` and Dockerfile to copy `/app/target/billing-service-0.0.1-SNAPSHOT.jar`.

### ADR 006: Microservices Structural Decoupling into Multi-Module Root
* **Date**: November 2026
* **Context**: `billing-service` was originally placed as a child subfolder inside the root of `patient-service`, coupling their repositories, Docker contexts, and Maven lifecycles incorrectly.
* **Decision**:
  1. Extracted `patient-service` code and configuration into its own dedicated `patient-service/` subfolder.
  2. Standardized `billing-service` as an independent sibling folder at root.
  3. Established root `pom.xml` as a Maven aggregator POM (`packaging: pom`).
  4. Created root `docker-compose.yml` to spin up the database, REST service, and gRPC service with one command (`docker compose up --build`).
  5. Updated `.idea/` run configurations with correct context paths and module names.

### ADR 007: Configuring Protobuf and gRPC Client in Patient-Service
* **Date**: November 2026
* **Context**: Adding `src/main/proto/billing.proto` to `patient-service` failed to compile into Java classes because Maven had no build execution goals attached for `.proto` compilation.
* **Decision**:
  1. Configured `protobuf-maven-plugin` (version 0.6.1) with `protoc 3.25.5` and `protoc-gen-grpc-java 1.68.1`.
  2. Configured `build-helper-maven-plugin` to register `target/generated-sources/protobuf/java` and `grpc-java` as source directories.
  3. Switched from full `grpc-spring-boot-starter` to `grpc-client-spring-boot-starter` to avoid spinning up an unnecessary gRPC server on port 9090.
  4. Added gRPC client channel configuration in `application.properties` pointing to `billing-service` (`localhost:9001` locally, `billing-service:9001` in Docker Compose).

### ADR 008: Kafka Docker Image Resolution (apache/kafka:latest)
* **Date**: November 2026
* **Context**: Deploying `bitnami/kafka:latest` failed with `Status 404: failed to resolve reference "docker.io/bitnami/kafka:latest": not found` because Broadcom/Bitnami restricted and deprecated public Docker Hub tags.
* **Decision**:
  1. Switched to the official Apache Software Foundation image: **`apache/kafka:latest`**.
  2. Configured native KRaft environment variables in `docker-compose.yml` and `.idea/workspace.xml` (Node ID 1, roles `broker,controller`, internal listener `9092`, external listener `9094`, static cluster ID).

### ADR 009: Protobuf Build Lifecycle & Stale Module Cleanup
* **Date**: November 2026
* **Context**: Protobuf files in `patient-service` were not compiling during IntelliJ IDE build, and a phantom module `patient-service (1)` was created in `.idea/modules.xml` with broken source root mappings.
* **Decision**:
  1. Lifted `kr.motd.maven:os-maven-plugin:1.7.0` to root aggregator `pom.xml` so all modules inherit `${os.detected.classifier}` automatically.
  2. Bound `protobuf-maven-plugin` execution explicitly to the `<phase>generate-sources</phase>` phase with execution ID `protoc-compile`.
  3. Cleaned up stale `patient-service (1).iml` and corrupted `.idea/modules.xml` and `.idea/compiler.xml`.
  4. Added `option java_package = "patient.events";` to `patient_event.proto`.
