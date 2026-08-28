package com.cognodb.supplychain.service;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cognodb.supplychain.dto.ApiResponse;
import com.cognodb.supplychain.dto.BatchUploadResponse;
import com.cognodb.supplychain.dto.NodeEntityRequest;
import com.cognodb.supplychain.dto.NodeEntityResponse;
import com.cognodb.supplychain.dto.ParentNodeDto;
import com.cognodb.supplychain.model.ComponentNode;
import com.cognodb.supplychain.model.ProductNode;
import com.cognodb.supplychain.model.RawMaterialNode;
import com.cognodb.supplychain.model.SubAssemblyNode;
import com.cognodb.supplychain.model.SupplierNode;
import com.cognodb.supplychain.utils.AppEnums.EntityType;
import com.cognodb.supplychain.utils.AppEnums.ParentEdge;

@Service
public class SeedService {

	private static final Logger log = LoggerFactory.getLogger(SeedService.class);

	private static final String ID = "id";
	private static final String ENTITY_TYPE = "entityType";
	private static final String NAME = "name";
	private static final String CATEGORY = "category";
	private static final String PARENT_ID = "parentId";
	private static final String PARENT_NAME = "parentName";

	public static int successCount = 0;

	private final SupplierService supplierService;
	private final RawMaterialService rawMaterialService;
	private final ComponentService componentService;
	private final SubAssemblyService subAssemblyService;
	private final ProductService productService;
	private final SseService sseService;

	private final ExecutorService executorService = Executors.newFixedThreadPool(8);

	public SeedService(SupplierService supplierService, RawMaterialService rawMaterialService,
			ComponentService componentService, SubAssemblyService subAssemblyService, ProductService productService,
			SseService sseService) {
		this.supplierService = supplierService;
		this.rawMaterialService = rawMaterialService;
		this.componentService = componentService;
		this.subAssemblyService = subAssemblyService;
		this.productService = productService;
		this.sseService = sseService;
	}

	private boolean isValidStringInputs(String name, String category) {
		if (name == null || name.trim().isEmpty() || name.trim().length() >= 100) {
			log.warn("String validation failed: Name is null, empty, or exceeds 99 characters. Received: '{}'", name);
			return false;
		}
		if (category == null || category.trim().isEmpty() || category.trim().length() >= 50) {
			log.warn("String validation failed: Category is null, empty, or exceeds 49 characters. Received: '{}'",
					category);
			return false;
		}
		return true;
	}

	private ResponseEntity<ApiResponse> buildSuccessResponse(String id, String name, String category, String entityType,
			String message, HttpStatus status) {
		NodeEntityResponse response = NodeEntityResponse.builder()
				.id(id)
				.name(name)
				.category(category)
				.entityType(entityType)
				.build();
		response.setMessage(message);
		response.setCode(status.value());
		response.setSuccess(true);
		return ResponseEntity.status(status).body(response);
	} 
	
	private ResponseEntity<ApiResponse> buildFailureResponse(String message, HttpStatus status) {
		ApiResponse response = ApiResponse.builder()
				.code(status.value())
				.message(message)
				.success(false)
				.build();
		return ResponseEntity.status(status).body(response);
	}

