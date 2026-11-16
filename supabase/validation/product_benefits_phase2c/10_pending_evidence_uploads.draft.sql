-- REVIEW DRAFT ONLY. Depends on 01, 03, 04, and 06. No scheduled cleanup.
begin;

create table public.shop_benefit_upload_jobs (
  id uuid primary key default gen_random_uuid(),
  case_id uuid not null references public.shop_benefit_cases(id) on delete restrict,
  store_id uuid not null references public.shop_stores(id) on delete restrict,
  actor_profile_id uuid not null references public.shop_profiles(id) on delete restrict,
  evidence_type text not null check (evidence_type in
    ('authorization_letter','manufacturer_terms','invoice','seller_terms','other')),
  mime_type text not null check (mime_type in ('image/jpeg','image/png','application/pdf')),
  size_bytes bigint not null check (size_bytes between 1 and 10485760),
  sha256_hex text not null check (sha256_hex ~ '^[0-9a-f]{64}$'),
  document_expires_at timestamptz,
  storage_path text not null unique,
  status text not null default 'pending' check (status in
    ('pending','complete','cleanup_claimed','cleanup_failed','cleaned')),
  evidence_id uuid references public.shop_benefit_evidence(id) on delete restrict,
  request_key text not null,
  created_at timestamptz not null default now(),
  last_attempt_at timestamptz not null default now(),
  completed_at timestamptz,
  cleaned_at timestamptz,
  cleanup_lease_id uuid,
  cleanup_lease_expires_at timestamptz,
  last_error_code text,
  unique (store_id,request_key),
  check (status <> 'complete' or (evidence_id is not null and completed_at is not null)),
  check (status <> 'cleanup_claimed' or
    (cleanup_lease_id is not null and cleanup_lease_expires_at is not null))
);
create unique index shop_benefit_upload_active_hash_idx
  on public.shop_benefit_upload_jobs(case_id,sha256_hex)
  where status <> 'cleaned';
create index shop_benefit_upload_cleanup_idx
  on public.shop_benefit_upload_jobs(status,last_attempt_at)
  where status in ('pending','cleanup_failed','cleanup_claimed');
alter table public.shop_benefit_upload_jobs enable row level security;
revoke all on public.shop_benefit_upload_jobs from public, anon, authenticated, service_role;

create table public.shop_benefit_upload_events (
  id uuid primary key default gen_random_uuid(),
  job_id uuid not null references public.shop_benefit_upload_jobs(id) on delete restrict,
  actor_profile_id uuid references public.shop_profiles(id) on delete restrict,
  action text not null check (action in
    ('reserved','finalized','cleanup_claimed','cleanup_failed','cleaned')),
  request_key uuid not null unique,
  created_at timestamptz not null default now()
);
alter table public.shop_benefit_upload_events enable row level security;
revoke all on public.shop_benefit_upload_events from public, anon, authenticated, service_role;
create trigger shop_benefit_upload_events_immutable before update or delete
  on public.shop_benefit_upload_events for each row
  execute function public.adishop_benefit_events_immutable();

insert into public.shop_role_permissions(role_id,permission)
select r.id,'benefit.evidence.cleanup' from public.shop_roles r
where r.code='SHOP_SUPER_ADMIN'
on conflict (role_id,permission) do nothing;

create function public.adishop_benefit_upload_reserve(
  p_actor uuid,p_store_id uuid,p_case_id uuid,p_evidence_type text,p_mime_type text,
  p_size_bytes bigint,p_sha256_hex text,p_document_expires_at timestamptz,p_request_key text
) returns jsonb language plpgsql security definer set search_path = public as $$
declare c public.shop_benefit_cases%rowtype; j public.shop_benefit_upload_jobs%rowtype;
  existing_evidence public.shop_benefit_evidence%rowtype; extension text;
