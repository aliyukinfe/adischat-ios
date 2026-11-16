-- Local-only proposal. Depends on Phase 9B.11 reviewed-content preparation.
begin;
create or replace function public.adishop_public_reviewed_media_locator(p_kind text,p_id uuid)
returns jsonb language plpgsql stable security definer
set search_path = public, shop_catalog_private as $$
declare result jsonb;
begin
  if p_kind = 'image' then
    select jsonb_build_object('kind','image','id',i.id,'revision',r.revision,
      'bucket',i.bucket,'storagePath',i.storage_path,
      'productId',p.id,'storeId',p.store_id)
    into result
    from public.shop_product_images i
    join public.shop_products p on p.id=i.product_id and p.store_id=i.store_id
    join shop_catalog_private.content_revisions r
      on r.entity_kind='image' and r.entity_id=i.id
    where i.id=p_id and i.deleted_at is null
      and i.bucket='shop-product-images' and i.storage_path is not null
      and shop_catalog_private.is_current_reviewed('image',i.id)
      and shop_catalog_private.is_public_product(p.id);
  elsif p_kind = 'product-cover' then
    select jsonb_build_object('kind','product-cover','id',p.id,'revision',r.revision,
      'bucket',i.bucket,'storagePath',i.storage_path,
      'productId',p.id,'storeId',p.store_id)
    into result
    from public.shop_products p
    join public.shop_product_images i on i.product_id=p.id and i.store_id=p.store_id
    join shop_catalog_private.content_revisions r
      on r.entity_kind='image' and r.entity_id=i.id
    where p.id=p_id and i.deleted_at is null
      and i.bucket='shop-product-images' and i.storage_path is not null
      and shop_catalog_private.is_current_reviewed('image',i.id)
      and shop_catalog_private.is_public_product(p.id)
    order by i.is_primary desc nulls last,i.display_order,i.id limit 1;
  elsif p_kind in ('store-logo','store-cover') then
    select jsonb_build_object('kind',p_kind,'id',s.id,'revision',r.revision,
      'bucket','shop-store-assets','sourceUrl',
      case when p_kind='store-logo' then review.public_payload->>'logoUrl'
        else review.public_payload->>'coverUrl' end)
    into result
    from public.shop_stores s
    join shop_catalog_private.content_revisions r
      on r.entity_kind='store' and r.entity_id=s.id
    join shop_catalog_private.reviewed_content review
      on review.entity_kind='store' and review.entity_id=s.id
      and review.source_version=r.revision::text and review.decision='approved'
    where s.id=p_id and s.status='active' and s.approval_status='approved'
      and s.deleted_at is null
      and shop_catalog_private.is_current_reviewed('store',s.id);
  end if;
  return result;
end;
$$;
revoke all on function public.adishop_public_reviewed_media_locator(text,uuid)
  from public,anon,authenticated;
grant execute on function public.adishop_public_reviewed_media_locator(text,uuid)
  to service_role;
commit;
