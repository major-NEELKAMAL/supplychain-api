package com.cognodb.supplychain.service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.cognodb.supplychain.dto.NodeEntityResponse;
import com.cognodb.supplychain.dto.ParentNodeDto;
import com.cognodb.supplychain.model.RawMaterialNode;
import com.cognodb.supplychain.model.SupplierNode;
import com.cognodb.supplychain.repository.RawMaterialRepository;
import com.cognodb.supplychain.utils.AppEnums.EntityType;
import com.cognodb.supplychain.utils.AppEnums.ParentEdge;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RawMaterialService {

	private final RawMaterialRepository rawMaterialRepository;
	private final ObjectMapper objectMapper;

	private Map<String, Object> toMap(RawMaterialNode entity) {
		Map<String, Object> map = objectMapper.convertValue(entity, new TypeReference<Map<String, Object>>() {});
		Set<String> parentIds = (entity.getSuppliers() != null)
				? entity.getSuppliers().stream().map(SupplierNode::getId).filter(Objects::nonNull).collect(Collectors.toSet())
				: Set.of();
		map.remove("suppliers");
		map.put("parentIds", parentIds);
		return map;
	}

	public List<RawMaterialNode> findAllByIds(List<String> ids) {
		if (ids == null || ids.isEmpty()) {
			return List.of();
		}
		return rawMaterialRepository.findAllByIds(ids);
	}

	public List<RawMaterialNode> findAllByNames(List<String> names) {
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
		return rawMaterialRepository.findAllByNames(cleanNames);
	}

	public List<NodeEntityResponse> findAllWithParents(int limit, int offset) {
		return rawMaterialRepository.findAllRawMaterialsWithParents(limit, offset).stream().<NodeEntityResponse>map(rm -> {
			List<ParentNodeDto> parents = (rm.getSuppliers() == null) ? List.of()
					: rm.getSuppliers().stream().map(s -> ParentNodeDto.builder().parentId(s.getId())
							.parentName(s.getName()).parentEdge(ParentEdge.ALREADY_LINKED.name()).build())
							.collect(Collectors.toList());

			return NodeEntityResponse.builder().id(rm.getId()).name(rm.getName()).category(rm.getCategory())
					.entityType(EntityType.RAW_MATERIAL.name()).parentNodeDto(parents).build();
		}).collect(Collectors.toList());
	}

	public List<RawMaterialNode> findByNameContaining(String keyword) {
		return rawMaterialRepository.findRawMaterialsByNameContaining(keyword);
	}

	public Optional<RawMaterialNode> findById(String id) {
		return rawMaterialRepository.findRawMaterialById(id);
	}

	public RawMaterialNode findByName(String name) {
		return rawMaterialRepository.findRawMaterialByName(name).orElse(null);
	}

	public boolean existsById(String id) {
		return rawMaterialRepository.rawMaterialExistsById(id);
	}

	@Transactional
	public RawMaterialNode save(RawMaterialNode entity) {
		return rawMaterialRepository.create(toMap(entity));
	}

	@Transactional
	public List<RawMaterialNode> saveAll(List<RawMaterialNode> materials) {
		List<Map<String, Object>> payload = materials.stream().map(this::toMap).toList();
		return rawMaterialRepository.createAll(payload);
	}

	@Transactional
	public RawMaterialNode update(RawMaterialNode entity) {
		if (entity.getId() == null || entity.getId().isBlank()) {
			throw new IllegalArgumentException("ID is required for update");
		}
		return rawMaterialRepository.update(toMap(entity));
	}

	@Transactional
	public List<RawMaterialNode> updateAll(List<RawMaterialNode> materials) {
		List<Map<String, Object>> payload = materials.stream().map(this::toMap).toList();
		return rawMaterialRepository.updateAll(payload);
	}

	@Transactional
	public boolean deleteById(String id) {
		Long deletedCount = rawMaterialRepository.deleteRawMaterialCascadeById(id);
		return deletedCount != null && deletedCount > 0;
	}

	@Transactional
	public long deleteAll() {
		return rawMaterialRepository.deleteAllRawMaterials();
	}
}