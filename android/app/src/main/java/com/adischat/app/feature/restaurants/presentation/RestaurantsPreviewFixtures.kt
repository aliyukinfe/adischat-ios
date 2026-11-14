package com.adischat.app.feature.restaurants.presentation

import com.adischat.app.FeatureFlags

/**
 * Isolated offline-only typed preview fixtures for Phase R3D Jetpack Compose Previews
 * and UI foundation verification.
 *
 * IMPORTANT: Never import into [com.adischat.app.feature.restaurants.data.RestaurantsRepository]
 * or return these rows as a fallback when a live backend call fails.
 */
object RestaurantsPreviewFixtures {

    val restaurants: List<RestaurantCardUiModel> = listOf(
        RestaurantCardUiModel(
            id = "prev-rest-habesha",
            slug = "habesha-hearth",
            brandName = "Habesha Hearth",
            cuisineTag = "Ethiopian",
            city = "Addis Ababa",
            primaryBranchName = "Bole Atlas",
            primaryAddress = "Cape Verde St, Bole Atlas",
            description = "Wood-fired traditional Ethiopian platters, clay-pot stews, and freshly roasted buna ceremony served tableside.",
            logoUrl = null,
            coverUrl = null,
            isOpenNowLabel = "Dine-in Open • Till 10:30 PM",
            branchCount = 2,
            isLiveOrderingEnabled = false,
            startingPriceMinor = 28000L,
            highlightBadge = "No.1 Hot Seller",
            isHighlightBadgeDemo = true,
            ratingLabel = null,
        ),
        RestaurantCardUiModel(
            id = "prev-rest-kaffa",
            slug = "kaffa-roastery-bistro",
            brandName = "Kaffa Roastery & Bistro",
            cuisineTag = "Cafe & Bakery",
            city = "Addis Ababa",
            primaryBranchName = "Kazanchis",
            primaryAddress = "Guinea Conakry St, Kazanchis",
            description = "Single-origin highland pour-overs, sourdough paninis, and warm spiced pastries for relaxed table dining.",
            logoUrl = null,
            coverUrl = null,
            isOpenNowLabel = "Dine-in Open • Till 9:00 PM",
            branchCount = 1,
            isLiveOrderingEnabled = false,
            startingPriceMinor = 16000L,
        ),
        RestaurantCardUiModel(
            id = "prev-rest-entoto",
            slug = "entoto-flame-kitchen",
            brandName = "Entoto Flame Kitchen",
            cuisineTag = "Grill & Steak",
            city = "Addis Ababa",
            primaryBranchName = "Sarbet",
            primaryAddress = "Pushkin Square, Sarbet",
            description = "Charcoal-seared tibs cuts, rosemary brisket skewers, and stone-baked flatbreads crafted for group tables.",
            logoUrl = null,
            coverUrl = null,
            isOpenNowLabel = "Dine-in Open • Till 11:00 PM",
            branchCount = 2,
            isLiveOrderingEnabled = false,
            startingPriceMinor = 42000L,
        ),
        RestaurantCardUiModel(
            id = "prev-rest-fasting",
            slug = "zema-garden-kitchen",
            brandName = "Zema Garden Kitchen",
            cuisineTag = "Plant-Based",
            city = "Addis Ababa",
            primaryBranchName = "Piassa",
            primaryAddress = "Churchill Ave, Piassa",
            description = "Seasonal fasting bowls, cold-pressed greens, and house-fermented injera combos in a sunlit courtyard.",
            logoUrl = null,
            coverUrl = null,
            isOpenNowLabel = "Dine-in Open • Till 9:30 PM",
            branchCount = 1,
            isLiveOrderingEnabled = false,
            startingPriceMinor = 24000L,
        ),
    )

