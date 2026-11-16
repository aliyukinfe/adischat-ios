import { handleAdishopFunction } from "../_shared/adishop.ts";
Deno.serve((request) => handleAdishopFunction("adishop-seller-reorder-product-media", request));
