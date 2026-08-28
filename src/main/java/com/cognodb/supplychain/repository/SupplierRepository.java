package com.cognodb.supplychain.repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import com.cognodb.supplychain.model.SupplierNode;

@org.springframework.stereotype.Repository
public interface SupplierRepository extends Repository<SupplierNode, String> {

	@Query("""
			MATCH (s:Supplier)
			WHERE s.id IN $ids
			RETURN s
			""")
	List<SupplierNode> findAllByIds(@Param("ids") List<String> ids);

	@Query("""
			MATCH (s:Supplier)
			WHERE toLower(s.name) IN $names
			RETURN s
			""")
	List<SupplierNode> findAllByNames(@Param("names") List<String> names);

	@Query("""
			MATCH (s:Supplier)
			WHERE toLower(s.name) CONTAINS toLower($keyword)
			RETURN s
			ORDER BY s.name
			""")
	List<SupplierNode> findSuppliersByNameContaining(@Param("keyword") String keyword);

	@Query("""
			MATCH (s:Supplier {id: $id})
			RETURN s
			LIMIT 1
			""")
	Optional<SupplierNode> findSupplierById(@Param("id") String id);

	@Query("""
			MATCH (s:Supplier)
			WHERE toLower(s.name) = toLower($name)
			RETURN s
			LIMIT 1
			""")
	Optional<SupplierNode> findSupplierByName(@Param("name") String name);

	@Query("""
			MATCH (s:Supplier {id: $id})
			RETURN count(s) > 0
			""")
	boolean supplierExistsById(@Param("id") String id);

	@Query("""
			CREATE (s:Supplier)
			SET s = $supplier,
			    s.createdAt = localdatetime(),
			    s.updatedAt = localdatetime()
			RETURN s
			""")
	SupplierNode create(@Param("supplier") Map<String, Object> supplier);

	@Query("""
			MATCH (s:Supplier {id: $supplier.id})
			SET s += $supplier,
			    s.updatedAt = localdatetime()
			RETURN s
			""")
	SupplierNode update(@Param("supplier") Map<String, Object> supplier);

	@Query("""
			UNWIND $suppliers AS s
			CREATE (sup:Supplier)
			SET sup = s,
			    sup.createdAt = localdatetime(),
			    sup.updatedAt = localdatetime()
			RETURN sup
			""")
	List<SupplierNode> createAll(@Param("suppliers") List<Map<String, Object>> suppliers);

	@Query("""
			UNWIND $suppliers AS s
			MATCH (sup:Supplier {id: s.id})
			SET sup += s,
			    sup.updatedAt = localdatetime()
			RETURN sup
			""")
	List<SupplierNode> updateAll(@Param("suppliers") List<Map<String, Object>> suppliers);

	@Query("""
			MATCH (s:Supplier {id: $id})
			OPTIONAL MATCH (s)-[*1..10]->(child)
			WITH s, collect(DISTINCT child) AS children

			OPTIONAL MATCH (children)<-[r]-(otherParent)
			WHERE NOT (s)-[*0..10]->(otherParent) AND otherParent <> s

			WITH s, children, collect(DISTINCT children) AS sharedChildren
			WITH s, [c IN children WHERE NOT c IN sharedChildren] AS safeChildren

			DETACH DELETE s
			FOREACH (c IN safeChildren | DETACH DELETE c)

			RETURN size(safeChildren) + 1 AS deletedCount
			""")
	Long deleteSupplierCascadeById(@Param("id") String id);

	@Query("""
			MATCH (s:Supplier)
			DETACH DELETE s
			RETURN count(*) AS deletedCount
			""")
	long deleteAllSuppliers();

	@Query("""
			MATCH (n)
			DETACH DELETE n
			RETURN count(n) AS deletedNodeCount
			""")
	long deleteEntireGraph();

	@Query("""
			MATCH (s:Supplier)
			RETURN s
			ORDER BY s.name
			SKIP $offset
			   LIMIT $limit
			""")
	List<SupplierNode> findAllSuppliersWithParents(@Param("limit") int limit, @Param("offset") int offset);
}