import { handleAdishopFunction } from "../_shared/adishop.ts";

Deno.serve((request) => handleAdishopFunction("adishop-get-seller-order-group", request));
