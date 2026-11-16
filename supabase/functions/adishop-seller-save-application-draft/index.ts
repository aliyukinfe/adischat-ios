import { handleAdishopFunction } from "../_shared/adishop.ts";
Deno.serve((request) => handleAdishopFunction("adishop-seller-save-application-draft", request));
