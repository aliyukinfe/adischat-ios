-- Seller-owned soft removal reuses the existing protected product update route.
-- Historical orders and product audit data remain intact.
create or replace function public.adishop_seller_update_product(
  p_profile_id uuid,
  p_product_id uuid,
  p_payload jsonb,
  p_request_id text default null
)
returns jsonb
language plpgsql
security definer
set search_path=public
as $$
declare
  result jsonb;
  store_record public.shop_stores%rowtype;
  product_record public.shop_products%rowtype;
  previous_status text;
  previous_moderation_status text;
begin
  if coalesce((p_payload->>'archiveProduct')::boolean, false) then
    store_record := public.adishop_seller_owned_store(p_profile_id);

    select * into product_record
    from public.shop_products
    where id=p_product_id and store_id=store_record.id
    for update;

    if not found then
      raise exception 'seller-product-not-found';
    end if;

    if product_record.deleted_at is not null or product_record.status='archived' then
      return jsonb_build_object('id',p_product_id,'archived',true);
    end if;

    previous_status := product_record.status;
    previous_moderation_status := product_record.moderation_status;

    update public.shop_products
    set status='archived',
        moderation_status='archived',
        deleted_at=now(),
        updated_at=now()
    where id=p_product_id;

    insert into public.shop_product_status_history(
      product_id,actor_profile_id,actor_role,event_code,
      previous_status,next_status,previous_moderation_status,next_moderation_status,
      public_message,request_id
    ) values (
      p_product_id,p_profile_id,'seller','seller.product.archive',
      previous_status,'archived',previous_moderation_status,'archived',
      'Product removed from store.',nullif(trim(coalesce(p_request_id,'')),'')
    ) on conflict do nothing;

    return jsonb_build_object('id',p_product_id,'archived',true);
  end if;

  result:=public.adishop_seller_update_product_category_core(
    p_profile_id,p_product_id,p_payload,p_request_id
  );

  update public.shop_products p set
    selling_unit_code=case when p_payload ? 'sellingUnitCode' then upper(trim(p_payload->>'sellingUnitCode')) else p.selling_unit_code end,
    minimum_order_quantity=case when p_payload ? 'minimumOrderQuantity' then greatest(1,(p_payload->>'minimumOrderQuantity')::integer) else p.minimum_order_quantity end,
    product_type_description=case when p_payload ? 'productTypeDescription' then nullif(trim(p_payload->>'productTypeDescription'),'') else p.product_type_description end,
    updated_at=now()
  where p.id=p_product_id and exists(
    select 1 from public.shop_stores s where s.id=p.store_id and s.owner_profile_id=p_profile_id
  );

  if not found then raise exception 'seller-product-not-found'; end if;
  return public.adishop_seller_get_product(p_profile_id,p_product_id);
end;
$$;

revoke all on function public.adishop_seller_update_product(uuid,uuid,jsonb,text) from public, anon, authenticated;
grant execute on function public.adishop_seller_update_product(uuid,uuid,jsonb,text) to service_role;
