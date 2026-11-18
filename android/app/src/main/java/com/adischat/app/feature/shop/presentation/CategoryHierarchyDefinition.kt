package com.adischat.app.feature.shop.presentation

import com.adischat.app.feature.shop.data.ShopCategoryAttributeDto
import com.adischat.app.feature.shop.data.ShopCategoryDto

/**
 * Authoritative AdisGebeya marketplace category hierarchy and product-specific attribute definitions.
 * Provides hierarchical category resolution (Main -> Department -> Product Type -> Subtype),
 * cross-department mapping, safe database fallback IDs, and descendant filtering for buyers.
 */
object CategoryHierarchyDefinition {

    // Authoritative Root Categories
    const val FASHION_ROOT_ID = "00000000-0000-4000-8000-000000000102"
    const val SHOES_ROOT_ID = "586ab99f-66fc-4c03-8464-ee2f902eccdd"
    const val ELECTRONICS_ROOT_ID = "00000000-0000-4000-8000-000000000101"
    const val HOME_ROOT_ID = "00000000-0000-4000-8000-000000000103"
    const val BEAUTY_ROOT_ID = "00000000-0000-4000-8000-000000000104"

    // Existing Database Production Category IDs
    const val MENS_CLOTHING_ID = "679f286d-3bdb-48ea-8a0f-593fdca66c50"
    const val WOMENS_CLOTHING_ID = "c185bdc9-f022-44de-8fa7-6158e177b070"
    const val KIDS_CLOTHING_ID = "89e3a6a1-0e42-4916-ba6c-2fdf9a667ebf"

    const val JACKETS_ID = "db8c93f5-e3a0-46eb-8778-713d4c9b23c3"
    const val SHIRTS_ID = "4b75f4e5-3b88-46e2-8069-33082443228c"
    const val T_SHIRTS_ID = "68f28559-2640-4b65-84d8-e36532c82931"
    const val TROUSERS_ID = "7a56e0f2-2b60-4543-8a52-512e969cc253"
    const val DRESSES_ID = "9035a12e-ad49-455f-82b8-9ffaf02655ca"
    const val TRADITIONAL_CLOTHING_ID = "46d88c99-1383-42e7-9d62-f1bf4bb48999"
    const val UNIFORMS_ID = "ec1e2cfc-eefb-4fe7-9159-ca7788470a1a"
    const val UNDERWEAR_ID = "1cfca35f-ea5a-4318-ae20-e4b2d197df31"
    const val SPORTSWEAR_ID = "2cb7f7fb-e93d-4c3c-8f4d-176c4ca82057"

    const val MENS_SHOES_ID = "b9b2956d-425b-4049-850e-9b2a88125acc"
    const val WOMENS_SHOES_ID = "08b0d893-223c-42c5-86cd-1e01e07bc82b"
    const val KIDS_SHOES_ID = "2ba3dd18-474b-4544-8ea1-c1b3a3e97279"
    const val SPORTS_SHOES_ID = "83af6f95-e949-489e-8a11-f6d68602e1b1"

    const val PHONES_ID = "3884fbf3-4ba9-425f-bc1d-bcaecba49c30"
    const val TABLETS_ID = "ee5530a0-0e13-4dbf-a795-3b98c56fa769"
    const val LAPTOPS_ID = "aa487399-52e8-466c-bb92-c0e86b02660a"
    const val DESKTOPS_ID = "8c7eb220-410b-4ea5-802c-7b2fb5e5aa49"
    const val AUDIO_ID = "e72a81fa-5847-494b-9706-e3d64bc8c919"
    const val SMART_WATCHES_ID = "27493a7e-128a-442a-a035-7ce3c965c40d"
    const val TELEVISIONS_ID = "0c441b8c-f0cf-4e5a-a384-cb91dc201639"

