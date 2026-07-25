create or replace function public.adishop_seller_update_product(
  p_profile_id uuid,
  p_product_id uuid,
  p_payload jsonb,
  p_request_id text default null
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare store_record public.shop_stores%rowtype;
declare product_record public.shop_products%rowtype;
declare category_record public.shop_categories%rowtype;
declare new_title text;
declare new_slug text;
declare new_status text;
declare new_moderation_status text;
declare image jsonb;
declare variant jsonb;
declare new_variant_id uuid;
declare image_count integer := 0;
declare previous_status text;
declare previous_moderation_status text;
begin
  store_record := public.adishop_seller_owned_store(p_profile_id);
  select * into product_record from public.shop_products where id = p_product_id and store_id = store_record.id and deleted_at is null for update;
  if not found then raise exception 'seller-product-not-found'; end if;
  if product_record.moderation_status in ('under_review','suspended','archived') then raise exception 'seller-product-locked'; end if;
  previous_status := product_record.status;
  previous_moderation_status := product_record.moderation_status;

  if p_payload ? 'categoryId' and nullif(trim(p_payload->>'categoryId'), '') is not null then
    select * into category_record from public.shop_categories where id = (p_payload->>'categoryId')::uuid and is_active = true;
    if not found then raise exception 'category-not-found'; end if;
  elsif product_record.category_id is not null then
    select * into category_record from public.shop_categories where id = product_record.category_id;
  end if;

  new_title := nullif(trim(coalesce(p_payload->>'title', product_record.title)), '');
  if new_title is null then new_title := product_record.title; end if;
  new_slug := lower(regexp_replace(regexp_replace(new_title, '[^a-zA-Z0-9]+', '-', 'g'), '(^-|-$)', '', 'g'));
  if new_slug = '' then new_slug := 'product-' || replace(p_product_id::text, '-', ''); end if;
  new_slug := new_slug || '-' || substring(replace(p_product_id::text, '-', '') from 1 for 8);

  new_status := product_record.status;
  new_moderation_status := product_record.moderation_status;
  if product_record.moderation_status in ('approved','rejected','changes_requested') then
    new_status := 'draft';
    new_moderation_status := 'pending';
  end if;

  update public.shop_products
  set title = new_title,
      slug = new_slug,
      brand = nullif(trim(coalesce(p_payload->>'brand', brand)), ''),
      category_id = coalesce(category_record.id, category_id),
      category_slug = coalesce(category_record.slug, category_slug),
      short_description = left(coalesce(p_payload->>'shortDescription', p_payload->>'short_description', short_description, ''), 240),
      description = left(coalesce(p_payload->>'description', description, ''), 5000),
      status = new_status,
      moderation_status = new_moderation_status,
      price_minor = greatest(0, coalesce((p_payload->>'priceMinor')::integer, price_minor)),
      compare_at_price_minor = nullif((p_payload->>'compareAtPriceMinor')::integer, 0),
      discount_percent = null,
      stock_status = case when coalesce((p_payload->>'stockQuantity')::integer, 0) <= 0 then 'out_of_stock' else 'in_stock' end,
      cash_on_delivery_enabled = coalesce((p_payload->>'cashOnDeliveryEnabled')::boolean, cash_on_delivery_enabled),
      product_type = case when p_payload->>'productType' in ('physical','digital') then p_payload->>'productType' else product_type end,
      seller_sku = nullif(trim(coalesce(p_payload->>'sellerSku', seller_sku)), ''),
      specifications = coalesce(p_payload->'specifications', specifications, '{}'::jsonb),
      shipping_details = coalesce(p_payload->'shippingDetails', shipping_details, '{}'::jsonb),
      return_policy = coalesce(p_payload->'returnPolicy', return_policy, '{}'::jsonb),
      updated_at = now()
  where id = p_product_id
  returning * into product_record;

  if p_payload ? 'images' then
    delete from public.shop_product_images where product_id = p_product_id;
    for image in select * from jsonb_array_elements(coalesce(p_payload->'images', '[]'::jsonb)) loop
      if nullif(trim(image->>'imageUrl'), '') is not null and image_count < 8 then
        insert into public.shop_product_images(product_id, image_url, storage_path, alt_text, is_primary, display_order)
        values (
          p_product_id,
          left(trim(image->>'imageUrl'), 2000),
          nullif(trim(coalesce(image->>'storagePath', '')), ''),
          nullif(trim(coalesce(image->>'altText', '')), ''),
          image_count = 0,
          image_count
        );
        image_count := image_count + 1;
      end if;
    end loop;
  end if;

  if p_payload ? 'variants' then
    delete from public.shop_inventory inv where inv.product_id = p_product_id and inv.variant_id is not null;
    delete from public.shop_product_variants v where v.product_id = p_product_id;
    for variant in select * from jsonb_array_elements(coalesce(p_payload->'variants', '[]'::jsonb)) loop
      if nullif(trim(variant->>'title'), '') is not null then
        insert into public.shop_product_variants(product_id, sku, title, options, currency, price_minor, compare_at_price_minor, status)
        values (
          p_product_id,
          nullif(trim(coalesce(variant->>'sku', '')), ''),
          left(trim(variant->>'title'), 120),
          coalesce(variant->'options', '{}'::jsonb),
          'ETB',
          greatest(0, coalesce((variant->>'priceMinor')::integer, product_record.price_minor)),
          nullif((variant->>'compareAtPriceMinor')::integer, 0),
          case when variant->>'status' in ('active','paused','archived') then variant->>'status' else 'active' end
        )
        returning id into new_variant_id;

        insert into public.shop_inventory(product_id, variant_id, quantity_available, low_stock_threshold)
        values (
          p_product_id,
          new_variant_id,
          greatest(0, coalesce((variant->>'quantityAvailable')::integer, 0)),
          greatest(0, coalesce((variant->>'lowStockThreshold')::integer, 3))
        );
      end if;
    end loop;
  end if;

  if p_payload ? 'stockQuantity' then
    update public.shop_inventory
    set quantity_available = greatest(0, coalesce((p_payload->>'stockQuantity')::integer, 0)),
        low_stock_threshold = greatest(0, coalesce((p_payload->>'lowStockThreshold')::integer, 3)),
        updated_at = now()
    where shop_inventory.product_id = p_product_id and shop_inventory.variant_id is null;
    if not found then
      insert into public.shop_inventory(product_id, variant_id, quantity_available, low_stock_threshold, updated_at)
      values (p_product_id, null, greatest(0, coalesce((p_payload->>'stockQuantity')::integer, 0)), greatest(0, coalesce((p_payload->>'lowStockThreshold')::integer, 3)), now());
    end if;
  end if;

  insert into public.shop_product_status_history(
    product_id, actor_profile_id, actor_role, event_code, previous_status, next_status,
    previous_moderation_status, next_moderation_status, public_message, request_id
  )
  values (
    p_product_id, p_profile_id, 'seller', 'seller.product.update',
    previous_status, new_status, previous_moderation_status, new_moderation_status,
    'Product draft updated.', nullif(trim(coalesce(p_request_id, '')), '')
  )
  on conflict do nothing;

  return public.adishop_seller_get_product(p_profile_id, p_product_id);
end;
$$;

