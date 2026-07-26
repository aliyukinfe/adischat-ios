import { handleAdishopFunction } from "../_shared/adishop.ts";

Deno.serve((request) => handleAdishopFunction("adishop-buyer-post-delivery-actions", request));
