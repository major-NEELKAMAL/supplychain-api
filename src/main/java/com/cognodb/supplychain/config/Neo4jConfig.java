package com.cognodb.supplychain.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Component;

@Component
public class Neo4jConfig implements CommandLineRunner {

	private static final Logger logger = LoggerFactory.getLogger(Neo4jConfig.class);

	private final Neo4jClient neo4jClient;

	public Neo4jConfig(Neo4jClient neo4jClient) {
		this.neo4jClient = neo4jClient;
	}

	@Override
	public void run(String... args) {

		neo4jClient.query("CREATE CONSTRAINT supplier_id_unique IF NOT EXISTS FOR (s:Supplier) REQUIRE s.id IS UNIQUE")
				.run();
		neo4jClient.query(
				"CREATE CONSTRAINT rawmaterial_id_unique IF NOT EXISTS FOR (r:RawMaterial) REQUIRE r.id IS UNIQUE")
				.run();
		neo4jClient
				.query("CREATE CONSTRAINT component_id_unique IF NOT EXISTS FOR (c:Component) REQUIRE c.id IS UNIQUE")
				.run();
		neo4jClient.query(
				"CREATE CONSTRAINT subassembly_id_unique IF NOT EXISTS FOR (sa:SubAssembly) REQUIRE sa.id IS UNIQUE")
				.run();
		neo4jClient.query("CREATE CONSTRAINT product_id_unique IF NOT EXISTS FOR (p:Product) REQUIRE p.id IS UNIQUE")
				.run();

		neo4jClient
				.query("CREATE CONSTRAINT supplier_name_unique IF NOT EXISTS FOR (s:Supplier) REQUIRE s.name IS UNIQUE")
				.run();
		neo4jClient.query(
				"CREATE CONSTRAINT rawmaterial_name_unique IF NOT EXISTS FOR (r:RawMaterial) REQUIRE r.name IS UNIQUE")
				.run();
		neo4jClient.query(
				"CREATE CONSTRAINT component_name_unique IF NOT EXISTS FOR (c:Component) REQUIRE c.name IS UNIQUE")
				.run();
		neo4jClient.query(
				"CREATE CONSTRAINT subassembly_name_unique IF NOT EXISTS FOR (sa:SubAssembly) REQUIRE sa.name IS UNIQUE")
				.run();
		neo4jClient
				.query("CREATE CONSTRAINT product_name_unique IF NOT EXISTS FOR (p:Product) REQUIRE p.name IS UNIQUE")
				.run();

	}
}
