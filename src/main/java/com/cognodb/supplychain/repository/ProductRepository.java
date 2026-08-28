package com.cognodb.supplychain.repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import com.cognodb.supplychain.dto.ImpactedProductDto;
import com.cognodb.supplychain.model.ProductNode;

@org.springframework.stereotype.Repository
public interface ProductRepository extends Repository<ProductNode, String> {

	@Query("""
			MATCH (p:Product)
			WHERE p.id IN $ids
			RETURN p
			""")
	List<ProductNode> findAllByIds(@Param("ids") List<String> ids);

	@Query("""
			MATCH (p:Product)
			WHERE toLower(p.name) IN $names
			RETURN p
			""")
	List<ProductNode> findAllByNames(@Param("names") List<String> names);

	@Query("""
			MATCH (p:Product)
			WHERE toLower(p.name) CONTAINS toLower($keyword)
			RETURN p
			ORDER BY p.name
			""")
	List<ProductNode> findProductsByNameContaining(@Param("keyword") String keyword);

	@Query("""
			MATCH (p:Product {id: $id})
			OPTIONAL MATCH (sa:SubAssembly)-[r:BUILDS]->(p)
			RETURN p, collect(sa) AS subAssemblies, collect(r) AS builds
			""")
	Optional<ProductNode> findProductById(@Param("id") String id);

	@Query("""
			MATCH (p:Product)
			WHERE p.name = $name
			RETURN p
			LIMIT 1
			""")
	Optional<ProductNode> findProductByName(@Param("name") String name);

	@Query("""
			MATCH (p:Product {id: $id})
			RETURN count(p) > 0
			""")
	boolean productExistsById(@Param("id") String id);

	@Query("""
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
			""")
	List<ImpactedProductDto> findImpactBySupplier(@Param("entityId") String entityId);

	@Query("""
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
			""")
	List<ImpactedProductDto> findImpactByRawMaterial(@Param("entityId") String entityId);

	@Query("""
			MATCH (c:Component {id: $entityId})
			OPTIONAL MATCH (c)-[:ASSEMBLED_INTO]->(sa:SubAssembly)
			OPTIONAL MATCH (sa)-[:BUILDS]->(p:Product)
			RETURN DISTINCT null AS supplierId, null AS supplierName,
			                null AS rawMaterialId, null AS rawMaterialName,
			                c.id AS componentId, c.name AS componentName,
			                sa.id AS subAssemblyId, sa.name AS subAssemblyName,
			                p.id AS productId, p.name AS productName, 2 AS depth
			ORDER BY p.name
			""")
	List<ImpactedProductDto> findImpactByComponent(@Param("entityId") String entityId);

	@Query("""
			MATCH (sa:SubAssembly {id: $entityId})
			OPTIONAL MATCH (sa)-[:BUILDS]->(p:Product)
			RETURN DISTINCT null AS supplierId, null AS supplierName,
			                null AS rawMaterialId, null AS rawMaterialName,
			                null AS componentId, null AS componentName,
			                sa.id AS subAssemblyId, sa.name AS subAssemblyName,
			                p.id AS productId, p.name AS productName, 1 AS depth
			ORDER BY p.name
			""")
	List<ImpactedProductDto> findImpactBySubAssembly(@Param("entityId") String entityId);

	@Query("""
			CREATE (prod:Product)
			SET prod = $product,
			    prod.createdAt = localdatetime(),
			    prod.updatedAt = localdatetime()
			WITH prod
			UNWIND (CASE WHEN $product.parentIds IS NULL OR size($product.parentIds) = 0 THEN [null] ELSE $product.parentIds END) AS subAssemblyId
			OPTIONAL MATCH (sa:SubAssembly {id: subAssemblyId})
			FOREACH (_ IN CASE WHEN sa IS NOT NULL THEN [1] ELSE [] END |
			    CREATE (sa)-[:BUILDS]->(prod)
			)
			RETURN prod
			""")
	ProductNode create(@Param("product") Map<String, Object> product);

	@Query("""
			UNWIND $products AS p
			CREATE (prod:Product)
			SET prod = p,
			    prod.createdAt = localdatetime(),
			    prod.updatedAt = localdatetime()
			WITH prod, p
			UNWIND (CASE WHEN p.parentIds IS NULL OR size(p.parentIds) = 0 THEN [null] ELSE p.parentIds END) AS subAssemblyId
			OPTIONAL MATCH (sa:SubAssembly {id: subAssemblyId})
			FOREACH (_ IN CASE WHEN sa IS NOT NULL THEN [1] ELSE [] END |
			    CREATE (sa)-[:BUILDS]->(prod)
			)
			RETURN prod
			""")
	List<ProductNode> createAll(@Param("products") List<Map<String, Object>> products);

	@Query("""
			MATCH (prod:Product {id: $product.id})

			SET prod.name = $product.name,
			    prod.category = $product.category,
			    prod.updatedAt = localdatetime()

			WITH prod, coalesce($product.parentIds, []) AS desiredParentIds

			// Delete relationships whose parent is not in the desired set
			OPTIONAL MATCH (oldSa:SubAssembly)-[oldRel:BUILDS]->(prod)
			WHERE NOT oldSa.id IN desiredParentIds
			DELETE oldRel

			WITH DISTINCT prod, desiredParentIds

			// Find existing SubAssembly nodes only
			OPTIONAL MATCH (newSa:SubAssembly)
			WHERE newSa.id IN desiredParentIds

			WITH prod, collect(DISTINCT newSa) AS desiredParents

			// Preserve existing relationships and create missing relationships
			FOREACH (parent IN desiredParents |
			    MERGE (parent)-[:BUILDS]->(prod)
			)

			RETURN prod
			""")
	ProductNode update(@Param("product") Map<String, Object> product);

	@Query("""
			UNWIND $products AS p

			MATCH (prod:Product {id: p.id})

			SET prod.name = p.name,
			    prod.category = p.category,
			    prod.updatedAt = localdatetime()

			WITH prod, coalesce(p.parentIds, []) AS desiredParentIds

			// Delete existing BUILDS relationships not in the desired set
			OPTIONAL MATCH (oldSa:SubAssembly)-[oldRel:BUILDS]->(prod)
			WHERE NOT oldSa.id IN desiredParentIds
			DELETE oldRel

			WITH DISTINCT prod, desiredParentIds

			// Keep the product row even when desiredParentIds is empty
			OPTIONAL MATCH (newSa:SubAssembly)
			WHERE newSa.id IN desiredParentIds

			WITH prod, collect(DISTINCT newSa) AS desiredParents

			// Existing relationship is preserved; missing relationship is created
			FOREACH (parent IN desiredParents |
			    MERGE (parent)-[:BUILDS]->(prod)
			)

			RETURN DISTINCT prod
			""")
	List<ProductNode> updateAll(@Param("products") List<Map<String, Object>> products);

	@Query("""
			MATCH (p:Product {id: $id})
			DETACH DELETE p
			RETURN count(p) AS deletedCount
			""")
	Long deleteProductCascadeById(@Param("id") String id);

	@Query("""
			MATCH (p:Product)
			DETACH DELETE p
			RETURN count(*) AS deletedCount
			""")
	long deleteAllProducts();

	@Query("""
				MATCH (p:Product)
				OPTIONAL MATCH (p)<-[r:BUILDS]-(sa:SubAssembly)
				RETURN p, collect(r) AS rels, collect(sa) AS subAssemblies
				ORDER BY p.name
				SKIP $offset
			LIMIT $limit
				""")
	List<ProductNode> findAllProductsWithParents(@Param("limit") int limit, @Param("offset") int offset);
}