# Supply Chain Blast-Radius Tracker (Backend API)

Spring Boot REST API service powering the Supply Chain Blast-Radius Tracker. Built with Java 21, Spring Boot, and the official Neo4j Java Driver over the Bolt protocol to interact with **CognoDB Cloud**.

- **Live API Endpoint:** [https://supplychain-api-3ntq.onrender.com/api/supply-chain](https://supplychain-api-3ntq.onrender.com/api/supply-chain)
- **Frontend Repository:** [https://github.com/major-NEELKAMAL/supply-chain-frontend](https://github.com/major-NEELKAMAL/supply-chain-frontend)

---

## Core Cypher Queries

### 1. Multi-Hop Impact Query (Parameterized)
Executes variable-length path traversals (2 to 4 hops) to return affected downstream end-products along with the exact hop distance:

MATCH path = (s:Supplier {id: $supplierId})-[:SUPPLIES|USED_IN*1..4]->(p:Product)
RETURN DISTINCT p.id AS productId, p.name AS productName, length(path) AS depth

### 2. Data Seeding Query
Populates CognoDB using MERGE idempotency to prevent duplicate node creation:

MERGE (s1:Supplier {id: 'SUP-50'}) SET s1.name = 'MicroChip Corp', s1.region = 'Taiwan'
MERGE (c1:Component {id: 'CMP-101'}) SET c1.name = 'Microcontroller Unit'
MERGE (c2:Component {id: 'CMP-202'}) SET c2.name = 'Control Board'
MERGE (p1:Product {id: 'PRD-900'}) SET p1.name = 'Medical Monitor'
MERGE (p2:Product {id: 'PRD-901'}) SET p2.name = 'Automotive ECU'

MERGE (s1)-[:SUPPLIES]->(c1)
MERGE (c1)-[:USED_IN]->(c2)
MERGE (c2)-[:USED_IN]->(p1)
MERGE (c1)-[:USED_IN]->(p2)

---

## API Reference

| Method | Endpoint | Description |
| :--- | :--- | :--- |
| POST | /api/supply-chain/seed | Executes Cypher batch statements to seed test graph data in CognoDB. |
| GET | /api/supply-chain/impact/{supplierId} | Performs multi-hop Cypher traversal and returns downstream impact paths. |

---

## Local Development Setup

### Prerequisites
- Java 21 JDK
- Apache Maven 3.8+
- Active CognoDB Cloud instance URI & credentials

### Environment Variables
Ensure the following variables are set before running the application:

export NEO4J_URI=bolt+s://<your-instance-id>.databases.cognodb.cloud
export NEO4J_USER=cognodb
export NEO4J_PASSWORD=<your-cognodb-password>
export JASYPT_PASSWORD=<your-jasypt-encryption-key>

### Run Locally
mvn clean package -DskipTests
java -Djasypt.encryptor.password=${JASYPT_PASSWORD} -jar target/*.jar

Service starts on http://localhost:8081.