    const val FURNITURE_ID = "97385dba-3aaf-4fcd-8bf7-6cd7f54e8e6f"
    const val BEDDING_ID = "5abfc134-cb63-4006-83a4-bf8940695771"
    const val KITCHENWARE_ID = "b11566aa-30ca-4702-8a90-336c1e55ff67"
    const val HOME_DECOR_ID = "cb213197-0f81-4b10-863a-bb035c9115c5"

    // Canonical Subtype & Department IDs (Virtual / Extended)
    // Deterministic IDs ensure consistent client state and SQL migration compatibility
    const val VIRT_MEN_DEPT = "canonical-men-dept"
    const val VIRT_MEN_CLOTHING = "canonical-men-clothing"
    const val VIRT_MEN_SHOES = "canonical-men-shoes"
    const val VIRT_MEN_JEANS = "canonical-men-jeans"
    const val VIRT_MEN_SUITS = "canonical-men-suits"
    const val VIRT_MEN_SNEAKERS = "canonical-men-sneakers"
    const val VIRT_MEN_FORMAL_SHOES = "canonical-men-formal-shoes"
    const val VIRT_MEN_BOOTS = "canonical-men-boots"
    const val VIRT_MEN_SANDALS = "canonical-men-sandals"

    const val VIRT_WOMEN_DEPT = "canonical-women-dept"
    const val VIRT_WOMEN_CLOTHING = "canonical-women-clothing"
    const val VIRT_WOMEN_SHOES = "canonical-women-shoes"
    const val VIRT_WOMEN_JEANS = "canonical-women-jeans"
    const val VIRT_WOMEN_TOPS = "canonical-women-tops"
    const val VIRT_WOMEN_DRESSES = "canonical-women-dresses"
    const val VIRT_WOMEN_SKIRTS = "canonical-women-skirts"
    const val VIRT_WOMEN_HEELS = "canonical-women-heels"
    const val VIRT_WOMEN_FLATS = "canonical-women-flats"
    const val VIRT_WOMEN_SNEAKERS = "canonical-women-sneakers"
    const val VIRT_WOMEN_BOOTS = "canonical-women-boots"
    const val VIRT_WOMEN_SANDALS = "canonical-women-sandals"

    const val VIRT_KIDS_DEPT = "canonical-kids-dept"
    const val VIRT_KIDS_CLOTHING = "canonical-kids-clothing"
    const val VIRT_KIDS_SHOES = "canonical-kids-shoes"

    /**
     * Map of synthetic canonical IDs to their valid production PostgreSQL category IDs.
     * When saving products to Supabase, this mapping guarantees 100% foreign key safety.
     */
    val DB_FALLBACK_CATEGORY_MAP: Map<String, String> = mapOf(
        VIRT_MEN_DEPT to MENS_CLOTHING_ID,
        VIRT_MEN_CLOTHING to MENS_CLOTHING_ID,
        VIRT_MEN_SHOES to MENS_SHOES_ID,
        VIRT_MEN_JEANS to TROUSERS_ID,
        VIRT_MEN_SUITS to MENS_CLOTHING_ID,
        VIRT_MEN_SNEAKERS to MENS_SHOES_ID,
        VIRT_MEN_FORMAL_SHOES to MENS_SHOES_ID,
        VIRT_MEN_BOOTS to MENS_SHOES_ID,
        VIRT_MEN_SANDALS to MENS_SHOES_ID,

        VIRT_WOMEN_DEPT to WOMENS_CLOTHING_ID,
        VIRT_WOMEN_CLOTHING to WOMENS_CLOTHING_ID,
        VIRT_WOMEN_SHOES to WOMENS_SHOES_ID,
        VIRT_WOMEN_JEANS to TROUSERS_ID,
        VIRT_WOMEN_TOPS to SHIRTS_ID,
        VIRT_WOMEN_DRESSES to DRESSES_ID,
        VIRT_WOMEN_SKIRTS to WOMENS_CLOTHING_ID,
        VIRT_WOMEN_HEELS to WOMENS_SHOES_ID,
        VIRT_WOMEN_FLATS to WOMENS_SHOES_ID,
        VIRT_WOMEN_SNEAKERS to WOMENS_SHOES_ID,
        VIRT_WOMEN_BOOTS to WOMENS_SHOES_ID,
        VIRT_WOMEN_SANDALS to WOMENS_SHOES_ID,

        VIRT_KIDS_DEPT to KIDS_CLOTHING_ID,
        VIRT_KIDS_CLOTHING to KIDS_CLOTHING_ID,
        VIRT_KIDS_SHOES to KIDS_SHOES_ID,
    )

