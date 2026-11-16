-- Staging-only candidate. Do not apply until separately approved.
begin;

create or replace function public.adishop_qa_moderation_queue(
  p_session_jti text, p_kind text default null, p_limit integer default 20, p_offset integer default 0)
returns jsonb language plpgsql volatile security definer set search_path = '' as $$
declare actor uuid; result jsonb;
begin
  actor := shop_qa_private.locked_actor(p_session_jti);
  perform 1 from public.shop_user_roles ur join public.shop_roles r on r.id=ur.role_id
    where ur.profile_id=actor and ur.revoked_at is null
      and r.code in ('SHOP_ADMIN','SHOP_SUPER_ADMIN') for share of ur;
  if not found then raise exception 'review-forbidden' using errcode='42501'; end if;
  if (p_kind is not null and p_kind not in ('store','product','image')) or
     p_limit is null or p_limit not between 1 and 50 or
     p_offset is null or p_offset < 0 then
    raise exception 'invalid-review-input' using errcode='22023'; end if;
  with items as (
    select 'store'::text kind,s.id,s.id target_id,s.updated_at revision,null::integer image_revision,
      case when rs.store_updated_at=s.updated_at then 'approved'
        when exists(select 1 from public.shop_audit_logs a where a.entity_id=s.id
          and a.action='qa.store.reject' and a.metadata @> jsonb_build_object('expectedUpdatedAt',s.updated_at))
          then 'rejected' else 'pending' end review_status
      from public.shop_stores s left join public.shop_qa_reviewed_stores rs on rs.store_id=s.id
      where s.deleted_at is null and s.owner_profile_id is distinct from actor
        and (p_kind is null or p_kind='store')
    union all
    select 'product',p.id,p.store_id,p.updated_at,null::integer,
      case when rc.product_updated_at=p.updated_at then 'approved'
        when exists(select 1 from public.shop_audit_logs a where a.entity_id=p.id
          and a.action='qa.review.reject' and a.metadata @> jsonb_build_object('expectedUpdatedAt',p.updated_at))
          then 'rejected' else 'pending' end
      from public.shop_products p left join public.shop_qa_reviewed_catalog rc on rc.product_id=p.id
      join public.shop_stores s on s.id=p.store_id and s.deleted_at is null
      where p.deleted_at is null and s.owner_profile_id is distinct from actor
        and (p_kind is null or p_kind='product')
    union all
    select 'image',i.id,p.id,null::timestamptz,i.revision,
      case when ri.image_revision=i.revision then 'approved'
        when exists(select 1 from public.shop_audit_logs a where a.entity_id=i.id
          and a.action='qa.media.reject' and a.metadata @> jsonb_build_object('revision',i.revision))
          then 'rejected' else 'pending' end
      from public.shop_product_images i
      join public.shop_products p on p.id=i.product_id and p.deleted_at is null
      join public.shop_stores s on s.id=p.store_id and s.deleted_at is null
      left join public.shop_qa_reviewed_images ri on ri.image_id=i.id
      where s.owner_profile_id is distinct from actor and (p_kind is null or p_kind='image')
  ), page as (
    select * from items order by kind,id limit p_limit offset p_offset
  )
  select jsonb_build_object('total',(select count(*) from items),
    'items',coalesce(jsonb_agg(jsonb_build_object('entityKind',kind,'entityId',id,
      'targetId',target_id,'reviewStatus',review_status,
      'expectedRevision',case when kind='image' then to_jsonb(image_revision) else to_jsonb(revision) end)
      order by kind,id),'[]'::jsonb)) into result from page;
  return result;
end $$;
revoke all on function public.adishop_qa_moderation_queue(text,text,integer,integer) from public,anon,authenticated;
grant execute on function public.adishop_qa_moderation_queue(text,text,integer,integer) to service_role;

create or replace function public.adishop_qa_moderation_preview(
  p_session_jti text,p_kind text,p_id uuid)
