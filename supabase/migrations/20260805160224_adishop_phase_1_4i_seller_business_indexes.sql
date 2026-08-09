-- Cover Phase 1.4I foreign keys used by ownership cleanup and audit queries.
create index if not exists shop_seller_settings_updated_by_idx
  on public.shop_seller_settings(updated_by_profile_id);
create index if not exists shop_inventory_history_variant_idx
  on public.shop_inventory_history(variant_id) where variant_id is not null;
create index if not exists shop_inventory_history_actor_idx
  on public.shop_inventory_history(actor_profile_id) where actor_profile_id is not null;
create index if not exists shop_seller_customer_notes_customer_idx
  on public.shop_seller_customer_notes(customer_profile_id);
create index if not exists shop_seller_customer_notes_updated_by_idx
  on public.shop_seller_customer_notes(updated_by_profile_id);
create index if not exists shop_seller_coupons_created_by_idx
  on public.shop_seller_coupons(created_by_profile_id);
