package com.cognodb.supplychain.controller;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.cognodb.supplychain.dto.ApiResponse;
import com.cognodb.supplychain.dto.NodeEntityRequest;
import com.cognodb.supplychain.dto.NodeEntityResponse;
import com.cognodb.supplychain.dto.SearchResponse;
import com.cognodb.supplychain.service.ComponentService;
import com.cognodb.supplychain.service.ProductService;
import com.cognodb.supplychain.service.RawMaterialService;
import com.cognodb.supplychain.service.SeedService;
import com.cognodb.supplychain.service.SseService;
import com.cognodb.supplychain.service.SubAssemblyService;
import com.cognodb.supplychain.service.SupplierService;
import com.cognodb.supplychain.utils.AppEnums.EntityType;

import jakarta.validation.Valid;

@RestController
@RequestMapping("${api.version.one}/seed")
@CrossOrigin(origins = "*")
public class SeedDataController {

	private static final Logger log = LoggerFactory.getLogger(SeedDataController.class);

	private final SupplierService supplierService;
	private final RawMaterialService rawMaterialService;
	private final ComponentService componentService;
	private final SubAssemblyService subAssemblyService;
	private final ProductService productService;
	private final SeedService seedService;
	private final SseService sseService;

	public SeedDataController(SupplierService supplierService, RawMaterialService rawMaterialService,
			ComponentService componentService, SubAssemblyService subAssemblyService, ProductService productService,
			SeedService seedService, SseService sseService) {
		this.supplierService = supplierService;
		this.rawMaterialService = rawMaterialService;
		this.componentService = componentService;
		this.subAssemblyService = subAssemblyService;
		this.productService = productService;
		this.seedService = seedService;
		this.sseService = sseService;
	}

	@GetMapping
	public ResponseEntity<ApiResponse> getAllEntities() {
		log.info("GET request received: Fetching all seeded graph entities with parent mapping.");

		List<NodeEntityResponse> allEntities = new ArrayList<>();

		allEntities.addAll(supplierService.findAllWithParents());
		allEntities.addAll(rawMaterialService.findAllWithParents());
		allEntities.addAll(componentService.findAllWithParents());
		allEntities.addAll(subAssemblyService.findAllWithParents());
		allEntities.addAll(productService.findAllWithParents());

		log.info("Successfully fetched {} total graph entities with parent relationships.", allEntities.size());

		ApiResponse response = ApiResponse.builder().message("Seeded entities retrieved successfully.")
				.code(HttpStatus.OK.value()).success(true).data(allEntities).build();

		return ResponseEntity.ok(response);
	}

	@GetMapping("/search")
	public ResponseEntity<ApiResponse> searchEntities(@RequestParam(defaultValue = "") String keyword,
			@RequestParam(required = false) String entityType) {

		log.info("GET search request received: keyword='{}', entityType='{}'", keyword, entityType);
		List<SearchResponse> results = new ArrayList<>();

		EntityType targetType = null;
		if (entityType != null && !entityType.isBlank()) {
			try {
				targetType = EntityType.fromString(entityType);
			} catch (Exception e) {
				log.warn("Search parameter parsing failed for entityType: '{}'", entityType);
				return buildError("Invalid entityType parameter: " + entityType, HttpStatus.BAD_REQUEST);
			}
		}

		if (targetType == null || EntityType.SUPPLIER.equals(targetType)) {
			supplierService.findByNameContaining(keyword)
					.forEach(s -> results.add(SearchResponse.builder().id(s.getId()).name(s.getName())
							.category(s.getCategory()).entityType(EntityType.SUPPLIER.name()).build()));
		}

		if (targetType == null || EntityType.RAW_MATERIAL.equals(targetType)) {
			rawMaterialService.findByNameContaining(keyword)
					.forEach(rm -> results.add(SearchResponse.builder().id(rm.getId()).name(rm.getName())
							.category(rm.getCategory()).entityType(EntityType.RAW_MATERIAL.name()).build()));
		}

		if (targetType == null || EntityType.COMPONENT.equals(targetType)) {
			componentService.findByNameContaining(keyword)
					.forEach(c -> results.add(SearchResponse.builder().id(c.getId()).name(c.getName())
							.category(c.getCategory()).entityType(EntityType.COMPONENT.name()).build()));
		}

		if (targetType == null || EntityType.SUB_ASSEMBLY.equals(targetType)) {
			subAssemblyService.findByNameContaining(keyword)
					.forEach(sa -> results.add(SearchResponse.builder().id(sa.getId()).name(sa.getName())
							.category(sa.getCategory()).entityType(EntityType.SUB_ASSEMBLY.name()).build()));
		}

		if (targetType == null || EntityType.PRODUCT.equals(targetType)) {
			productService.findByNameContaining(keyword).forEach(p -> results.add(SearchResponse.builder().id(p.getId())
					.name(p.getName()).category(p.getCategory()).entityType(EntityType.PRODUCT.name()).build()));
		}

		log.info("Search completed. Found {} matching items for query '{}'.", results.size(), keyword);

		ApiResponse response = ApiResponse.builder().message("Search results retrieved successfully.")
				.code(HttpStatus.OK.value()).success(true).data(results).build();

		return ResponseEntity.ok(response);
	}