    /**
     * Resolves the category ID to save in the database.
     * If the ID is an existing DB category, it is preserved.
     * If it is a canonical virtual ID, it maps to the safe DB fallback.
     */
    fun resolveSaveableCategoryId(categoryId: String, categories: List<ShopCategoryDto>): String {
        val trimmed = categoryId.trim()
        if (categories.any { it.id == trimmed }) return trimmed
        return DB_FALLBACK_CATEGORY_MAP[trimmed] ?: trimmed
    }

    /**
     * Constructs the canonical hierarchy by augmenting the production categories.
     * Transforms flat categories into rich multi-tier navigation:
     * Main Category -> Department -> Product Type -> Subtype.
     */
    fun buildAuthoritativeHierarchy(categories: List<ShopCategoryDto>): List<ShopCategoryDto> {
        val byId = categories.associateBy { it.id }.toMutableMap()
        val result = categories.toMutableList()

        // 1. Convert Men / Women / Kids in Fashion into Department branches
        val fashionCategory = byId[FASHION_ROOT_ID]
        if (fashionCategory != null) {
            val menIndex = result.indexOfFirst { it.id == MENS_CLOTHING_ID }
            if (menIndex >= 0) {
                result[menIndex] = result[menIndex].copy(
                    name = "Men",
                    isLeaf = false,
                    sellerSelectable = false,
                    searchTerms = listOf("men", "mens", "male", "gentlemen"),
                )
            }
            val womenIndex = result.indexOfFirst { it.id == WOMENS_CLOTHING_ID }
            if (womenIndex >= 0) {
                result[womenIndex] = result[womenIndex].copy(
                    name = "Women",
                    isLeaf = false,
                    sellerSelectable = false,
                    searchTerms = listOf("women", "womens", "female", "ladies"),
                )
            }
            val kidsIndex = result.indexOfFirst { it.id == KIDS_CLOTHING_ID }
            if (kidsIndex >= 0) {
                result[kidsIndex] = result[kidsIndex].copy(
                    name = "Kids",
                    isLeaf = false,
                    sellerSelectable = false,
                    searchTerms = listOf("kids", "children", "boys", "girls"),
                )
            }

            // 2. Add Department Product Types & Subtypes under Men
            if (result.none { it.id == VIRT_MEN_CLOTHING }) {
                result.add(
                    ShopCategoryDto(
                        id = VIRT_MEN_CLOTHING,
                        name = "Men's Clothing",
                        slug = "mens-clothing-dept",
                        parentId = MENS_CLOTHING_ID,
                        displayOrder = 1,
                        isLeaf = false,
                        sellerSelectable = false,
                        buyerVisible = true,
                        code = "MENS_CLOTHING_DEPT",
                        searchTerms = listOf("men clothing", "mens apparel"),
                    )
                )
                result.add(
                    ShopCategoryDto(
                        id = VIRT_MEN_SHOES,
                        name = "Men's Shoes",
                        slug = "mens-shoes-dept",
                        parentId = MENS_CLOTHING_ID,
                        displayOrder = 2,
                        isLeaf = false,
                        sellerSelectable = false,
                        buyerVisible = true,
                        code = "MENS_SHOES_DEPT",
                        searchTerms = listOf("men shoes", "footwear", "sneakers", "boots"),
                    )
                )
            }

            // Men's Clothing Subtypes
            val menClothingItems = listOf(
                Triple(VIRT_MEN_JEANS, "Jeans", listOf("jeans", "denim", "pants")),
                Triple(VIRT_MEN_SUITS, "Suits & Blazers", listOf("suit", "blazer", "tuxedo", "formal")),
                Triple("men-shirts", "Shirts", listOf("shirt", "dress shirt", "button up")),
                Triple("men-t-shirts", "T-Shirts & Polos", listOf("t-shirt", "tee", "polo")),
                Triple("men-trousers", "Trousers & Chinos", listOf("trousers", "slacks", "pants", "chinos")),
                Triple("men-jackets", "Jackets & Coats", listOf("jacket", "coat", "hoodie", "outerwear")),
                Triple("men-traditional", "Traditional Clothing", listOf("habesha", "traditional", "kidan")),
                Triple("men-sportswear", "Sportswear & Gym", listOf("sportswear", "tracksuit", "activewear")),
                Triple("men-underwear", "Underwear & Socks", listOf("boxers", "briefs", "socks", "underwear")),
            )
            menClothingItems.forEachIndexed { idx, (id, name, terms) ->
                if (result.none { it.id == id }) {
                    val fallback = when (id) {
                        VIRT_MEN_JEANS -> TROUSERS_ID
                        VIRT_MEN_SUITS -> MENS_CLOTHING_ID
                        "men-shirts" -> SHIRTS_ID
                        "men-t-shirts" -> T_SHIRTS_ID
                        "men-trousers" -> TROUSERS_ID
                        "men-jackets" -> JACKETS_ID
                        "men-traditional" -> TRADITIONAL_CLOTHING_ID
                        "men-sportswear" -> SPORTSWEAR_ID
                        "men-underwear" -> UNDERWEAR_ID
                        else -> MENS_CLOTHING_ID
                    }
                    result.add(
                        ShopCategoryDto(
                            id = id,
                            name = name,
                            slug = id,
                            parentId = VIRT_MEN_CLOTHING,
                            displayOrder = idx + 1,
                            isLeaf = true,
                            sellerSelectable = true,
                            buyerVisible = true,
                            code = id.uppercase().replace("-", "_"),
                            searchTerms = terms,
                        )
                    )
                    if (id !in DB_FALLBACK_CATEGORY_MAP) {
                        (DB_FALLBACK_CATEGORY_MAP as MutableMap)[id] = fallback
                    }
                }
            }

            // Men's Shoes Subtypes
            val menShoeItems = listOf(
                Triple(VIRT_MEN_SNEAKERS, "Sneakers & Trainers", listOf("sneakers", "trainers", "kicks")),
                Triple(VIRT_MEN_FORMAL_SHOES, "Formal Shoes & Loafers", listOf("oxford", "derby", "loafer", "dress shoes")),
                Triple(VIRT_MEN_BOOTS, "Boots", listOf("boots", "ankle boots", "leather boots")),
                Triple(VIRT_MEN_SANDALS, "Sandals & Slippers", listOf("sandals", "slippers", "slides")),
            )
            menShoeItems.forEachIndexed { idx, (id, name, terms) ->
                if (result.none { it.id == id }) {
                    result.add(
                        ShopCategoryDto(
                            id = id,
                            name = name,
                            slug = id,
                            parentId = VIRT_MEN_SHOES,
                            displayOrder = idx + 1,
                            isLeaf = true,
                            sellerSelectable = true,
                            buyerVisible = true,
                            code = id.uppercase().replace("-", "_"),
                            searchTerms = terms,
                        )
                    )
                }
            }

            // 3. Add Department Product Types & Subtypes under Women
            if (result.none { it.id == VIRT_WOMEN_CLOTHING }) {
                result.add(
                    ShopCategoryDto(
                        id = VIRT_WOMEN_CLOTHING,
                        name = "Women's Clothing",
                        slug = "womens-clothing-dept",
                        parentId = WOMENS_CLOTHING_ID,
                        displayOrder = 1,
                        isLeaf = false,
                        sellerSelectable = false,
                        buyerVisible = true,
                        code = "WOMENS_CLOTHING_DEPT",
                        searchTerms = listOf("women clothing", "womens apparel", "ladies"),
                    )
                )
                result.add(
                    ShopCategoryDto(
                        id = VIRT_WOMEN_SHOES,
                        name = "Women's Shoes",
                        slug = "womens-shoes-dept",
                        parentId = WOMENS_CLOTHING_ID,
                        displayOrder = 2,
                        isLeaf = false,
                        sellerSelectable = false,
                        buyerVisible = true,
                        code = "WOMENS_SHOES_DEPT",
                        searchTerms = listOf("women shoes", "heels", "flats", "sandals"),
                    )
                )
            }

            // Women's Clothing Subtypes
            val womenClothingItems = listOf(
                Triple(VIRT_WOMEN_DRESSES, "Dresses", listOf("dress", "gown", "maxi", "mini", "evening")),
                Triple(VIRT_WOMEN_TOPS, "Tops & Blouses", listOf("top", "blouse", "shirt", "tunic")),
                Triple(VIRT_WOMEN_JEANS, "Jeans", listOf("jeans", "denim", "skinny jeans", "mom jeans")),
                Triple(VIRT_WOMEN_SKIRTS, "Skirts", listOf("skirt", "midi", "pleated", "pencil skirt")),
                Triple("women-t-shirts", "T-Shirts", listOf("t-shirt", "tee")),
                Triple("women-trousers", "Trousers & Pants", listOf("trousers", "pants", "leggings")),
                Triple("women-jackets", "Jackets & Coats", listOf("jacket", "coat", "cardigan", "blazer")),
                Triple("women-traditional", "Traditional Habesha Kemis", listOf("habesha kemis", "traditional", "kidan")),
                Triple("women-sportswear", "Sportswear & Activewear", listOf("activewear", "gym", "tights", "leggings")),
                Triple("women-underwear", "Lingerie & Underwear", listOf("bra", "underwear", "panties", "lingerie")),
            )
            womenClothingItems.forEachIndexed { idx, (id, name, terms) ->
                if (result.none { it.id == id }) {
                    val fallback = when (id) {
                        VIRT_WOMEN_DRESSES -> DRESSES_ID
                        VIRT_WOMEN_TOPS -> SHIRTS_ID
                        VIRT_WOMEN_JEANS -> TROUSERS_ID
                        VIRT_WOMEN_SKIRTS -> WOMENS_CLOTHING_ID
                        "women-t-shirts" -> T_SHIRTS_ID
                        "women-trousers" -> TROUSERS_ID
                        "women-jackets" -> JACKETS_ID
                        "women-traditional" -> TRADITIONAL_CLOTHING_ID
                        "women-sportswear" -> SPORTSWEAR_ID
                        "women-underwear" -> UNDERWEAR_ID
                        else -> WOMENS_CLOTHING_ID
                    }
                    result.add(
                        ShopCategoryDto(
                            id = id,
                            name = name,
                            slug = id,
                            parentId = VIRT_WOMEN_CLOTHING,
                            displayOrder = idx + 1,
                            isLeaf = true,
                            sellerSelectable = true,
                            buyerVisible = true,
                            code = id.uppercase().replace("-", "_"),
                            searchTerms = terms,
                        )
                    )
                    if (id !in DB_FALLBACK_CATEGORY_MAP) {
                        (DB_FALLBACK_CATEGORY_MAP as MutableMap)[id] = fallback
                    }
                }
            }

            // Women's Shoes Subtypes
            val womenShoeItems = listOf(
                Triple(VIRT_WOMEN_HEELS, "Heels & Pumps", listOf("heels", "pumps", "stilettos")),
                Triple(VIRT_WOMEN_FLATS, "Flats & Loafers", listOf("flats", "ballet flats", "loafers")),
                Triple(VIRT_WOMEN_SNEAKERS, "Sneakers", listOf("sneakers", "trainers")),
                Triple(VIRT_WOMEN_BOOTS, "Boots", listOf("boots", "ankle boots", "knee high")),
                Triple(VIRT_WOMEN_SANDALS, "Sandals & Slippers", listOf("sandals", "slippers", "wedges")),
            )
            womenShoeItems.forEachIndexed { idx, (id, name, terms) ->
                if (result.none { it.id == id }) {
                    result.add(
                        ShopCategoryDto(
                            id = id,
                            name = name,
                            slug = id,
                            parentId = VIRT_WOMEN_SHOES,
                            displayOrder = idx + 1,
                            isLeaf = true,
                            sellerSelectable = true,
                            buyerVisible = true,
                            code = id.uppercase().replace("-", "_"),
                            searchTerms = terms,
                        )
                    )
                }
            }
        }

        return result
    }