    val branchesByRestaurantId: Map<String, List<RestaurantBranchUiModel>> = mapOf(
        "prev-rest-habesha" to listOf(
            RestaurantBranchUiModel(
                id = "prev-br-bole",
                restaurantId = "prev-rest-habesha",
                code = "BOLE-01",
                name = "Bole Atlas Flagship",
                address = "Cape Verde St, Near Edna Mall, Bole Atlas",
                city = "Addis Ababa",
                openingHours = listOf(
                    OpeningHourRowUiModel("Mon – Thu", "11:00 AM – 10:30 PM", isHighlighted = true),
                    OpeningHourRowUiModel("Fri – Sun", "10:30 AM – 11:30 PM"),
                ),
                requireStaffSessionActivation = true,
                requireSessionCode = false,
                isDineInActive = true,
            ),
            RestaurantBranchUiModel(
                id = "prev-br-cmс",
                restaurantId = "prev-rest-habesha",
                code = "CMC-02",
                name = "CMC Courtyard",
                address = "Michael Square Rd, CMC",
                city = "Addis Ababa",
                openingHours = listOf(
                    OpeningHourRowUiModel("Daily", "11:30 AM – 10:00 PM", isHighlighted = true),
                ),
                requireStaffSessionActivation = true,
                requireSessionCode = true,
                isDineInActive = true,
            ),
        ),
        "prev-rest-kaffa" to listOf(
            RestaurantBranchUiModel(
                id = "prev-br-kazanchis",
                restaurantId = "prev-rest-kaffa",
                code = "KAZ-01",
                name = "Kazanchis Terrace",
                address = "Guinea Conakry St, Kazanchis",
                city = "Addis Ababa",
                openingHours = listOf(
                    OpeningHourRowUiModel("Mon – Sat", "7:00 AM – 9:00 PM", isHighlighted = true),
                    OpeningHourRowUiModel("Sun", "8:00 AM – 8:00 PM"),
                ),
                requireStaffSessionActivation = true,
                requireSessionCode = false,
                isDineInActive = true,
            ),
        ),
        "prev-rest-entoto" to listOf(
            RestaurantBranchUiModel(
                id = "prev-br-sarbet",
                restaurantId = "prev-rest-entoto",
                code = "SRB-01",
                name = "Sarbet Main Grill",
                address = "Pushkin Square, Sarbet",
                city = "Addis Ababa",
                openingHours = listOf(
                    OpeningHourRowUiModel("Daily", "12:00 PM – 11:00 PM", isHighlighted = true),
                ),
                requireStaffSessionActivation = true,
                requireSessionCode = false,
                isDineInActive = true,
            ),
        ),
        "prev-rest-fasting" to listOf(
            RestaurantBranchUiModel(
                id = "prev-br-piassa",
                restaurantId = "prev-rest-fasting",
                code = "PIA-01",
                name = "Piassa Courtyard",
                address = "Churchill Ave, Piassa",
                city = "Addis Ababa",
                openingHours = listOf(
                    OpeningHourRowUiModel("Daily", "9:30 AM – 9:30 PM", isHighlighted = true),
                ),
                requireStaffSessionActivation = true,
                requireSessionCode = false,
                isDineInActive = true,
            ),
        ),
    )

    val categoriesByRestaurantId: Map<String, List<MenuCategoryUiModel>> = mapOf(
        "prev-rest-habesha" to listOf(
            MenuCategoryUiModel("cat-juice", "Fresh Juice", sortOrder = 1, iconKey = "juice"),
            MenuCategoryUiModel("cat-shakes", "Shakes", sortOrder = 2, iconKey = "shake", badgeText = "SALE"),
            MenuCategoryUiModel("cat-drinks", "Coffee & Tea", sortOrder = 3, iconKey = "cafe"),
            MenuCategoryUiModel("cat-breakfast", "Breakfast", sortOrder = 4, iconKey = "bakery"),
            MenuCategoryUiModel("cat-burgers", "Burgers", sortOrder = 5, iconKey = "burger"),
            MenuCategoryUiModel("cat-pizza", "Pizza", sortOrder = 6, iconKey = "pizza"),
            MenuCategoryUiModel("cat-platters", "Traditional", sortOrder = 7, iconKey = "meal", badgeText = "HOT"),
            MenuCategoryUiModel("cat-tibs", "Sizzling Tibs", sortOrder = 8, iconKey = "meal", badgeText = "HOT"),
            MenuCategoryUiModel("cat-sides", "Fasting & Sides", sortOrder = 9, iconKey = "meal"),
            MenuCategoryUiModel("cat-desserts", "Desserts", sortOrder = 10, iconKey = "shake"),
        ),
        "prev-rest-kaffa" to listOf(
            MenuCategoryUiModel("cat-kaffa-coffee", "Highland Coffee", sortOrder = 1, iconKey = "cafe"),
            MenuCategoryUiModel("cat-kaffa-pastry", "Artisan Bakery", sortOrder = 2, iconKey = "bakery"),
            MenuCategoryUiModel("cat-kaffa-savory", "All-Day Brunch", sortOrder = 3, iconKey = "meal"),
        ),
    )

