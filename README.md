# Concurrency Control API - Inventory Management System

A production-ready REST API demonstrating **pessimistic** and **optimistic locking** strategies for handling concurrent order requests in an inventory management system. Built with Spring Boot, PostgreSQL, and Docker.

## 🎯 Features

- **Dual Locking Strategies**: Compare pessimistic and optimistic locking side-by-side
- **Row-Level Database Locks**: Uses PostgreSQL `SELECT ... FOR UPDATE` for pessimistic locking
- **Version-Based Optimistic Locking**: JPA `@Version` annotation with automatic conflict detection
- **Automatic Retry Logic**: Exponential backoff for optimistic locking conflicts (3 attempts)
- **Concurrent Testing Scripts**: Bash scripts to simulate high-contention scenarios
- **Database Lock Monitoring**: Real-time lock inspection tool
- **Fully Dockerized**: Docker Compose orchestration with health checks
- **Data Integrity**: Database-level constraints prevent overselling
- **Comprehensive Audit Trail**: All order attempts logged with status

## 📋 Prerequisites

- **Docker** (v20.10+) and **Docker Compose** (v2.0+)
- **Git Bash** or **WSL** (for running test scripts on Windows)
- **curl** (for API testing)
- **jq** (optional, for pretty JSON formatting)

## 🚀 Quick Start

### 1. Clone the Repository

```bash
git clone <repository-url>
cd concurrency-control-api
```

### 2. Configure Environment Variables

Create a `.env` file from the example:

```bash
cp .env.example .env
```

**`.env.example` contents:**

```env
# Port for the application
API_PORT=8080

# Database connection string
DATABASE_URL=jdbc:postgresql://db:5432/inventory_db

# Database credentials
POSTGRES_USER=user
POSTGRES_PASSWORD=password
POSTGRES_DB=inventory_db
```

### 3. Start the Application

```bash
docker-compose up --build
```

Wait for both services to become healthy (usually 30-60 seconds):

- ✅ `concurrency-control-api-db-1` - Database healthy
- ✅ `concurrency-control-api-app-1` - Application healthy

The API will be available at `http://localhost:8080`

### 4. Verify the Application

```bash
# Check application health
curl http://localhost:8080/actuator/health

# Get initial product state
curl http://localhost:8080/api/products/1
```

Expected response:

```json
{
  "id": 1,
  "name": "Super Widget",
  "stock": 100,
  "version": 1
}
```

## 📚 API Documentation

### Products Endpoints

#### Get Product by ID

```http
GET /api/products/{id}
```

**Success Response (200 OK):**

```json
{
  "id": 1,
  "name": "Super Widget",
  "stock": 100,
  "version": 1
}
```

**Error Response (404 Not Found):**

```json
{
  "error": "Product not found"
}
```

#### Reset Product Inventory

```http
POST /api/products/reset
```

Resets all products to their initial seed values.

**Response (200 OK):**

```json
{
  "message": "Product inventory reset successfully."
}
```

### Orders Endpoints

#### Place Order - Pessimistic Locking

```http
POST /api/orders/pessimistic
Content-Type: application/json
```

**Request Body:**

```json
{
  "productId": 1,
  "quantity": 10,
  "userId": "user123"
}
```

**Success Response (201 Created):**

```json
{
  "orderId": 123,
  "productId": 1,
  "quantityOrdered": 10,
  "stockRemaining": 90,
  "newVersion": null
}
```

**Error Responses:**

- **400 Bad Request** (Insufficient stock):

```json
{
  "error": "Insufficient stock"
}
```