	@PostMapping
	public ResponseEntity<ApiResponse> createEntity(@Valid @RequestBody NodeEntityRequest request) {
		log.info("POST request received to create entity: type='{}', name='{}'", request.getEntityType(),
				request.getName());

		return seedService.createEntity(request);

	}

	@PutMapping
	public ResponseEntity<ApiResponse> updateEntity(@Valid @RequestBody NodeEntityRequest request) {
		log.info("PUT request received to update entity: id='{}', type='{}'", request.getId(), request.getEntityType());

		if (request.getId() == null || request.getId().isBlank()) {
			log.warn("PUT entity update rejected: ID field is blank or missing.");
			return buildError("Entity ID is mandatory for update operations.", HttpStatus.BAD_REQUEST);
		}

		return seedService.updateEntity(request);
		
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<ApiResponse> deleteEntity(@PathVariable String id, @RequestParam String entityType) {
		log.info("DELETE cascade request received for entity ID: '{}' of type: '{}'", id, entityType);

		if (id == null || id.isBlank()) {
			log.warn("DELETE operation aborted: ID parameter is blank.");
			return buildError("Entity ID is required for delete operation.", HttpStatus.BAD_REQUEST);
		}

		if (entityType == null || entityType.isBlank()) {
			log.warn("DELETE operation aborted: entityType parameter is missing.");
			return buildError("entityType parameter is required for delete operation.", HttpStatus.BAD_REQUEST);
		}

		EntityType type;
		try {
			type = EntityType.fromString(entityType);
		} catch (Exception e) {
			log.warn("DELETE operation aborted: Unsupported entityType '{}'", entityType);
			return buildError("Unsupported entityType: " + entityType, HttpStatus.BAD_REQUEST);
		}

		boolean isDeleted = switch (type) {
		case SUPPLIER -> {
			if (!supplierService.existsById(id)) {
				log.warn("Supplier not found with ID '{}'", id);
				yield false;
			}
			log.info("Deleting Supplier node '{}' and safe-cleaning downstream supply tree...", id);
			yield supplierService.deleteById(id);
		}
		case RAW_MATERIAL -> {
			if (!rawMaterialService.existsById(id)) {
				log.warn("RawMaterial not found with ID '{}'", id);
				yield false;
			}
			log.info("Deleting RawMaterial node '{}' and safe-cleaning downstream component nodes...", id);
			yield rawMaterialService.deleteById(id);
		}
		case COMPONENT -> {
			if (!componentService.existsById(id)) {
				log.warn("Component not found with ID '{}'", id);
				yield false;
			}
			log.info("Deleting Component node '{}' and safe-cleaning downstream sub-assemblies...", id);
			yield componentService.deleteById(id);
		}
		case SUB_ASSEMBLY -> {
			if (!subAssemblyService.existsById(id)) {
				log.warn("SubAssembly not found with ID '{}'", id);
				yield false;
			}
			log.info("Deleting SubAssembly node '{}' and safe-cleaning downstream products...", id);
			yield subAssemblyService.deleteById(id);
		}
		case PRODUCT -> {
			if (!productService.existsById(id)) {
				log.warn("Product not found with ID '{}'", id);
				yield false;
			}
			log.info("Deleting terminal Product node '{}'...", id);
			yield productService.deleteById(id);
		}
		};

		if (!isDeleted) {
			log.warn("DELETE operation failed for node ID '{}' of type '{}'", id, entityType);
			return buildError("Could not delete node or connected hierarchy with ID: " + id,
					HttpStatus.INTERNAL_SERVER_ERROR);
		}

		log.info("Node with ID '{}' ({}) and orphan downstream tree were successfully deleted.", id, entityType);

		ApiResponse response = ApiResponse.builder()
				.message("Node and all unlinked downstream orphan nodes deleted successfully.")
				.code(HttpStatus.OK.value()).success(true).build();

		return ResponseEntity.ok(response);
	}

	@GetMapping("/subscribe/{userId}")
	public SseEmitter subscribeToNotifications(@PathVariable String userId) {
		log.info("SSE subscription request received for userId: '{}'", userId);
		return sseService.subscribe(userId);
	}

	@PostMapping(value = "/upload-future", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<ApiResponse> uploadCsvWithFuture(@RequestParam("file") MultipartFile file,
			@RequestParam("userId") String userId) throws IOException {

		log.info("POST async CSV upload received for userId: '{}', filename: '{}'", userId, file.getOriginalFilename());

		if (file.isEmpty()) {
			log.warn("CSV Upload failed: Uploaded multipart file is empty for userId: '{}'", userId);
			return buildError("Uploaded CSV file is empty", HttpStatus.BAD_REQUEST);
		}

		seedService.processCsvAsync(file.getInputStream(), userId).thenAccept(batchResult -> {
			log.info("Async CSV process completed for userId '{}'. Dispatching SSE notification...", userId);
			sseService.sendUploadResult(userId, batchResult);
		});

		ApiResponse response = ApiResponse.builder()
				.message("Batch upload started. You will receive a notification upon completion.")
				.code(HttpStatus.ACCEPTED.value()).success(true).build();

		return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
	}

	@DeleteMapping("/delete-all")
	public ResponseEntity<ApiResponse> deleteAllGraphData() {
		log.info("DELETE-ALL request received: Purging all graph nodes.");

		seedService.deleteAllGraphData();

		ApiResponse response = ApiResponse.builder()
				.message("All database graph nodes and relationships cleared successfully.").code(HttpStatus.OK.value())
				.success(true).build();

		return ResponseEntity.ok(response);
	}

	@PostMapping("/default")
	public ResponseEntity<ApiResponse> seedDefaultData(@RequestParam("userId") String userId) throws IOException {
		ClassPathResource resource = new ClassPathResource("supply_chain_graph.csv");

		seedService.processCsvAsync(resource.getInputStream(), userId).thenAccept(batchResult -> {
			log.info("Async CSV process completed for userId '{}'. Dispatching SSE notification...", userId);
			sseService.sendUploadResult(userId, batchResult);
		});

		ApiResponse response = ApiResponse.builder()
				.message("Batch upload started. You will receive a notification upon completion.")
				.code(HttpStatus.ACCEPTED.value()).success(true).build();

		return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
	}

	private ResponseEntity<ApiResponse> buildError(String message, HttpStatus status) {
		ApiResponse response = ApiResponse.builder().message(message).code(status.value()).success(false).build();
		return ResponseEntity.status(status).body(response);
	}
}