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
import com.cognodb.supplychain.model.ComponentNode;
import com.cognodb.supplychain.model.RawMaterialNode;
import com.cognodb.supplychain.repository.ComponentRepository;
import com.cognodb.supplychain.utils.AppEnums.EntityType;
import com.cognodb.supplychain.utils.AppEnums.ParentEdge;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ComponentService {

	private final ComponentRepository componentRepository;
	private final ObjectMapper objectMapper;

	private Map<String, Object> toMap(ComponentNode entity) {
		Map<String, Object> map = objectMapper.convertValue(entity, new TypeReference<Map<String, Object>>() {});
		Set<String> parentIds = (entity.getRawMaterials() != null)
				? entity.getRawMaterials().stream().map(RawMaterialNode::getId).filter(Objects::nonNull).collect(Collectors.toSet())
				: Set.of();
		map.remove("rawMaterials");
		map.put("parentIds", parentIds);
		return map;
	}

	public List<ComponentNode> findAllByIds(List<String> ids) {
		if (ids == null || ids.isEmpty()) {
			return List.of();
		}
		return componentRepository.findAllByIds(ids);
	}

	public List<ComponentNode> findAllByNames(List<String> names) {
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
		return componentRepository.findAllByNames(cleanNames);
	}

	public List<NodeEntityResponse> findAllWithParents(int limit, int offset) {
		return componentRepository.findAllComponentsWithParents(limit, offset).stream().<NodeEntityResponse>map(c -> {
			List<ParentNodeDto> parents = (c.getRawMaterials() == null) ? List.of()
					: c.getRawMaterials().stream().map(rm -> ParentNodeDto.builder().parentId(rm.getId())
							.parentName(rm.getName()).parentEdge(ParentEdge.ALREADY_LINKED.name()).build())
							.collect(Collectors.toList());

			return NodeEntityResponse.builder().id(c.getId()).name(c.getName()).category(c.getCategory())
					.entityType(EntityType.COMPONENT.name()).parentNodeDto(parents).build();
		}).collect(Collectors.toList());
	}

	public List<ComponentNode> findByNameContaining(String keyword) {
		return componentRepository.findComponentsByNameContaining(keyword);
	}

	public Optional<ComponentNode> findById(String id) {
		return componentRepository.findComponentById(id);
	}

	public ComponentNode findByName(String name) {
		return componentRepository.findComponentByName(name).orElse(null);
	}

	public boolean existsById(String id) {
		return componentRepository.componentExistsById(id);
	}

	@Transactional
	public ComponentNode save(ComponentNode entity) {
		return componentRepository.create(toMap(entity));
	}

	@Transactional
	public List<ComponentNode> saveAll(List<ComponentNode> components) {
		List<Map<String, Object>> payload = components.stream().map(this::toMap).toList();
		return componentRepository.createAll(payload);
	}

	@Transactional
	public ComponentNode update(ComponentNode entity) {
		if (entity.getId() == null || entity.getId().isBlank()) {
			throw new IllegalArgumentException("ID is required for update");
		}
		return componentRepository.update(toMap(entity));
	}

	@Transactional
	public List<ComponentNode> updateAll(List<ComponentNode> components) {
		List<Map<String, Object>> payload = components.stream().map(this::toMap).toList();
		return componentRepository.updateAll(payload);
	}

	@Transactional
	public boolean deleteById(String id) {
		Long deletedCount = componentRepository.deleteComponentCascadeById(id);
		return deletedCount != null && deletedCount > 0;
	}

	@Transactional
	public long deleteAll() {
		return componentRepository.deleteAllComponents();
	}
}