-- Local-only SQL/RLS checks; not an HTTP/Auth parity test.
do $$
declare
  approved_id uuid := '20000000-0000-4000-8000-000000000001';
  pending_id uuid := '20000000-0000-4000-8000-000000000002';
  approved_image_id uuid := '50000000-0000-4000-8000-000000000001';
  pending_image_id uuid := '50000000-0000-4000-8000-000000000002';
  original_title text;
  rejected boolean;
  passed integer := 0;
begin
  select title into original_title from public.shop_products where id=approved_id;
  rejected := false;
  begin
    update public.shop_products set title='IMEI: 123456789012345' where id=approved_id;
  exception when others then rejected := sqlerrm='public-product-contains-private-identifier'; end;
  if not rejected or (select title from public.shop_products where id=approved_id)<>original_title then
    raise exception 'approved title mutation leaked';
  end if;
  passed := passed+1;
  rejected := false;
  begin
    update public.shop_products set short_description='Serial Number: ABC123456789' where id=approved_id;
  exception when others then rejected := sqlerrm='public-product-contains-private-identifier'; end;
  if not rejected then raise exception 'approved summary mutation leaked'; end if;
  passed := passed+1;
  rejected := false;
  begin
    update public.shop_products set description='MAC address: 01:23:45:67:89:AB' where id=approved_id;
  exception when others then rejected := sqlerrm='public-product-contains-private-identifier'; end;
  if not rejected then raise exception 'approved description mutation leaked'; end if;
  passed := passed+1;
  rejected := false;
  begin
    update public.shop_product_images set alt_text='Serial: ABC123456789' where id=approved_image_id;
  exception when others then rejected := sqlerrm='public-image-alt-contains-private-identifier'; end;
  if not rejected or (select alt_text from public.shop_product_images where id=approved_image_id)<>'Synthetic laptop' then
    raise exception 'approved image alt mutation leaked';
  end if;
  passed := passed+1;

  update public.shop_products set title='Serial Number: ABC123456789' where id=pending_id;
  if (select title from public.shop_products where id=pending_id) <> 'Serial Number: ABC123456789' then
    raise exception 'seller draft was altered';
  end if;
  passed := passed+1;
  rejected := false;
  begin
    update public.shop_products set status='active',moderation_status='approved' where id=pending_id;
  exception when others then rejected := sqlerrm='public-product-contains-private-identifier'; end;
  if not rejected or (select moderation_status from public.shop_products where id=pending_id)='approved' then
    raise exception 'private draft was published';
  end if;
  passed := passed+1;

  update public.shop_products set title='Safe older listing' where id=pending_id;
  insert into public.shop_product_images(id,product_id,alt_text) values
    (pending_image_id,pending_id,'S/N: ABC123456789');
  rejected := false;
  begin
    update public.shop_products set status='active',moderation_status='approved' where id=pending_id;
  exception when others then rejected := sqlerrm='public-product-contains-private-identifier'; end;
  if not rejected then raise exception 'private draft image was published'; end if;
  passed := passed+1;
  update public.shop_product_images set deleted_at=now() where id=pending_image_id;
  update public.shop_products set status='active',moderation_status='approved' where id=pending_id;
  if (select moderation_status from public.shop_products where id=pending_id)<>'approved' then
    raise exception 'safe older listing could not publish';
  end if;
  passed := passed+1;

  if not has_column_privilege('anon','public.shop_products','title','SELECT') or
     not has_column_privilege('authenticated','public.shop_products','short_description','SELECT') or
     has_table_privilege('anon','public.shop_product_attribute_values','SELECT') then
    raise exception 'synthetic legacy role grants mismatch';
  end if;
  passed := passed+1;
  if public.adishop_phase9b4_redact_identifiers('MAC address: 01:23:45:67:89:AB') ~* '01:23:45:67:89:AB' then
    raise exception 'detail MAC redaction failed';
  end if;
  passed := passed+1;
  raise notice 'Phase 9B.6 listing-privacy SQL checks passed: %',passed;
end;
$$;