    /**
     * Resolves all category IDs (database & synthetic) that fall under a given category,
     * including cross-department equivalents (e.g. Men includes Men's Shoes and Men's Clothing).
     */
    fun resolveDescendantIds(activeCategoryId: String, categories: List<ShopCategoryDto>): Set<String> {
        val result = mutableSetOf(activeCategoryId)
        val pending = ArrayDeque<String>()
        pending.add(activeCategoryId)

        // Cross-mapped departments
        when (activeCategoryId) {
            FASHION_ROOT_ID -> {
                result.add(SHOES_ROOT_ID)
                result.add(MENS_SHOES_ID)
                result.add(WOMENS_SHOES_ID)
                result.add(KIDS_SHOES_ID)
            }
            MENS_CLOTHING_ID, VIRT_MEN_DEPT, VIRT_MEN_CLOTHING -> {
                result.add(MENS_CLOTHING_ID)
                result.add(MENS_SHOES_ID)
                result.add(VIRT_MEN_SHOES)
            }
            WOMENS_CLOTHING_ID, VIRT_WOMEN_DEPT, VIRT_WOMEN_CLOTHING -> {
                result.add(WOMENS_CLOTHING_ID)
                result.add(WOMENS_SHOES_ID)
                result.add(VIRT_WOMEN_SHOES)
            }
            KIDS_CLOTHING_ID, VIRT_KIDS_DEPT, VIRT_KIDS_CLOTHING -> {
                result.add(KIDS_CLOTHING_ID)
                result.add(KIDS_SHOES_ID)
            }
            SHOES_ROOT_ID -> {
                result.add(MENS_SHOES_ID)
                result.add(WOMENS_SHOES_ID)
                result.add(KIDS_SHOES_ID)
                result.add(SPORTS_SHOES_ID)
            }
        }

        while (pending.isNotEmpty()) {
            val curr = pending.removeFirst()
            categories.filter { it.parentId == curr }.forEach { child ->
                if (result.add(child.id)) {
                    pending.add(child.id)
                }
            }
        }

        // Also add any DB fallback IDs mapped to children in result
        val fallbackIds = result.mapNotNull { DB_FALLBACK_CATEGORY_MAP[it] }
        result.addAll(fallbackIds)

        return result
    }

    /**
     * Generates a clean breadcrumb string for a category using the canonical hierarchy.
     */
    fun canonicalBreadcrumb(category: ShopCategoryDto, categories: List<ShopCategoryDto>): String {
        val byId = categories.associateBy { it.id }
        val path = mutableListOf(category.name)
        var parentId = category.parentId
        val visited = mutableSetOf(category.id)
        while (!parentId.isNullOrBlank() && visited.add(parentId)) {
            val parent = byId[parentId] ?: break
            path.add(0, parent.name)
            parentId = parent.parentId
        }
        return path.joinToString(" > ")
    }
}
