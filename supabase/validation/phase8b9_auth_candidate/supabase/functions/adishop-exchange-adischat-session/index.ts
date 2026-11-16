import { handleAdishopFunction } from "../_shared/adishop.ts";
Deno.serve((request) => handleAdishopFunction("adishop-exchange-adischat-session", request));
