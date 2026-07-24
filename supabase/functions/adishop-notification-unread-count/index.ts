import { handleAdishopFunction } from "../_shared/adishop.ts";

Deno.serve((request) => handleAdishopFunction("adishop-notification-unread-count", request));
