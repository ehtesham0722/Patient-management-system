# Service & API Specifications

This document catalogs all microservices in the system, their public endpoints, internal contracts, data models, and integration patterns.

---

## 1. `api-gateway`

* **Port**: `4004`
* **Technology**: Spring Cloud Gateway Server WebFlux (Spring Boot 4.1.2 / Spring Cloud 2025.1.4)
* **Configuration File**: `api-gateway/src/main/resources/application.yml`
* **Property Prefix**: `spring.cloud.gateway.server.webflux.routes`

### Route Table

| Route ID | Incoming Predicate | Filter | Upstream Target | Purpose |
| :--- | :--- | :--- | :--- | :--- |
| `patient-service-route` | `Path=/api/patients, /api/patients/**` | `StripPrefix=1` | `http://patient-service:4000` | Routes patient management requests. Forwarded as `/patients` and `/patients/{id}`. |
| `api-docs-patient-routes` | `Path=/api-docs/patients` | `RewritePath=/api-docs/patients, /v3/api-docs` | `http://patient-service:4000` | Exposes patient service OpenAPI v3 JSON spec through the gateway. |

### Environment Variables
| Variable | Default Value | Description |
| :--- | :--- | :--- |
| `PATIENT_SERVICE_URL` | `http://patient-service:4000` | Target URL for patient-service container/host |

---

## 2. `auth-service`

* **Port**: `4005`
* **Technology**: Spring Boot 4.1.2, Spring Security, JJWT (Java JWT 0.12.6), Spring Data JPA, MySQL
* **Base URL**: `http://localhost:4005`

### Endpoints

#### 2.1 User Login
* **Method**: `POST`
* **Endpoint**: `/login`
* **Content-Type**: `application/json`
* **Request Body**:
  ```json
  {
    "email": "testuser@test.com",
    "password": "password"
  }
  ```
* **Response `200 OK`**:
  ```json
  {
    "token": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ0ZXN0dXNlckB0ZXN0LmNvbSIsInJvbGUiOiJBRE1JTiIsImlhdCI6MTczMTgzMTYwMCwiZXhwIjoxNzMxODY3NjAwfQ..."
  }
  ```
* **Response `401 Unauthorized`**: If user not found or password does not match BCrypt hash.

### Pre-Seeded Users (`data.sql`)
| Email | Password | Role | User ID |
| :--- | :--- | :--- | :--- |
| `testuser@test.com` | `password` *(BCrypt: `$2b$12$7hoRZfJrRKD...`)* | `ADMIN` | `223e4567-e89b-12d3-a456-426614174006` |

### Environment Variables
| Variable | Default Value | Description |
| :--- | :--- | :--- |
| `SPRING_DATASOURCE_URL` | `jdbc:mysql://auth-service-db:3306/db` | MySQL JDBC URL for authentication database |
| `SPRING_DATASOURCE_USERNAME` | `admin_user` | Database user |
| `SPRING_DATASOURCE_PASSWORD` | `password` | Database password |
| `JWT_SECRET` | `dGhpc2lzYXZlcnlz...` | HMAC-SHA256 signing secret (Base64 or raw string >= 256 bits) |

---

## 3. `patient-service`

* **Port**: `4000`
* **Base URL**: `http://localhost:4000`
* **Swagger UI**: `http://localhost:4000/swagger-ui/index.html`
* **OpenAPI Specs**: `http://localhost:4000/v3/api-docs`

### REST API Endpoints

#### 3.1 Get All Patients
* **Method**: `GET`
* **Endpoint**: `/patients`
* **Via Gateway**: `GET http://localhost:4004/api/patients`
* **Response `200 OK`**:
  ```json
  [
    {
      "patientId": "123e4567-e89b-12d3-a456-426614174000",
      "name": "John Doe",
      "email": "john.doe@example.com",
      "address": "123 Main St, Springfield",
      "dateOfBirth": "1985-06-15"
    }
  ]
  ```

#### 3.2 Create Patient
* **Method**: `POST`
* **Endpoint**: `/patients`
* **Via Gateway**: `POST http://localhost:4004/api/patients`
* **Request Body**:
  ```json
  {
    "name": "Jane Doe",
    "email": "jane.doe@example.com",
    "address": "456 Elm St, Shelbyville",
    "dateOfBirth": "1992-04-18"
  }
  ```
* **Validation Rules**:
  * `name`: Not Null, Not Blank, max length 100
  * `email`: Not Null, Valid Email format, Unique in database
  * `address`: Not Null, Not Blank
  * `dateOfBirth`: Not Null, Past Date
* **Response `200 OK`**: Returns saved `PatientResponseDTO`.
* **Response `400 Bad Request`**: Validation error payload.
* **Response `409 Conflict`**: `EmailAlreadyExistsException` if email is already registered.

#### 3.3 Update Patient Details
* **Method**: `PUT`
* **Endpoint**: `/patients/{id}`
* **Via Gateway**: `PUT http://localhost:4004/api/patients/{id}`
* **Path Parameter**: `id` (UUID format)

#### 3.4 Delete Patient
* **Method**: `DELETE`
* **Endpoint**: `/patients/{id}`
* **Via Gateway**: `DELETE http://localhost:4004/api/patients/{id}`
* **Response `204 No Content`**

---

## 4. `billing-service`

* **gRPC Port**: `9001`
* **HTTP Port**: `4001`
* **Protocol**: Protocol Buffers 3 over gRPC (HTTP/2)
* **Contract File**: `billing-service/src/main/proto/billing.proto`
* **Service Name**: `billing.BillingService`

### Protobuf Definition (`billing.proto`)
```protobuf
syntax = "proto3";

option java_multiple_files = true;
option java_package = "billing";

service BillingService {
  rpc CreateBillingAccount (BillingRequest) returns (BillingResponse);
}

message BillingRequest {
  string patientId = 1;
  string name = 2;
  string email = 3;
}

message BillingResponse {
  string accountId = 1;
  string status = 2;
  string message = 3;
}
```

---

## 5. `analytic-service`

* **Port**: `4002`
* **Technology**: Spring Boot 3.4.0, Spring Kafka Consumer, Protocol Buffers
* **Contract File**: `analytic-service/src/main/proto/patient_event.proto`
* **Consumer Topic**: `patient-events`
* **Consumer Group**: `analytics-group`

### Protobuf Definition (`patient_event.proto`)
```protobuf
syntax = "proto3";

package patient.events;

option java_multiple_files = true;
option java_package = "com.pm.patientservice.kafka";

message PatientEvent {
  string event_id = 1;
  string event_type = 2;    // CREATED, UPDATED, DELETED
  string patient_id = 3;
  string name = 4;
  string email = 5;
  int64 timestamp = 6;
}
```
