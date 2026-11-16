-- Synthetic fixture only. Human review is mandatory for real listings.
do $$
declare actor uuid:='40000000-0000-4000-8000-000000000002'; row record;
begin
  for row in select entity_kind,entity_id,revision from shop_catalog_private.content_revisions
    order by case entity_kind when 'store' then 1 when 'image' then 2
      when 'variant' then 3 else 4 end loop
    perform public.adishop_review_public_content(actor,row.entity_kind,row.entity_id,row.revision,'approved');
  end loop;
  if (public.adishop_admin_public_content_queue(actor,null,50,0)->>'total')::int<>0 then
    raise exception 'fixture reviews incomplete'; end if;
  raise notice 'phase9b11 synthetic review coverage: 4/4';
end $$;
