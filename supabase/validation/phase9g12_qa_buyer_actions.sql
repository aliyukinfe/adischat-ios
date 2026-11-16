-- Disposable local QA candidate. Requires phase9g11_qa_atomic_candidate.sql.
begin;

create or replace function public.adishop_qa_favorite_action(
  p_session_jti text, p_action text, p_product_id uuid)
returns jsonb language plpgsql volatile security definer set search_path='' as $$
declare actor uuid; result jsonb;
begin
  actor := shop_qa_private.locked_actor(p_session_jti);
  if p_product_id is null or p_action not in ('add','remove') then
    raise exception 'invalid-favorite-input' using errcode='22023';
  end if;
  if p_action='add' then
    if not exists(select 1 from public.shop_qa_public_catalog where id=p_product_id) then
      raise exception 'product-unavailable' using errcode='22023';
    end if;
    insert into public.shop_favorites(profile_id,product_id)
      values(actor,p_product_id) on conflict(profile_id,product_id) do nothing;
  else
    delete from public.shop_favorites where profile_id=actor and product_id=p_product_id;
  end if;
  select jsonb_build_object('id',id,'product_id',product_id,'created_at',created_at)
    into result from public.shop_favorites where profile_id=actor and product_id=p_product_id;
  return coalesce(result,'{}'::jsonb);
end $$;
revoke all on function public.adishop_qa_favorite_action(text,text,uuid) from public,anon,authenticated;
grant execute on function public.adishop_qa_favorite_action(text,text,uuid) to service_role;

create or replace function public.adishop_qa_address_action(
  p_session_jti text,p_action text,p_address_id uuid default null,
  p_label text default null,p_recipient_name text default null,p_phone text default null,
  p_city text default null,p_address_line1 text default null,
  p_address_line2 text default null,p_is_default boolean default false)
returns jsonb language plpgsql volatile security definer set search_path='' as $$
declare actor uuid; result jsonb;
begin
  actor := shop_qa_private.locked_actor(p_session_jti);
  if p_action not in ('create','update','delete','set_default') then
    raise exception 'invalid-address-action' using errcode='22023';
  end if;
  if p_action in ('update','delete','set_default') and not exists (
    select 1 from public.shop_addresses where id=p_address_id and profile_id=actor
      and deleted_at is null for update) then
    raise exception 'address-not-found' using errcode='22023';
  end if;
  if p_action in ('create','update') then
    if length(btrim(coalesce(p_recipient_name,''))) not between 1 and 120 or
       length(btrim(coalesce(p_phone,''))) not between 1 and 40 or
       length(btrim(coalesce(p_city,''))) not between 1 and 120 or
       length(btrim(coalesce(p_address_line1,''))) not between 1 and 240 or
       length(coalesce(p_label,'')) > 60 or length(coalesce(p_address_line2,'')) > 240 then
      raise exception 'invalid-address-input' using errcode='22023';
    end if;
    if p_is_default then
      update public.shop_addresses set is_default=false,updated_at=now()
        where profile_id=actor and deleted_at is null and is_default;
    end if;
    if p_action='create' then
      insert into public.shop_addresses(profile_id,label,recipient_name,phone,city,
        address_line1,address_line2,is_default)
      values(actor,p_label,p_recipient_name,p_phone,p_city,p_address_line1,
        p_address_line2,p_is_default) returning id into p_address_id;
    else
      update public.shop_addresses set label=p_label,recipient_name=p_recipient_name,
        phone=p_phone,city=p_city,address_line1=p_address_line1,
        address_line2=p_address_line2,is_default=p_is_default,updated_at=now()
        where id=p_address_id and profile_id=actor and deleted_at is null;
    end if;
  elsif p_action='set_default' then
    update public.shop_addresses set is_default=false,updated_at=now()
      where profile_id=actor and deleted_at is null and is_default;
    update public.shop_addresses set is_default=true,updated_at=now()
      where id=p_address_id and profile_id=actor and deleted_at is null;
  else
    update public.shop_addresses set deleted_at=now(),is_default=false,updated_at=now()
      where id=p_address_id and profile_id=actor and deleted_at is null;
  end if;
  select jsonb_build_object('id',id,'label',label,'recipient_name',recipient_name,
    'phone',phone,'city',city,'address_line1',address_line1,
    'address_line2',address_line2,'is_default',is_default)
    into result from public.shop_addresses where id=p_address_id and profile_id=actor;
  return result;
end $$;
revoke all on function public.adishop_qa_address_action(text,text,uuid,text,text,text,text,text,text,boolean)
  from public,anon,authenticated;
grant execute on function public.adishop_qa_address_action(text,text,uuid,text,text,text,text,text,text,boolean)
  to service_role;

create or replace function public.adishop_qa_cart_action(
  p_session_jti text,p_action text,p_product_id uuid default null,
  p_variant_id uuid default null,p_item_id uuid default null,p_quantity integer default null)
returns jsonb language plpgsql volatile security definer set search_path='' as $$
declare actor uuid; v_cart_id uuid; line record; unit_price integer; available integer;
  current_quantity integer; variants_exist boolean; result jsonb;
