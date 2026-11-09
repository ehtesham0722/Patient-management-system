# System Architecture & Design

This document details the high-level design, repository structure, service boundary definitions, and data flow patterns across the Patient Management System.

---

## 1. High-Level Architecture Diagram

```
+--------------------------------------------------------------------------+
|                                CLIENTS                                   |
|               (Web Apps / Mobile Apps / Third-Party APIs)                |
+------------------------------------+-------------------------------------+
                                     |
                                     | HTTPS / JSON (REST)
                                     v
+--------------------------------------------------------------------------+
|                             API LAYER                                    |
|                                                                          |
|   +------------------------------------------------------------------+   |
|   |                         patient-service                          |   |
|   |                       (Spring Boot 3.4.0)                        |   |
|   |                                                                  |   |
|   |  - Controller: /patients (CRUD operations)                       |   |
|   |  - Validation: Bean validation & custom validation groups        |   |
|   |  - OpenApi 3: /swagger-ui/index.html                             |   |
|   |  - Persistence: Spring Data JPA + Hibernate                      |   |
|   |  - gRPC Client: BillingServiceGrpcClient (calls billing-service) |   |
|   |  - Event Producer: KafkaProducer (publishes to patient-events)   |   |
|   +----+--------------------------+----------------------------+-----+   |
+--------|--------------------------|----------------------------|---------+
         |                          |                            |
  JDBC   |                     gRPC |             Kafka Pub/Sub  |
(TCP 3306)                     (TCP 9001)                 (TCP 9092)
         v                          v                            v
+------------------+      +-------------------+       +--------------------+
|patient-service-db|      |  billing-service  |       |    Apache Kafka    |
|    (MySQL 8)     |      |(Spring Boot 3.4.0)|       |  (apache/kafka)    |
|                  |      |                   |       |     KRaft Mode     |
|- Database: `db`  |      |- gRPC Port: `9001`|       |- Node ID: `1`      |
|- Host Port: 3307 |      |- HTTP Port: `4001`|       |- Internal: `9092`  |
+------------------+      |- Proto: billing   |       |- Host: `9094`      |
                          +-------------------+       +--------------------+
```

---

## 2. Multi-Module Project Structure

The project has been organized into a decoupled, clean multi-module repository:

```
Patient-management-system/
├── pom.xml                    # Root Aggregator POM (packaging: pom)
│                              # Aggregates modules: patient-service, billing-service
│                              # Provides os-maven-plugin for protoc platform detection
├── docker-compose.yml         # Multi-container orchestration (MySQL, Kafka, Patient, Billing)
│
├── patient-service/           # Independent Microservice (REST + gRPC Client + Kafka Producer)
│   ├── pom.xml                # Spring Boot 3.4.0 + gRPC Client Starter + Spring Kafka
│   ├── Dockerfile             # Multi-stage Docker build
│   └── src/
│       ├── main/proto/
│       │   ├── billing.proto         # Stubs for calling billing-service via gRPC
│       │   └── patient_event.proto   # Schema for Kafka patient lifecycle events
│       ├── main/java/com/pm/patientservice/
│       │   ├── controller/           # REST endpoints
│       │   ├── dto/                  # Request / Response DTOs & Validators
│       │   ├── grpc/                 # BillingServiceGrpcClient
│       │   ├── kafka/                # KafkaProducer
│       │   ├── model/                # JPA Entities
│       │   ├── repository/           # Spring Data JPA Repositories
│       │   └── service/              # Business Logic
│       └── main/resources/ (application.properties, data.sql)
│
├── billing-service/           # Independent Microservice (gRPC Server)
│   ├── pom.xml                # Spring Boot 3.4.0 + gRPC Server Starter + Protobuf plugin
│   ├── Dockerfile             # Multi-stage Docker build
│   └── src/
│       ├── main/proto/
│       │   └── billing.proto         # Service contract for CreateBillingAccount
│       └── main/java/com/pm/billingservice/
│           └── service/              # BillingGrpcService (Server implementation)
│
├── api-calls/                 # HTTP request collection files
├── grpc-request/              # gRPC request templates
└── docs/                      # Architectural specifications & guides
```

