# HR Backend (Spring Boot)

## Overview
HR Nexus backend service built with Spring Boot. Provides REST APIs, auth via Keycloak, persistence with PostgreSQL, and eventing via Kafka.

## Recent Features
- Refresh token endpoint: `POST /public/auth/refresh`
- Stronger authorization: ownership/team/HR checks for leave and PDF access
- Typed DTOs and validation for loans and leave decisions
- Kafka-backed notifications with DB fallback
- Full audit history: `request_history` table tracking actions and actors
- Pagination on list endpoints (`?page=0&size=10`)
- Optimistic locking on requests to prevent double-approval races
- Task assignment notifications + email with full task context

## Requirements
- Java 17
- Docker Desktop (recommended for local dependencies)
- Maven (or use the Maven Wrapper)

## Local Setup
1. Start dependencies with Docker Compose:
   ```bash
   docker compose up -d
   ```
2. Configure environment variables (see `.env` for local defaults).
3. Run the application:
   ```bash
   .\mvnw spring-boot:run
   ```

## Environment Variables
These are read from the environment (or via your IDE run config). Key ones:
- `SERVER_PORT` (default `8081`)
- `SPRING_DATASOURCE_URL` (default `jdbc:postgresql://localhost:5433/hr_db`)
- `SPRING_DATASOURCE_USERNAME` (default `postgres`)
- `SPRING_DATASOURCE_PASSWORD`
- `KEYCLOAK_AUTH_SERVER_URL` (default `http://localhost:8080`)
- `KEYCLOAK_REALM` (default `hr-realm`)
- `KEYCLOAK_CLIENT_ID` (default `hr-backend`)
- `KEYCLOAK_CLIENT_SECRET`
- `KEYCLOAK_ADMIN_USERNAME`
- `KEYCLOAK_ADMIN_PASSWORD`
- `SPRING_KAFKA_BOOTSTRAP_SERVERS` (default `localhost:9092`)
- `APP_KAFKA_ENABLED` (default `true`)
- `APP_BASE_URL` (frontend base URL used for QR links)
- `APP_KAFKA_TOPIC_REQUEST_EVENTS` (default `request-events`)
- `APP_KAFKA_TOPIC_NOTIFICATION_EVENTS` (default `notification-events`)

## Ports (Defaults)
- Backend API: `http://localhost:8081`
- Keycloak: `http://localhost:8080`
- PostgreSQL: `localhost:5433`
- Kafka: `localhost:9092`
- PgAdmin: `http://localhost:5050`

## API Docs
Springdoc UI is available at:
- `http://localhost:8081/swagger-ui/index.html`

## Notifications
- Table: `notifications`
- Fields: `id`, `user_id`, `message`, `type`, `reference_type`, `reference_id`, `action_url`, `read`, `created_at`
- API:
  - `GET /api/notifications`
  - `PATCH /api/notifications/{id}/read`
  - `PATCH /api/notifications/read-all`

## Task Assignment
- When a Team Leader assigns a task:
  - Notification is created for the assignee
  - Email is sent with full task details (title, description, project, priority, start/due dates)
- Task details endpoint:
  - `GET /api/employee/tasks/{taskId}`

## Useful Commands
- Run tests:
  ```bash
  .\mvnw test
  ```
- Package jar:
  ```bash
  .\mvnw clean package
  ```
