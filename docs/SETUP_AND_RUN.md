# Local Development & Environment Setup Guide

This guide covers setting up your development environment, configuring IntelliJ IDEA, running services in Docker Compose, and troubleshooting common issues.

---

## 1. Environment Requirements

* **Operating System**: Windows / macOS / Linux
* **Java Development Kit (JDK)**: JDK 17 (recommended: Eclipse Temurin or Oracle JDK 17)
* **Docker**: Docker Desktop with Linux containers enabled
* **Build Tool**: Apache Maven 3.9+ (bundled Maven Wrapper `./mvnw` provided in repository root and each sub-module)

---

## 2. Running via Docker Compose (Recommended)

From the repository root:
```bash
# Build and start MySQL, patient-service, and billing-service
docker compose up --build -d

# View real-time logs
docker compose logs -f

# Stop all services
docker compose down
```

### Services Started:
| Container Name | Internal Port | Host Port | Purpose |
| :--- | :--- | :--- | :--- |
| `patient-service-db` | `3306` | `3307` | MySQL 8.x database |
| `kafka` | `9092` (internal), `9094` (host) | `9092`, `9094` | Apache Kafka (`apache/kafka:latest`) message broker (KRaft mode) |
| `patient-service` | `4000` | `4000` | Patient REST API & Swagger UI |
| `billing-service` | `4001`, `9001` | `4001`, `9001` | Billing gRPC Server & Web endpoint |

---

## 3. Running Services Locally via Maven

### 3.1 Start MySQL via Docker
```bash
docker run -d \
  --name patient-service-db \
  --network internal \
  -p 3307:3306 \
  -e MYSQL_DATABASE=db \
  -e MYSQL_ROOT_PASSWORD=password \
  -e MYSQL_USER=admin_user \
  -e MYSQL_PASSWORD=password \
  mysql:latest
```

### 3.2 Database Connection Details
| Parameter | Value |
| :--- | :--- |
| **Driver** | MySQL |
| **Host** | `localhost` |
| **Port** | `3307` |
| **User** | `admin_user` *(or `root`)* |
| **Password** | `password` |
| **Database** | `db` |
| **JDBC URL** | `jdbc:mysql://localhost:3307/db` |

### 3.3 Compile All Modules
From the repository root:
```bash
./mvnw clean compile
```

### 3.4 Launch Each Service
Open two separate terminal windows:

**Terminal 1: Patient Service**
```bash
cd patient-service
./mvnw spring-boot:run
```
*(Listens on HTTP `http://localhost:4000`)*

**Terminal 2: Billing Service**
```bash
cd billing-service
./mvnw spring-boot:run
```
*(Listens on HTTP `4001` and gRPC `localhost:9001`)*

---

## 4. IntelliJ IDEA Setup & Run Configurations

### 4.1 Importing the Project
1. Open IntelliJ IDEA -> **File** -> **Open...**
2. Select the repository root folder (`Patient-management-system`).
3. Click the **Maven** tool window on the right -> Click **Reload All Maven Projects** (blue circular sync icon).
4. Verify that:
   * The root `patient-management-system` aggregator POM is loaded.
   * Both `patient-service` and `billing-service` modules are recognized as child modules.
   * In `billing-service`, `target/generated-sources/protobuf/java` and `grpc-java` are marked as **Generated Sources Root** (blue/brown folder icon).

### 4.2 Pre-Configured Run Configurations
Located in `.idea/workspace.xml`:
* **`PatientServiceApplication`** (Spring Boot): Runs `patient-service` natively.
* **`BillingServiceApplication`** (Spring Boot): Runs `billing-service` natively.
* **`patient-service`** (Docker Deployment): Builds and runs `patient-service` container.
* **`billing-service`** (Docker Deployment): Builds and runs `billing-service` container.
* **`Patient-service-DB`** (Docker Deployment): Runs the MySQL container.
* **HTTP Requests**: Located in `api-calls/patient-service/` for single-click execution in IntelliJ.

---

## 5. Troubleshooting Common Issues

### Issue 1: `release version 21 not supported`
* **Cause**: Trying to compile with `<java.version>21</java.version>` when JDK 17 is active.
* **Fix**: Ensure `<java.version>17</java.version>` is set across all POM files and IntelliJ project SDK is set to 17.

### Issue 2: `AbstractMethodError: Receiver class io.grpc.netty.shaded...`
* **Cause**: gRPC version mismatch between Spring Boot's dependency management and explicitly imported `grpc-netty-shaded`.
* **Fix**: Ensure `billing-service/pom.xml` uses `spring-boot-starter-parent` `3.4.0` (Spring Boot 3.x), which harmonizes with `net.devh:grpc-spring-boot-starter:3.1.0.RELEASE`.

### Issue 3: `failed to calculate checksum of ref ... "/pom.xml": not found`
* **Cause**: Dockerfile context folder was set to a subfolder (`src/main`) instead of the service root.
* **Fix**: Set context folder to `patient-service` (or `billing-service`) where `pom.xml` and `Dockerfile` reside.

### Issue 4: Docker Runner Stage Fails to Copy JAR
* **Cause**: Case sensitivity mismatch between Linux Docker filesystem and Maven artifact name (e.g., `Billing-service` vs `billing-service`).
* **Fix**: Standardized POM `<artifactId>billing-service</artifactId>` and Dockerfile `COPY --from=builder /app/target/billing-service-0.0.1-SNAPSHOT.jar ./app.jar`.

### Issue 5: Protobuf Sources Not Generated During IntelliJ IDE Build
* **Cause**: IntelliJ IDEA's native compiler (Javac / Build Project) does not execute Maven plugins by default.
* **Fix**: 
  1. In the Maven sidebar, expand `patient-service` ➔ `Plugins` ➔ `protobuf`.
  2. Right-click `protobuf:compile` ➔ check **Execute Before Build**.
  3. Right-click `protobuf:compile-custom` ➔ check **Execute Before Build**.
  4. Or trigger manual generation via `patient-service` ➔ `Lifecycle` ➔ `compile`.
