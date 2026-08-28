package com.cognodb.supplychain.repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import com.cognodb.supplychain.model.ProductNode;
import com.cognodb.supplychain.model.SubAssemblyNode;

@org.springframework.stereotype.Repository
public interface SubAssemblyRepository extends Repository<SubAssemblyNode, String> {

	@Query("""
			MATCH (sa:SubAssembly)
			WHERE sa.id IN $ids
			RETURN sa
			""")
	List<SubAssemblyNode> findAllByIds(@Param("ids") List<String> ids);

	@Query("""
			MATCH (sa:SubAssembly)
			WHERE toLower(sa.name) IN $names
			RETURN sa
			""")
	List<SubAssemblyNode> findAllByNames(@Param("names") List<String> names);

	@Query("""
			MATCH (sa:SubAssembly)
			WHERE toLower(sa.name) CONTAINS toLower($keyword)
			RETURN sa
			ORDER BY sa.name
			""")
	List<SubAssemblyNode> findSubAssembliesByNameContaining(@Param("keyword") String keyword);

	@Query("""
			MATCH (sa:SubAssembly {id: $id})
			OPTIONAL MATCH (sa)<-[r:ASSEMBLED_INTO]-(c:Component)
			RETURN sa, collect(r) AS rels, collect(c) AS components
			ORDER BY sa.name
			""")
	Optional<SubAssemblyNode> findSubAssemblyById(@Param("id") String id);

	@Query("""
			MATCH (sa:SubAssembly)
			WHERE toLower(sa.name) = toLower($keyword)
			RETURN sa
			LIMIT 1
			""")
	Optional<SubAssemblyNode> findSubAssemblyByName(@Param("name") String name);

	@Query("""
			MATCH (sa:SubAssembly {id: $id})
			RETURN count(sa) > 0
			""")
	boolean subAssemblyExistsById(@Param("id") String id);

	@Query("""
			CREATE (sub:SubAssembly)
			SET sub = $subAssembly,
			    sub.createdAt = localdatetime(),
			    sub.updatedAt = localdatetime()
			WITH sub
			UNWIND (CASE WHEN $subAssembly.parentIds IS NULL OR size($subAssembly.parentIds) = 0 THEN [null] ELSE $subAssembly.parentIds END) AS componentId
			OPTIONAL MATCH (c:Component {id: componentId})
			FOREACH (_ IN CASE WHEN c IS NOT NULL THEN [1] ELSE [] END |
			    CREATE (c)-[:ASSEMBLED_INTO]->(sub)
			)
			RETURN sub
			""")
	SubAssemblyNode create(@Param("subAssembly") Map<String, Object> subAssembly);

	@Query("""
			UNWIND $subAssemblies AS sa
			CREATE (sub:SubAssembly)
			SET sub = sa,
			    sub.createdAt = localdatetime(),
			    sub.updatedAt = localdatetime()
			WITH sub, sa
			UNWIND (CASE WHEN sa.parentIds IS NULL OR size(sa.parentIds) = 0 THEN [null] ELSE sa.parentIds END) AS componentId
			OPTIONAL MATCH (c:Component {id: componentId})
			FOREACH (_ IN CASE WHEN c IS NOT NULL THEN [1] ELSE [] END |
			    CREATE (c)-[:ASSEMBLED_INTO]->(sub)
			)
			RETURN sub
			""")
	List<SubAssemblyNode> createAll(@Param("subAssemblies") List<Map<String, Object>> subAssemblies);

	@Query("""
			  MATCH (sub:SubAssembly {id: $subAssembly.id})

			 SET sub.name = $subAssembly.name,
			sub.category = $subAssembly.category,
			sub.updatedAt = localdatetime()

			  WITH sub, coalesce($subAssembly.parentIds, []) AS desiredParentIds

			  // Delete obsolete Component -> SubAssembly relationships
			  OPTIONAL MATCH (oldComponent:Component)-[oldRel:ASSEMBLED_INTO]->(sub)
			  WHERE NOT oldComponent.id IN desiredParentIds
			  DELETE oldRel

			  WITH DISTINCT sub, desiredParentIds

			  // Find only existing Components
			  OPTIONAL MATCH (newComponent:Component)
			  WHERE newComponent.id IN desiredParentIds

			  WITH sub, collect(DISTINCT newComponent) AS desiredComponents

			  // Preserve existing links and create missing links
			  FOREACH (component IN desiredComponents |
			      MERGE (component)-[:ASSEMBLED_INTO]->(sub)
			  )

			  RETURN sub
			  """)
	SubAssemblyNode update(@Param("subAssembly") Map<String, Object> subAssembly);

	@Query("""
			  UNWIND $subAssemblies AS sa

			  MATCH (sub:SubAssembly {id: sa.id})

			  SET sub.name = $subAssembly.name,
			sub.category = $subAssembly.category,
			sub.updatedAt = localdatetime()

			  WITH sub, coalesce(sa.parentIds, []) AS desiredParentIds

			  // Delete obsolete Component -> SubAssembly relationships
			  OPTIONAL MATCH (oldComponent:Component)-[oldRel:ASSEMBLED_INTO]->(sub)
			  WHERE NOT oldComponent.id IN desiredParentIds
			  DELETE oldRel

			  WITH DISTINCT sub, desiredParentIds

			  // Find only existing Components
			  OPTIONAL MATCH (newComponent:Component)
			  WHERE newComponent.id IN desiredParentIds

			  WITH sub, collect(DISTINCT newComponent) AS desiredComponents

			  // Preserve existing links and create missing links
			  FOREACH (component IN desiredComponents |
			      MERGE (component)-[:ASSEMBLED_INTO]->(sub)
			  )

			  RETURN DISTINCT sub
			  """)
	List<SubAssemblyNode> updateAll(@Param("subAssemblies") List<Map<String, Object>> subAssemblies);

	@Query("""
			MATCH (sa:SubAssembly {id: $id})
			OPTIONAL MATCH (sa)-[*1..10]->(child)
			WITH sa, collect(DISTINCT child) AS children

			OPTIONAL MATCH (children)<-[r]-(otherParent)
			WHERE NOT (sa)-[*0..10]->(otherParent) AND otherParent <> sa

			WITH sa, children, collect(DISTINCT children) AS sharedChildren
			WITH sa, [c IN children WHERE NOT c IN sharedChildren] AS safeChildren

			DETACH DELETE sa
			FOREACH (c IN safeChildren | DETACH DELETE c)

			RETURN size(safeChildren) + 1 AS deletedCount
			""")
	long deleteSubAssemblyCascadeById(@Param("id") String id);

	@Query("""
			MATCH (sa:SubAssembly)
			DETACH DELETE sa
			RETURN count(*) AS deletedCount
			""")
	long deleteAllSubAssemblies();

	@Query("""
				MATCH (sa:SubAssembly)
				OPTIONAL MATCH (sa)<-[r:ASSEMBLED_INTO]-(c:Component)
				RETURN sa, collect(r) AS rels, collect(c) AS components
				ORDER BY sa.name
				SKIP $offset
			LIMIT $limit
				""")
	List<SubAssemblyNode> findAllSubAssembliesWithParents(@Param("limit") int limit, @Param("offset") int offset);
}