import { handleAdishopFunction } from "../_shared/adishop.ts";
Deno.serve((request) => handleAdishopFunction("adishop-seller-respond-application-info-request", request));
