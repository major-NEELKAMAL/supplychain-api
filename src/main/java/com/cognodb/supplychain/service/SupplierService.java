package com.cognodb.supplychain.service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.cognodb.supplychain.dto.NodeEntityResponse;
import com.cognodb.supplychain.model.SupplierNode;
import com.cognodb.supplychain.repository.SupplierRepository;
import com.cognodb.supplychain.utils.AppEnums.EntityType;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SupplierService {

	private final SupplierRepository supplierRepository;
	private final ObjectMapper objectMapper;

	private Map<String, Object> toMap(SupplierNode entity) {
		return objectMapper.convertValue(entity, new TypeReference<Map<String, Object>>() {});
	}

	public List<SupplierNode> findAllByIds(List<String> ids) {
		if (ids == null || ids.isEmpty()) {
			return List.of();
		}
		return supplierRepository.findAllByIds(ids);
	}

	public List<SupplierNode> findAllByNames(List<String> names) {
		if (names == null || names.isEmpty()) {
			return List.of();
		}
		List<String> cleanNames = names.stream()
				.filter(Objects::nonNull)
				.map(String::trim)
	            .filter(name -> !name.isBlank())
	            .map(String::toLowerCase)
				.distinct()
				.collect(Collectors.toList());
		return supplierRepository.findAllByNames(cleanNames);
	}

	public List<NodeEntityResponse> findAllWithParents() {
		return supplierRepository.findAllSuppliersWithParents().stream()
				.<NodeEntityResponse>map(
						s -> NodeEntityResponse.builder().id(s.getId()).name(s.getName()).category(s.getCategory())
								.entityType(EntityType.SUPPLIER.name()).parentNodeDto(List.of()).build())
				.collect(Collectors.toList());
	}

	public List<SupplierNode> findByNameContaining(String keyword) {
		return supplierRepository.findSuppliersByNameContaining(keyword);
	}

	public Optional<SupplierNode> findById(String id) {
		return supplierRepository.findSupplierById(id);
	}

	public SupplierNode findByName(String name) {
		return supplierRepository.findSupplierByName(name).orElse(null);
	}

	public boolean existsById(String id) {
		return supplierRepository.supplierExistsById(id);
	}

	@Transactional
	public SupplierNode save(SupplierNode entity) {
		return supplierRepository.create(toMap(entity));
	}

	@Transactional
	public List<SupplierNode> saveAll(List<SupplierNode> suppliers) {
		List<Map<String, Object>> payload = suppliers.stream().map(this::toMap).toList();
		return supplierRepository.createAll(payload);
	}

	@Transactional
	public SupplierNode update(SupplierNode entity) {
		if (entity.getId() == null || entity.getId().isBlank()) {
			throw new IllegalArgumentException("ID is required for update");
		}
		return supplierRepository.update(toMap(entity));
	}

	@Transactional
	public List<SupplierNode> updateAll(List<SupplierNode> suppliers) {
		List<Map<String, Object>> payload = suppliers.stream().map(this::toMap).toList();
		return supplierRepository.updateAll(payload);
	}

	@Transactional
	public boolean deleteById(String id) {
		Long deletedCount = supplierRepository.deleteSupplierCascadeById(id);
		return deletedCount != null && deletedCount > 0;
	}

	@Transactional
	public long deleteAll() {
		return supplierRepository.deleteAllSuppliers();
	}

	@Transactional
	public long deleteEntireGraph() {
		return supplierRepository.deleteEntireGraph();
	}
}