begin
  if p_actor is null or p_store_id is null or p_case_id is null or
     p_evidence_type is null or p_evidence_type not in ('authorization_letter','manufacturer_terms','invoice','seller_terms','other') or
     p_mime_type is null or p_mime_type not in ('image/jpeg','image/png','application/pdf') or
     p_size_bytes is null or p_size_bytes not between 1 and 10485760 or
     p_sha256_hex is null or p_sha256_hex !~ '^[0-9a-f]{64}$' or
     p_request_key is null or length(p_request_key) not between 8 and 160 or
     (p_evidence_type in ('authorization_letter','manufacturer_terms') and
      p_document_expires_at is null)
    then raise exception 'invalid-benefit-upload'; end if;
  select * into c from public.shop_benefit_cases where id=p_case_id for update;
  if not found or c.store_id<>p_store_id or c.submitted_by_profile_id<>p_actor or c.status<>'draft' or
     not exists (select 1 from public.shop_profiles p where p.id=p_actor and p.status='active') or
     not exists (select 1 from public.shop_stores s where s.id=p_store_id
       and s.owner_profile_id=p_actor and s.status='active' and s.approval_status='approved'
       and s.suspended_at is null and s.deleted_at is null)
    then raise exception 'benefit-upload-not-found'; end if;
  perform pg_advisory_xact_lock(hashtextextended('benefit-upload-key:' ||
    p_store_id::text || ':' || p_request_key,0));
  select * into j from public.shop_benefit_upload_jobs
    where store_id=p_store_id and request_key=p_request_key;
  if not found then
    perform pg_advisory_xact_lock(hashtextextended('benefit-upload-hash:' ||
      p_case_id::text || ':' || p_sha256_hex,0));
    select * into existing_evidence from public.shop_benefit_evidence
      where case_id=p_case_id and sha256_hex=p_sha256_hex and deleted_at is null;
    if found then
      if existing_evidence.evidence_type<>p_evidence_type or existing_evidence.mime_type<>p_mime_type or
         existing_evidence.size_bytes<>p_size_bytes or
         existing_evidence.document_expires_at is distinct from p_document_expires_at
        then raise exception 'benefit-upload-request-conflict'; end if;
      return jsonb_build_object('state','complete','evidenceId',existing_evidence.id);
    end if;
    select * into j from public.shop_benefit_upload_jobs
      where case_id=p_case_id and sha256_hex=p_sha256_hex and status<>'cleaned';
  end if;
  if found then
    if j.actor_profile_id<>p_actor or j.case_id<>p_case_id or j.store_id<>p_store_id or j.sha256_hex<>p_sha256_hex or
       j.evidence_type<>p_evidence_type or j.mime_type<>p_mime_type or
       j.size_bytes<>p_size_bytes or j.document_expires_at is distinct from p_document_expires_at
      then raise exception 'benefit-upload-request-conflict'; end if;
    if j.status='complete' then
      return jsonb_build_object('state','complete','evidenceId',j.evidence_id);
    end if;
    if j.status<>'pending' then raise exception 'benefit-upload-unavailable'; end if;
    update public.shop_benefit_upload_jobs set last_attempt_at=now() where id=j.id;
    return jsonb_build_object('state','pending','jobId',j.id,'path',j.storage_path);
  end if;
  extension := case p_mime_type when 'image/jpeg' then 'jpg'
    when 'image/png' then 'png' else 'pdf' end;
  insert into public.shop_benefit_upload_jobs(
    case_id,store_id,actor_profile_id,evidence_type,mime_type,size_bytes,
    sha256_hex,document_expires_at,storage_path,request_key
  ) values(p_case_id,p_store_id,p_actor,p_evidence_type,p_mime_type,p_size_bytes,
    p_sha256_hex,p_document_expires_at,
    p_store_id::text || '/' || p_case_id::text || '/' || gen_random_uuid()::text || '.' || extension,
    p_request_key) returning * into j;
  insert into public.shop_benefit_upload_events(job_id,actor_profile_id,action,request_key)
    values(j.id,p_actor,'reserved',gen_random_uuid());
  return jsonb_build_object('state','pending','jobId',j.id,'path',j.storage_path);
