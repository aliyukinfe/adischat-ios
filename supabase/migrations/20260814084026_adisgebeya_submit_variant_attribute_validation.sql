-- A required buyer-selectable attribute is complete when it is represented by
-- an active option with values. Sellers should not enter a second, conflicting
-- single specification for the same Color/Size variant axis.
create or replace function public.adishop_seller_submit_product(
  p_profile_id uuid,p_product_id uuid,p_request_id text default null
)
returns jsonb
language plpgsql
security definer
set search_path=public
as $$
declare
  store_record public.shop_stores%rowtype;
  product_record public.shop_products%rowtype;
  image_count integer;
  active_option_count integer;
  active_variant_count integer;
  invalid_variant_count integer;
  missing_required_count integer;
  legacy_stock integer;
  errors jsonb:='[]'::jsonb;
  previous_status text;
  previous_moderation_status text;
begin
  store_record:=public.adishop_seller_owned_store(p_profile_id);
  select * into product_record from public.shop_products
  where id=p_product_id and store_id=store_record.id and deleted_at is null for update;
  if not found then raise exception 'seller-product-not-found'; end if;
  if product_record.moderation_status in ('under_review','suspended','archived','submitted') then raise exception 'seller-product-locked'; end if;
  previous_status:=product_record.status;
  previous_moderation_status:=product_record.moderation_status;

  select count(*) into image_count from public.shop_product_images where product_id=p_product_id and deleted_at is null;
  select count(*) into active_option_count from public.shop_product_options where product_id=p_product_id and status='active';
  select count(*) into active_variant_count from public.shop_product_variants where product_id=p_product_id and status='active';
  select coalesce(max(quantity_available-quantity_reserved),0) into legacy_stock
  from public.shop_inventory where product_id=p_product_id and variant_id is null and inventory_scope='sellable';

  select count(*) into missing_required_count
  from public.shop_category_attributes ca
  join public.shop_attribute_definitions a on a.id=ca.attribute_definition_id
  where ca.category_id=product_record.category_id and ca.required=true
    and not exists(
      select 1 from public.shop_product_attribute_values pav
      where pav.product_id=p_product_id and pav.attribute_definition_id=ca.attribute_definition_id
    )
    and not exists(
      select 1
      from public.shop_product_options po
      where po.product_id=p_product_id
        and po.status='active'
        and upper(po.code)=upper(a.code)
        and exists(
          select 1 from public.shop_product_option_values pov
          where pov.product_option_id=po.id
        )
    );

  select count(*) into invalid_variant_count
  from public.shop_product_variants v
  left join public.shop_inventory inv on inv.variant_id=v.id
  where v.product_id=p_product_id and v.status='active' and (
    v.price_minor<=0 or inv.id is null or inv.quantity_available<inv.quantity_reserved
    or (select count(*) from public.shop_variant_option_values vov where vov.variant_id=v.id)<>active_option_count
  );

  if length(trim(product_record.title))<3 then errors:=errors||'["Product name is required."]'::jsonb; end if;
  if product_record.category_id is null then errors:=errors||'["Choose a category."]'::jsonb; end if;
  if length(trim(coalesce(product_record.description,'')))<12 then errors:=errors||'["Add a product description."]'::jsonb; end if;
  if image_count<1 then errors:=errors||'["Add at least one product image."]'::jsonb; end if;
  if missing_required_count>0 then errors:=errors||'["Complete the required category specifications."]'::jsonb; end if;
  if active_option_count>0 and active_variant_count<1 then errors:=errors||'["Add at least one sellable variant."]'::jsonb; end if;
  if active_variant_count>0 and invalid_variant_count>0 then errors:=errors||'["Check variant price, stock and option combinations."]'::jsonb; end if;
  if active_variant_count=0 and product_record.price_minor<=0 then errors:=errors||'["Enter a valid price."]'::jsonb; end if;
  if active_variant_count=0 and product_record.stock_status<>'out_of_stock' and legacy_stock<1 then errors:=errors||'["Add available stock."]'::jsonb; end if;
  if jsonb_array_length(errors)>0 then raise exception 'product-validation-failed:%',errors::text; end if;

  update public.shop_products set
    status='draft',moderation_status='submitted',submitted_at=now(),reviewed_at=null,
    reviewed_by_profile_id=null,validation_errors='[]'::jsonb,
    submitted_version=submitted_version+1,updated_at=now()
  where id=p_product_id returning * into product_record;

  insert into public.shop_product_status_history(
    product_id,actor_profile_id,actor_role,event_code,previous_status,next_status,
    previous_moderation_status,next_moderation_status,public_message,request_id
  ) values (
    p_product_id,p_profile_id,'seller','seller.product.submit',previous_status,'draft',
    previous_moderation_status,'submitted','Product submitted for review.',nullif(trim(coalesce(p_request_id,'')),'')
  ) on conflict do nothing;
  perform public.adishop_create_notification(
    p_profile_id,'product_submitted','Product submitted',
    'Your product was submitted for AdisGebeya review.',jsonb_build_object('productId',p_product_id)
  );
  return public.adishop_seller_product_summary(product_record);
end;
$$;

revoke all on function public.adishop_seller_submit_product(uuid,uuid,text) from public, anon, authenticated;
grant execute on function public.adishop_seller_submit_product(uuid,uuid,text) to service_role;
