package com.cognodb.supplychain.utils;

public final class AppEnums {

	private AppEnums() {
	}

	public enum ParentEdge {
		ADD, REMOVE, ALREADY_LINKED
	}

	public enum EntityType {
		SUPPLIER, RAW_MATERIAL, COMPONENT, SUB_ASSEMBLY, PRODUCT;

		public static EntityType fromString(String value) {
			if (value == null)
				return null;

			String normalized = value.replaceAll("[_\\s]", "").toUpperCase();

			for (EntityType type : EntityType.values()) {
				if (type.name().replace("_", "").equals(normalized)) {
					return type;
				}
			}

			throw new IllegalArgumentException("Unknown EntityType: " + value);
		}
	}
}