    fun menuItemsFor(restaurantId: String, branchId: String?): List<MenuItemUiModel> {
        val isCmcBranch = branchId == "prev-br-cmс"
        return when (restaurantId) {
            "prev-rest-habesha" -> listOf(
                // 1. Fresh Juice
                MenuItemUiModel(
                    id = "item-mango-juice",
                    restaurantId = restaurantId,
                    categoryId = "cat-juice",
                    name = "Fresh Highland Mango Juice",
                    description = "Cold-pressed ripe Wolayta mango served chilled without added sugar.",
                    basePriceMinor = 18000L,
                    isAvailableAtBranch = true,
                    dietaryTags = listOf("Cold-pressed", "Popular"),
                    promoRibbonText = "Weekly Hot • Demo",
                    spotlightTags = listOf("hot_sellers", "deals"),
                    optionGroups = listOf(
                        MenuOptionGroupUiModel(
                            id = "og-juice-add",
                            name = "Optional Infusions",
                            isRequired = false,
                            minSelect = 0,
                            maxSelect = 2,
                            options = listOf(
                                MenuOptionUiModel("opt-ginger", "og-juice-add", "Ginger Shot", 2000L, true),
                                MenuOptionUiModel("opt-mint", "og-juice-add", "Fresh Lime & Mint", 1500L, true),
                            ),
                        ),
                    ),
                ),
                MenuItemUiModel(
                    id = "item-pineapple-juice",
                    restaurantId = restaurantId,
                    categoryId = "cat-juice",
                    name = "Pineapple & Ginger Cooler",
                    description = "Pressed golden pineapple blended with highland ginger root.",
                    basePriceMinor = 19000L,
                    isAvailableAtBranch = true,
                    dietaryTags = listOf("Ginger & Pineapple", "Popular"),
                    promoRibbonText = "Hot Seller • Demo",
                    spotlightTags = listOf("hot_sellers"),
                ),
                // 2. Shakes
                MenuItemUiModel(
                    id = "item-espresso-shake",
                    restaurantId = restaurantId,
                    categoryId = "cat-shakes",
                    name = "Addis Espresso Caramel Shake",
                    description = "Double Yirgacheffe espresso spun with vanilla cream and sea-salt caramel.",
                    basePriceMinor = 29000L,
                    isAvailableAtBranch = true,
                    dietaryTags = listOf("House Favorite"),
                    promoRibbonText = "QA Promo • Demo",
                    spotlightTags = listOf("deals", "chefs_pick"),
                ),
                MenuItemUiModel(
                    id = "item-berry-shake",
                    restaurantId = restaurantId,
                    categoryId = "cat-shakes",
                    name = "Wild Strawberry Yogurt Shake",
                    description = "Seasonal Ziway strawberries blended with strained house yogurt.",
                    basePriceMinor = 26000L,
                    isAvailableAtBranch = false,
                    dietaryTags = listOf("Seasonal"),
                ),
                // 3. Coffee & Hot Drinks
                MenuItemUiModel(
                    id = "item-jebena-buna",
                    restaurantId = restaurantId,
                    categoryId = "cat-drinks",
                    name = "Jebena Buna Service (For 2)",
                    description = "Freshly roasted Yirgacheffe beans brewed in clay jebena with rue herb and toasted kollo.",
                    basePriceMinor = 28000L,
                    isAvailableAtBranch = true,
                    dietaryTags = listOf("Tableside", "Chef Pick"),
                    spotlightTags = listOf("chefs_pick"),
                ),
                MenuItemUiModel(
                    id = "item-spiced-macchiato",
                    restaurantId = restaurantId,
                    categoryId = "cat-drinks",
                    name = "Cardamom Buna Macchiato",
                    description = "Rich espresso layered with steamed milk and warm green cardamom foam.",
                    basePriceMinor = 14000L,
                    isAvailableAtBranch = true,
                    optionGroups = listOf(
                        MenuOptionGroupUiModel(
                            id = "og-milk",
                            name = "Milk Choice",
                            isRequired = true,
                            minSelect = 1,
                            maxSelect = 1,
                            options = listOf(
                                MenuOptionUiModel("opt-whole-milk", "og-milk", "Fresh Dairy Milk", 0L, true),
                                MenuOptionUiModel("opt-oat-milk", "og-milk", "Steamed Oat Milk", 4000L, true),
                            ),
                        ),
                    ),
                ),
                // 4. Breakfast
                MenuItemUiModel(
                    id = "item-chechebsa",
                    restaurantId = restaurantId,
                    categoryId = "cat-breakfast",
                    name = "Spiced Honey Chechebsa",
                    description = "Crisp torn kitta flatbread tossed with berbere, clarified butter, and tigray blossom honey.",
                    basePriceMinor = 32000L,
                    isAvailableAtBranch = true,
                    optionGroups = listOf(
                        MenuOptionGroupUiModel(
                            id = "og-chechebsa-fat",
                            name = "Preparation",
                            isRequired = true,
                            minSelect = 1,
                            maxSelect = 1,
                            options = listOf(
                                MenuOptionUiModel("opt-kibe", "og-chechebsa-fat", "Traditional Niter Kibbeh", 0L, true),
                                MenuOptionUiModel("opt-fasting-oil", "og-chechebsa-fat", "Fasting Cold-Pressed Oil", 0L, true),
                            ),
                        ),
                        MenuOptionGroupUiModel(
                            id = "og-chechebsa-extra",
                            name = "Optional Extras",
                            isRequired = false,
                            minSelect = 0,
                            maxSelect = 1,
                            options = listOf(
                                MenuOptionUiModel("opt-extra-honey", "og-chechebsa-extra", "Extra Raw Blossom Honey", 5000L, true),
                            ),
                        ),
                    ),
                ),
                MenuItemUiModel(
                    id = "item-genfo-bowl",
                    restaurantId = restaurantId,
                    categoryId = "cat-breakfast",
                    name = "Roasted Barley Genfo Bowl",
                    description = "Warm heritage barley porridge finished with awaze center and house ayib.",
                    basePriceMinor = 29000L,
                    isAvailableAtBranch = true,
                ),
                // 5. Burgers
                MenuItemUiModel(
                    id = "item-berbere-burger",
                    restaurantId = restaurantId,
                    categoryId = "cat-burgers",
                    name = "Habesha Berbere Beef Burger",
                    description = "Grilled highland beef patty with aged cheese, caramelized onion, and spiced mayo.",
                    basePriceMinor = 54000L,
                    priceOverrideMinor = if (isCmcBranch) 56000L else null,
                    isAvailableAtBranch = true,
                    optionGroups = listOf(
                        MenuOptionGroupUiModel(
                            id = "og-burger-size",
                            name = "Patty Stack",
                            isRequired = true,
                            minSelect = 1,
                            maxSelect = 1,
                            options = listOf(
                                MenuOptionUiModel("opt-single-patty", "og-burger-size", "Single 180g Patty", 0L, true),
                                MenuOptionUiModel("opt-double-patty", "og-burger-size", "Double Stack", 18000L, true),
                            ),
                        ),
                    ),
                ),
                MenuItemUiModel(
                    id = "item-chicken-burger",
                    restaurantId = restaurantId,
                    categoryId = "cat-burgers",
                    name = "Crispy Awaze Chicken Burger",
                    description = "Buttermilk crisp chicken fillet glazed in sweet awaze sauce with slaw.",
                    basePriceMinor = 49000L,
                    isAvailableAtBranch = true,
                    dietaryTags = listOf("Crispy", "Popular"),
                    promoRibbonText = "Hot Seller • Demo",
                    spotlightTags = listOf("hot_sellers"),
                ),
                // 6. Pizza
                MenuItemUiModel(
                    id = "item-tibs-pizza",
                    restaurantId = restaurantId,
                    categoryId = "cat-pizza",
                    name = "Wood-Fired Tibs & Jalapeño Pizza",
                    description = "Sourdough crust topped with tender spiced beef, red onion, green chili, and mozzarella.",
                    basePriceMinor = 64000L,
                    isAvailableAtBranch = true,
                    spotlightTags = listOf("chefs_pick"),
                    optionGroups = listOf(
                        MenuOptionGroupUiModel(
                            id = "og-pizza-size",
                            name = "Pizza Size",
                            isRequired = true,
                            minSelect = 1,
                            maxSelect = 1,
                            options = listOf(
                                MenuOptionUiModel("opt-pizza-reg", "og-pizza-size", "Regular 10\"", 0L, true),
                                MenuOptionUiModel("opt-pizza-lrg", "og-pizza-size", "Large 14\"", 22000L, true),
                            ),
                        ),
                    ),
                ),
                MenuItemUiModel(
                    id = "item-margherita-pizza",
                    restaurantId = restaurantId,
                    categoryId = "cat-pizza",
                    name = "Basil & Mozzarella Margherita",
                    description = "Crisp blistered crust with roasted San Marzano tomato sauce and sweet basil.",
                    basePriceMinor = 48000L,
                    isAvailableAtBranch = true,
                    dietaryTags = listOf("Vegetarian"),
                ),
                // 7. Traditional Ethiopian
                MenuItemUiModel(
                    id = "item-doro-wot",
                    restaurantId = restaurantId,
                    categoryId = "cat-platters",
                    name = "Slow-Simmered Doro Wot",
                    description = "Heritage spiced chicken slow-cooked in berbere reduction with farm egg and house ayib.",
                    basePriceMinor = 62000L,
                    isAvailableAtBranch = !isCmcBranch,
                    dietaryTags = listOf("Slow Cooked"),
                    promoRibbonText = "Chef's Pick • Demo",
                    spotlightTags = listOf("chefs_pick", "hot_sellers"),
                    optionGroups = listOf(
                        MenuOptionGroupUiModel(
                            id = "og-injera-type",
                            name = "Injera Selection",
                            isRequired = true,
                            minSelect = 1,
                            maxSelect = 1,
                            options = listOf(
                                MenuOptionUiModel("opt-sergegna", "og-injera-type", "100% Red Teff", 0L, true),
                                MenuOptionUiModel("opt-white-teff", "og-injera-type", "White Magna Teff", 4000L, true),
                            ),
                        ),
                    ),
                ),
                MenuItemUiModel(
                    id = "item-kitfo-special",
                    restaurantId = restaurantId,
                    categoryId = "cat-platters",
                    name = "Special Kitfo Platter",
                    description = "Hand-minced lean beef seasoned with mitmita, korerima, ayib cheese, and braised greens.",
                    basePriceMinor = 74000L,
                    isAvailableAtBranch = true,
                    optionGroups = listOf(
                        MenuOptionGroupUiModel(
                            id = "og-kitfo-style",
                            name = "Preparation Style",
                            isRequired = true,
                            minSelect = 1,
                            maxSelect = 1,
                            options = listOf(
                                MenuOptionUiModel("opt-leb-leb", "og-kitfo-style", "Leb-Leb (Lightly Warmed)", 0L, true),
                                MenuOptionUiModel("opt-ye-bet", "og-kitfo-style", "Well Done (Betsega)", 0L, true),
                            ),
                        ),
                    ),
                ),
                // 8. Sizzling Tibs
                MenuItemUiModel(
                    id = "item-shekla-tibs",
                    restaurantId = restaurantId,
                    categoryId = "cat-tibs",
                    name = "Clay-Pot Shekla Tibs",
                    description = "Prime beef strips seared in clarified butter with rosemary, garlic, jalapeño, and warm red-teff injera.",
                    basePriceMinor = 68000L,
                    priceOverrideMinor = if (isCmcBranch) 71000L else null,
                    isAvailableAtBranch = true,
                    dietaryTags = listOf("Chef Signature", "Halal"),
                    promoRibbonText = "House No.1 • Demo",
                    spotlightTags = listOf("chefs_pick", "hot_sellers", "deals"),
                    optionGroups = listOf(
                        MenuOptionGroupUiModel(
                            id = "og-portion",
                            name = "Portion Size",
                            isRequired = true,
                            minSelect = 1,
                            maxSelect = 1,
                            sortOrder = 1,
                            options = listOf(
                                MenuOptionUiModel("opt-single", "og-portion", "Solo Platter", 0L, true),
                                MenuOptionUiModel("opt-share", "og-portion", "Shared Table (2–3 Guests)", 38000L, true),
                            ),
                        ),
                        MenuOptionGroupUiModel(
                            id = "og-spice",
                            name = "Heat Level",
                            isRequired = true,
                            minSelect = 1,
                            maxSelect = 1,
                            sortOrder = 2,
                            options = listOf(
                                MenuOptionUiModel("opt-mild", "og-spice", "Mild Awaze", 0L, true),
                                MenuOptionUiModel("opt-med", "og-spice", "Medium Mitmita", 0L, true),
                                MenuOptionUiModel("opt-hot", "og-spice", "Extra Fiery", 0L, true),
                            ),
                        ),
                        MenuOptionGroupUiModel(
                            id = "og-extras",
                            name = "Optional Table Add-ons",
                            isRequired = false,
                            minSelect = 0,
                            maxSelect = 2,
                            sortOrder = 3,
                            options = listOf(
                                MenuOptionUiModel("opt-ayib", "og-extras", "Fresh Ayib Cottage Cheese", 9500L, true),
                                MenuOptionUiModel("opt-kocho", "og-extras", "Toasted Kocho Side", 7500L, true),
                                MenuOptionUiModel("opt-injera", "og-extras", "Extra Teff Injera Roll", 6000L, false),
                            ),
                        ),
                    ),
                ),
                // 9. Fasting & Sides
                MenuItemUiModel(
                    id = "item-beyaynetu",
                    restaurantId = restaurantId,
                    categoryId = "cat-sides",
                    name = "Royal Beyaynetu Mosaic",
                    description = "Vibrant six-bowl fasting platter with misir wot, kik alicha, gomen, beetroot, and flaxseed dip.",
                    basePriceMinor = 42000L,
                    isAvailableAtBranch = true,
                    dietaryTags = listOf("Plant-Based", "Fasting"),
                    optionGroups = listOf(
                        MenuOptionGroupUiModel(
                            id = "og-bey-extras",
                            name = "Optional Toppings",
                            isRequired = false,
                            minSelect = 0,
                            maxSelect = 2,
                            options = listOf(
                                MenuOptionUiModel("opt-shiro-cup", "og-bey-extras", "Warm Bozena-Free Shiro Cup", 11000L, true),
                                MenuOptionUiModel("opt-chili", "og-bey-extras", "Stuffed Sinig Chili", 4500L, true),
                            ),
                        ),
                    ),
                ),
                // 10. Desserts
                MenuItemUiModel(
                    id = "item-honey-teff-cake",
                    restaurantId = restaurantId,
                    categoryId = "cat-desserts",
                    name = "Warm Honey Teff Cake",
                    description = "Light teff-flour sponge glazed with wildflower honey and crushed pistachio.",
                    basePriceMinor = 24000L,
                    isAvailableAtBranch = true,
                ),
                MenuItemUiModel(
                    id = "item-espresso-tiramisu",
                    restaurantId = restaurantId,
                    categoryId = "cat-desserts",
                    name = "Yirgacheffe Espresso Tiramisu",
                    description = "Whipped mascarpone layered with Ethiopian espresso-soaked ladyfingers.",
                    basePriceMinor = 27000L,
                    isAvailableAtBranch = true,
                ),
            )
            "prev-rest-kaffa" -> listOf(
                MenuItemUiModel(
                    id = "item-kaffa-pourover",
                    restaurantId = restaurantId,
                    categoryId = "cat-kaffa-coffee",
                    name = "Guji Highland V60 Pour-Over",
                    description = "Single-origin washed Guji beans brewed tableside with floral bergamot notes.",
                    basePriceMinor = 18000L,
                    isAvailableAtBranch = true,
                    dietaryTags = listOf("Single Origin"),
                    optionGroups = listOf(
                        MenuOptionGroupUiModel(
                            id = "og-kaffa-roast",
                            name = "Brew Style",
                            isRequired = true,
                            minSelect = 1,
                            maxSelect = 1,
                            options = listOf(
                                MenuOptionUiModel("opt-kaffa-hot", "og-kaffa-roast", "Hot Pour-Over", 0L, true),
                                MenuOptionUiModel("opt-kaffa-iced", "og-kaffa-roast", "Flash Chilled", 2000L, true),
                            ),
                        ),
                    ),
                ),
                MenuItemUiModel(
                    id = "item-kaffa-panini",
                    restaurantId = restaurantId,
                    categoryId = "cat-kaffa-savory",
                    name = "Roasted Mushroom Sourdough Panini",
                    description = "Pressed country sourdough with thyme mushrooms, caramelized onion, and aged cheese.",
                    basePriceMinor = 39000L,
                    isAvailableAtBranch = true,
                    dietaryTags = listOf("Vegetarian"),
                ),
                MenuItemUiModel(
                    id = "item-kaffa-croissant",
                    restaurantId = restaurantId,
                    categoryId = "cat-kaffa-pastry",
                    name = "Spiced Cardamom Butter Croissant",
                    description = "Flaky laminated pastry glazed with Ethiopian cardamom honey syrup.",
                    basePriceMinor = 16000L,
                    isAvailableAtBranch = true,
                ),
            )
            else -> listOf(
                MenuItemUiModel(
                    id = "item-default-1",
                    restaurantId = restaurantId,
                    categoryId = "cat-default",
                    name = "House Tasting Platter",
                    description = "Seasonal kitchen selection prepared fresh when seated.",
                    basePriceMinor = 34000L,
                    isAvailableAtBranch = true,
                ),
            )
        }
    }

