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

import com.cognodb.supplychain.dto.ImpactedProductDto;
import com.cognodb.supplychain.dto.NodeEntityResponse;
import com.cognodb.supplychain.dto.ParentNodeDto;
import com.cognodb.supplychain.model.ProductNode;
import com.cognodb.supplychain.model.SubAssemblyNode;
import com.cognodb.supplychain.repository.ProductRepository;
import com.cognodb.supplychain.utils.AppEnums.EntityType;
import com.cognodb.supplychain.utils.AppEnums.ParentEdge;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {

	private final ProductRepository productRepository;
	private final ObjectMapper objectMapper;

	private Map<String, Object> toMap(ProductNode entity) {
	    Map<String, Object> map = objectMapper.convertValue(entity, new TypeReference<Map<String, Object>>() {});
	    
	   
	    Set<String> parentIds = (entity.getSubAssemblies() != null)
	            ? entity.getSubAssemblies().stream()
	                    .map(SubAssemblyNode::getId)
	                    .filter(Objects::nonNull)
	                    .collect(Collectors.toSet())
	            : Set.of();
	            
	    map.remove("subAssemblies");
	    map.put("parentIds", parentIds);
	    return map;
	}

	public List<ProductNode> findAllByIds(List<String> ids) {
		if (ids == null || ids.isEmpty()) {
			return List.of();
		}
		return productRepository.findAllByIds(ids);
	}

	public List<ProductNode> findAllByNames(List<String> names) {
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
		return productRepository.findAllByNames(cleanNames);
	}

	public List<NodeEntityResponse> findAllWithParents() {
		return productRepository.findAllProductsWithParents().stream().<NodeEntityResponse>map(p -> {
			List<ParentNodeDto> parents = (p.getSubAssemblies() == null) ? List.of()
					: p.getSubAssemblies().stream().map(sa -> ParentNodeDto.builder().parentId(sa.getId())
							.parentName(sa.getName()).parentEdge(ParentEdge.ALREADY_LINKED.name()).build())
							.collect(Collectors.toList());

			return NodeEntityResponse.builder().id(p.getId()).name(p.getName()).category(p.getCategory())
					.entityType(EntityType.PRODUCT.name()).parentNodeDto(parents).build();
		}).collect(Collectors.toList());
	}

	public List<ProductNode> findByNameContaining(String keyword) {
		return productRepository.findProductsByNameContaining(keyword);
	}

	public Optional<ProductNode> findById(String id) {
		return productRepository.findProductById(id);
	}

	public ProductNode findByName(String name) {
		return productRepository.findProductByName(name).orElse(null);
	}

	public boolean existsById(String id) {
		return productRepository.productExistsById(id);
	}

	@Transactional
	public ProductNode save(ProductNode entity) {
		return productRepository.create(toMap(entity));
	}

	@Transactional
	public List<ProductNode> saveAll(List<ProductNode> products) {
		List<Map<String, Object>> payload = products.stream().map(this::toMap).toList();
		return productRepository.createAll(payload);
	}

	@Transactional
	public ProductNode update(ProductNode entity) {
		if (entity.getId() == null || entity.getId().isBlank()) {
			throw new IllegalArgumentException("ID is required for update");
		}
		return productRepository.update(toMap(entity));
	}

	@Transactional
	public List<ProductNode> updateAll(List<ProductNode> products) {
		List<Map<String, Object>> payload = products.stream().map(this::toMap).toList();
		return productRepository.updateAll(payload);
	}

	@Transactional
	public boolean deleteById(String id) {
		Long count = productRepository.deleteProductCascadeById(id);
		return count != null && count > 0;
	}

	@Transactional
	public long deleteAll() {
		return productRepository.deleteAllProducts();
	}

	public List<ImpactedProductDto> getImpactedProducts(String entityId, EntityType entityType) {
		if (entityType == null) {
			return productRepository.findImpactBySupplier(entityId);
		}

		return switch (entityType) {
		case RAW_MATERIAL -> productRepository.findImpactByRawMaterial(entityId);
		case COMPONENT -> productRepository.findImpactByComponent(entityId);
		case SUB_ASSEMBLY -> productRepository.findImpactBySubAssembly(entityId);
		case SUPPLIER -> productRepository.findImpactBySupplier(entityId);
		default -> productRepository.findImpactBySupplier(entityId);
		};
	}
}