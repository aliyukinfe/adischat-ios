import { handleAdishopFunction } from "../_shared/adishop.ts";
import { handleAdminAllProducts } from "../_shared/adishop-admin-all-products.ts";

Deno.serve((request) => new URL(request.url).searchParams.get("view") === "all-v2"
  ? handleAdminAllProducts(request)
  : handleAdishopFunction("adishop-admin-products", request));
