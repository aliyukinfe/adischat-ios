# Shop screen decomposition

Baseline `ShopScreen.kt`: 327,026 bytes, 6,318 lines, 151 composables.

Final `ShopScreen.kt`: 219,158 bytes, 4,414 lines, 106 composables.

Extracted focused files:

- `ShopTrendingScreen.kt`: 3,292 bytes, 71 lines
- `ShopCartScreen.kt`: 16,431 bytes, 302 lines
- `ShopStoreScreen.kt`: 17,629 bytes, 331 lines
- `ShopSellerOrdersScreen.kt`: 27,520 bytes, 483 lines
- `ShopSellerProductsScreen.kt`: 31,965 bytes, 552 lines
- `ShopSellerCenterScreen.kt`: 37,027 bytes, 707 lines

Generated debug class surface for `ShopScreenKt` fell from 253 classes / 2,799,762 bytes to 212 classes / 2,028,188 bytes. Superseded commented seller-application UI and the unused legacy product-detail implementation were removed. Route names, APIs, DTOs, and backend behavior were preserved.