---

## 3. Communication Protocols & Strategies

### A. External Gateway Communication (REST / HTTP)
* Used for external client consumption (UI, dashboards, frontends).
* Endpoints utilize standard REST semantics (`GET`, `POST`, `PUT`, `DELETE`), JSON payload serialization, and HTTP status codes (`200`, `201`, `400`, `404`, `409`).
* Validations are applied at the controller boundary using Jakarta Validation groups (`CreatePatientValidatorGroup`).

### B. Synchronous Inter-Service RPC (gRPC / HTTP/2)
* Microservices communicate with `billing-service` using **gRPC** over HTTP/2.
* **Why gRPC for Billing?**
  * **Strict Contracts**: Schema defined in `.proto` files ensures backward and forward compatibility.
  * **High Performance**: Binary Protobuf serialization avoids JSON parsing overhead.
  * **Multiplexed Connections**: Reuses single TCP connections over HTTP/2 with minimal connection overhead.
* **Server Reflection**: Enabled via `io.grpc.protobuf.services.ProtoReflectionService`, enabling zero-configuration schema inspection in Postman and CLI tools (`grpcurl`).
* **Client Implementation**: `patient-service` injects `BillingServiceGrpcClient` configured with `grpc-client-spring-boot-starter`.

### C. Asynchronous Event-Driven Messaging (Apache Kafka)
* Decouples event publishers from downstream consumers (e.g. notifications, audit logs, analytics).
* **Broker**: Official Apache Kafka image (`apache/kafka:latest`) deployed in **KRaft mode** (no ZooKeeper dependency).
* **Schema**: Protocol Buffers defined in [`patient_event.proto`](file:///C:/Users/Ehtesham%20Ahmed/Desktop/ecomers/Patient-management-system/patient-service/src/main/proto/patient_event.proto) (`patient.events.PatientEvent`).
* **Network Topology**:
  * **Container Listener (`PLAINTEXT://kafka:9092`)**: Used by microservices within the Docker bridge network.
  * **External Listener (`EXTERNAL://localhost:9094`)**: Used by local development tools and IDEs outside Docker.

---

## 4. Data Storage & Schema Design

### Patient Entity Schema
```sql
CREATE TABLE IF NOT EXISTS patient (
    id              VARCHAR(36) PRIMARY KEY,
    name            VARCHAR(255) NOT NULL,
    email           VARCHAR(255) UNIQUE NOT NULL,
    address         VARCHAR(255) NOT NULL,
    date_of_birth   DATE NOT NULL,
    registered_date DATE NOT NULL
);
```

* **ID Format**: 36-character string representation of standard UUID (`UUID.randomUUID().toString()`).
* **Hibernate Mapping**: Mapped with `@JdbcTypeCode(SqlTypes.VARCHAR)` in `Patient.java` to guarantee compatibility across MySQL, H2, and PostgreSQL without binary truncation errors.

---

## 5. Containerization & Networking

* **Docker Network**: All containers run attached to a bridge network named `internal`.
* **Orchestration**: Managed via `docker-compose.yml` at the repository root.
* **DNS Resolution**: Containers resolve each other by service container names (`patient-service-db`, `kafka`, `patient-service`, `billing-service`).
* **Host Port Isolation**:
  * MySQL internal container port: `3306` ➔ Host port: `3307` (avoids clashes with local MySQL).
  * Kafka internal port: `9092` ➔ Host port: `9094`.
  * Patient service container port: `4000` ➔ Host port: `4000`.
  * Billing service container port: `9001` (gRPC) / `4001` (HTTP) ➔ Host port: `9001` / `4001`.