end $$;

create function public.adishop_benefit_upload_finalize(p_actor uuid,p_job_id uuid)
returns jsonb language plpgsql security definer set search_path = public as $$
declare j public.shop_benefit_upload_jobs%rowtype; c public.shop_benefit_cases%rowtype;
  evidence public.shop_benefit_evidence%rowtype; target_case_id uuid;
begin
  select case_id into target_case_id from public.shop_benefit_upload_jobs where id=p_job_id;
  if not found then raise exception 'benefit-upload-not-found'; end if;
  select * into c from public.shop_benefit_cases where id=target_case_id for update;
  select * into j from public.shop_benefit_upload_jobs where id=p_job_id for update;
  if j.actor_profile_id<>p_actor or c.store_id<>j.store_id or
     not exists (select 1 from public.shop_profiles p where p.id=p_actor and p.status='active') or
     not exists (select 1 from public.shop_stores s where s.id=j.store_id
       and s.owner_profile_id=p_actor and s.status='active' and s.approval_status='approved'
       and s.suspended_at is null and s.deleted_at is null)
    then raise exception 'benefit-upload-not-found'; end if;
  if j.status='complete' then
    select * into evidence from public.shop_benefit_evidence where id=j.evidence_id;
    return jsonb_build_object('id',evidence.id,'evidenceType',evidence.evidence_type,
      'mimeType',evidence.mime_type,'documentExpiresAt',evidence.document_expires_at,
      'createdAt',evidence.created_at);
  end if;
  if c.status<>'draft' then raise exception 'benefit-upload-not-found'; end if;
  if j.status<>'pending' then raise exception 'benefit-upload-unavailable'; end if;
  insert into public.shop_benefit_evidence(
    case_id,bucket,storage_path,mime_type,evidence_type,document_expires_at,
    size_bytes,sha256_hex,uploaded_by_profile_id
  ) values(j.case_id,'shop-benefit-evidence',j.storage_path,j.mime_type,j.evidence_type,
    j.document_expires_at,j.size_bytes,j.sha256_hex,p_actor) returning * into evidence;
  update public.shop_benefit_upload_jobs set status='complete',evidence_id=evidence.id,
    completed_at=now() where id=j.id;
  insert into public.shop_benefit_upload_events(job_id,actor_profile_id,action,request_key)
    values(j.id,p_actor,'finalized',gen_random_uuid());
  return jsonb_build_object('id',evidence.id,'evidenceType',evidence.evidence_type,
    'mimeType',evidence.mime_type,'documentExpiresAt',evidence.document_expires_at,
    'createdAt',evidence.created_at);
end $$;

create function public.adishop_benefit_upload_claim_cleanup(p_actor uuid,p_limit integer)
returns jsonb language plpgsql security definer set search_path = public as $$
declare j public.shop_benefit_upload_jobs%rowtype; c public.shop_benefit_cases%rowtype;
  candidate record;
  result jsonb := '[]'::jsonb; lease uuid;
