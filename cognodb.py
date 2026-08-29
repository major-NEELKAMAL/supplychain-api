import pandas as pd
import random

random.seed(42)

categories = {
    "SUPPLIER": "Vendor",
    "RAW_MATERIAL": "Primary Resource",
    "COMPONENT": "Intermediate Part",
    "SUB_ASSEMBLY": "Assembled Module",
    "PRODUCT": "Finished Goods"
}

data = []

# 1. Create guaranteed root suppliers
created_entities = {
    "SUPPLIER": [f"Supplier_{i:03d}" for i in range(1, 15)]
}

for sup in created_entities["SUPPLIER"]:
    data.append({
        "id": "",
        "entityType": "SUPPLIER",
        "name": sup,
        "category": categories["SUPPLIER"],
        "parentId": "",
        "parentName": ""
    })

# 2. Build downstream levels linked strictly to previously created parent entities
levels = [
    ("RAW_MATERIAL", "SUPPLIER", 100, "RawMaterial"),
    ("COMPONENT", "RAW_MATERIAL", 200, "Component"),
    ("SUB_ASSEMBLY", "COMPONENT", 300, "SubAssembly"),
    ("PRODUCT", "SUB_ASSEMBLY", 386, "Product")  # Totals exactly 1,000 entries
]

for current_type, parent_type, count, prefix in levels:
    created_entities[current_type] = []
    for i in range(1, count + 1):
        entity_name = f"{prefix}_{i:04d}"
        parent_name = random.choice(created_entities[parent_type])
        
        created_entities[current_type].append(entity_name)
        data.append({
            "id": "",
            "entityType": current_type,
            "name": entity_name,
            "category": categories[current_type],
            "parentId": "",
            "parentName": parent_name
        })

df = pd.DataFrame(data)
df.to_csv("supply_chain_graph.csv", index=False)
print(f"Generated {len(df)} rows with strict top-down parent linkage.")
