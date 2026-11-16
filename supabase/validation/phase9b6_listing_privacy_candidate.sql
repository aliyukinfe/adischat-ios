-- LOCAL REVIEW PROPOSAL ONLY. Do not execute on production.
-- Companion to phase9b4_electronics_atomic_candidate.sql. Existing clients read
-- public.shop_products directly, so its public text must be safe at publication.
begin;
set local lock_timeout = '5s';
lock table public.shop_products, public.shop_product_images,
  public.shop_product_variants, public.shop_stores in share row exclusive mode;

create or replace function public.adishop_phase9b6_has_device_identifier(p_text text)
returns boolean language sql immutable security definer set search_path = public as $$
  select coalesce(p_text ~* '(IMEI|serial number|serial no|serial|S/N|MAC address)[[:space:]#:=/-]*[A-Za-z0-9:-]{6,}', false)
$$;
revoke all on function public.adishop_phase9b6_has_device_identifier(text) from public, anon, authenticated;

create or replace function public.adishop_phase9b6_variant_has_identifier(p_title text, p_options jsonb)
returns boolean language sql immutable security definer set search_path = public as $$
  select public.adishop_phase9b6_has_device_identifier(p_title)
    or public.adishop_phase9b6_has_device_identifier(p_options::text)
    or exists (select 1 from jsonb_each_text(coalesce(p_options,'{}'::jsonb)) option_value
      where upper(option_value.key) ~ '(IMEI|SERIAL|DEVICE_ID|MAC_ADDRESS)'
        and nullif(trim(option_value.value),'') is not null)
$$;
revoke all on function public.adishop_phase9b6_variant_has_identifier(text,jsonb) from public, anon, authenticated;

do $$
begin
  if exists (select 1 from public.shop_stores s where s.status='active'
      and s.approval_status='approved' and s.deleted_at is null
      and (public.adishop_phase9b6_has_device_identifier(s.name)
        or public.adishop_phase9b6_has_device_identifier(s.description))) then
    raise exception 'existing-public-store-private-identifier; stop for reviewed remediation';
  end if;
  if exists (select 1 from public.shop_products p where p.status='active'
      and p.moderation_status='approved' and p.deleted_at is null and (
        public.adishop_phase9b6_has_device_identifier(p.title)
        or public.adishop_phase9b6_has_device_identifier(p.short_description)
        or public.adishop_phase9b6_has_device_identifier(p.description)
        or exists (select 1 from public.shop_product_images i where i.product_id=p.id
          and i.deleted_at is null and public.adishop_phase9b6_has_device_identifier(i.alt_text))
        or exists (select 1 from public.shop_product_variants v where v.product_id=p.id
          and v.status='active' and public.adishop_phase9b6_variant_has_identifier(v.title,v.options))
      )) then
    raise exception 'existing-public-private-identifier; stop for reviewed remediation';
  end if;
end;
$$;

create or replace function public.adishop_phase9b6_guard_public_image_alt()
returns trigger language plpgsql security definer set search_path = public as $$
begin
  if new.deleted_at is null and public.adishop_phase9b6_has_device_identifier(new.alt_text)
    and exists (select 1 from public.shop_products p where p.id=new.product_id
      and p.status='active' and p.moderation_status='approved' and p.deleted_at is null) then
    raise exception 'public-image-alt-contains-private-identifier';
  end if;
  return new;
end;
$$;
revoke all on function public.adishop_phase9b6_guard_public_image_alt() from public, anon, authenticated;

drop trigger if exists adishop_phase9b6_guard_public_image_alt on public.shop_product_images;
create trigger adishop_phase9b6_guard_public_image_alt
before insert or update of alt_text,deleted_at,product_id
on public.shop_product_images for each row execute function public.adishop_phase9b6_guard_public_image_alt();

create or replace function public.adishop_phase9b6_guard_public_variant()
returns trigger language plpgsql security definer set search_path = public as $$
begin
  if new.status='active' and
    public.adishop_phase9b6_variant_has_identifier(new.title,new.options) and
    exists (select 1 from public.shop_products p where p.id=new.product_id
      and p.status='active' and p.moderation_status='approved' and p.deleted_at is null) then
    raise exception 'public-variant-contains-private-identifier';
  end if;
  return new;
end;
$$;
revoke all on function public.adishop_phase9b6_guard_public_variant() from public, anon, authenticated;
drop trigger if exists adishop_phase9b6_guard_public_variant on public.shop_product_variants;
create trigger adishop_phase9b6_guard_public_variant
before insert or update of title,options,status,product_id
on public.shop_product_variants for each row execute function public.adishop_phase9b6_guard_public_variant();

create or replace function public.adishop_phase9b6_guard_public_store()
returns trigger language plpgsql security definer set search_path = public as $$
begin
  if new.status='active' and new.approval_status='approved' and new.deleted_at is null
    and (public.adishop_phase9b6_has_device_identifier(new.name)
      or public.adishop_phase9b6_has_device_identifier(new.description)) then
    raise exception 'public-store-contains-private-identifier';
  end if;
  return new;
end;
$$;
revoke all on function public.adishop_phase9b6_guard_public_store() from public, anon, authenticated;
drop trigger if exists adishop_phase9b6_guard_public_store on public.shop_stores;
create trigger adishop_phase9b6_guard_public_store
before insert or update of name,description,status,approval_status,deleted_at
on public.shop_stores for each row execute function public.adishop_phase9b6_guard_public_store();

-- Publication of a pending product with pre-existing sensitive image alt text
-- also needs a product-side check: the image trigger does not fire on approval.
create or replace function public.adishop_phase9b6_guard_public_product()
returns trigger language plpgsql security definer set search_path = public as $$
begin
  if new.status = 'active' and new.moderation_status = 'approved' and new.deleted_at is null then
    -- Wait for in-flight child edits before checking the public snapshot.
    perform 1 from public.shop_product_images i
      where i.product_id=new.id and i.deleted_at is null for share;
    perform 1 from public.shop_product_variants v
      where v.product_id=new.id and v.status='active' for share;
    if public.adishop_phase9b6_has_device_identifier(new.title)
      or public.adishop_phase9b6_has_device_identifier(new.short_description)
      or public.adishop_phase9b6_has_device_identifier(new.description)
      or exists (select 1 from public.shop_product_images i where i.product_id=new.id
        and i.deleted_at is null and public.adishop_phase9b6_has_device_identifier(i.alt_text))
      or exists (select 1 from public.shop_product_variants v where v.product_id=new.id
        and v.status='active' and public.adishop_phase9b6_variant_has_identifier(v.title,v.options)) then
      raise exception 'public-product-contains-private-identifier';
    end if;
  end if;
  return new;
end;
$$;
revoke all on function public.adishop_phase9b6_guard_public_product() from public, anon, authenticated;
drop trigger if exists adishop_phase9b6_guard_public_product on public.shop_products;
create trigger adishop_phase9b6_guard_public_product
before insert or update of title,short_description,description,status,moderation_status,deleted_at
on public.shop_products for each row execute function public.adishop_phase9b6_guard_public_product();
commit;
