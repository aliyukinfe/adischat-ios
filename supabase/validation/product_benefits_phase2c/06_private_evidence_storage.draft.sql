-- REVIEW DRAFT ONLY. Execute only in isolated staging after inspecting every
-- existing storage.objects policy. Never apply to production without approval.
begin;

insert into storage.buckets(id,name,public,file_size_limit,allowed_mime_types)
values ('shop-benefit-evidence','shop-benefit-evidence',false,10485760,
  array['image/jpeg','image/png','application/pdf']::text[])
on conflict (id) do nothing;

do $$ begin
  if not exists (select 1 from storage.buckets
    where id='shop-benefit-evidence' and name='shop-benefit-evidence'
      and public=false and file_size_limit=10485760
      and allowed_mime_types=array['image/jpeg','image/png','application/pdf']::text[])
    then raise exception 'benefit-evidence-bucket-configuration-conflict'; end if;
end $$;

drop policy if exists shop_benefit_evidence_no_direct_client_access on storage.objects;
create policy shop_benefit_evidence_no_direct_client_access
on storage.objects as restrictive for all to anon,authenticated
using (bucket_id <> 'shop-benefit-evidence')
with check (bucket_id <> 'shop-benefit-evidence');

commit;