begin
  if p_actor is null or p_limit is null or p_limit not between 1 and 20 or
     not public.adishop_profile_has_permission(p_actor,'benefit.evidence.cleanup') or
     not exists (select 1 from public.shop_profiles p where p.id=p_actor and p.status='active')
    then raise exception 'benefit-cleanup-not-authorized'; end if;
  for candidate in select id,case_id from public.shop_benefit_upload_jobs
    where (status in ('pending','cleanup_failed') or
      (status='cleanup_claimed' and cleanup_lease_expires_at<now()))
      and last_attempt_at<now()-interval '1 hour'
    order by last_attempt_at limit p_limit * 3
  loop
    exit when jsonb_array_length(result)>=p_limit;
    select * into c from public.shop_benefit_cases where id=candidate.case_id for update;
    select * into j from public.shop_benefit_upload_jobs
      where id=candidate.id for update skip locked;
    if not found or j.last_attempt_at>=now()-interval '1 hour' or
       not (j.status in ('pending','cleanup_failed') or
         (j.status='cleanup_claimed' and j.cleanup_lease_expires_at<now())) then continue; end if;
    if c.status<>'draft' or exists (select 1 from public.shop_benefit_evidence e
      where e.storage_path=j.storage_path or e.id=j.evidence_id) then continue; end if;
    lease := gen_random_uuid();
    update public.shop_benefit_upload_jobs set status='cleanup_claimed',
      cleanup_lease_id=lease,cleanup_lease_expires_at=now()+interval '5 minutes'
      where id=j.id;
    insert into public.shop_benefit_upload_events(job_id,actor_profile_id,action,request_key)
      values(j.id,p_actor,'cleanup_claimed',gen_random_uuid());
    result := result || jsonb_build_array(jsonb_build_object(
      'jobId',j.id,'path',j.storage_path,'leaseId',lease));
  end loop;
  return result;
end $$;

create function public.adishop_benefit_upload_finish_cleanup(
  p_actor uuid,p_job_id uuid,p_lease_id uuid,p_success boolean,p_error_code text
) returns boolean language plpgsql security definer set search_path = public as $$
declare j public.shop_benefit_upload_jobs%rowtype;
begin
  if p_actor is null or p_job_id is null or p_lease_id is null or p_success is null or
     not public.adishop_profile_has_permission(p_actor,'benefit.evidence.cleanup') or
     not exists (select 1 from public.shop_profiles p where p.id=p_actor and p.status='active')
    then raise exception 'benefit-cleanup-not-authorized'; end if;
  select * into j from public.shop_benefit_upload_jobs where id=p_job_id for update;
  if not found then raise exception 'benefit-upload-not-found'; end if;
  if j.status='cleaned' and j.cleanup_lease_id=p_lease_id then return true; end if;
  if j.status<>'cleanup_claimed' or j.cleanup_lease_id<>p_lease_id or
     j.cleanup_lease_expires_at<now() then raise exception 'benefit-cleanup-lease-conflict'; end if;
  if exists (select 1 from public.shop_benefit_evidence e
      where e.storage_path=j.storage_path or e.id=j.evidence_id)
    then raise exception 'benefit-cleanup-evidence-protected'; end if;
  update public.shop_benefit_upload_jobs set
    status=case when p_success then 'cleaned' else 'cleanup_failed' end,
    cleaned_at=case when p_success then now() else null end,
    last_error_code=case when p_success then null else left(coalesce(p_error_code,'storage_error'),64) end,
    cleanup_lease_expires_at=null
    where id=j.id;
  insert into public.shop_benefit_upload_events(job_id,actor_profile_id,action,request_key)
    values(j.id,p_actor,case when p_success then 'cleaned' else 'cleanup_failed' end,gen_random_uuid());
  return true;
end $$;

revoke insert on public.shop_benefit_evidence from service_role;
revoke all on function public.adishop_benefit_upload_reserve(
  uuid,uuid,uuid,text,text,bigint,text,timestamptz,text),
  public.adishop_benefit_upload_finalize(uuid,uuid),
  public.adishop_benefit_upload_claim_cleanup(uuid,integer),
  public.adishop_benefit_upload_finish_cleanup(uuid,uuid,uuid,boolean,text)
  from public,anon,authenticated;
grant execute on function public.adishop_benefit_upload_reserve(
  uuid,uuid,uuid,text,text,bigint,text,timestamptz,text),
  public.adishop_benefit_upload_finalize(uuid,uuid),
  public.adishop_benefit_upload_claim_cleanup(uuid,integer),
  public.adishop_benefit_upload_finish_cleanup(uuid,uuid,uuid,boolean,text)
  to service_role;
commit;
