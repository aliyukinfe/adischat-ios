import { handleAdishopFunction } from "../_shared/adishop.ts";

Deno.serve((request) => handleAdishopFunction("adishop-buyer-refund-requests", request));
