package com.adischat.app.feature.shop.presentation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Agriculture
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Accessible
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Backpack
import androidx.compose.material.icons.filled.BakeryDining
import androidx.compose.material.icons.filled.Bathtub
import androidx.compose.material.icons.filled.Bed
import androidx.compose.material.icons.filled.Blender
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.Chair
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Cookie
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.DoorFront
import androidx.compose.material.icons.filled.DryCleaning
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ElectricalServices
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Factory
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Forest
import androidx.compose.material.icons.filled.FormatPaint
import androidx.compose.material.icons.filled.Grass
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Handyman
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Hiking
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Icecream
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LocalDrink
import androidx.compose.material.icons.filled.Kitchen
import androidx.compose.material.icons.filled.LocalGroceryStore
import androidx.compose.material.icons.filled.LocalLaundryService
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Luggage
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Microwave
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Mouse
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.OilBarrel
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Piano
import androidx.compose.material.icons.filled.Plumbing
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.PrecisionManufacturing
import androidx.compose.material.icons.filled.Redeem
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Stroller
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.Tablet
import androidx.compose.material.icons.filled.TireRepair
import androidx.compose.material.icons.filled.Toys
import androidx.compose.material.icons.filled.Texture
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Window
import androidx.compose.material.icons.filled.Yard
import androidx.compose.ui.graphics.vector.ImageVector
import com.adischat.app.feature.shop.data.ShopCategoryDto

internal fun categoryIconKey(category: ShopCategoryDto, categories: List<ShopCategoryDto> = emptyList()): String {
    var current = category
    val seen = mutableSetOf<String>()
    while (current.parentId != null && seen.add(current.id)) {
        current = categories.firstOrNull { it.id == current.parentId } ?: break
    }
    return current.slug
}

