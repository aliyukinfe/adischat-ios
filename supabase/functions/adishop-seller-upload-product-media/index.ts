import { handleAdishopFunction } from "../_shared/adishop.ts";
Deno.serve((request) => handleAdishopFunction("adishop-seller-upload-product-media", request));
