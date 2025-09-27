# 🚀 Auth Service – Rideswift

Authentication and authorization microservice for the **Rideswift** application.  
Provides user registration, login, token refresh, and logout using **JWT (RSA)**, **Postgres**, **Redis**, and **Kafka**.

---

## 📌 Features
- 🔑 User Registration & Login (with password hashing & JWT authentication)
- 🔄 Secure Token Refresh using Refresh Tokens
- 🚪 Logout (revokes refresh + access token tracking with JTI)
- 🐘 PostgreSQL as the database
- ⚡ Redis for caching/session storage
- 📡 Kafka integration for publishing user-created events

---

## 🛠️ Tech Stack
- **Java 21 + Spring Boot**
- **PostgreSQL 14.5**
- **Redis 6**
- **Kafka (Confluent 7.0.1) + Zookeeper**
- **Docker & Docker Compose**

---

## ⚙️ Prerequisites
Before running the project, ensure you have installed:
- [Docker](https://docs.docker.com/get-docker/)
- [Docker Compose](https://docs.docker.com/compose/)

---

## ▶️ Getting Started

### 1. Clone the Repository
```bash
git clone https://github.com/your-org/rideswift-auth-service.git
cd rideswift-auth-service
```

2. Build the Service JAR

Make sure your auth-service builds successfully:

cd Services/auth-service
mvn clean package -DskipTests


This creates target/app.jar.

3. Start All Services with Docker Compose

Below docker-compose.yml is present:

```
version: '3.8'
services:
  # --- Database & Cache ---
  postgres-db:
    image: postgres:14.5
    container_name: postgres-db
    environment:
      - POSTGRES_USER=admin
      - POSTGRES_PASSWORD=secret
      - POSTGRES_DB=rideswift
    ports:
      - "5432:5432"
    volumes:
      - postgres-data:/var/lib/postgresql/data

  redis-cache:
    image: redis:6-alpine
    container_name: redis-cache
    ports:
      - "6379:6379"

  # --- Kafka Message Broker (and its dependency Zookeeper) ---
  zookeeper:
    image: confluentinc/cp-zookeeper:7.0.1
    container_name: zookeeper
    environment:
      ZOOKEEPER_CLIENT_PORT: 2181
      ZOOKEEPER_TICK_TIME: 2000

  kafka:
    image: confluentinc/cp-kafka:7.0.1
    container_name: kafka
    ports:
      - "9092:9092" # Expose Kafka to the host machine
    depends_on:
      - zookeeper
    environment:
      KAFKA_BROKER_ID: 1
      KAFKA_ZOOKEEPER_CONNECT: 'zookeeper:2181'
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka:29092,PLAINTEXT_HOST://localhost:9092
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: PLAINTEXT:PLAINTEXT,PLAINTEXT_HOST:PLAINTEXT
      KAFKA_INTER_BROKER_LISTENER_NAME: PLAINTEXT
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1

  auth-service:
    build: path/auth-service # Local path to the service
    container_name: auth-service
    ports:
      - "8081:8080" # Expose service on host port 8081
    depends_on:
      - postgres-db
      - kafka
    environment:
      - SPRING_DATASOURCE_URL=jdbc:postgresql://postgres-db:5432/rideswift
      - SPRING_KAFKA_BOOTSTRAP_SERVERS=kafka:29092 # For service-to-kafka communication

volumes:
  postgres-data:
```
Then 
```
docker-compose up --build
```

Expected services:

```
postgres-db (Postgres)
redis-cache (Redis)
zookeeper
kafka
auth-service
```

📊 Architecture

- AuthController → REST API entry point
- AuthService → business logic (registration, login, refresh, logout)
- JwtService → handles JWT signing & validation (RSA-based)
- Postgres → persistent user & token storage
- Redis → cache (optional for token blacklisting)
- Kafka → event publishing (user created events)


🧹 Common Commands

Stop services:
```
docker-compose down
```

Clean volumes (reset database/cache):
```
docker-compose down -v
```
🔒 Security Notes

- Passwords are hashed with BCrypt
- Tokens signed with RSA256
- Refresh tokens stored hashed in DB
- Access tokens short-lived (15 min), Refresh tokens long-lived (30 days)
- Uses JTI for token tracking and revocation
