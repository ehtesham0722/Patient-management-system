# Patient Management System (PMS)

A modern, cloud-ready microservices architecture designed for healthcare management, patient lifecycle tracking, automated billing, and event streaming.

---

## 🏗 System Architecture Overview

```
                      +-----------------------------+
                      |   Client / Frontend / HTTP  |
                      +--------------+--------------+
                                     |
                                     | REST (HTTP/JSON)
                                     v
                       +---------------------------+
                       |      patient-service      |
                       |    (Port 4000 / REST)     |
                       +-------+-----------+-------+
                               |           |
             JDBC (TCP 3306)   |           | gRPC (HTTP/2 / Protobuf)
                               v           v
                     +-----------+       +-------------------+
                     |  MySQL 8  |       |  billing-service  |
                     | Port 3307 |       | (Port 9001 / gRPC)|
                     +-----------+       +-------------------+
                               |
                               | Kafka Pub/Sub Events
                               v
                     +-------------------+
                     |    Apache Kafka   |
                     |  (Ports 9092/9094)|
                     +-------------------+
```

The system is organized as a decoupled multi-module architecture communicating via protocol-appropriate channels:
* **External API Communication**: Standard RESTful APIs with OpenAPI / Swagger documentation for CRUD operations and client applications.
* **Synchronous Inter-Service Communication**: High-throughput, low-latency **gRPC / Protocol Buffers** for transactional service-to-service operations (such as account creation and billing triggers).
* **Asynchronous Event-Driven Messaging**: **Apache Kafka** (KRaft mode) for publishing domain lifecycle events (`patient-events`).
* **Data Storage**: MySQL 8 running containerized with isolated schema migration and persistence.
* **Multi-Module Orchestration**: Root Maven aggregator POM managing standalone sub-services `patient-service` and `billing-service`.

---

## 📁 Repository Layout

```
Patient-management-system/
├── pom.xml                    # Root Maven Aggregator POM
├── docker-compose.yml         # Full system multi-container orchestration (DB, Kafka, Patient, Billing)
├── patient-service/           # RESTful Patient Management Service + gRPC Client + Kafka Producer
│   ├── pom.xml
│   ├── Dockerfile
│   ├── mvnw / mvnw.cmd
│   └── src/
│       ├── main/proto/        # billing.proto, patient_event.proto
│       └── main/java/com/pm/patientservice/
│           ├── grpc/          # BillingServiceGrpcClient
│           └── kafka/         # KafkaProducer
├── billing-service/           # gRPC Billing Service (Server)
│   ├── pom.xml
│   ├── Dockerfile
│   ├── mvnw / mvnw.cmd
│   └── src/main/proto/        # billing.proto
├── api-calls/                 # HTTP request collections (IntelliJ / REST Client)
├── grpc-request/              # gRPC request templates
└── docs/                      # Comprehensive architectural specs & setup documentation
```

---

## 📦 Microservices & Infrastructure Breakdown

| Service / Component | Protocol / Port | Tech Stack | Responsibility |
| :--- | :--- | :--- | :--- |
| **`patient-service`** | REST / `4000` | Spring Boot 3.4.0, Java 17, JPA, MySQL, Spring Kafka, gRPC Client, OpenAPI 3 | Patient registration, records, search, validation, gRPC billing calls, and event publication. |
| **`billing-service`** | gRPC / `9001` (HTTP: `4001`) | Spring Boot 3.4.0, Java 17, Protobuf, `net.devh:grpc-spring-boot-starter` | Billing account management, invoicing, fee processing, and payment status tracking. |
| **`kafka`** | TCP / `9092` (Docker), `9094` (Host) | Apache Kafka (`apache/kafka:latest`) in KRaft mode | Event streaming bus for asynchronous inter-service domain event propagation. |
| **`patient-service-db`** | TCP / `3307` (Docker `3306`) | MySQL 8.x Docker container | Primary relational database for patient persistence and initial seed data. |

---

## 🚀 Quick Start Guide

### Option A: Run Full Stack via Docker Compose (Recommended)

From the project root:
```bash
docker compose up --build -d
```
This builds and starts:
1. `patient-service-db` (MySQL 8 on host port `3307`)
2. `kafka` (Apache Kafka on internal port `9092` / host port `9094`)
3. `patient-service` (REST API on host port `4000`)
4. `billing-service` (gRPC on port `9001`, HTTP on port `4001`)

To view live logs:
```bash
docker compose logs -f
```

To stop all containers:
```bash
docker compose down
```

---

### Option B: Run Locally via Maven

#### 1. Start Infrastructure (MySQL & Kafka) via Docker Compose
```bash
docker compose up -d patient-service-db kafka
```

#### 2. Build All Services from Root
```bash
./mvnw clean compile
```

#### 3. Run Services
Open two terminal windows:

* **Terminal 1: Patient Service**
  ```bash
  cd patient-service
  ./mvnw spring-boot:run
  ```
  * Base URL: `http://localhost:4000`
  * Swagger UI: `http://localhost:4000/swagger-ui/index.html`

* **Terminal 2: Billing Service**
  ```bash
  cd billing-service
  ./mvnw spring-boot:run
  ```
  * gRPC Server Port: `9001`
  * HTTP Web Port: `4001`
  * gRPC Server Reflection: Enabled

---

## 🧪 Testing Inter-Service & gRPC Endpoints

### Testing `billing-service` via `grpcurl`:
```bash
grpcurl -plaintext -d '{
  "patientId": "123456",
  "name": "John Doe",
  "email": "john.doe@example.com"
}' localhost:9001 billing.BillingService/CreateBillingAccount
```

### Testing in Postman:
1. Open Postman ➔ Select **New** ➔ **gRPC Request**.
2. Enter URL: `grpc://localhost:9001`.
3. Select **Use Server Reflection** ➔ Choose `billing.BillingService/CreateBillingAccount`.
4. Send your JSON payload in the **Message** tab.

---

## 📚 Project Documentation Hub

Detailed documentation is organized in the [`docs/`](./docs) directory:

* 📐 [**Architecture & Protocols**](./docs/ARCHITECTURE.md) - Deep dive into multi-module layout, sync vs async communication, schema management, and architectural diagrams.
* 🔌 [**Service & API Specifications**](./docs/SERVICES.md) - Complete endpoints, Protobuf contracts (`billing.proto`, `patient_event.proto`), and request/response models.
* 🛠 [**Environment & IDE Setup**](./docs/SETUP_AND_RUN.md) - Run configurations, troubleshooting, Kafka/MySQL setup, and IntelliJ IDEA configuration.
* 📝 [**Development Changelog & Roadmap**](./docs/CHANGELOG.md) - Living log of ongoing development, fixes, and planned milestones (ADR 001 through ADR 009).

---

## 🛠 Tech Stack Details

* **Language**: Java 17
* **Framework**: Spring Boot 3.4.0
* **API Documentation**: SpringDoc OpenAPI 3 (Swagger 2.7.0)
* **RPC & Serialization**: Google Protocol Buffers 4.x, gRPC 1.69.0, `net.devh:grpc-spring-boot-starter` / `grpc-client-spring-boot-starter` 3.1.0.RELEASE
* **Event Streaming**: Apache Kafka (`apache/kafka:latest`) in KRaft mode, Spring Kafka 3.3.0
* **Persistence & ORM**: Spring Data JPA, Hibernate 6.6
* **Database**: MySQL 8 (Dockerized) / H2 in-memory support for testing
* **Containerization**: Docker multi-stage builds (Eclipse Temurin JRE 17 runtime) & Docker Compose
