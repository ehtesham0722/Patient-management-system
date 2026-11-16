# Patient Management System (PMS)

A modern, cloud-ready microservices architecture designed for healthcare management, patient lifecycle tracking, automated billing, event streaming, centralized API routing, and secure authentication.

---

## 🏗 System Architecture Overview

```
                      +---------------------------------------+
                      |       Client / Frontend / HTTP        |
                      +-------------------+-------------------+
                                          |
                                          | HTTP / JSON (Port 4004)
                                          v
                      +---------------------------------------+
                      |              api-gateway              |
                      | (Spring Cloud Gateway WebFlux : 4004) |
                      +---------+-------------------+---------+
                                |                   |
                 /api/patients  |                   | /login (Port 4005)
                 (StripPrefix=1)|                   v
                                v         +-------------------+
                      +-----------------+ |   auth-service    |
                      | patient-service | | (Port 4005 / REST)|
                      | (Port 4000/REST)| +---------+---------+
                      +----+-------+----+           |
                           |       |                | JDBC (3306)
             JDBC (TCP 3306|       | gRPC (HTTP/2)  v
                           v       v         +-----------------+
                  +----------+ +-----------+ | auth-service-db |
                  | MySQL 8  | |  billing- | | (Port 3308/3306)|
                  |Port 3307 | |  service  | +-----------------+
                  +----------+ |(Port 9001)|
                               +-----------+
                                   |
                  Kafka Pub/Sub    | (patient-events)
                  (TCP 9092)       v
                            +---------------+
                            |  Apache Kafka |
                            | (Ports 9092/4)|
                            +-------+-------+
                                    |
                                    | Kafka Consumer
                                    v
                            +---------------+
                            |analytic-serv. |
                            | (Port 4002)   |
                            +---------------+
```

The system is organized as a decoupled, production-ready multi-module architecture:
* **API Gateway (`api-gateway`)**: Spring Cloud Gateway Server WebFlux providing a single entry point, path routing, prefix stripping, and OpenAPI documentation aggregation on port `4004`.
* **Authentication Service (`auth-service`)**: Spring Security & JJWT (0.12.6) token service for user credential verification and role-based JWT issuance on port `4005`.
* **Core Patient Management (`patient-service`)**: Domain REST API with bean validation, JPA persistence, gRPC billing client, and Kafka event publishing on port `4000`.
* **Synchronous Billing Integration (`billing-service`)**: High-throughput, low-latency **gRPC / Protocol Buffers** for transactional billing account provisioning on port `9001` (HTTP: `4001`).
* **Asynchronous Event Analytics (`analytic-service`)**: **Apache Kafka** consumer group processing patient lifecycle events (`patient-events`) serialized via Protocol Buffers on port `4002`.
* **Data Storage**: MySQL 8 containers (`patient-service-db` on host port `3307`, `auth-service-db` on host port `3308`) with automated schema migration.

---

## 📁 Repository Layout

```
Patient-management-system/
├── pom.xml                    # Root Maven Aggregator POM (packaging: pom)
├── docker-compose.yml         # Full multi-container orchestration (DBs, Kafka, 5 Microservices)
├── api-gateway/               # Spring Cloud Gateway Server WebFlux (Port 4004)
│   ├── pom.xml
│   ├── Dockerfile
│   └── src/main/resources/    # application.yml (Routes & Filters)
├── auth-service/              # JWT & Spring Security Authentication Service (Port 4005)
│   ├── pom.xml
│   ├── Dockerfile
│   └── src/main/
│       ├── java/com/pm/authservice/   # Controllers, DTOs, Security, JWT utilities
│       └── resources/                 # application.properties, data.sql (Admin seed)
├── patient-service/           # REST Patient Service + gRPC Client + Kafka Producer (Port 4000)
│   ├── pom.xml
│   ├── Dockerfile
│   └── src/main/
│       ├── proto/             # billing.proto, patient_event.proto
│       └── java/com/pm/patientservice/
│           ├── grpc/          # BillingServiceGrpcClient
│           └── kafka/         # KafkaProducer
├── billing-service/           # gRPC Billing Service (Ports 9001/4001)
│   ├── pom.xml
│   ├── Dockerfile
│   └── src/main/
│       ├── proto/             # billing.proto
│       └── java/com/pm/billingservice/ # BillingGrpcService
├── analytic-service/          # Kafka Event Consumer Analytics Service (Port 4002)
│   ├── pom.xml
│   ├── Dockerfile
│   └── src/main/
│       ├── proto/             # patient_event.proto
│       └── java/com/pm/analyticservice/ # KafkaConsumer
├── api-calls/                 # HTTP request collections (IntelliJ / REST Client)
├── grpc-request/              # gRPC request templates
└── docs/                      # Comprehensive architectural specs & setup documentation
```

---

## 📦 Microservices & Infrastructure Breakdown