	public CompletableFuture<BatchUploadResponse> processCsvAsync(InputStream inputStream, String userId) {
		log.info("Starting validated batch CSV import for user: {}", userId);

		return CompletableFuture.supplyAsync(() -> {
			BatchUploadResponse batchResult = new BatchUploadResponse();
			List<BatchUploadResponse.RowError> errorList = new ArrayList<>();

			List<Map<String, String>> supplierCreateRows = new ArrayList<>();
			List<Map<String, String>> supplierUpdateRows = new ArrayList<>();

			List<Map<String, String>> rawMaterialCreateRows = new ArrayList<>();
			List<Map<String, String>> rawMaterialUpdateRows = new ArrayList<>();

			List<Map<String, String>> componentCreateRows = new ArrayList<>();
			List<Map<String, String>> componentUpdateRows = new ArrayList<>();

			List<Map<String, String>> subAssemblyCreateRows = new ArrayList<>();
			List<Map<String, String>> subAssemblyUpdateRows = new ArrayList<>();

			List<Map<String, String>> productCreateRows = new ArrayList<>();
			List<Map<String, String>> productUpdateRows = new ArrayList<>();

			int rowNumber = 1;
			successCount = 0;

			try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
				String headerLine = reader.readLine();
				if (headerLine == null) {
					log.warn("CSV Processing declined: Uploaded stream contains no headers or body content.");
					batchResult.setTotalRows(0);
					batchResult.setSuccessCount(0);
					batchResult.setFailureCount(0);
					batchResult.setMessage("CSV file is empty.");
					batchResult.setCode(HttpStatus.BAD_REQUEST.value());
					batchResult.setSuccess(false);

					if (userId != null && !userId.isBlank()) {
						sseService.sendUploadResult(userId, batchResult);
					}
					return batchResult;
				}

				String[] headers = headerLine.split(",");
				String line;

				while ((line = reader.readLine()) != null) {
					rowNumber++;
					if (line.trim().isEmpty()) {
						log.debug("CSV Line {}: Skipping empty line.", rowNumber);
						continue;
					}

					String[] values = line.split(",");
					Map<String, String> record = new HashMap<>();

					record.put("rowNumber", String.valueOf(rowNumber));

					for (int i = 0; i < headers.length && i < values.length; i++) {
						record.put(headers[i].trim(), values[i].trim());
					}

					String entityId = record.get(ID);
					String entityTypeStr = record.get(ENTITY_TYPE);
					String parentIdentifier = record.get(PARENT_ID) != null && !record.get(PARENT_ID).isBlank()
							? record.get(PARENT_ID)
							: record.get(PARENT_NAME);

					if (entityTypeStr == null || entityTypeStr.isBlank()) {
						log.warn("CSV Line {}: Missing 'entityType' attribute.", rowNumber);
						errorList.add(new BatchUploadResponse.RowError(rowNumber, entityId, "UNKNOWN",
								"Missing 'entityType' attribute. Row declined."));
						continue;
					}

					EntityType entityType;
					try {
						entityType = EntityType.fromString(entityTypeStr);
					} catch (Exception e) {
						log.warn("CSV Line {}: Invalid 'entityType' attribute: {}", rowNumber, entityTypeStr);
						errorList.add(new BatchUploadResponse.RowError(rowNumber, entityId, entityTypeStr,
								"Invalid 'entityType' attribute. Row declined."));
						continue;
					}

					boolean isSupplier = EntityType.SUPPLIER.equals(entityType);
					if (!isSupplier && (parentIdentifier == null || parentIdentifier.isBlank())) {
						String errMsg = String.format(
								"Entity '%s' of type '%s' requires a valid 'parentId' or 'parentName'. Row declined.",
								entityId, entityType);
						log.warn("CSV Line {}: {}", rowNumber, errMsg);
						errorList.add(new BatchUploadResponse.RowError(rowNumber, entityId, entityType.name(), errMsg));
						continue;
					}

					switch (entityType) {
					case SUPPLIER -> {
						if (entityId != null && !entityId.isBlank())
							supplierUpdateRows.add(record);
						else
							supplierCreateRows.add(record);
					}
					case RAW_MATERIAL -> {
						if (entityId != null && !entityId.isBlank())
							rawMaterialUpdateRows.add(record);
						else
							rawMaterialCreateRows.add(record);
					}
					case COMPONENT -> {
						if (entityId != null && !entityId.isBlank())
							componentUpdateRows.add(record);
						else
							componentCreateRows.add(record);
					}
					case SUB_ASSEMBLY -> {
						if (entityId != null && !entityId.isBlank())
							subAssemblyUpdateRows.add(record);
						else
							subAssemblyCreateRows.add(record);
					}
					case PRODUCT -> {
						if (entityId != null && !entityId.isBlank())
							productUpdateRows.add(record);
						else
							productCreateRows.add(record);
					}
					}
				}

				successCount = processSequentiallyInBulk(supplierCreateRows, supplierUpdateRows, rawMaterialCreateRows,
						rawMaterialUpdateRows, componentCreateRows, componentUpdateRows, subAssemblyCreateRows,
						subAssemblyUpdateRows, productCreateRows, productUpdateRows, errorList);

				batchResult.setTotalRows(rowNumber - 1);
				batchResult.setSuccessCount(successCount);
				batchResult.setFailureCount(errorList.size());
				batchResult.setErrors(errorList);
				batchResult.setMessage("CSV batch processing completed.");
				batchResult.setCode(HttpStatus.OK.value());
				batchResult.setSuccess(true);

				if (userId != null && !userId.isBlank()) {
					sseService.sendUploadResult(userId, batchResult);
				}

				return batchResult;

			} catch (Exception e) {
				log.error("Fatal exception during CSV processing execution: {}", e.getMessage());
				batchResult.setTotalRows(rowNumber - 1);
				batchResult.setSuccessCount(successCount);
				batchResult.setFailureCount((rowNumber - 1) - successCount);
				batchResult.setErrors(errorList);
				batchResult.setMessage("CSV upload processing error: " + e.getMessage());
				batchResult.setCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
				batchResult.setSuccess(false);

				if (userId != null && !userId.isBlank()) {
					sseService.sendUploadResult(userId, batchResult);
				}
				throw new RuntimeException("CSV upload processing error: " + e.getMessage(), e);
			}
		}, executorService);
	}

	@Transactional
	public int processSequentiallyInBulk(List<Map<String, String>> supplierCreateRows,
			List<Map<String, String>> supplierUpdateRows, List<Map<String, String>> rawMaterialCreateRows,
			List<Map<String, String>> rawMaterialUpdateRows, List<Map<String, String>> componentCreateRows,
			List<Map<String, String>> componentUpdateRows, List<Map<String, String>> subAssemblyCreateRows,
			List<Map<String, String>> subAssemblyUpdateRows, List<Map<String, String>> productCreateRows,
			List<Map<String, String>> productUpdateRows, List<BatchUploadResponse.RowError> errorList) {

		Map<String, Object> parentLookupMap = new HashMap<>();

		// 1. SUPPLIERS
		if (!supplierCreateRows.isEmpty()) {
			List<SupplierNode> entities = buildSupplierNodes(supplierCreateRows, errorList);
			if (!entities.isEmpty()) {
				List<SupplierNode> saved = supplierService.saveAll(entities);
				populateLookupMap(saved, parentLookupMap);
				successCount += saved.size();
			}
		}
		if (!supplierUpdateRows.isEmpty()) {
			List<SupplierNode> entities = buildSupplierNodes(supplierUpdateRows, errorList);
			if (!entities.isEmpty()) {
				List<SupplierNode> updated = supplierService.updateAll(entities);
				populateLookupMap(updated, parentLookupMap);
				successCount += updated.size();
			}
		}

		// 2. RAW MATERIALS
		List<Map<String, String>> allRawMaterialRows = new ArrayList<>();
		allRawMaterialRows.addAll(rawMaterialCreateRows);
		allRawMaterialRows.addAll(rawMaterialUpdateRows);
		resolveMissingParents(allRawMaterialRows, parentLookupMap, supplierService::findAllByIds,
				supplierService::findAllByNames);

		if (!rawMaterialCreateRows.isEmpty()) {
			List<RawMaterialNode> entities = buildRawMaterialNodes(rawMaterialCreateRows, errorList, parentLookupMap);
			if (!entities.isEmpty()) {
				List<RawMaterialNode> saved = rawMaterialService.saveAll(entities);
				populateLookupMap(saved, parentLookupMap);
				successCount += saved.size();
			}
		}
		if (!rawMaterialUpdateRows.isEmpty()) {
			List<RawMaterialNode> entities = buildRawMaterialNodes(rawMaterialUpdateRows, errorList, parentLookupMap);
			if (!entities.isEmpty()) {
				List<RawMaterialNode> updated = rawMaterialService.updateAll(entities);
				populateLookupMap(updated, parentLookupMap);
				successCount += updated.size();
			}
		}

		// 3. COMPONENTS
		List<Map<String, String>> allComponentRows = new ArrayList<>();
		allComponentRows.addAll(componentCreateRows);
		allComponentRows.addAll(componentUpdateRows);
		resolveMissingParents(allComponentRows, parentLookupMap, rawMaterialService::findAllByIds,
				rawMaterialService::findAllByNames);

		if (!componentCreateRows.isEmpty()) {
			List<ComponentNode> entities = buildComponentNodes(componentCreateRows, errorList, parentLookupMap);
			if (!entities.isEmpty()) {
				List<ComponentNode> saved = componentService.saveAll(entities);
				populateLookupMap(saved, parentLookupMap);
				successCount += saved.size();
			}
		}
		if (!componentUpdateRows.isEmpty()) {
			List<ComponentNode> entities = buildComponentNodes(componentUpdateRows, errorList, parentLookupMap);
			if (!entities.isEmpty()) {
				List<ComponentNode> updated = componentService.updateAll(entities);
				populateLookupMap(updated, parentLookupMap);
				successCount += updated.size();
			}
		}

		// 4. SUB-ASSEMBLIES
		List<Map<String, String>> allSubAssemblyRows = new ArrayList<>();
		allSubAssemblyRows.addAll(subAssemblyCreateRows);
		allSubAssemblyRows.addAll(subAssemblyUpdateRows);
		resolveMissingParents(allSubAssemblyRows, parentLookupMap, componentService::findAllByIds,
				componentService::findAllByNames);

		if (!subAssemblyCreateRows.isEmpty()) {
			List<SubAssemblyNode> entities = buildSubAssemblyNodes(subAssemblyCreateRows, errorList, parentLookupMap);
			if (!entities.isEmpty()) {
				List<SubAssemblyNode> saved = subAssemblyService.saveAll(entities);
				populateLookupMap(saved, parentLookupMap);
				successCount += saved.size();
			}
		}
		if (!subAssemblyUpdateRows.isEmpty()) {
			List<SubAssemblyNode> entities = buildSubAssemblyNodes(subAssemblyUpdateRows, errorList, parentLookupMap);
			if (!entities.isEmpty()) {
				List<SubAssemblyNode> updated = subAssemblyService.updateAll(entities);
				populateLookupMap(updated, parentLookupMap);
				successCount += updated.size();
			}
		}

		// 5. PRODUCTS
		List<Map<String, String>> allProductRows = new ArrayList<>();
		allProductRows.addAll(productCreateRows);
		allProductRows.addAll(productUpdateRows);
		resolveMissingParents(allProductRows, parentLookupMap, subAssemblyService::findAllByIds,
				subAssemblyService::findAllByNames);

		if (!productCreateRows.isEmpty()) {
			List<ProductNode> entities = buildProductNodes(productCreateRows, errorList, parentLookupMap);
			if (!entities.isEmpty()) {
				List<ProductNode> saved = productService.saveAll(entities);
				successCount += saved.size();
			}
		}
		if (!productUpdateRows.isEmpty()) {
			List<ProductNode> entities = buildProductNodes(productUpdateRows, errorList, parentLookupMap);
			if (!entities.isEmpty()) {
				List<ProductNode> updated = productService.updateAll(entities);
				successCount += updated.size();
			}
		}

		return successCount;
	}

	private <T> void resolveMissingParents(List<Map<String, String>> rows, Map<String, Object> lookupMap,
			Function<List<String>, List<T>> findByIdsFn, Function<List<String>, List<T>> findByNamesFn) {

		Set<String> missingIds = new HashSet<>();
		Set<String> missingNames = new HashSet<>();

		for (Map<String, String> row : rows) {
			String pId = row.get(PARENT_ID);
			String pName = row.get(PARENT_NAME);

			if (pId != null && !pId.isBlank() && !lookupMap.containsKey(pId)) {
				missingIds.add(pId);
			}
			if (pName != null && !pName.isBlank()) {
				String lowerName = pName.trim().toLowerCase();
				if (!lookupMap.containsKey(lowerName)) {
					missingNames.add(lowerName);
				}
			}
		}

		if (!missingIds.isEmpty()) {
			List<T> foundByIds = findByIdsFn.apply(new ArrayList<>(missingIds));
			populateLookupMap(foundByIds, lookupMap);
		}

		if (!missingNames.isEmpty()) {
			List<T> foundByNames = findByNamesFn.apply(new ArrayList<>(missingNames));
			populateLookupMap(foundByNames, lookupMap);
		}
	}

	private <T> void populateLookupMap(List<T> entities, Map<String, Object> lookupMap) {
		for (T entity : entities) {
			if (entity instanceof SupplierNode s) {
				lookupMap.put(s.getId(), s);
				if (s.getName() != null) {
					lookupMap.put(s.getName().trim().toLowerCase(), s);
				}
			} else if (entity instanceof RawMaterialNode rm) {
				lookupMap.put(rm.getId(), rm);
				if (rm.getName() != null) {
					lookupMap.put(rm.getName().trim().toLowerCase(), rm);
				}
			} else if (entity instanceof ComponentNode c) {
				lookupMap.put(c.getId(), c);
				if (c.getName() != null) {
					lookupMap.put(c.getName().trim().toLowerCase(), c);
				}
			} else if (entity instanceof SubAssemblyNode sa) {
				lookupMap.put(sa.getId(), sa);
				if (sa.getName() != null) {
					lookupMap.put(sa.getName().trim().toLowerCase(), sa);
				}
			}
		}
	}

	private List<SupplierNode> buildSupplierNodes(List<Map<String, String>> rows,
			List<BatchUploadResponse.RowError> errorList) {
		List<SupplierNode> nodes = new ArrayList<>();

		List<String> supplierIds = rows.stream()
				.map(row -> row.get(ID))
				.filter(id -> id != null && !id.isBlank())
				.distinct()
				.toList();

		List<String> supplierNamesLower = rows.stream()
				.map(row -> row.get(NAME))
				.filter(name -> name != null && !name.isBlank())
				.map(name -> name.trim().toLowerCase())
				.distinct()
				.toList();

		Set<String> existingIds = new HashSet<>();
		if (!supplierIds.isEmpty()) {
			existingIds = supplierService.findAllByIds(supplierIds).stream()
					.map(SupplierNode::getId)
					.collect(Collectors.toSet());
		}

		Set<String> existingNamesLower = new HashSet<>();
		if (!supplierNamesLower.isEmpty()) {
			existingNamesLower = supplierService.findAllByNames(supplierNamesLower).stream()
					.map(s -> s.getName().trim().toLowerCase())
					.collect(HashSet::new, Set::add, Set::addAll);
		}

		for (Map<String, String> row : rows) {
			int rowNum = Integer.parseInt(row.getOrDefault("rowNumber", "-1"));
			String id = row.get(ID);
			String name = row.get(NAME);
			String category = row.get(CATEGORY);

			if (!isValidStringInputs(name, category)) {
				errorList.add(new BatchUploadResponse.RowError(rowNum, id, EntityType.SUPPLIER.name(),
						"Invalid input parameters."));
				continue;
			}

			String lowerName = name.trim().toLowerCase();

			if (id != null && !id.isBlank() && existingIds.contains(id)) {
				log.warn("CSV Line {}: Skipping Supplier creation: ID '{}' already exists.", rowNum, id);
				errorList.add(new BatchUploadResponse.RowError(rowNum, id, EntityType.SUPPLIER.name(),
						"Supplier with ID '" + id + "' already exists."));
				continue;
			}

			if (existingNamesLower.contains(lowerName)) {
				log.warn("CSV Line {}: Skipping Supplier creation: Supplier with name '{}' already exists.", rowNum, name);
				errorList.add(new BatchUploadResponse.RowError(rowNum, id, EntityType.SUPPLIER.name(),
						"Supplier with name '" + name + "' already exists."));
				continue;
			}

			String targetId = (id != null && !id.isBlank()) ? id : UUID.randomUUID().toString();
			row.put(ID, targetId);

			existingIds.add(targetId);
			existingNamesLower.add(lowerName);

			nodes.add(SupplierNode.builder().id(targetId).name(name).category(category).build());
		}
		return nodes;
	}

	private List<RawMaterialNode> buildRawMaterialNodes(List<Map<String, String>> rows,
			List<BatchUploadResponse.RowError> errorList, Map<String, Object> lookupMap) {
		List<RawMaterialNode> nodes = new ArrayList<>();

		List<String> rawMaterialIds = rows.stream()
				.map(row -> row.get(ID))
				.filter(id -> id != null && !id.isBlank())
				.distinct()
				.toList();

		List<String> rawMaterialNamesLower = rows.stream()
				.map(row -> row.get(NAME))
				.filter(name -> name != null && !name.isBlank())
				.map(name -> name.trim().toLowerCase())
				.distinct()
				.toList();

		Set<String> existingIds = new HashSet<>();
		if (!rawMaterialIds.isEmpty()) {
			existingIds = rawMaterialService.findAllByIds(rawMaterialIds).stream()
					.map(RawMaterialNode::getId)
					.collect(Collectors.toSet());
		}

		Set<String> existingNamesLower = new HashSet<>();
		if (!rawMaterialNamesLower.isEmpty()) {
			existingNamesLower = rawMaterialService.findAllByNames(rawMaterialNamesLower).stream()
					.map(rm -> rm.getName().trim().toLowerCase())
					.collect(HashSet::new, Set::add, Set::addAll);
		}

		for (Map<String, String> row : rows) {
			int rowNum = Integer.parseInt(row.getOrDefault("rowNumber", "-1"));
			String id = row.get(ID);
			String name = row.get(NAME);
			String category = row.get(CATEGORY);
			String pId = row.get(PARENT_ID);
			String pName = row.get(PARENT_NAME);

			if (!isValidStringInputs(name, category)) {
				errorList.add(new BatchUploadResponse.RowError(rowNum, id, EntityType.RAW_MATERIAL.name(),
						"Invalid input parameters."));
				continue;
			}

			String lowerName = name.trim().toLowerCase();

			if (id != null && !id.isBlank() && existingIds.contains(id)) {
				log.warn("CSV Line {}: Skipping RawMaterial creation: ID '{}' already exists.", rowNum, id);
				errorList.add(new BatchUploadResponse.RowError(rowNum, id, EntityType.RAW_MATERIAL.name(),
						"RawMaterial with ID '" + id + "' already exists."));
				continue;
			}

			if (existingNamesLower.contains(lowerName)) {
				log.warn("CSV Line {}: Skipping RawMaterial creation: Name '{}' already exists.", rowNum, name);
				errorList.add(new BatchUploadResponse.RowError(rowNum, id, EntityType.RAW_MATERIAL.name(),
						"RawMaterial with name '" + name + "' already exists."));
				continue;
			}

			SupplierNode supplier = null;
			if (pId != null && !pId.isBlank()) {
				supplier = (SupplierNode) lookupMap.get(pId);
			}
			if (supplier == null && pName != null && !pName.isBlank()) {
				supplier = (SupplierNode) lookupMap.get(pName.trim().toLowerCase());
			}

			if (supplier == null) {
				log.warn("CSV Line {}: Parent Supplier reference missing for RawMaterial '{}'", rowNum, name);
				errorList.add(new BatchUploadResponse.RowError(rowNum, id, EntityType.RAW_MATERIAL.name(),
						"Parent Supplier reference missing."));
				continue;
			}

			String targetId = (id != null && !id.isBlank()) ? id : UUID.randomUUID().toString();
			row.put(ID, targetId);

			existingIds.add(targetId);
			existingNamesLower.add(lowerName);

			nodes.add(RawMaterialNode.builder().id(targetId).name(name).category(category).suppliers(Set.of(supplier)).build());
		}
		return nodes;
	}

	private List<ComponentNode> buildComponentNodes(List<Map<String, String>> rows,
			List<BatchUploadResponse.RowError> errorList, Map<String, Object> lookupMap) {
		List<ComponentNode> nodes = new ArrayList<>();

		List<String> componentIds = rows.stream()
				.map(row -> row.get(ID))
				.filter(id -> id != null && !id.isBlank())
				.distinct()
				.toList();

		List<String> componentNamesLower = rows.stream()
				.map(row -> row.get(NAME))
				.filter(name -> name != null && !name.isBlank())
				.map(name -> name.trim().toLowerCase())
				.distinct()
				.toList();

		Set<String> existingIds = new HashSet<>();
		if (!componentIds.isEmpty()) {
			existingIds = componentService.findAllByIds(componentIds).stream()
					.map(ComponentNode::getId)
					.collect(Collectors.toSet());
		}

		Set<String> existingNamesLower = new HashSet<>();
		if (!componentNamesLower.isEmpty()) {
			existingNamesLower = componentService.findAllByNames(componentNamesLower).stream()
					.map(c -> c.getName().trim().toLowerCase())
					.collect(HashSet::new, Set::add, Set::addAll);
		}

		for (Map<String, String> row : rows) {
			int rowNum = Integer.parseInt(row.getOrDefault("rowNumber", "-1"));
			String id = row.get(ID);
			String name = row.get(NAME);
			String category = row.get(CATEGORY);
			String pId = row.get(PARENT_ID);
			String pName = row.get(PARENT_NAME);

			if (!isValidStringInputs(name, category)) {
				errorList.add(new BatchUploadResponse.RowError(rowNum, id, EntityType.COMPONENT.name(),
						"Invalid input parameters."));
				continue;
			}

			String lowerName = name.trim().toLowerCase();

			if (id != null && !id.isBlank() && existingIds.contains(id)) {
				log.warn("CSV Line {}: Skipping Component creation: ID '{}' already exists.", rowNum, id);
				errorList.add(new BatchUploadResponse.RowError(rowNum, id, EntityType.COMPONENT.name(),
						"Component with ID '" + id + "' already exists."));
				continue;
			}

			if (existingNamesLower.contains(lowerName)) {
				log.warn("CSV Line {}: Skipping Component creation: Name '{}' already exists.", rowNum, name);
				errorList.add(new BatchUploadResponse.RowError(rowNum, id, EntityType.COMPONENT.name(),
						"Component with name '" + name + "' already exists."));
				continue;
			}

			RawMaterialNode rawMaterial = null;
			if (pId != null && !pId.isBlank()) {
				rawMaterial = (RawMaterialNode) lookupMap.get(pId);
			}
			if (rawMaterial == null && pName != null && !pName.isBlank()) {
				rawMaterial = (RawMaterialNode) lookupMap.get(pName.trim().toLowerCase());
			}

			if (rawMaterial == null) {
				log.warn("CSV Line {}: Parent RawMaterial reference missing for Component '{}'", rowNum, name);
				errorList.add(new BatchUploadResponse.RowError(rowNum, id, EntityType.COMPONENT.name(),
						"Parent RawMaterial reference missing."));
				continue;
			}

			String targetId = (id != null && !id.isBlank()) ? id : UUID.randomUUID().toString();
			row.put(ID, targetId);

			existingIds.add(targetId);
			existingNamesLower.add(lowerName);

			nodes.add(ComponentNode.builder().id(targetId).name(name).category(category).rawMaterials(Set.of(rawMaterial)).build());
		}
		return nodes;
	}

	private List<SubAssemblyNode> buildSubAssemblyNodes(List<Map<String, String>> rows,
			List<BatchUploadResponse.RowError> errorList, Map<String, Object> lookupMap) {
		List<SubAssemblyNode> nodes = new ArrayList<>();

		List<String> subAssemblyIds = rows.stream()
				.map(row -> row.get(ID))
				.filter(id -> id != null && !id.isBlank())
				.distinct()
				.toList();

		List<String> subAssemblyNamesLower = rows.stream()
				.map(row -> row.get(NAME))
				.filter(name -> name != null && !name.isBlank())
				.map(name -> name.trim().toLowerCase())
				.distinct()
				.toList();

		Set<String> existingIds = new HashSet<>();
		if (!subAssemblyIds.isEmpty()) {
			existingIds = subAssemblyService.findAllByIds(subAssemblyIds).stream()
					.map(SubAssemblyNode::getId)
					.collect(Collectors.toSet());
		}

		Set<String> existingNamesLower = new HashSet<>();
		if (!subAssemblyNamesLower.isEmpty()) {
			existingNamesLower = subAssemblyService.findAllByNames(subAssemblyNamesLower).stream()
					.map(sa -> sa.getName().trim().toLowerCase())
					.collect(HashSet::new, Set::add, Set::addAll);
		}

		for (Map<String, String> row : rows) {
			int rowNum = Integer.parseInt(row.getOrDefault("rowNumber", "-1"));
			String id = row.get(ID);
			String name = row.get(NAME);
			String category = row.get(CATEGORY);
			String pId = row.get(PARENT_ID);
			String pName = row.get(PARENT_NAME);

			if (!isValidStringInputs(name, category)) {
				errorList.add(new BatchUploadResponse.RowError(rowNum, id, EntityType.SUB_ASSEMBLY.name(),
						"Invalid input parameters."));
				continue;
			}

			String lowerName = name.trim().toLowerCase();

			if (id != null && !id.isBlank() && existingIds.contains(id)) {
				log.warn("CSV Line {}: Skipping SubAssembly creation: ID '{}' already exists.", rowNum, id);
				errorList.add(new BatchUploadResponse.RowError(rowNum, id, EntityType.SUB_ASSEMBLY.name(),
						"SubAssembly with ID '" + id + "' already exists."));
				continue;
			}

			if (existingNamesLower.contains(lowerName)) {
				log.warn("CSV Line {}: Skipping SubAssembly creation: Name '{}' already exists.", rowNum, name);
				errorList.add(new BatchUploadResponse.RowError(rowNum, id, EntityType.SUB_ASSEMBLY.name(),
						"SubAssembly with name '" + name + "' already exists."));
				continue;
			}

			ComponentNode component = null;
			if (pId != null && !pId.isBlank()) {
				component = (ComponentNode) lookupMap.get(pId);
			}
			if (component == null && pName != null && !pName.isBlank()) {
				component = (ComponentNode) lookupMap.get(pName.trim().toLowerCase());
			}

			if (component == null) {
				log.warn("CSV Line {}: Parent Component reference missing for SubAssembly '{}'", rowNum, name);
				errorList.add(new BatchUploadResponse.RowError(rowNum, id, EntityType.SUB_ASSEMBLY.name(),
						"Parent Component reference missing."));
				continue;
			}

			String targetId = (id != null && !id.isBlank()) ? id : UUID.randomUUID().toString();
			row.put(ID, targetId);

			existingIds.add(targetId);
			existingNamesLower.add(lowerName);

			nodes.add(SubAssemblyNode.builder().id(targetId).name(name).category(category).components(Set.of(component)).build());
		}
		return nodes;
	}

	private List<ProductNode> buildProductNodes(List<Map<String, String>> rows,
			List<BatchUploadResponse.RowError> errorList, Map<String, Object> lookupMap) {
		List<ProductNode> nodes = new ArrayList<>();

		List<String> productIds = rows.stream()
				.map(row -> row.get(ID))
				.filter(id -> id != null && !id.isBlank())
				.distinct()
				.toList();

		List<String> productNamesLower = rows.stream()
				.map(row -> row.get(NAME))
				.filter(name -> name != null && !name.isBlank())
				.map(name -> name.trim().toLowerCase())
				.distinct()
				.toList();

		Set<String> existingIds = new HashSet<>();
		if (!productIds.isEmpty()) {
			existingIds = productService.findAllByIds(productIds).stream()
					.map(ProductNode::getId)
					.collect(Collectors.toSet());
		}

		Set<String> existingNamesLower = new HashSet<>();
		if (!productNamesLower.isEmpty()) {
			existingNamesLower = productService.findAllByNames(productNamesLower).stream()
					.map(p -> p.getName().trim().toLowerCase())
					.collect(HashSet::new, Set::add, Set::addAll);
		}

		for (Map<String, String> row : rows) {
			int rowNum = Integer.parseInt(row.getOrDefault("rowNumber", "-1"));
			String id = row.get(ID);
			String name = row.get(NAME);
			String category = row.get(CATEGORY);
			String pId = row.get(PARENT_ID);
			String pName = row.get(PARENT_NAME);

			if (!isValidStringInputs(name, category)) {
				errorList.add(new BatchUploadResponse.RowError(rowNum, id, EntityType.PRODUCT.name(),
						"Invalid input parameters."));
				continue;
			}

			String lowerName = name.trim().toLowerCase();

			if (id != null && !id.isBlank() && existingIds.contains(id)) {
				log.warn("CSV Line {}: Skipping Product creation: ID '{}' already exists.", rowNum, id);
				errorList.add(new BatchUploadResponse.RowError(rowNum, id, EntityType.PRODUCT.name(),
						"Product with ID '" + id + "' already exists."));
				continue;
			}

			if (existingNamesLower.contains(lowerName)) {
				log.warn("CSV Line {}: Skipping Product creation: Name '{}' already exists.", rowNum, name);
				errorList.add(new BatchUploadResponse.RowError(rowNum, id, EntityType.PRODUCT.name(),
						"Product with name '" + name + "' already exists."));
				continue;
			}

			SubAssemblyNode subAssembly = null;
			if (pId != null && !pId.isBlank()) {
				subAssembly = (SubAssemblyNode) lookupMap.get(pId);
			}
			if (subAssembly == null && pName != null && !pName.isBlank()) {
				subAssembly = (SubAssemblyNode) lookupMap.get(pName.trim().toLowerCase());
			}

			if (subAssembly == null) {
				log.warn("CSV Line {}: Parent SubAssembly reference missing for Product '{}'", rowNum, name);
				errorList.add(new BatchUploadResponse.RowError(rowNum, id, EntityType.PRODUCT.name(),
						"Parent SubAssembly reference missing."));
				continue;
			}

			String targetId = (id != null && !id.isBlank()) ? id : UUID.randomUUID().toString();
			row.put(ID, targetId);

			existingIds.add(targetId);
			existingNamesLower.add(lowerName);

			nodes.add(ProductNode.builder().id(targetId).name(name).category(category).subAssemblies(Set.of(subAssembly)).build());
		}
		return nodes;
	}

	@Transactional
	public ResponseEntity<ApiResponse> createEntity(NodeEntityRequest request) {
		if (request == null) {
			log.warn("Create failed: Request payload is null.");
			return buildFailureResponse("Request payload is null.", HttpStatus.BAD_REQUEST);
		}
		if (!isValidStringInputs(request.getName(), request.getCategory())) {
			log.warn("Create failed: Invalid string inputs for name='{}', category='{}'.", request.getName(),
					request.getCategory());
			return buildFailureResponse("Invalid string inputs for name or category.", HttpStatus.BAD_REQUEST);
		}
		if (request.getEntityType() == null || request.getEntityType().isBlank()) {
			log.warn("Create failed: entityType is null or blank.");
			return buildFailureResponse("entityType is null or blank.", HttpStatus.BAD_REQUEST);
		}

		EntityType entityType = EntityType.fromString(request.getEntityType());

		return switch (entityType) {
		case SUPPLIER -> {
			SupplierNode existingSupplier = supplierService.findByName(request.getName());
			if (existingSupplier != null) {
				log.warn("Create Supplier failed: Supplier with name '{}' already exists.", request.getName());
				yield buildFailureResponse("Supplier with name '" + request.getName() + "' already exists.", HttpStatus.CONFLICT);
			}

			SupplierNode entity = SupplierNode.builder().id(UUID.randomUUID().toString()).name(request.getName())
					.category(request.getCategory()).build();

			SupplierNode savedSupplier = supplierService.save(entity);
			yield buildSuccessResponse(savedSupplier.getId(), request.getName(), request.getCategory(),
					request.getEntityType(), "Supplier created successfully", HttpStatus.CREATED);
		}

		case RAW_MATERIAL -> {
			if (request.getParentNodeDto() == null || request.getParentNodeDto().isEmpty()) {
				log.warn("Create RawMaterial failed: Missing parent supplier information.");
				yield buildFailureResponse("Missing parent supplier information.", HttpStatus.BAD_REQUEST);
			}
			
			SupplierNode existingSupplier = supplierService.findById(request.getParentNodeDto().getFirst().getParentId()).orElse(null);
			if (existingSupplier == null) {
				log.warn("Supplier with id '{}' does not exist.", request.getParentNodeDto().getFirst().getParentId());
				yield buildFailureResponse("Parent supplier does not exist.", HttpStatus.NOT_FOUND);
			}
			
			RawMaterialNode existingRawMaterial = rawMaterialService.findByName(request.getName());
			if (existingRawMaterial != null) {
				log.warn("Create RawMaterial failed: RawMaterial with name '{}' already exists.", request.getName());
				yield buildFailureResponse("RawMaterial with name '" + request.getName() + "' already exists.", HttpStatus.CONFLICT);
			}

			RawMaterialNode entity = RawMaterialNode.builder().id(UUID.randomUUID().toString()).name(request.getName())
					.category(request.getCategory()).suppliers(Set.of(existingSupplier)).build();

			RawMaterialNode savedRawMaterial = rawMaterialService.save(entity);

			yield buildSuccessResponse(savedRawMaterial.getId(), request.getName(), request.getCategory(),
					request.getEntityType(), "RawMaterial created successfully", HttpStatus.CREATED);
		}

		case COMPONENT -> {
			if (request.getParentNodeDto() == null || request.getParentNodeDto().isEmpty()) {
				log.warn("Create Component failed: Missing parent raw material information.");
				yield buildFailureResponse("Missing parent raw material information.", HttpStatus.BAD_REQUEST);
			}

			RawMaterialNode existingRawMaterial = rawMaterialService.findById(request.getParentNodeDto().getFirst().getParentId()).orElse(null);
			if (existingRawMaterial == null) {
				log.warn("RawMaterial with id '{}' does not exist.", request.getParentNodeDto().getFirst().getParentId());
				yield buildFailureResponse("Parent raw material does not exist.", HttpStatus.NOT_FOUND);
			}

			ComponentNode existingComponent = componentService.findByName(request.getName());
			if (existingComponent != null) {
				log.warn("Create Component failed: Component with name '{}' already exists.", request.getName());
				yield buildFailureResponse("Component with name '" + request.getName() + "' already exists.", HttpStatus.CONFLICT);
			}

			ComponentNode entity = ComponentNode.builder().id(UUID.randomUUID().toString()).name(request.getName())
					.category(request.getCategory()).rawMaterials(Set.of(existingRawMaterial)).build();

			ComponentNode savedComponent = componentService.save(entity);

			yield buildSuccessResponse(savedComponent.getId(), request.getName(), request.getCategory(),
					request.getEntityType(), "Component created successfully", HttpStatus.CREATED);
		}

		case SUB_ASSEMBLY -> {
			if (request.getParentNodeDto() == null || request.getParentNodeDto().isEmpty()) {
				log.warn("Create SubAssembly failed: Missing parent component information.");
				yield buildFailureResponse("Missing parent component information.", HttpStatus.BAD_REQUEST);
			}

			ComponentNode existingComponent = componentService.findById(request.getParentNodeDto().getFirst().getParentId()).orElse(null);
			if (existingComponent == null) {
				log.warn("Component with id '{}' does not exist.", request.getParentNodeDto().getFirst().getParentId());
				yield buildFailureResponse("Parent component does not exist.", HttpStatus.NOT_FOUND);
			}

			SubAssemblyNode existingSubAssembly = subAssemblyService.findByName(request.getName());
			if (existingSubAssembly != null) {
				log.warn("Create SubAssembly failed: SubAssembly with name '{}' already exists.", request.getName());
				yield buildFailureResponse("SubAssembly with name '" + request.getName() + "' already exists.", HttpStatus.CONFLICT);
			}

			SubAssemblyNode entity = SubAssemblyNode.builder().id(UUID.randomUUID().toString()).name(request.getName())
					.category(request.getCategory()).components(Set.of(existingComponent)).build();

			SubAssemblyNode savedSubAssembly = subAssemblyService.save(entity);

			yield buildSuccessResponse(savedSubAssembly.getId(), request.getName(), request.getCategory(),
					request.getEntityType(), "SubAssembly created successfully", HttpStatus.CREATED);
		}

		case PRODUCT -> {
			if (request.getParentNodeDto() == null || request.getParentNodeDto().isEmpty()) {
				log.warn("Create Product failed: Missing parent sub-assembly information.");
				yield buildFailureResponse("Missing parent sub-assembly information.", HttpStatus.BAD_REQUEST);
			}

			SubAssemblyNode existingSubAssembly = subAssemblyService.findById(request.getParentNodeDto().getFirst().getParentId()).orElse(null);
			if (existingSubAssembly == null) {
				log.warn("SubAssembly with id '{}' does not exist.", request.getParentNodeDto().getFirst().getParentId());
				yield buildFailureResponse("Parent sub-assembly does not exist.", HttpStatus.NOT_FOUND);
			}

			ProductNode existingProduct = productService.findByName(request.getName());
			if (existingProduct != null) {
				log.warn("Create Product failed: Product with name '{}' already exists.", request.getName());
				yield buildFailureResponse("Product with name '" + request.getName() + "' already exists.", HttpStatus.CONFLICT);
			}

			ProductNode entity = ProductNode.builder().id(UUID.randomUUID().toString()).name(request.getName())
					.category(request.getCategory()).subAssemblies(Set.of(existingSubAssembly)).build();

			ProductNode savedProduct = productService.save(entity);

			yield buildSuccessResponse(savedProduct.getId(), request.getName(), request.getCategory(),
					request.getEntityType(), "Product created successfully", HttpStatus.CREATED);
		}
		};
	}

	@Transactional
	public ResponseEntity<ApiResponse> updateEntity(NodeEntityRequest request) {
		if (request == null || request.getId() == null || request.getId().isBlank()) {
			log.warn("Update failed: Request payload or request ID is null/blank.");
			return buildFailureResponse("Request payload or request ID is null/blank.", HttpStatus.BAD_REQUEST);
		}
		if (!isValidStringInputs(request.getName(), request.getCategory())) {
			log.warn("Update failed: Invalid string inputs.");
			return buildFailureResponse("Invalid string inputs for name or category.", HttpStatus.BAD_REQUEST);
		}

		EntityType entityType = EntityType.fromString(request.getEntityType());

		return switch (entityType) {
		case SUPPLIER -> {
			SupplierNode supplierNode = supplierService.findById(request.getId()).orElse(null);
			if (supplierNode == null) {
				log.warn("Update Supplier failed: Supplier with id '{}' not found.", request.getId());
				yield buildFailureResponse("Supplier with id '" + request.getId() + "' not found.", HttpStatus.NOT_FOUND);
			}

			SupplierNode existingNameSupplier = supplierService.findByName(request.getName());
			if (existingNameSupplier != null && !existingNameSupplier.getId().equals(request.getId())) {
				log.warn("Update Supplier failed: Name '{}' is already taken by another Supplier.", request.getName());
				yield buildFailureResponse("Supplier with name '" + request.getName() + "' already exists.", HttpStatus.CONFLICT);
			}

			supplierNode.setName(request.getName());
			supplierNode.setCategory(request.getCategory());
			SupplierNode updatedSupplier = supplierService.update(supplierNode);

			yield buildSuccessResponse(updatedSupplier.getId(), updatedSupplier.getName(),
					updatedSupplier.getCategory(), request.getEntityType(), "Supplier updated successfully",
					HttpStatus.OK);
		}

		case RAW_MATERIAL -> {
			if (request.getParentNodeDto() == null || request.getParentNodeDto().isEmpty()) {
				log.warn("Update RawMaterial failed: Missing parent supplier information.");
				yield buildFailureResponse("Missing parent supplier information.", HttpStatus.BAD_REQUEST);
			}

			RawMaterialNode rawMaterialNode = rawMaterialService.findById(request.getId()).orElse(null);
			if (rawMaterialNode == null) {
				log.warn("Update RawMaterial failed: RawMaterial with id '{}' not found.", request.getId());
				yield buildFailureResponse("RawMaterial with id '" + request.getId() + "' not found.", HttpStatus.NOT_FOUND);
			}

			RawMaterialNode existingNameRM = rawMaterialService.findByName(request.getName());
			if (existingNameRM != null && !existingNameRM.getId().equals(request.getId())) {
				log.warn("Update RawMaterial failed: Name '{}' is already taken by another RawMaterial.", request.getName());
				yield buildFailureResponse("RawMaterial with name '" + request.getName() + "' already exists.", HttpStatus.CONFLICT);
			}

			Set<SupplierNode> suppliers = rawMaterialNode.getSuppliers() != null 
					? new HashSet<>(rawMaterialNode.getSuppliers()) 
					: new HashSet<>();

			for (ParentNodeDto dto : request.getParentNodeDto()) {
				if (dto.getParentEdge() == null || dto.getParentEdge().isBlank()) continue;

				ParentEdge edge = ParentEdge.valueOf(dto.getParentEdge().toUpperCase());
				if (edge == ParentEdge.ALREADY_LINKED) continue;

				SupplierNode supplier = supplierService.findById(dto.getParentId()).orElse(null);
				if (supplier == null) {
					log.warn("Update RawMaterial: Parent Supplier with id '{}' not found, skipping.", dto.getParentId());
					continue;
				}

				if (edge == ParentEdge.ADD) {
					suppliers.add(supplier);
				} else if (edge == ParentEdge.REMOVE) {
					suppliers.removeIf(s -> s.getId().equals(supplier.getId()));
				}
			}

			if (suppliers.isEmpty()) {
				log.warn("Update RawMaterial failed: RawMaterial must have at least one Supplier.");
				yield buildFailureResponse("RawMaterial must have at least one Supplier.", HttpStatus.BAD_REQUEST);
			}

			rawMaterialNode.setSuppliers(suppliers);
			rawMaterialNode.setName(request.getName());
			rawMaterialNode.setCategory(request.getCategory());

			RawMaterialNode updatedRawMaterial = rawMaterialService.update(rawMaterialNode);

			yield buildSuccessResponse(updatedRawMaterial.getId(), updatedRawMaterial.getName(),
					updatedRawMaterial.getCategory(), request.getEntityType(), "RawMaterial updated successfully",
					HttpStatus.OK);
		}

		case COMPONENT -> {
			if (request.getParentNodeDto() == null || request.getParentNodeDto().isEmpty()) {
				log.warn("Update Component failed: Missing parent raw material information.");
				yield buildFailureResponse("Missing parent raw material information.", HttpStatus.BAD_REQUEST);
			}

			ComponentNode componentNode = componentService.findById(request.getId()).orElse(null);
			if (componentNode == null) {
				log.warn("Update Component failed: Component with id '{}' not found.", request.getId());
				yield buildFailureResponse("Component with id '" + request.getId() + "' not found.", HttpStatus.NOT_FOUND);
			}

			ComponentNode existingNameComponent = componentService.findByName(request.getName());
			if (existingNameComponent != null && !existingNameComponent.getId().equals(request.getId())) {
				log.warn("Update Component failed: Name '{}' is already taken by another Component.", request.getName());
				yield buildFailureResponse("Component with name '" + request.getName() + "' already exists.", HttpStatus.CONFLICT);
			}

			Set<RawMaterialNode> rawMaterials = componentNode.getRawMaterials() != null 
					? new HashSet<>(componentNode.getRawMaterials()) 
					: new HashSet<>();

			for (ParentNodeDto dto : request.getParentNodeDto()) {
				if (dto.getParentEdge() == null || dto.getParentEdge().isBlank()) continue;

				ParentEdge edge = ParentEdge.valueOf(dto.getParentEdge().toUpperCase());
				if (edge == ParentEdge.ALREADY_LINKED) continue;

				RawMaterialNode rawMaterial = rawMaterialService.findById(dto.getParentId()).orElse(null);
				if (rawMaterial == null) {
					log.warn("Update Component: Parent RawMaterial with id '{}' not found, skipping.", dto.getParentId());
					continue;
				}

				if (edge == ParentEdge.ADD) {
					rawMaterials.add(rawMaterial);
				} else if (edge == ParentEdge.REMOVE) {
					rawMaterials.removeIf(rm -> rm.getId().equals(rawMaterial.getId()));
				}
			}

			if (rawMaterials.isEmpty()) {
				log.warn("Update Component failed: Component must have at least one RawMaterial.");
				yield buildFailureResponse("Component must have at least one RawMaterial.", HttpStatus.BAD_REQUEST);
			}

			componentNode.setRawMaterials(rawMaterials);
			componentNode.setName(request.getName());
			componentNode.setCategory(request.getCategory());

			ComponentNode updatedComponent = componentService.update(componentNode);

			yield buildSuccessResponse(updatedComponent.getId(), updatedComponent.getName(),
					updatedComponent.getCategory(), request.getEntityType(), "Component updated successfully",
					HttpStatus.OK);
		}

		case SUB_ASSEMBLY -> {
			if (request.getParentNodeDto() == null || request.getParentNodeDto().isEmpty()) {
				log.warn("Update SubAssembly failed: Missing parent component information.");
				yield buildFailureResponse("Missing parent component information.", HttpStatus.BAD_REQUEST);
			}

			SubAssemblyNode subAssemblyNode = subAssemblyService.findById(request.getId()).orElse(null);
			if (subAssemblyNode == null) {
				log.warn("Update SubAssembly failed: SubAssembly with id '{}' not found.", request.getId());
				yield buildFailureResponse("SubAssembly with id '" + request.getId() + "' not found.", HttpStatus.NOT_FOUND);
			}

			SubAssemblyNode existingNameSubAssembly = subAssemblyService.findByName(request.getName());
			if (existingNameSubAssembly != null && !existingNameSubAssembly.getId().equals(request.getId())) {
				log.warn("Update SubAssembly failed: Name '{}' is already taken by another SubAssembly.", request.getName());
				yield buildFailureResponse("SubAssembly with name '" + request.getName() + "' already exists.", HttpStatus.CONFLICT);
			}
			
			Set<ComponentNode> components = subAssemblyNode.getComponents() != null 
					? new HashSet<>(subAssemblyNode.getComponents()) 
					: new HashSet<>();

			for (ParentNodeDto dto : request.getParentNodeDto()) {
				if (dto.getParentEdge() == null || dto.getParentEdge().isBlank()) continue;

				ParentEdge edge = ParentEdge.valueOf(dto.getParentEdge().toUpperCase());
				if (edge == ParentEdge.ALREADY_LINKED) continue;

				ComponentNode component = componentService.findById(dto.getParentId()).orElse(null);
				if (component == null) {
					log.warn("Update SubAssembly: Parent Component with id '{}' not found, skipping.", dto.getParentId());
					continue;
				}

				if (edge == ParentEdge.ADD) {
					components.add(component);
				} else if (edge == ParentEdge.REMOVE) {
					components.removeIf(c -> c.getId().equals(component.getId()));
				}
			}

			if (components.isEmpty()) {
				log.warn("Update SubAssembly failed: SubAssembly must have at least one Component.");
				yield buildFailureResponse("SubAssembly must have at least one Component.", HttpStatus.BAD_REQUEST);
			}
			
			subAssemblyNode.setComponents(components);
			subAssemblyNode.setName(request.getName());
			subAssemblyNode.setCategory(request.getCategory());

			SubAssemblyNode updatedSubAssembly = subAssemblyService.update(subAssemblyNode);
			yield buildSuccessResponse(updatedSubAssembly.getId(), updatedSubAssembly.getName(),
					updatedSubAssembly.getCategory(), request.getEntityType(), "SubAssembly updated successfully",
					HttpStatus.OK);
		}

		case PRODUCT -> {
			if (request.getParentNodeDto() == null || request.getParentNodeDto().isEmpty()) {
				log.warn("Update Product failed: Missing parent sub-assembly information.");
				yield buildFailureResponse("Missing parent sub-assembly information.", HttpStatus.BAD_REQUEST);
			}

			ProductNode productNode = productService.findById(request.getId()).orElse(null);
			if (productNode == null) {
				log.warn("Update Product failed: Product with id '{}' not found.", request.getId());
				yield buildFailureResponse("Product with id '" + request.getId() + "' not found.", HttpStatus.NOT_FOUND);
			}

			ProductNode existingNameProduct = productService.findByName(request.getName());
			if (existingNameProduct != null && !existingNameProduct.getId().equals(request.getId())) {
				log.warn("Update Product failed: Name '{}' is already taken by another Product.", request.getName());
				yield buildFailureResponse("Product with name '" + request.getName() + "' already exists.", HttpStatus.CONFLICT);
			}

			Set<SubAssemblyNode> subAssemblies = productNode.getSubAssemblies() != null 
					? new HashSet<>(productNode.getSubAssemblies()) 
					: new HashSet<>();

			for (ParentNodeDto dto : request.getParentNodeDto()) {
				if (dto.getParentEdge() == null || dto.getParentEdge().isBlank()) continue;

				ParentEdge edge = ParentEdge.valueOf(dto.getParentEdge().toUpperCase());
				if (edge == ParentEdge.ALREADY_LINKED) continue;

				SubAssemblyNode subAssembly = subAssemblyService.findById(dto.getParentId()).orElse(null);
				if (subAssembly == null) {
					log.warn("Update Product: Parent SubAssembly with id '{}' not found, skipping.", dto.getParentId());
					continue;
				}

				if (edge == ParentEdge.ADD) {
					subAssemblies.add(subAssembly);
				} else if (edge == ParentEdge.REMOVE) {
					subAssemblies.removeIf(sa -> sa.getId().equals(subAssembly.getId()));
				}
			}

			if (subAssemblies.isEmpty()) {
				log.warn("Update Product failed: Product must have at least one SubAssembly.");
				yield buildFailureResponse("Product must have at least one SubAssembly.", HttpStatus.BAD_REQUEST);
			}
			
			productNode.setSubAssemblies(subAssemblies);
			productNode.setName(request.getName());
			productNode.setCategory(request.getCategory());

			ProductNode updatedProduct = productService.update(productNode);

			yield buildSuccessResponse(updatedProduct.getId(), updatedProduct.getName(), updatedProduct.getCategory(),
					request.getEntityType(), "Product updated successfully", HttpStatus.OK);
		}
		};
	}

	@Transactional
	public void deleteAllGraphData() {
		log.info("Executing database wipe: Clearing all nodes and relationships.");
		supplierService.deleteEntireGraph();
		log.info("Database wipe completed successfully.");
	}
}