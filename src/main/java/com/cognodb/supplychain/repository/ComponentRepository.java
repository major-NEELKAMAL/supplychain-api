package com.cognodb.supplychain.repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import com.cognodb.supplychain.model.ComponentNode;

@org.springframework.stereotype.Repository
public interface ComponentRepository extends Repository<ComponentNode, String> {

	@Query("""
			MATCH (c:Component)
			WHERE c.id IN $ids
			RETURN c
			""")
	List<ComponentNode> findAllByIds(@Param("ids") List<String> ids);

	@Query("""
			MATCH (c:Component)
			WHERE toLower(c.name) IN $names
			RETURN c
			""")
	List<ComponentNode> findAllByNames(@Param("names") List<String> names);

	@Query("""
			MATCH (c:Component)
			WHERE toLower(c.name) CONTAINS toLower($keyword)
			RETURN c
			ORDER BY c.name
			""")
	List<ComponentNode> findComponentsByNameContaining(@Param("keyword") String keyword);

	@Query("""
			MATCH (c:Component {id: $id})
			RETURN c
			LIMIT 1
			""")
	Optional<ComponentNode> findComponentById(@Param("id") String id);

	@Query("""
			MATCH (c:Component)
			WHERE c.name = $name
			RETURN c
			LIMIT 1
			""")
	Optional<ComponentNode> findComponentByName(@Param("name") String name);

	@Query("""
			MATCH (c:Component {id: $id})
			RETURN count(c) > 0
			""")
	boolean componentExistsById(@Param("id") String id);

	@Query("""
			CREATE (comp:Component)
			SET comp = $component,
			    comp.createdAt = localdatetime(),
			    comp.updatedAt = localdatetime()
			WITH comp
			UNWIND (CASE WHEN $component.parentIds IS NULL OR size($component.parentIds) = 0 THEN [null] ELSE $component.parentIds END) AS rawMaterialId
			OPTIONAL MATCH (m:RawMaterial {id: rawMaterialId})
			FOREACH (_ IN CASE WHEN m IS NOT NULL THEN [1] ELSE [] END |
			    CREATE (m)-[:YIELDS]->(comp)
			)
			RETURN comp
			""")
	ComponentNode create(@Param("component") Map<String, Object> component);

	@Query("""
			UNWIND $components AS c
			CREATE (comp:Component)
			SET comp = c,
			    comp.createdAt = localdatetime(),
			    comp.updatedAt = localdatetime()
			WITH comp, c
			UNWIND (CASE WHEN c.parentIds IS NULL OR size(c.parentIds) = 0 THEN [null] ELSE c.parentIds END) AS rawMaterialId
			OPTIONAL MATCH (m:RawMaterial {id: rawMaterialId})
			FOREACH (_ IN CASE WHEN m IS NOT NULL THEN [1] ELSE [] END |
			    CREATE (m)-[:YIELDS]->(comp)
			)
			RETURN comp
			""")
	List<ComponentNode> createAll(@Param("components") List<Map<String, Object>> components);

	@Query("""
			 MATCH (component:Component {id: $component.id})

			SET component.name = $component.name,
			   component.category = $component.category,
			   component.updatedAt = localdatetime()

			 WITH component, coalesce($component.parentIds, []) AS desiredParentIds

			 // Delete obsolete RawMaterial -> Component relationships
			 OPTIONAL MATCH (oldMaterial:RawMaterial)-[oldRel:YIELDS]->(component)
			 WHERE NOT oldMaterial.id IN desiredParentIds
			 DELETE oldRel

			 WITH DISTINCT component, desiredParentIds

			 // Find only existing RawMaterials
			 OPTIONAL MATCH (newMaterial:RawMaterial)
			 WHERE newMaterial.id IN desiredParentIds

			 WITH component, collect(DISTINCT newMaterial) AS desiredMaterials

			 // Preserve existing links and create missing links
			 FOREACH (material IN desiredMaterials |
			     MERGE (material)-[:YIELDS]->(component)
			 )

			 RETURN component
			 """)
	ComponentNode update(@Param("component") Map<String, Object> component);

	@Query("""
			UNWIND $components AS c

			MATCH (component:Component {id: c.id})

			SET component.name = $component.name,
			  component.category = $component.category,
			  component.updatedAt = localdatetime()

			WITH component, coalesce(c.parentIds, []) AS desiredParentIds

			// Delete obsolete RawMaterial -> Component relationships
			OPTIONAL MATCH (oldMaterial:RawMaterial)-[oldRel:YIELDS]->(component)
			WHERE NOT oldMaterial.id IN desiredParentIds
			DELETE oldRel

			WITH DISTINCT component, desiredParentIds

			// Find only existing RawMaterials
			OPTIONAL MATCH (newMaterial:RawMaterial)
			WHERE newMaterial.id IN desiredParentIds

			WITH component, collect(DISTINCT newMaterial) AS desiredMaterials

			// Preserve existing links and create missing links
			FOREACH (material IN desiredMaterials |
			    MERGE (material)-[:YIELDS]->(component)
			)

			RETURN DISTINCT component
			""")
	List<ComponentNode> updateAll(@Param("components") List<Map<String, Object>> components);

	@Query("""
			MATCH (c:Component {id: $id})
			OPTIONAL MATCH (c)-[*1..10]->(child)
			WITH c, collect(DISTINCT child) AS children

			OPTIONAL MATCH (children)<-[r]-(otherParent)
			WHERE NOT (c)-[*0..10]->(otherParent) AND otherParent <> c

			WITH c, children, collect(DISTINCT children) AS safeChildren

			DETACH DELETE c
			FOREACH (ch IN safeChildren | DETACH DELETE ch)

			RETURN size(safeChildren) + 1 AS deletedCount
			""")
	long deleteComponentCascadeById(@Param("id") String id);

	@Query("""
			MATCH (c:Component)
			DETACH DELETE c
			RETURN count(*) AS deletedCount
			""")
	long deleteAllComponents();

	@Query("""
				MATCH (c:Component)
				OPTIONAL MATCH (c)<-[r:YIELDS]-(rm:RawMaterial)
				RETURN c, collect(r) AS rels, collect(rm) AS rawMaterials
				ORDER BY c.name
				SKIP $offset
			LIMIT $limit
				""")
	List<ComponentNode> findAllComponentsWithParents(@Param("limit") int limit, @Param("offset") int offset);
}