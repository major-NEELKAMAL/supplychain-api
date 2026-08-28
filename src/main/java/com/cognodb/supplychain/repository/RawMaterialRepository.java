package com.cognodb.supplychain.repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import com.cognodb.supplychain.model.RawMaterialNode;

@org.springframework.stereotype.Repository
public interface RawMaterialRepository extends Repository<RawMaterialNode, String> {

	@Query("""
			MATCH (r:RawMaterial)
			WHERE r.id IN $ids
			RETURN r
			""")
	List<RawMaterialNode> findAllByIds(@Param("ids") List<String> ids);

	@Query("""
			MATCH (r:RawMaterial)
			WHERE toLower(r.name) IN $names			
			RETURN r
			""")
	List<RawMaterialNode> findAllByNames(@Param("names") List<String> names);

	@Query("""
			MATCH (r:RawMaterial)
			WHERE toLower(r.name) CONTAINS toLower($keyword)
			RETURN r
			ORDER BY r.name
			""")
	List<RawMaterialNode> findRawMaterialsByNameContaining(@Param("keyword") String keyword);

	@Query("""
			MATCH (r:RawMaterial {id: $id})
			RETURN r
			LIMIT 1
			""")
	Optional<RawMaterialNode> findRawMaterialById(@Param("id") String id);

	@Query("""
			MATCH (r:RawMaterial)
			WHERE r.name = $name
			RETURN r
			LIMIT 1
			""")
	Optional<RawMaterialNode> findRawMaterialByName(@Param("name") String name);

	@Query("""
			MATCH (r:RawMaterial {id: $id})
			RETURN count(r) > 0
			""")
	boolean rawMaterialExistsById(@Param("id") String id);

	@Query("""
			CREATE (m:RawMaterial)
			SET m = $material,
			    m.createdAt = localdatetime(),
			    m.updatedAt = localdatetime()
			WITH m
			UNWIND (CASE WHEN $material.parentIds IS NULL OR size($material.parentIds) = 0 THEN [null] ELSE $material.parentIds END) AS supplierId
			OPTIONAL MATCH (s:Supplier {id: supplierId})
			FOREACH (_ IN CASE WHEN s IS NOT NULL THEN [1] ELSE [] END |
			    CREATE (s)-[:SUPPLIES]->(m)
			)
			RETURN m
			""")
	RawMaterialNode create(@Param("material") Map<String, Object> material);

	@Query("""
			UNWIND $materials AS rm
			CREATE (m:RawMaterial)
			SET m = rm,
			    m.createdAt = localdatetime(),
			    m.updatedAt = localdatetime()
			WITH m, rm
			UNWIND (CASE WHEN rm.parentIds IS NULL OR size(rm.parentIds) = 0 THEN [null] ELSE rm.parentIds END) AS supplierId
			OPTIONAL MATCH (s:Supplier {id: supplierId})
			FOREACH (_ IN CASE WHEN s IS NOT NULL THEN [1] ELSE [] END |
			    CREATE (s)-[:SUPPLIES]->(m)
			)
			RETURN m
			""")
	List<RawMaterialNode> createAll(@Param("materials") List<Map<String, Object>> materials);

	@Query("""
		    MATCH (material:RawMaterial {id: $material.id})

		   SET material.name = $material.name,
    material.category = $material.category,
    material.updatedAt = localdatetime()
		    WITH material, coalesce($material.parentIds, []) AS desiredParentIds

		    // Delete obsolete Supplier -> RawMaterial relationships
		    OPTIONAL MATCH (oldSupplier:Supplier)-[oldRel:SUPPLIES]->(material)
		    WHERE NOT oldSupplier.id IN desiredParentIds
		    DELETE oldRel

		    WITH DISTINCT material, desiredParentIds

		    // Find only existing Suppliers
		    OPTIONAL MATCH (newSupplier:Supplier)
		    WHERE newSupplier.id IN desiredParentIds

		    WITH material, collect(DISTINCT newSupplier) AS desiredSuppliers

		    // Preserve existing links and create missing links
		    FOREACH (supplier IN desiredSuppliers |
		        MERGE (supplier)-[:SUPPLIES]->(material)
		    )

		    RETURN material
		    """)
		RawMaterialNode update(
		    @Param("material") Map<String, Object> material
		);
	
	@Query("""
		    UNWIND $materials AS rm

		    MATCH (material:RawMaterial {id: rm.id})

		    SET material.name = $material.name,
    material.category = $material.category,
    material.updatedAt = localdatetime()

		    WITH material, coalesce(rm.parentIds, []) AS desiredParentIds

		    // Delete obsolete Supplier -> RawMaterial relationships
		    OPTIONAL MATCH (oldSupplier:Supplier)-[oldRel:SUPPLIES]->(material)
		    WHERE NOT oldSupplier.id IN desiredParentIds
		    DELETE oldRel

		    WITH DISTINCT material, desiredParentIds

		    // Find only existing Suppliers
		    OPTIONAL MATCH (newSupplier:Supplier)
		    WHERE newSupplier.id IN desiredParentIds

		    WITH material, collect(DISTINCT newSupplier) AS desiredSuppliers

		    // Preserve existing links and create missing links
		    FOREACH (supplier IN desiredSuppliers |
		        MERGE (supplier)-[:SUPPLIES]->(material)
		    )

		    RETURN DISTINCT material
		    """)
		List<RawMaterialNode> updateAll(
		    @Param("materials") List<Map<String, Object>> materials
		);
	
	@Query("""
			MATCH (r:RawMaterial {id: $id})
			OPTIONAL MATCH (r)-[*1..10]->(child)
			WITH r, collect(DISTINCT child) AS children

			OPTIONAL MATCH (children)<-[r]-(otherParent)
			WHERE NOT (r)-[*0..10]->(otherParent) AND otherParent <> r

			WITH r, children, collect(DISTINCT children) AS sharedChildren
			WITH r, [c IN children WHERE NOT c IN sharedChildren] AS safeChildren

			DETACH DELETE r
			FOREACH (c IN safeChildren | DETACH DELETE c)

			RETURN size(safeChildren) + 1 AS deletedCount
			""")
	long deleteRawMaterialCascadeById(@Param("id") String id);

	@Query("""
			MATCH (r:RawMaterial)
			DETACH DELETE r
			RETURN count(*) AS deletedCount
			""")
	long deleteAllRawMaterials();

	@Query("""
			MATCH (rm:RawMaterial)
			OPTIONAL MATCH (rm)<-[r:SUPPLIES]-(s:Supplier)
			RETURN rm, collect(r) AS rels, collect(s) AS suppliers
			ORDER BY rm.name
			""")
	List<RawMaterialNode> findAllRawMaterialsWithParents();
}