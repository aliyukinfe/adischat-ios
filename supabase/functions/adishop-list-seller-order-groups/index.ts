import { handleAdishopFunction } from "../_shared/adishop.ts";

Deno.serve((request) => handleAdishopFunction("adishop-list-seller-order-groups", request));
