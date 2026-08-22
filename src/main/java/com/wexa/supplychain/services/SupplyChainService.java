package com.wexa.supplychain.services;

import com.wexa.supplychain.dto.ImpactedProductDto;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Session;
import org.neo4j.driver.Values;
import org.neo4j.driver.summary.ResultSummary;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class SupplyChainService {

    private final Driver driver;

    public SupplyChainService(Driver driver) {
        this.driver = driver;
    }

    // Seed realistic graph data
    public void seedDatabase() {
        String seedCypher = 
            "MERGE (s1:Supplier {id: 'SUP-50'}) SET s1.name = 'MicroChip Corp', s1.region = 'Taiwan' " +
            "MERGE (c1:Component {id: 'CMP-101'}) SET c1.name = 'Microcontroller Unit' " +
            "MERGE (c2:Component {id: 'CMP-202'}) SET c2.name = 'Control Board' " +
            "MERGE (p1:Product {id: 'PRD-900'}) SET p1.name = 'Medical Monitor' " +
            "MERGE (p2:Product {id: 'PRD-901'}) SET p2.name = 'Automotive ECU' " +
            "MERGE (s1)-[:SUPPLIES]->(c1) " +
            "MERGE (c1)-[:USED_IN]->(c2) " +
            "MERGE (c2)-[:USED_IN]->(p1) " +
            "MERGE (c1)-[:USED_IN]->(p2)";

        try (Session session = driver.session()) {
            // Fix: Consume the result by retrieving summary stats instead of returning raw Result
            session.executeWrite(tx -> {
                var result = tx.run(seedCypher);
                return result.consume(); // Consumes the result inside the transaction block
            });
        }
    }

    // Multi-hop Graph Query to trace blast radius
    public List<ImpactedProductDto> getImpactedProducts(String supplierId) {
        String cypher = 
            "MATCH path = (s:Supplier {id: $supplierId})-[:SUPPLIES|USED_IN*1..4]->(p:Product) " +
            "RETURN DISTINCT p.id AS productId, p.name AS productName, length(path) AS depth";

        try (Session session = driver.session()) {
            return session.executeRead(tx -> {
                var result = tx.run(cypher, Values.parameters("supplierId", supplierId));
                List<ImpactedProductDto> list = new ArrayList<>();
                // Fix: Iterating and mapping records inside the transaction callback
                while (result.hasNext()) {
                    Record record = result.next();
                    list.add(new ImpactedProductDto(
                        record.get("productId").asString(),
                        record.get("productName").asString(),
                        record.get("depth").asInt()
                    ));
                }
                return list; // Return the constructed list, not the driver Result
            });
        }
    }
}