| Service / Container | Internal Port | Host Port | Protocol / Stack | Responsibility |
| :--- | :--- | :--- | :--- | :--- |
| **`api-gateway`** | `4004` | `4004` | HTTP / Spring Cloud Gateway Server WebFlux | Unified entry point, routing (`/api/patients` -> `patient-service`), OpenAPI proxy. |
| **`auth-service`** | `4005` | `4005` | REST / Spring Security, JJWT, MySQL | Authentication (`POST /login`), password hashing (BCrypt), JWT issuance. |
| **`patient-service`** | `4000` | `4000` | REST / Spring Boot 3.4.0, JPA, MySQL, gRPC Client, Kafka Producer | Patient records CRUD, bean validation, gRPC billing invocation, event publication. |
| **`billing-service`** | `9001` (gRPC), `4001` (HTTP) | `9001`, `4001` | gRPC (HTTP/2 / Protobuf 3), `net.devh:grpc-spring-boot-starter` | Billing account management and invoicing via gRPC. |
| **`analytic-service`**| `4002` | `4002` | HTTP / Spring Kafka Consumer, Protobuf | Consumes `patient-events` from Kafka for audit and analytics. |
| **`kafka`** | `9092` | `9092`, `9094` | Apache Kafka (KRaft mode) | Distributed event streaming bus. |
| **`patient-service-db`** | `3306` | `3307` | MySQL 8.x | Database for patient records and profiles. |
| **`auth-service-db`** | `3306` | `3308` | MySQL 8.x | Database for user credentials and roles. |

---

## 🚀 Quick Start Guide

### Option A: Run Full Stack via Docker Compose (Recommended)

From the project root:
```bash
docker compose up --build -d
```

This builds and launches all 5 microservices and 3 infrastructure containers:
1. `patient-service-db` (MySQL on host port `3307`)
2. `auth-service-db` (MySQL on host port `3308`)
3. `kafka` (Kafka KRaft on internal port `9092` / host port `9094`)
4. `patient-service` (REST API on host port `4000`)
5. `billing-service` (gRPC on port `9001`, HTTP on port `4001`)
6. `analytic-service` (Kafka Consumer on host port `4002`)
7. `api-gateway` (Spring Cloud Gateway on host port `4004`)
8. `auth-service` (Authentication Service on host port `4005`)

To monitor live logs:
```bash
docker compose logs -f
```

To stop all services:
```bash
docker compose down
```

---

### Option B: Run Locally via Maven

#### 1. Start Infrastructure Containers
```bash
docker compose up -d patient-service-db auth-service-db kafka
```

#### 2. Build All Services from Root
```bash
./mvnw clean compile
```

#### 3. Run Microservices
Open separate terminal tabs:

* **API Gateway**:
  ```bash
  cd api-gateway && ./mvnw spring-boot:run
  ```
* **Auth Service**:
  ```bash
  cd auth-service && ./mvnw spring-boot:run
  ```
* **Patient Service**:
  ```bash
  cd patient-service && ./mvnw spring-boot:run
  ```
* **Billing Service**:
  ```bash
  cd billing-service && ./mvnw spring-boot:run
  ```
* **Analytic Service**:
  ```bash
  cd analytic-service && ./mvnw spring-boot:run
  ```

---

## 🧪 Testing the APIs

### 1. Test Gateway Routing (Patient API)
```bash
curl --location 'http://localhost:4004/api/patients'
```

### 2. Test User Login (Auth Service)
Default seeded admin account (`data.sql`):
```bash
curl --location 'http://localhost:4005/login' \
  --header 'Content-Type: application/json' \
  --data '{
    "email": "testuser@test.com",
    "password": "password"
  }'
```
*Response*: `{"token": "eyJhbGciOiJIUzI1NiJ9..."}`

### 3. Test Direct Patient Service & OpenAPI Docs
* Swagger UI (Direct): `http://localhost:4000/swagger-ui/index.html`
* Swagger UI (via Gateway): `http://localhost:4004/api-docs/patients`

### 4. Test Billing Service (gRPC) via `grpcurl`
```bash
grpcurl -plaintext -d '{
  "patientId": "123456",
  "name": "John Doe",
  "email": "john.doe@example.com"
}' localhost:9001 billing.BillingService/CreateBillingAccount
```

---

## 📚 Project Documentation Hub

Detailed documentation is available in the [`docs/`](./docs) directory:

* 📐 [**Architecture & Protocols**](./docs/ARCHITECTURE.md) - Deep dive into multi-module layout, sync vs async communication, schema management, and architectural diagrams.
* 🔌 [**Service & API Specifications**](./docs/SERVICES.md) - Complete endpoints, Protobuf contracts (`billing.proto`, `patient_event.proto`), and request/response models.
* 🛠 [**Environment & IDE Setup**](./docs/SETUP_AND_RUN.md) - Run configurations, troubleshooting, Kafka/MySQL setup, and IntelliJ IDEA configuration.
* 📝 [**Development Changelog & Roadmap**](./docs/CHANGELOG.md) - Living log of ongoing development, fixes, and Architecture Decision Records (ADR 001 through ADR 013).

---

## 🛠 Tech Stack Details

* **Language**: Java 17
* **Framework**: Spring Boot 3.4.0 / Spring Boot 4.1.2 (Gateway & Auth)
* **API Gateway**: Spring Cloud Gateway Server WebFlux (Spring Cloud 2025.1.4)
* **Security & Auth**: Spring Security 6 / 7, JJWT (Java JWT) 0.12.6, BCrypt
* **API Documentation**: SpringDoc OpenAPI 3 (Swagger UI)
* **RPC & Serialization**: Google Protocol Buffers 4.x, gRPC 1.69.0, `net.devh:grpc-spring-boot-starter` 3.1.0.RELEASE
* **Event Streaming**: Apache Kafka (`apache/kafka:latest`) in KRaft mode, Spring Kafka 3.3.0
* **Persistence & ORM**: Spring Data JPA, Hibernate 6.6
* **Database**: MySQL 8 (Dockerized) / H2 in-memory support for testing
* **Containerization**: Docker multi-stage builds (Eclipse Temurin JRE 17 runtime) & Docker Compose