begin
  actor:=shop_qa_private.locked_actor(p_session_jti);
  if p_action not in ('read','add','update','remove','clear') then
    raise exception 'invalid-cart-action' using errcode='22023'; end if;
  select id into v_cart_id from public.shop_carts
    where profile_id=actor and status='active' and currency='ETB' for update;
  if p_action='add' then
    if p_product_id is null or p_quantity not between 1 and 99 then
      raise exception 'invalid-cart-input' using errcode='22023'; end if;
    select price_minor into unit_price from public.shop_qa_public_catalog where id=p_product_id;
    if unit_price is null then raise exception 'product-unavailable' using errcode='22023'; end if;
    select exists(select 1 from public.shop_product_variants
      where product_id=p_product_id and status='active') into variants_exist;
    if variants_exist and p_variant_id is null then
      raise exception 'variant-required' using errcode='22023'; end if;
    if p_variant_id is not null then
      select reviewed.price_minor into unit_price
        from public.shop_product_variants variant
        join public.shop_qa_reviewed_variants reviewed on reviewed.variant_id=variant.id
          and reviewed.variant_updated_at=variant.updated_at
        where variant.id=p_variant_id and variant.product_id=p_product_id and variant.status='active'
          and public.adishop_qa_variant_reviewed(variant.id,
            (select store_id from public.shop_products where id=p_product_id));
      if unit_price is null then raise exception 'variant-unavailable' using errcode='22023'; end if;
    end if;
    if v_cart_id is null then
      insert into public.shop_carts(profile_id,status,currency) values(actor,'active','ETB') returning id into v_cart_id;
    end if;
    select id,quantity into line from public.shop_cart_items
      where cart_id=v_cart_id and product_id=p_product_id
        and variant_id is not distinct from p_variant_id for update;
    current_quantity:=coalesce(line.quantity,0)+p_quantity;
  elsif p_action='update' or p_action='remove' then
    if v_cart_id is null or p_item_id is null then raise exception 'cart-item-not-found' using errcode='22023'; end if;
    select id,product_id,variant_id,quantity into line from public.shop_cart_items
      where id=p_item_id and cart_id=v_cart_id for update;
    if line.id is null then raise exception 'cart-item-not-found' using errcode='22023'; end if;
    if p_action='update' then
      if p_quantity not between 1 and 99 then raise exception 'invalid-quantity' using errcode='22023'; end if;
      current_quantity:=p_quantity; p_product_id:=line.product_id; p_variant_id:=line.variant_id;
    end if;
  elsif p_action='clear' then
    if v_cart_id is not null then
      delete from public.shop_cart_items where cart_id=v_cart_id;
    end if;
  end if;
  if p_action in ('add','update') then
    select quantity_available-quantity_reserved into available from public.shop_inventory
      where product_id=p_product_id and variant_id is not distinct from p_variant_id for share;
    if available is null or current_quantity>available then
      raise exception 'insufficient-inventory' using errcode='22023'; end if;
    if p_action='add' then
      if line.id is null then
        insert into public.shop_cart_items(cart_id,product_id,variant_id,quantity,unit_price_minor)
          values(v_cart_id,p_product_id,p_variant_id,current_quantity,unit_price);
      else update public.shop_cart_items set quantity=current_quantity,unit_price_minor=unit_price,
        updated_at=now() where id=line.id; end if;
    else update public.shop_cart_items set quantity=current_quantity,updated_at=now() where id=line.id; end if;
  elsif p_action='remove' then delete from public.shop_cart_items where id=line.id; end if;

  if v_cart_id is null then
    return null;
  end if;
  select jsonb_build_object(
    'id', v_cart_id,
    'currency', 'ETB',
    'totals', jsonb_build_object(
      'subtotalMinor', coalesce((select sum(quantity * unit_price_minor) from public.shop_cart_items where cart_id = v_cart_id), 0),
      'discountMinor', 0,
      'deliveryEstimateMinor', 0,
      'taxMinor', 0
    ),
    'items', coalesce((
      select jsonb_agg(jsonb_build_object(
        'id', item.id,
        'product_id', item.product_id,
        'variant_id', item.variant_id,
        'quantity', item.quantity,
        'unit_price_minor', item.unit_price_minor,
        'shop_products', (
          select jsonb_build_object(
            'id', p.id,
            'title', p.title,
            'short_description', p.short_description,
            'price_minor', p.price_minor,
            'currency', p.currency,
            'stock_status', p.stock_status,
            'shop_stores', jsonb_build_object('id', s.id, 'name', s.name, 'city', s.city),
            'shop_product_images', '[]'::jsonb
          )
          from public.shop_qa_public_catalog p
          join public.shop_qa_public_stores s on s.id = p.store_id
          where p.id = item.product_id
        ),
        'shop_product_variants', (
          select jsonb_build_object(
            'id', v.id,
            'title', rv.title,
            'options', rv.options,
            'price_minor', rv.price_minor,
            'status', v.status
          )
          from public.shop_product_variants v
          join public.shop_qa_reviewed_variants rv on rv.variant_id = v.id and rv.variant_updated_at = v.updated_at
          where v.id = item.variant_id and v.status = 'active'
        )
      ) order by item.created_at)
      from public.shop_cart_items item
      where item.cart_id = v_cart_id
    ), '[]'::jsonb)
  ) into result;
  return result;
end $$;
revoke all on function public.adishop_qa_cart_action(text,text,uuid,uuid,uuid,integer)
  from public,anon,authenticated;
grant execute on function public.adishop_qa_cart_action(text,text,uuid,uuid,uuid,integer) to service_role;

grant select on public.shop_qa_public_catalog to service_role;
grant select on public.shop_qa_reviewed_variants to service_role;

commit;