- **404 Not Found** (Product doesn't exist):

```json
{
  "error": "Product not found"
}
```

#### Place Order - Optimistic Locking

```http
POST /api/orders/optimistic
Content-Type: application/json
```

**Request Body:**

```json
{
  "productId": 1,
  "quantity": 10,
  "userId": "user123"
}
```

**Success Response (201 Created):**

```json
{
  "orderId": 124,
  "productId": 1,
  "quantityOrdered": 10,
  "stockRemaining": 80,
  "newVersion": 3
}
```

**Error Responses:**

- **400 Bad Request** (Insufficient stock)
- **404 Not Found** (Product doesn't exist)
- **409 Conflict** (Failed after all retries):

```json
{
  "error": "Failed to place order due to concurrent modification. Please try again."
}
```

#### Get Order Statistics

```http
GET /api/orders/stats
```

**Response (200 OK):**

```json
{
  "totalOrders": 50,
  "successfulOrders": 10,
  "failedOutOfStock": 35,
  "failedConflict": 5
}
```

## 🧪 Running Concurrent Tests

### Test Pessimistic Locking

```bash
./concurrent-test.sh pessimistic
```

This script:

1. Resets inventory to initial state
2. Fires 20 concurrent requests (10 units each)
3. Displays order statistics

**Expected Result:**

- Initial stock: 100 units
- 10 successful orders (100 ÷ 10)
- 10 failed orders (out of stock)
- 0 conflicts (pessimistic locking prevents conflicts)

### Test Optimistic Locking

```bash
./concurrent-test.sh optimistic
```

**Expected Result:**

- Initial stock: 100 units
- ~10 successful orders
- ~10 failed orders (out of stock)
- 0-5 failed conflicts (automatically retried internally)

### Monitor Database Locks

In a separate terminal:

```bash
./monitor-locks.sh
```

This continuously displays active PostgreSQL locks. Useful for observing pessimistic locks in real-time.

## 🔄 Application Flow

### Pessimistic Locking Flow

```
1. Client → POST /api/orders/pessimistic
2. Application starts transaction
3. Execute: SELECT * FROM products WHERE id = ? FOR UPDATE
   ├─ Acquires row-level WRITE lock
   └─ Other transactions wait here
4. Check: stock >= quantity?
   ├─ YES → Proceed to step 5
   └─ NO → Rollback, return 400 Bad Request
5. Update: stock = stock - quantity
6. Insert order record (status: SUCCESS)
7. Commit transaction (releases lock)
8. Return 201 Created
```

**Advantages:**

- Simple implementation
- Guaranteed no conflicts
- Predictable behavior

**Disadvantages:**

- Reduced concurrency (transactions wait)
- Potential for deadlocks
- Lock contention under high load

### Optimistic Locking Flow

```
1. Client → POST /api/orders/optimistic
2. Application starts transaction (attempt 1/3)
3. Read: SELECT * FROM products WHERE id = ?
   └─ No lock, reads current version
4. Check: stock >= quantity?
   ├─ YES → Proceed to step 5
   └─ NO → Rollback, return 400 Bad Request
5. Update: UPDATE products SET stock = ?, version = version + 1
           WHERE id = ? AND version = ?
   ├─ Success (1 row affected) → Proceed to step 6
   └─ Failure (0 rows affected) → Version conflict!
       ├─ Retry with exponential backoff (50ms, 100ms, 200ms)
       └─ After 3 attempts → Return 409 Conflict
6. Insert order record (status: SUCCESS)
7. Commit transaction
8. Return 201 Created with new version
```

**Advantages:**

- Higher concurrency (no locks)
- Better throughput
- Scalable under load

**Disadvantages:**

- More complex implementation
- Requires retry logic
- Increased load under high contention

## 🏗️ Architecture

```
flowchart LR
    subgraph DockerCompose [Docker Compose]
        App["<b>Spring Boot App</b><br>(Port 8080)<br><br>• Controllers<br>• Services<br>• Repositories<br>• DTOs<br>• Entities"]

        DB["<b>PostgreSQL 15</b><br>(Port 5432)<br><br>• products<br>• orders<br><br><i>Constraints:</i><br>• stock >= 0"]

        App <--> DB
    end

    style App text-align:left, fill:#f9f9f9, stroke:#333, stroke-width:2px
    style DB text-align:left, fill:#f9f9f9, stroke:#333, stroke-width:2px
    style DockerCompose fill:#ffffff, stroke:#666, stroke-width:2px, stroke-dasharray: 5 5
```

### Project Structure

```
concurrency-control-api/
├── src/
│   ├── main/
│   │   ├── java/com/gpp/concurrency/
│   │   │   ├── controller/
│   │   │   │   ├── OrdersController.java       # Order endpoints
│   │   │   │   └── ProductsController.java     # Product endpoints
│   │   │   ├── service/
│   │   │   │   ├── InventoryService.java       # Core locking logic
│   │   │   │   └── OrderAuditService.java      # Order audit trail
│   │   │   ├── repository/
│   │   │   │   ├── ProductRepository.java      # Pessimistic lock query
│   │   │   │   └── OrderRepository.java
│   │   │   ├── entity/
│   │   │   │   ├── Product.java                # @Version annotation
│   │   │   │   └── Order.java
│   │   │   ├── dto/
│   │   │   │   ├── OrderRequest.java
│   │   │   │   ├── OrderResponse.java
│   │   │   │   └── OrderStatsResponse.java
│   │   │   └── exception/
│   │   │       ├── GlobalExceptionHandler.java
│   │   │       ├── InsufficientStockException.java
│   │   │       └── ProductNotFoundException.java
│   │   └── resources/
│   │       └── application.properties
│   └── test/
│       └── java/com/gpp/concurrency/
│           └── ConcurrencyControlApiApplicationTests.java
├── seeds/
│   └── init.sql                                 # Database initialization
├── docker-compose.yml                           # Service orchestration
├── Dockerfile                                   # Multi-stage build
├── concurrent-test.sh                           # Concurrency test script
├── monitor-locks.sh                             # Lock monitoring script
├── .env.example                                 # Environment template
├── pom.xml                                      # Maven dependencies
└── README.md
```

## 🔍 Database Schema

### Products Table

```sql
CREATE TABLE products (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    stock INTEGER NOT NULL CHECK (stock >= 0),  -- Prevents overselling
    version INTEGER NOT NULL DEFAULT 1          -- For optimistic locking
);
```

**Initial Data:**

- Product 1: "Super Widget" (100 units)
- Product 2: "Mega Gadget" (50 units)

### Orders Table

```sql
CREATE TABLE orders (
    id SERIAL PRIMARY KEY,
    product_id INTEGER REFERENCES products(id),
    quantity_ordered INTEGER NOT NULL,
    user_id VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL,  -- SUCCESS, FAILED_OUT_OF_STOCK, FAILED_CONFLICT
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
```

## 📊 Interpreting Test Results

### Example Test Output

```json
{
  "totalOrders": 20,
  "successfulOrders": 10,
  "failedOutOfStock": 10,
  "failedConflict": 0
}
```

### Product State After Test

```json
{
  "id": 1,
  "name": "Super Widget",
  "stock": 0,
  "version": 55
}
```

### Analysis

- **totalOrders (20)**: All concurrent requests were processed ✅
- **successfulOrders (10)**: Exactly 10 orders succeeded (100 initial stock ÷ 10 units per request) ✅
- **failedOutOfStock (10)**: Remaining 10 requests correctly rejected ✅
- **failedConflict (0)**: Optimistic locking retries handled conflicts internally ✅
- **stock (0)**: Perfect inventory tracking - no overselling ✅
- **version (55)**: High version indicates many internal retries during conflict resolution

The high version number is **expected and correct** - it shows that optimistic locking detected conflicts and successfully retried transactions multiple times before committing.

## 🛠️ Development

### Build Locally

```bash
# Build with Maven
./mvnw clean package -DskipTests

# Build Docker image
docker build -t concurrency-control-api .
```

### Run Tests

```bash
./mvnw test
```

### Connect to Database

```bash
docker exec -it concurrency-control-api-db-1 psql -U user -d inventory_db
```

**Useful SQL queries:**

```sql
-- View all products
SELECT * FROM products;

-- View all orders
SELECT * FROM orders ORDER BY created_at DESC;

-- Check stock levels
SELECT name, stock, version FROM products;

-- Count orders by status
SELECT status, COUNT(*) FROM orders GROUP BY status;
```

## 🐛 Troubleshooting

### Application won't start

```bash
# Check logs
docker-compose logs app

# Ensure database is healthy
docker-compose ps
```

### Port already in use

Edit `.env` file and change `API_PORT`:

```env
API_PORT=8081
```

### Health check failing

```bash
# Check if actuator is accessible
curl http://localhost:8080/actuator/health

# Verify dependencies in pom.xml includes spring-boot-starter-actuator
```

### Scripts not running on Windows

Use **Git Bash** or **WSL**:

```bash
# Make scripts executable
chmod +x concurrent-test.sh monitor-locks.sh

# Run in Git Bash
./concurrent-test.sh optimistic
```

## 📝 Configuration

### Environment Variables

| Variable            | Default                                  | Description            |
| ------------------- | ---------------------------------------- | ---------------------- |
| `API_PORT`          | `8080`                                   | Application port       |
| `DATABASE_URL`      | `jdbc:postgresql://db:5432/inventory_db` | JDBC connection string |
| `POSTGRES_USER`     | `user`                                   | Database username      |
| `POSTGRES_PASSWORD` | `password`                               | Database password      |
| `POSTGRES_DB`       | `inventory_db`                           | Database name          |

### Application Properties

Key configurations in `application.properties`:

```properties
# Server
server.port=${SERVER_PORT:8080}

# Database
spring.datasource.url=${SPRING_DATASOURCE_URL}
spring.jpa.hibernate.ddl-auto=none  # Schema managed by init.sql

# JPA
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true
```

## 🎓 Learning Outcomes

This project demonstrates:

1. **Concurrency Control**: Practical implementation of pessimistic vs optimistic locking
2. **Transaction Management**: ACID properties in distributed systems
3. **Race Condition Prevention**: Techniques to maintain data integrity
4. **Database Constraints**: Defense-in-depth with CHECK constraints
5. **Retry Strategies**: Exponential backoff for conflict resolution
6. **Container Orchestration**: Docker Compose with health checks
7. **API Design**: RESTful principles and proper HTTP status codes
8. **Error Handling**: Graceful degradation and meaningful error messages
9. **Testing**: Simulating high-contention scenarios
10. **Production Readiness**: Logging, monitoring, and observability

## 👨‍💻 Author

Created as part of the GPP Task 13 - Inventory Management with Concurrency Control

---

**Happy Testing! 🚀**