// Canonical buyer-visible leaf slugs; unknown categories retain the parent/fallback icon.
internal val categoryLeafIcons: Map<String, ImageVector> = buildMap {
    fun add(icon: ImageVector, vararg slugs: String) { slugs.forEach { put(it, icon) } }

    add(Icons.Default.PhoneAndroid, "mobile-phones")
    add(Icons.Default.Tablet, "tablets")
    add(Icons.Default.Laptop, "laptops")
    add(Icons.Default.Computer, "desktop-computers")
    add(Icons.Default.Mouse, "computer-accessories")
    add(Icons.Default.Devices, "phone-accessories")
    add(Icons.Default.Cable, "chargers-cables")
    add(Icons.Default.Headphones, "earphones-headphones")
    add(Icons.Default.Watch, "smart-watches")
    add(Icons.Default.CameraAlt, "cameras")
    add(Icons.Default.Tv, "tvs")
    add(Icons.Default.Router, "networking")
    add(Icons.Default.SportsEsports, "gaming")
    add(Icons.Default.Usb, "storage-devices")
    add(Icons.Default.Power, "power-banks")

    add(Icons.Default.Checkroom, "mens-clothing", "womens-clothing", "shirts", "t-shirts")
    add(Icons.Default.ChildCare, "kids-clothing", "baby-clothing")
    add(Icons.Default.Style, "traditional-clothing", "dresses")
    add(Icons.Default.School, "uniforms")
    add(Icons.Default.AcUnit, "jackets")
    add(Icons.Default.DryCleaning, "trousers", "underwear")
    add(Icons.Default.FitnessCenter, "sportswear", "fitness")

    add(Icons.Default.Face, "makeup")
    add(Icons.Default.Spa, "skincare", "personal-care", "permitted-wellness-products")
    add(Icons.Default.ContentCut, "hair-care", "pet-grooming")
    add(Icons.Default.AutoAwesome, "perfume", "sequins", "fashion-jewelry")
    add(Icons.Default.Brush, "beauty-tools", "art-supplies")
    add(Icons.Default.Inventory2, "salon-supplies", "factory-components", "building-supplies")

    add(Icons.Default.Chair, "furniture")
    add(Icons.Default.Kitchen, "kitchen", "refrigerators")
    add(Icons.Default.Bed, "bedding", "pet-beds")
    add(Icons.Default.Palette, "home-decor", "party-decorations", "paint-finishes")
    add(Icons.Default.Lightbulb, "lighting")
    add(Icons.Default.Inventory2, "home-storage", "containers", "boxes")
    add(Icons.Default.Bathtub, "bathroom", "sanitary-ware")
    add(Icons.Default.CleaningServices, "cleaning")
    add(Icons.Default.Yard, "garden", "greenhouse-supplies")

    add(Icons.Default.Coffee, "coffee")
    add(Icons.Default.LocalDrink, "tea", "beverages", "packaging-bottles")
    add(Icons.Default.Cookie, "snacks")
    add(Icons.Default.Grass, "grains", "permitted-seeds")
    add(Icons.Default.Spa, "spices")
    add(Icons.Default.Restaurant, "cooking-ingredients", "packaged-food", "baby-feeding")
    add(Icons.Default.BakeryDining, "bakery")
    add(Icons.Default.Icecream, "dairy")

    add(Icons.Default.SportsSoccer, "football")
    add(Icons.Default.DirectionsRun, "running", "sports-shoes")
    add(Icons.Default.DirectionsBike, "cycling")
    add(Icons.Default.Forest, "camping")
    add(Icons.Default.Hiking, "outdoor-gear")

    add(Icons.Default.ChildCare, "baby-care")
    add(Icons.Default.Stroller, "strollers")
    add(Icons.Default.MedicalServices, "diapers", "first-aid")
    add(Icons.Default.School, "educational-toys", "educational-materials")
    add(Icons.Default.Toys, "general-toys")

    add(Icons.Default.Diamond, "rings", "rhinestones-decorative-stones", "beads")
    add(Icons.Default.Link, "necklaces", "bracelets", "zippers", "elastic")
    add(Icons.Default.Star, "earrings", "garment-trims")
    add(Icons.Default.Watch, "watches")
    add(Icons.Default.Inventory2, "jewelry-accessories", "sewing-accessories", "event-accessories")

    add(Icons.Default.Texture, "cotton-fabric", "polyester-fabric", "denim", "canvas-fabric", "lining-fabric", "knit-fabric", "industrial-fabric", "textile-rolls", "lace", "ribbon")
    add(Icons.Default.Style, "leather", "synthetic-leather")
    add(Icons.Default.Checkroom, "mens-shoes", "womens-shoes", "kids-shoes")
    add(Icons.Default.ShoppingBag, "handbags", "shopping-bags")
    add(Icons.Default.Backpack, "backpacks")
    add(Icons.Default.Redeem, "wallets", "gifts", "wedding-supplies", "wrapping")
    add(Icons.Default.Link, "belts")
    add(Icons.Default.Luggage, "luggage")

    add(Icons.Default.DirectionsCar, "auto-parts", "vehicle-accessories")
    add(Icons.Default.TwoWheeler, "motorcycle-parts")
    add(Icons.Default.Bolt, "vehicle-batteries", "electrical-hardware", "electrical-materials")
    add(Icons.Default.TireRepair, "tires")
    add(Icons.Default.OilBarrel, "vehicle-oils")
    add(Icons.Default.Devices, "vehicle-electronics")
    add(Icons.Default.Handyman, "vehicle-tools", "hand-tools", "farming-tools", "craft-tools")

    add(Icons.Default.Build, "power-tools")
    add(Icons.Default.Settings, "fasteners", "screws", "industrial-spare-parts")
    add(Icons.Default.Lock, "locks")
    add(Icons.Default.Plumbing, "hardware-plumbing", "construction-plumbing", "pumps")
    add(Icons.Default.WaterDrop, "irrigation")
    add(Icons.Default.Construction, "welding-supplies", "manufacturing-supplies")
    add(Icons.Default.Factory, "industrial-machinery", "agricultural-machinery")
    add(Icons.Default.Power, "motors")
    add(Icons.Default.AcUnit, "compressors", "air-conditioners", "fans")

    add(Icons.Default.Sell, "labels")
    add(Icons.Default.Layers, "packaging-film", "printing-materials", "tiles")
    add(Icons.Default.Print, "printer-supplies", "office-printer-supplies", "office-equipment")
    add(Icons.Default.Edit, "pens", "thread", "embroidery-supplies")
    add(Icons.Default.MenuBook, "notebooks", "physical-books")
    add(Icons.Default.Description, "paper")
    add(Icons.Default.School, "school-supplies")

    add(Icons.Default.Accessible, "mobility-products")
    add(Icons.Default.MonitorHeart, "personal-health-devices", "health-accessories")
    add(Icons.Default.Pets, "pet-food", "pet-accessories", "pet-cages", "animal-equipment")
    add(Icons.Default.DoorFront, "doors")
    add(Icons.Default.Window, "windows")
    add(Icons.Default.LocalLaundryService, "washing-machines")
    add(Icons.Default.Microwave, "microwaves", "ovens")
    add(Icons.Default.Blender, "small-appliances")
    add(Icons.Default.MusicNote, "music-equipment")
    add(Icons.Default.Piano, "instruments")
    add(Icons.Default.Album, "physical-media")
    add(Icons.Default.Celebration, "balloons")
    add(Icons.Default.FormatPaint, "buttons")
}

internal fun categoryIcon(category: ShopCategoryDto, categories: List<ShopCategoryDto> = emptyList()): ImageVector =
    categoryLeafIcons[category.slug] ?: when (categoryIconKey(category, categories)) {
        "electronics" -> Icons.Default.Devices
        "fashion" -> Icons.Default.Checkroom
        "home" -> Icons.Default.Chair
        "beauty" -> Icons.Default.Spa
        "groceries" -> Icons.Default.LocalGroceryStore
        "books" -> Icons.Default.MenuBook
        "sports" -> Icons.Default.SportsSoccer
        "jewelry-watches" -> Icons.Default.Diamond
        "toys" -> Icons.Default.ChildCare
        "crafts-sewing-accessories" -> Icons.Default.Palette
        "fabric-textile-leather" -> Icons.Default.Texture
        "shoes-bags-accessories" -> Icons.Default.ShoppingBag
        "automotive-motorcycle" -> Icons.Default.DirectionsCar
        "tools-hardware" -> Icons.Default.Build
        "industrial-manufacturing" -> Icons.Default.PrecisionManufacturing
        "packaging-printing" -> Icons.Default.Inventory2
        "office-school-stationery" -> Icons.Default.Edit
        "health-wellness" -> Icons.Default.HealthAndSafety
        "pet-supplies" -> Icons.Default.Pets
        "agriculture-farming" -> Icons.Default.Agriculture
        "construction-building-materials" -> Icons.Default.Construction
        "appliances" -> Icons.Default.Kitchen
        "gifts-party-events" -> Icons.Default.Celebration
        else -> Icons.Default.Category
    }
