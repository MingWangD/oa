# judicial-appraisal-backend

Standalone Spring Boot backend for a judicial appraisal workflow system.

## Requirements
- Java 17
- Maven 3.9+
- MySQL 8+
- Redis (optional for later phases)
- MinIO (optional for later phases)

## Run locally
1. Create a MySQL database named `judicial_appraisal`.
2. Import `src/main/resources/db/schema.sql`.
3. Update `src/main/resources/application.yml`.
4. Start the app:

```bash
mvn spring-boot:run
```

## API docs
- Swagger UI: `/swagger-ui.html`
- OpenAPI JSON: `/v3/api-docs`