    fun discoverState(
        loadState: RestaurantsLoadState = RestaurantsLoadState.Content,
        isDineInEnabled: Boolean = FeatureFlags.RESTAURANTS_DINE_IN_ENABLED,
    ): RestaurantsDiscoverUiState = RestaurantsDiscoverUiState(
        loadState = loadState,
        availableCuisineFilters = listOf(RestaurantsDiscoverUiState.ALL_CUISINES) +
            restaurants.map { it.cuisineTag }.distinct(),
        allRestaurants = if (loadState == RestaurantsLoadState.Content) restaurants else emptyList(),
        isDineInEnabled = isDineInEnabled,
    )

    fun profileState(
        restaurantId: String = restaurants.first().id,
        isDineInEnabled: Boolean = FeatureFlags.RESTAURANTS_DINE_IN_ENABLED,
    ): RestaurantProfileUiState {
        val restaurant = restaurants.firstOrNull { it.id == restaurantId } ?: restaurants.first()
        val branches = branchesByRestaurantId[restaurant.id].orEmpty()
        return RestaurantProfileUiState(
            loadState = RestaurantsLoadState.Content,
            restaurant = restaurant,
            branches = branches,
            selectedBranchId = branches.firstOrNull()?.id,
            isDineInEnabled = isDineInEnabled,
        )
    }

