# System Architecture & Design

This document details the high-level design, repository structure, service boundary definitions, protocols, and data flow patterns across the Patient Management System.

---

## 1. High-Level Architecture Diagram

```
+----------------------------------------------------------------------------------------------------+
|                                              CLIENTS                                               |
|                            (Web Apps / Mobile Apps / Postman / cURL)                               |
+-------------------------------------------------+--------------------------------------------------+
                                                  |
                                                  | HTTP / JSON (Port 4004)
                                                  v
+----------------------------------------------------------------------------------------------------+
|                                            API GATEWAY                                             |
|                               (Spring Cloud Gateway Server WebFlux)                                |
|                                                                                                    |
|  - Inbound Port: 4004                                                                              |
|  - Route: /api/patients, /api/patients/**  --> patient-service:4000 (StripPrefix=1)                |
|  - Route: /api-docs/patients               --> patient-service:4000/v3/api-docs (RewritePath)       |
+-----------------------+---------------------------------------------------+------------------------+
                        |                                                   |
       /api/patients    |                                     Direct Access | (Or Gateway Proxy)
       (StripPrefix=1)  |                                                   v
                        v                                   +--------------------------------+
+------------------------------------------------+          |          auth-service          |
|                patient-service                 |          | (Spring Boot / Spring Security)|
|              (Spring Boot 3.4.0)               |          |                                |
|                                                |          | - Port: 4005                   |
| - Controller: /patients (CRUD operations)      |          | - Endpoint: POST /login        |
| - Validation: Bean Validation & Groups         |          | - JWT: JJWT 0.12.6 HMAC-SHA256 |
| - Persistence: Spring Data JPA + MySQL         |          | - Password: BCryptEncoder      |
| - gRPC Client: BillingServiceGrpcClient        |          | - Persistence: MySQL           |
| - Event Producer: KafkaProducer (patient-events)          +---------------+----------------+
+----+-------------------+--------------------+--+                          |
     |                   |                    |                             | JDBC (TCP 3306)
JDBC |              gRPC |      Kafka Pub/Sub |                             v
(3306|           (TCP 9001)        (TCP 9092) |                    +-----------------+
     v                   v                    v                    | auth-service-db |
+------------------+ +-------------------+ +--------------------+  |    (MySQL 8)    |
|patient-service-db| |  billing-service  | |    Apache Kafka    |  |- Host Port: 3308|
|    (MySQL 8)     | |(Spring Boot 3.4.0)| |  (apache/kafka)    |  |- Database: `db` |
|- Host Port: 3307 | |- gRPC Port: `9001`| |     KRaft Mode     |  +-----------------+
|- Database: `db`  | |- HTTP Port: `4001`| |- Node ID: `1`      |
+------------------+ |- Proto: `billing` | |- Internal: `9092`  |
                     +-------------------+ |- Host: `9094`      |
                                           +---------+----------+
                                                     |
                                                     | Kafka Consumer (9092)
                                                     v
                                           +--------------------+
                                           |  analytic-service  |
                                           |(Spring Boot 3.4.0) |
                                           |- HTTP Port: `4002` |
                                           |- Consumer Group:   |
                                           |  `analytics-group` |
                                           |- Proto:            |
                                           |  `patient_event`   |
                                           +--------------------+
```

---

## 2. Multi-Module Project Structure

The project is organized as an independent multi-module Maven project rooted at `pom.xml`:

