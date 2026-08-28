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
import com.cognodb.supplychain.model.ProductNode;
import com.cognodb.supplychain.model.SubAssemblyNode;
import com.cognodb.supplychain.repository.SubAssemblyRepository;
import com.cognodb.supplychain.utils.AppEnums.EntityType;
import com.cognodb.supplychain.utils.AppEnums.ParentEdge;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SubAssemblyService {

	private final SubAssemblyRepository subAssemblyRepository;
	private final ObjectMapper objectMapper;

	private Map<String, Object> toMap(SubAssemblyNode entity) {
		Map<String, Object> map = objectMapper.convertValue(entity, new TypeReference<Map<String, Object>>() {});
		Set<String> parentIds = (entity.getComponents() != null)
				? entity.getComponents().stream().map(ComponentNode::getId).filter(Objects::nonNull).collect(Collectors.toSet())
				: Set.of();
		map.remove("components");
		map.put("parentIds", parentIds);
		return map;
	}
	
	public List<SubAssemblyNode> findAllByIds(List<String> ids) {
		if (ids == null || ids.isEmpty()) {
			return List.of();
		}
		return subAssemblyRepository.findAllByIds(ids);
	}

	public List<SubAssemblyNode> findAllByNames(List<String> names) {
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
		return subAssemblyRepository.findAllByNames(cleanNames);
	}

	public List<NodeEntityResponse> findAllWithParents() {
		return subAssemblyRepository.findAllSubAssembliesWithParents().stream().<NodeEntityResponse>map(sa -> {
			List<ParentNodeDto> parents = (sa.getComponents() == null) ? List.of()
					: sa.getComponents().stream().map(c -> ParentNodeDto.builder().parentId(c.getId())
							.parentName(c.getName()).parentEdge(ParentEdge.ALREADY_LINKED.name()).build())
							.collect(Collectors.toList());

			return NodeEntityResponse.builder().id(sa.getId()).name(sa.getName()).category(sa.getCategory())
					.entityType(EntityType.SUB_ASSEMBLY.name()).parentNodeDto(parents).build();
		}).collect(Collectors.toList());
	}

	public List<SubAssemblyNode> findByNameContaining(String keyword) {
		return subAssemblyRepository.findSubAssembliesByNameContaining(keyword);
	}

	public Optional<SubAssemblyNode> findById(String id) {
		return subAssemblyRepository.findSubAssemblyById(id);
	}

	public SubAssemblyNode findByName(String name) {
		return subAssemblyRepository.findSubAssemblyByName(name).orElse(null);
	}

	public boolean existsById(String id) {
		return subAssemblyRepository.subAssemblyExistsById(id);
	}

	@Transactional
	public SubAssemblyNode save(SubAssemblyNode entity) {
		return subAssemblyRepository.create(toMap(entity));
	}

	@Transactional
	public List<SubAssemblyNode> saveAll(List<SubAssemblyNode> subAssemblies) {
		List<Map<String, Object>> payload = subAssemblies.stream().map(this::toMap).toList();
		return subAssemblyRepository.createAll(payload);
	}

	@Transactional
	public SubAssemblyNode update(SubAssemblyNode entity) {
		if (entity.getId() == null || entity.getId().isBlank()) {
			throw new IllegalArgumentException("ID is required for update");
		}
		return subAssemblyRepository.update(toMap(entity));
	}

	@Transactional
	public List<SubAssemblyNode> updateAll(List<SubAssemblyNode> subAssemblies) {
		List<Map<String, Object>> payload = subAssemblies.stream().map(this::toMap).toList();
		return subAssemblyRepository.updateAll(payload);
	}

	@Transactional
	public boolean deleteById(String id) {
		Long deletedCount = subAssemblyRepository.deleteSubAssemblyCascadeById(id);
		return deletedCount != null && deletedCount > 0;
	}

	@Transactional
	public long deleteAll() {
		return subAssemblyRepository.deleteAllSubAssemblies();
	}
}