    fun promotionsFor(restaurantId: String): List<RestaurantPromotionUiModel> = when (restaurantId) {
        "prev-rest-habesha" -> listOf(
            RestaurantPromotionUiModel(
                id = "qa-promo-50",
                title = "Save ETB 50",
                subtitle = "on 500+ (Demo)",
                tone = PromotionTone.GOLD,
                isDemoFixture = true,
            ),
            RestaurantPromotionUiModel(
                id = "qa-promo-10",
                title = "Get ETB 10 Off",
                subtitle = "Demo Voucher",
                tone = PromotionTone.CORAL_RED,
                isDemoFixture = true,
            ),
            RestaurantPromotionUiModel(
                id = "qa-promo-deals",
                title = "Restaurant Deals",
                subtitle = "QA Preview Only",
                tone = PromotionTone.CREAM,
                isDemoFixture = true,
            ),
        )
        else -> emptyList()
    }

    fun menuState(
        restaurantId: String = restaurants.first().id,
        branchId: String? = null,
        isDineInEnabled: Boolean = FeatureFlags.RESTAURANTS_DINE_IN_ENABLED,
    ): RestaurantMenuUiState {
        val restaurant = restaurants.firstOrNull { it.id == restaurantId } ?: restaurants.first()
        val branches = branchesByRestaurantId[restaurant.id].orEmpty()
        val resolvedBranchId = branchId ?: branches.firstOrNull()?.id
        val categories = categoriesByRestaurantId[restaurant.id]
            ?: listOf(MenuCategoryUiModel("cat-default", "All Dishes", sortOrder = 1))
        val items = menuItemsFor(restaurant.id, resolvedBranchId)
        val promotions = promotionsFor(restaurant.id)
        return RestaurantMenuUiState(
            loadState = if (items.isEmpty()) RestaurantsLoadState.Empty else RestaurantsLoadState.Content,
            restaurantName = restaurant.brandName,
            restaurantBrandTagline = "${restaurant.cuisineTag} • ${restaurant.city}",
            branches = branches,
            selectedBranchId = resolvedBranchId,
            categories = categories,
            selectedCategoryId = null,
            items = items,
            promotions = promotions,
            isBrowseOnly = true,
            isDineInEnabled = isDineInEnabled,
        )
    }
}