```
Patient-management-system/
├── pom.xml                    # Root Aggregator POM (packaging: pom)
│                              # Aggregates modules: patient-service, billing-service, analytic-service, api-gateway, auth-service
│                              # Configures os-maven-plugin for protoc platform detection
├── docker-compose.yml         # Multi-container orchestration (MySQL DBs, Kafka, and 5 Microservices)
│
├── api-gateway/               # API Gateway (Spring Cloud Gateway Server WebFlux)
│   ├── pom.xml                # Spring Boot 4.1.2 + spring-cloud-starter-gateway-server-webflux 5.0.4
│   ├── Dockerfile             # Multi-stage Docker build (Port 4004)
│   └── src/main/resources/
│       └── application.yml    # Routes under spring.cloud.gateway.server.webflux.routes
│
├── auth-service/              # Identity & Access Management Service
│   ├── pom.xml                # Spring Boot 4.1.2 + Spring Security + JJWT 0.12.6 + MySQL
│   ├── Dockerfile             # Multi-stage Docker build (Port 4005)
│   └── src/main/
│       ├── java/com/pm/authservice/
│       │   ├── config/        # SecurityConfig (BCryptPasswordEncoder, permitAll rules)
│       │   ├── controller/    # AuthController (POST /login)
│       │   ├── dto/           # LoginRequestDto, LoginResponseDto
│       │   ├── model/         # User JPA Entity (users table)
│       │   ├── repository/    # UserRepository (findByEmail)
│       │   ├── service/       # AuthService, UserService
│       │   └── util/          # JwtUtil (HMAC-SHA256 token generation & resilient key parsing)
│       └── resources/
│           ├── application.properties # Server port 4005, JWT Secret property
│           └── data.sql       # Seed admin user with BCrypt hashed password
│
├── patient-service/           # Core Patient Domain Microservice
│   ├── pom.xml                # Spring Boot 3.4.0 + gRPC Client Starter + Spring Kafka
│   ├── Dockerfile             # Multi-stage Docker build (Port 4000)
│   └── src/main/
│       ├── proto/
│       │   ├── billing.proto         # Stubs for calling billing-service via gRPC
│       │   └── patient_event.proto   # Schema for Kafka patient lifecycle events
│       ├── java/com/pm/patientservice/
│       │   ├── controller/           # REST endpoints (/patients)
│       │   ├── dto/                  # Request / Response DTOs & Custom Validation Groups
│       │   ├── grpc/                 # BillingServiceGrpcClient (Blocking Stub)
│       │   ├── kafka/                # KafkaProducer (Spring Kafka Template)
│       │   ├── mapper/               # PatientMapper
│       │   ├── model/                # Patient Entity (MySQL schema)
│       │   ├── repository/           # PatientRepository
│       │   └── service/              # PatientService
│       └── resources/ (application.properties, data.sql)
│
├── billing-service/           # Synchronous Billing Microservice (gRPC Server)
│   ├── pom.xml                # Spring Boot 3.4.0 + gRPC Server Starter + Protobuf plugin
│   ├── Dockerfile             # Multi-stage Docker build (Ports 9001/4001)
│   └── src/main/
│       ├── proto/
│       │   └── billing.proto         # Service contract for CreateBillingAccount
│       └── java/com/pm/billingservice/
│           └── service/              # BillingGrpcService (Server implementation)
│
└── analytic-service/          # Kafka Event Consumer Analytics Service
    ├── pom.xml                # Spring Boot 3.4.0 + Spring Kafka + Protobuf plugin
    ├── Dockerfile             # Multi-stage Docker build (Port 4002)
    └── src/main/
        ├── proto/
        │   └── patient_event.proto   # Schema for deserializing PatientEvent
        └── java/com/pm/analyticservice/
            └── kafka/                # KafkaConsumer (@KafkaListener on patient-events)
```

---

## 3. Communication Protocols & Data Flows

### 3.1 Edge to Gateway (HTTP/1.1 REST)
* Clients communicate with `api-gateway` on port `4004`.
* Routes strip path prefixes (e.g., `/api/patients` is rewritten to `/patients` before forwarding to `patient-service:4000`).
* Route predicates match both root collection endpoints (`/api/patients`) and sub-paths (`/api/patients/**`).

### 3.2 Synchronous Service-to-Service (gRPC / HTTP/2)
* `patient-service` acts as a **gRPC Client** invoking `billing-service` on port `9001`.
* Service contract defined in `billing.proto` (`CreateBillingAccount`).
* Binary Protobuf serialization ensures minimal payload size and low latency.

### 3.3 Asynchronous Domain Events (Kafka / KRaft)
* When patient domain events occur (creation, update, deletion), `patient-service` publishes a serialized `PatientEvent` to the `patient-events` Kafka topic.
* `analytic-service` listens to topic `patient-events` via `@KafkaListener` in consumer group `analytics-group`.

### 3.4 Security & Token Verification (JJWT & BCrypt)
* `auth-service` verifies user credentials against `auth-service-db` using `BCryptPasswordEncoder`.
* Generates signed JWT tokens with 10-hour validity using HMAC-SHA256 (`Keys.hmacShaKeyFor`).
* `JwtUtil` supports both Base64-encoded secrets and standard raw secrets seamlessly.

### 3.5 Database Isolation
* **`patient-service-db`**: MySQL 8 instance on internal port `3306`, mapped to host port `3307`. Stores patient demographic and medical records.
* **`auth-service-db`**: Dedicated MySQL 8 instance on internal port `3306`, mapped to host port `3308`. Stores user credentials, roles, and authentication records.
