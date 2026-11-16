# Local Development & Environment Setup Guide

This guide covers setting up your development environment, running all microservices via Docker Compose or locally via Maven, and troubleshooting common issues.

---

## 1. Environment Requirements

* **Operating System**: Windows / macOS / Linux
* **Java Development Kit (JDK)**: JDK 17 (recommended: Eclipse Temurin 17)
* **Docker**: Docker Desktop with Linux container support enabled
* **Build Tool**: Apache Maven 3.9+ (bundled Maven Wrapper `./mvnw` provided)

---

## 2. Running via Docker Compose (Recommended)

From the repository root:
```bash
# Build and start all 5 microservices and 3 database/streaming containers
docker compose up --build -d

# View real-time logs across all services
docker compose logs -f

# Stop and remove all containers and networks
docker compose down
```

### Services & Port Mappings:
| Container Name | Internal Port | Host Port | Purpose |
| :--- | :--- | :--- | :--- |
| `api-gateway` | `4004` | `4004` | API Gateway entry point & route aggregator |
| `auth-service` | `4005` | `4005` | Authentication & JWT token issuer |
| `patient-service` | `4000` | `4000` | Patient CRUD REST API & gRPC client |
| `billing-service` | `4001`, `9001` | `4001`, `9001` | Billing gRPC server & web endpoint |
| `analytic-service` | `4002` | `4002` | Analytics Kafka consumer |
| `kafka` | `9092` | `9092`, `9094` | Apache Kafka (KRaft mode) |
| `patient-service-db` | `3306` | `3307` | MySQL 8 instance for patient data |
| `auth-service-db` | `3306` | `3308` | MySQL 8 instance for user auth data |

---

## 3. Running Services Locally via Maven

### 3.1 Start Infrastructure Containers (MySQL & Kafka)
```bash
docker compose up -d patient-service-db auth-service-db kafka
```

### 3.2 Database Connection Summary
| Database | Host | Host Port | Container Port | User | Password | Database Name |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Patient DB** | `localhost` | `3307` | `3306` | `admin_user` | `password` | `db` |
| **Auth DB** | `localhost` | `3308` | `3306` | `admin_user` | `password` | `db` |

### 3.3 Build All Modules from Root
```bash
./mvnw clean compile
```

### 3.4 Launch Each Microservice
Run each command in a separate terminal:

* **1. API Gateway (Port 4004)**:
  ```bash
  cd api-gateway
  ./mvnw spring-boot:run
  ```

* **2. Auth Service (Port 4005)**:
  ```bash
  cd auth-service
  ./mvnw spring-boot:run
  ```

* **3. Patient Service (Port 4000)**:
  ```bash
  cd patient-service
  ./mvnw spring-boot:run
  ```

* **4. Billing Service (Ports 4001 / 9001)**:
  ```bash
  cd billing-service
  ./mvnw spring-boot:run
  ```

* **5. Analytic Service (Port 4002)**:
  ```bash
  cd analytic-service
  ./mvnw spring-boot:run
  ```

---

## 4. Troubleshooting & Common Pitfalls

### 4.1 API Gateway Returning `404 Not Found`
* **Cause 1 - Missing Route Prefix in YAML**: In Spring Cloud Gateway 5.x (`spring-cloud-starter-gateway-server-webflux`), routes must be nested under `spring.cloud.gateway.server.webflux.routes` (not just `spring.cloud.gateway.routes`).
* **Cause 2 - Exact Path Matching**: In Spring WebFlux, `Path=/api/patients/**` does not match `/api/patients` without trailing slash. Predicates should declare both:
  ```yaml
  predicates:
    - Path=/api/patients, /api/patients/**
  ```
* **Cause 3 - Double Slash in URL**: `http://localhost:4004//api/patients` fails path matching. Always use single slash: `http://localhost:4004/api/patients`.

### 4.2 Auth Service: `Failed to load driver class com.mysql.cj.jdbc.Driver`
* **Cause**: `auth-service/pom.xml` was generated with PostgreSQL driver rather than MySQL.
* **Fix**: Replace `org.postgresql:postgresql` with:
  ```xml
  <dependency>
      <groupId>com.mysql</groupId>
      <artifactId>mysql-connector-j</artifactId>
      <scope>runtime</scope>
  </dependency>
  ```

### 4.3 Auth Service: `Illegal base64 character 24` in `JwtUtil`
* **Cause**: `@Value("${{jwt.secret}")` contained a double opening brace `${{`, causing Spring to inject the raw string `"${{jwt.secret}"`. The first character `$` (ASCII hex `0x24`) failed Base64 decoding.
* **Fix**: Use `@Value("${jwt.secret:${JWT_SECRET:...}}")` and catch `IllegalArgumentException` in `JwtUtil` to support both Base64 and plain text secrets.

### 4.4 MySQL `data.sql` Syntax Errors
* **Cause**: PostgreSQL syntax (`id UUID PRIMARY KEY`, `"users"`) is invalid in MySQL.
* **Fix**: Use `VARCHAR(36) PRIMARY KEY` and standard unquoted table names (`users`).

### 4.5 Kafka Image 404 (`bitnami/kafka:latest`)
* **Cause**: Bitnami deprecated/restricted public latest tags on Docker Hub.
* **Fix**: Use the official Apache image: `apache/kafka:latest` in KRaft mode.
