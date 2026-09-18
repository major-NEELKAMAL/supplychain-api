# Supply Chain Blast-Radius Tracker (Backend API)

Spring Boot REST API service powering the **Supply Chain Blast-Radius Tracker**. Built with Java 21, Spring Boot, and Spring Data Neo4j to interact with **CognoDB Cloud** over the Bolt protocol.

- **Live API Endpoint:** https://api-supply-chain.royawl.com/api/v1/supply-chain/healthcheck
- **Frontend Live Application:** https://ui-supply-chain.royawl.com/
- **Frontend Repository:** https://github.com/major-NEELKAMAL/supply-chain-frontend

---

## Why a Graph Database?

In multi-tiered supply chains, evaluating downstream risk requires answering recursive structural queries: *"If a Supplier or Raw Material node fails, which components, sub-assemblies, and finished products are impacted downstream, and at what exact hop depth?"*

* **Index-Free Adjacency:** Relational databases require complex, multi-table JOIN operations or recursive Common Table Expressions (CTEs) that degrade exponentially as depth increases. CognoDB traverses graph pointers natively in O(1) time per relationship step.
* **Flexible Edge Modeling:** Graph nodes (Supplier, RawMaterial, Component, SubAssembly, Product) and directed relationships (SUPPLIES, YIELDS, ASSEMBLED_INTO, BUILDS) naturally mirror real-world Bill-of-Materials (BOM) networks without forcing relational schema normalization.
* **Declarative Multi-Hop Traversals:** OpenCypher enables clean variable and multi-tiered path matching across up to 4 hops in single queries.

---

## Data Model & Architecture

(:Supplier) -[:SUPPLIES]-> (:RawMaterial) -[:YIELDS]-> (:Component) -[:ASSEMBLED_INTO]-> (:SubAssembly) -[:BUILDS]-> (:Product)

### Graph Schema
- **Nodes & Properties:**
  - Supplier: { id: String, name: String }
  - RawMaterial: { id: String, name: String }
  - Component: { id: String, name: String }
  - SubAssembly: { id: String, name: String }
  - Product: { id: String, name: String, category: String }
- **Relationships:**
  - SUPPLIES: Connects Supplier to RawMaterial
  - YIELDS: Connects RawMaterial to Component
  - ASSEMBLED_INTO: Connects Component to SubAssembly
  - BUILDS: Connects SubAssembly to Product

---

## Core Cypher Queries (Via ProductRepository)

The backend dynamically analyzes blast radius depending on the targeted tier in the supply chain using parameterized Cypher queries:

### 1. Supplier Downstream Impact (4 Hops)
MATCH (s:Supplier {id: $entityId})
OPTIONAL MATCH (s)-[:SUPPLIES]->(rm:RawMaterial)
OPTIONAL MATCH (rm)-[:YIELDS]->(c:Component)
OPTIONAL MATCH (c)-[:ASSEMBLED_INTO]->(sa:SubAssembly)
OPTIONAL MATCH (sa)-[:BUILDS]->(p:Product)
RETURN DISTINCT s.id AS supplierId, s.name AS supplierName,
                rm.id AS rawMaterialId, rm.name AS rawMaterialName,
                c.id AS componentId, c.name AS componentName,
                sa.id AS subAssemblyId, sa.name AS subAssemblyName,
                p.id AS productId, p.name AS productName, 4 AS depth
ORDER BY p.name

### 2. Raw Material Downstream Impact (3 Hops)
MATCH (rm:RawMaterial {id: $entityId})
OPTIONAL MATCH (rm)-[:YIELDS]->(c:Component)
OPTIONAL MATCH (c)-[:ASSEMBLED_INTO]->(sa:SubAssembly)
OPTIONAL MATCH (sa)-[:BUILDS]->(p:Product)
RETURN DISTINCT null AS supplierId, null AS supplierName,
                rm.id AS rawMaterialId, rm.name AS rawMaterialName,
                c.id AS componentId, c.name AS componentName,
                sa.id AS subAssemblyId, sa.name AS subAssemblyName,
                p.id AS productId, p.name AS productName, 3 AS depth
ORDER BY p.name

### 3. Component & Sub-Assembly Impact (2 & 1 Hop)
Executed via findImpactByComponent (2 hops) and findImpactBySubAssembly (1 hop) to trace exact multi-tier blast radius down to the finished Product.

---

## API Reference

| Method | Endpoint | Description |
| :--- | :--- | :--- |
| **GET** | `/api/v1/seed/search?query={keyword}` | Real-time search/autocomplete for nodes across all tiers. |
| **POST** | `/api/v1/seed/default?userId={server_sent_event_userid}` | Seeds multi-tier test supply chain graph nodes & relationships into CognoDB. |
| **GET** | `/api/v1/supply-chain/impact/{id}?type={entityType}` | Performs tier-aware Cypher traversal returning downstream impact paths & hop depth. |

---

## Local Development Setup

### Prerequisites
- Java 21 JDK
- Maven 3.8+
- Active CognoDB Cloud Instance URI (bolt+s://...)

### Environment Variables & Run

mvn clean package

java -Djasypt.encryptor.password=cognodb -jar target/*.jar

Service runs locally on http://localhost:8081.

### Extra
To generate Supply chain graph default data csv file (present in resource directory) use cognodb.py file provided in root directory.

python3 cognodb.py

Place the generated file in resource directory.

---

## Production Deployment (Docker, ARM64)

The service is deployed as a Docker container on an ARM64 (aarch64) host, sitting behind nginx as a reverse proxy with TLS termination.

### Build & Push (multi-arch host required for cross-building)

docker buildx build --platform linux/arm64 \
  -t <your-dockerhub-username>/supplychain-api:latest \
  --push .

### Run

docker run -d --name supplychain-api --restart unless-stopped \
  -p 127.0.0.1:8081:8081 \
  -e JASYPT_PASSWORD='<your-real-value>' \
  -e JAVA_OPTS='-Xmx400m -Xms128m' \
  --memory=500m \
  <your-dockerhub-username>/supplychain-api:latest

`JAVA_OPTS` caps the JVM heap; `--memory` is the Docker/cgroup hard limit on the whole container. Keep the Docker limit comfortably above the JVM heap cap to leave room for thread stacks, metaspace, and GC overhead.

### nginx Reverse Proxy

The container binds to `127.0.0.1:8081` only (not exposed externally); nginx proxies `https://api-supply-chain.royawl.com` to it and handles HTTPS via a wildcard certificate for `*.royawl.com`.