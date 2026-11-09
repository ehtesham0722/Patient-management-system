# Service & API Specifications

This document catalogs the microservices, their interfaces, data contracts, and request/response specifications.

---

## 1. `patient-service`

* **Port**: `4000` (configurable via `server.port`)
* **Base URL**: `http://localhost:4000`
* **Swagger UI**: `http://localhost:4000/swagger-ui/index.html`
* **OpenAPI Specs**: `http://localhost:4000/v3/api-docs`

### REST API Endpoints

#### 1.1 Get All Patients
* **Method**: `GET`
* **Endpoint**: `/patients`
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

#### 1.2 Create Patient
* **Method**: `POST`
* **Endpoint**: `/patients`
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
  * `name`: Not Null, Not Blank
  * `email`: Not Null, Valid Email format, Unique in database
  * `address`: Not Null, Not Blank
  * `dateOfBirth`: Not Null, Past Date
* **Response `200 OK` / `201 Created`**: Returns the saved `PatientResponseDTO` with generated UUID.
* **Response `400 Bad Request`**: Validation error payload.
* **Response `409 Conflict`**: `EmailAlreadyExistsException` if email already registered.

#### 1.3 Update Patient Details
* **Method**: `PUT`
* **Endpoint**: `/patients/{id}`
* **Path Parameter**: `id` (UUID format)
* **Request Body**: Updated fields.

#### 1.4 Delete Patient
* **Method**: `DELETE`
* **Endpoint**: `/patients/{id}`
* **Response `204 No Content` / `200 OK`**

---

### Client Integrations in `patient-service`

#### A. gRPC Client: `BillingServiceGrpcClient`
* **Class**: `com.pm.patientservice.grpc.BillingServiceGrpcClient`
* **Stub**: `BillingServiceGrpc.BillingServiceBlockingStub`
* **Target Address**: Configured via `billing.service.address` (default `localhost`) and `billing.service.grpc.port` (default `9001`).
* **Method**:
  ```java
  public BillingResponse createBillingAccount(String patientId, String name, String email)
  ```

#### B. Kafka Producer: `KafkaProducer`
* **Class**: `com.pm.patientservice.kafka.KafkaProducer`
* **Template**: `KafkaTemplate<String, byte[]>`
* **Payload**: Serialized Protocol Buffer message `patient.events.PatientEvent`.

---

## 2. `billing-service`

* **gRPC Port**: `9001`
* **HTTP Port**: `4001`
* **Protocol**: Protocol Buffers 3 over gRPC (HTTP/2)
* **Package**: `billing`
* **Service Name**: `BillingService`
* **Contract File**: `billing-service/src/main/proto/billing.proto`

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
}
```

### Methods Catalog

#### 2.1 `CreateBillingAccount`
* **RPC Type**: Unary RPC
* **Full Method Name**: `billing.BillingService/CreateBillingAccount`
* **Input**: `BillingRequest`
  ```json
  {
    "patientId": "123e4567-e89b-12d3-a456-426614174000",
    "name": "John Doe",
    "email": "john.doe@example.com"
  }
  ```
* **Output**: `BillingResponse`
  ```json
  {
    "accountId": "ba-987654",
    "status": "ACTIVE"
  }
  ```
* **IntelliJ HTTP Client Request**: Located at `grpc-request/create-billing-account.http`.

---

## 3. Event Specifications (Kafka)

### Patient Domain Events Contract (`patient_event.proto`)
* **Contract File**: `patient-service/src/main/proto/patient_event.proto`
* **Package**: `patient.events`
* **Java Package**: `patient.events`

```protobuf
syntax = "proto3";

package patient.events;

option java_multiple_files = true;
option java_package = "patient.events";

message PatientEvent {
  string patientId = 1;
  string name = 2;
  string email = 3;
  string events_type = 4;
}
```

### Topics Catalog

| Topic Name | Producer | Consumer | Key | Value | Purpose |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `patient-events` | `patient-service` | Downstream services | `patientId` | `PatientEvent` (bytes) | Notifies subscribers when a patient is created, updated, or removed. |