returns jsonb language plpgsql volatile security definer set search_path = '' as $$
declare actor uuid; result jsonb;
begin
  actor := shop_qa_private.locked_actor(p_session_jti);
  perform 1 from public.shop_user_roles ur join public.shop_roles r on r.id=ur.role_id
    where ur.profile_id=actor and ur.revoked_at is null
      and r.code in ('SHOP_ADMIN','SHOP_SUPER_ADMIN') for share of ur;
  if not found then raise exception 'review-forbidden' using errcode='42501'; end if;
  if p_id is null or p_kind is null or p_kind not in ('store','product','image') then
    raise exception 'invalid-review-input' using errcode='22023'; end if;
  if p_kind='store' then
    select jsonb_build_object('entityKind','store','entityId',s.id,'targetId',s.id,
      'expectedRevision',s.updated_at,'reviewStatus',
      case when rs.store_updated_at=s.updated_at then 'approved'
        when exists(select 1 from public.shop_audit_logs a where a.entity_id=s.id
          and a.action='qa.store.reject' and a.metadata @> jsonb_build_object('expectedUpdatedAt',s.updated_at))
          then 'rejected' else 'pending' end,
      'content',jsonb_build_object('name',s.name,'description',s.description,'city',s.city,
        'status',s.status,'approvalStatus',s.approval_status),
      'permittedActions',case when s.status='active' and s.approval_status='approved'
        then jsonb_build_array('approve','reject','revoke') else jsonb_build_array('reject','revoke') end)
      into result from public.shop_stores s left join public.shop_qa_reviewed_stores rs on rs.store_id=s.id
      where s.id=p_id and s.deleted_at is null and s.owner_profile_id is distinct from actor for share of s;
  elsif p_kind='product' then
    select jsonb_build_object('entityKind','product','entityId',p.id,'targetId',p.store_id,
      'expectedRevision',p.updated_at,'reviewStatus',
      case when rc.product_updated_at=p.updated_at then 'approved'
        when exists(select 1 from public.shop_audit_logs a where a.entity_id=p.id
          and a.action='qa.review.reject' and a.metadata @> jsonb_build_object('expectedUpdatedAt',p.updated_at))
          then 'rejected' else 'pending' end,
      'content',jsonb_build_object('title',p.title,'shortDescription',p.short_description,
        'moderationStatus',p.moderation_status,'status',p.status),
      'permittedActions',jsonb_build_array('approve','reject'))
      into result from public.shop_products p join public.shop_stores s on s.id=p.store_id
      left join public.shop_qa_reviewed_catalog rc on rc.product_id=p.id
      where p.id=p_id and p.deleted_at is null and s.deleted_at is null
        and s.owner_profile_id is distinct from actor for share of p,s;
  else
    select jsonb_build_object('entityKind','image','entityId',i.id,'targetId',p.id,
      'expectedRevision',i.revision,'reviewStatus',
      case when ri.image_revision=i.revision then 'approved'
        when exists(select 1 from public.shop_audit_logs a where a.entity_id=i.id
          and a.action='qa.media.reject' and a.metadata @> jsonb_build_object('revision',i.revision))
          then 'rejected' else 'pending' end,
      'content',jsonb_build_object('altText',i.alt_text,'isPrimary',i.is_primary,
        'displayOrder',i.display_order),
      'permittedActions',jsonb_build_array('reject'))
      into result from public.shop_product_images i
      join public.shop_products p on p.id=i.product_id and p.deleted_at is null
      join public.shop_stores s on s.id=p.store_id and s.deleted_at is null
      left join public.shop_qa_reviewed_images ri on ri.image_id=i.id
      where i.id=p_id and s.owner_profile_id is distinct from actor for share of i,p,s;
  end if;
  if result is null then raise exception 'review-target-not-found' using errcode='22023'; end if;
  return result;
end $$;
revoke all on function public.adishop_qa_moderation_preview(text,text,uuid) from public,anon,authenticated;
grant execute on function public.adishop_qa_moderation_preview(text,text,uuid) to service_role;
commit;
