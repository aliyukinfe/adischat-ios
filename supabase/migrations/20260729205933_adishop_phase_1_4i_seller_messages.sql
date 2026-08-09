-- Phase 1.4I: protected buyer/seller conversations.
-- Mutations remain behind internally authenticated Edge Functions. Direct clients
-- receive read-only access for Realtime after Firebase third-party auth is enabled.

create table if not exists public.shop_conversations (
  id uuid primary key default gen_random_uuid(),
  store_id uuid not null references public.shop_stores(id) on delete restrict,
  customer_profile_id uuid not null references public.shop_profiles(id) on delete restrict,
  context_type text not null default 'general'
    check (context_type in ('general', 'product', 'order')),
  product_id uuid references public.shop_products(id) on delete set null,
  order_group_id uuid references public.shop_order_groups(id) on delete set null,
  status text not null default 'active'
    check (status in ('active', 'closed')),
  last_message_id uuid,
  last_message_preview text,
  last_message_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  check (
    (context_type = 'general' and product_id is null and order_group_id is null)
    or (context_type = 'product' and product_id is not null and order_group_id is null)
    or (context_type = 'order' and order_group_id is not null and product_id is null)
  )
);

create unique index if not exists shop_conversations_general_unique
  on public.shop_conversations(store_id, customer_profile_id)
  where context_type = 'general';

create unique index if not exists shop_conversations_product_unique
  on public.shop_conversations(store_id, customer_profile_id, product_id)
  where context_type = 'product';

create unique index if not exists shop_conversations_order_unique
  on public.shop_conversations(store_id, customer_profile_id, order_group_id)
  where context_type = 'order';

create index if not exists shop_conversations_store_recent_idx
  on public.shop_conversations(store_id, last_message_at desc nulls last, created_at desc);

create index if not exists shop_conversations_customer_recent_idx
  on public.shop_conversations(customer_profile_id, last_message_at desc nulls last, created_at desc);

create table if not exists public.shop_conversation_participant_state (
  conversation_id uuid not null references public.shop_conversations(id) on delete cascade,
  profile_id uuid not null references public.shop_profiles(id) on delete cascade,
  last_read_message_id uuid,
  last_read_at timestamptz,
  archived_at timestamptz,
  muted_until timestamptz,
  deleted_at timestamptz,
  typing_expires_at timestamptz,
  updated_at timestamptz not null default now(),
  primary key (conversation_id, profile_id)
);

create index if not exists shop_conversation_state_profile_idx
  on public.shop_conversation_participant_state(profile_id, updated_at desc);

create table if not exists public.shop_messages (
  id uuid primary key default gen_random_uuid(),
  conversation_id uuid not null references public.shop_conversations(id) on delete cascade,
  sender_profile_id uuid not null references public.shop_profiles(id) on delete restrict,
  message_type text not null default 'text'
    check (message_type in ('text', 'image', 'file', 'system')),
  body text,
  attachment_path text,
  attachment_name text,
  attachment_mime_type text,
  attachment_size_bytes integer,
  client_message_id text not null,
  created_at timestamptz not null default now(),
  edited_at timestamptz,
  deleted_at timestamptz,
  check (char_length(client_message_id) between 8 and 160),
  check (
    (message_type = 'text' and nullif(btrim(body), '') is not null and attachment_path is null)
    or (message_type in ('image', 'file') and attachment_path is not null)
    or message_type = 'system'
  ),
  unique (sender_profile_id, client_message_id)
);

alter table public.shop_conversations
  drop constraint if exists shop_conversations_last_message_id_fkey;

alter table public.shop_conversations
  add constraint shop_conversations_last_message_id_fkey
  foreign key (last_message_id) references public.shop_messages(id) on delete set null;

alter table public.shop_conversation_participant_state
  drop constraint if exists shop_conversation_state_last_read_message_id_fkey;

alter table public.shop_conversation_participant_state
  add constraint shop_conversation_state_last_read_message_id_fkey
  foreign key (last_read_message_id) references public.shop_messages(id) on delete set null;

create index if not exists shop_messages_conversation_created_idx
  on public.shop_messages(conversation_id, created_at desc, id desc)
  where deleted_at is null;

alter table public.shop_conversations enable row level security;
alter table public.shop_conversation_participant_state enable row level security;
alter table public.shop_messages enable row level security;

revoke all on public.shop_conversations from anon, authenticated;
revoke all on public.shop_conversation_participant_state from anon, authenticated;
revoke all on public.shop_messages from anon, authenticated;

grant select on public.shop_conversations to authenticated;
grant select on public.shop_conversation_participant_state to authenticated;
grant select on public.shop_messages to authenticated;

drop policy if exists shop_conversations_participant_read on public.shop_conversations;
create policy shop_conversations_participant_read
on public.shop_conversations
for select
to authenticated
using (
  auth.jwt()->>'iss' = 'https://securetoken.google.com/adischat-f4a77'
  and auth.jwt()->>'aud' = 'adischat-f4a77'
  and (
    exists (
      select 1
      from public.shop_profiles p
      where p.id = customer_profile_id
        and p.adischat_user_id = auth.jwt()->>'sub'
        and p.status = 'active'
        and p.deleted_at is null
    )
    or exists (
      select 1
      from public.shop_profiles p
      left join public.shop_store_members sm
        on sm.profile_id = p.id
       and sm.store_id = shop_conversations.store_id
       and sm.status = 'active'
      left join public.shop_stores s
        on s.id = shop_conversations.store_id
      where p.adischat_user_id = auth.jwt()->>'sub'
        and p.status = 'active'
        and p.deleted_at is null
        and (s.owner_profile_id = p.id or sm.id is not null)
    )
  )
);

drop policy if exists shop_conversation_state_participant_read on public.shop_conversation_participant_state;
create policy shop_conversation_state_participant_read
on public.shop_conversation_participant_state
for select
to authenticated
using (
  exists (
    select 1
    from public.shop_profiles p
    where p.id = profile_id
      and p.adischat_user_id = auth.jwt()->>'sub'
      and p.status = 'active'
      and p.deleted_at is null
  )
  and exists (
    select 1
    from public.shop_conversations c
    where c.id = conversation_id
  )
);

drop policy if exists shop_messages_participant_read on public.shop_messages;
create policy shop_messages_participant_read
on public.shop_messages
for select
to authenticated
using (
  exists (
    select 1
    from public.shop_conversations c
    where c.id = conversation_id
      and (
        exists (
          select 1
          from public.shop_profiles p
          where p.id = c.customer_profile_id
            and p.adischat_user_id = auth.jwt()->>'sub'
            and p.status = 'active'
            and p.deleted_at is null
        )
        or exists (
          select 1
          from public.shop_profiles p
          left join public.shop_store_members sm
            on sm.profile_id = p.id
           and sm.store_id = c.store_id
           and sm.status = 'active'
          left join public.shop_stores s on s.id = c.store_id
          where p.adischat_user_id = auth.jwt()->>'sub'
            and p.status = 'active'
            and p.deleted_at is null
            and (s.owner_profile_id = p.id or sm.id is not null)
        )
      )
  )
);

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values (
  'shop-chat-attachments',
  'shop-chat-attachments',
  false,
  10485760,
  array[
    'image/jpeg',
    'image/png',
    'image/webp',
    'application/pdf',
    'text/plain'
  ]::text[]
)
on conflict (id) do update
set public = excluded.public,
    file_size_limit = excluded.file_size_limit,
    allowed_mime_types = excluded.allowed_mime_types;

drop policy if exists shop_chat_attachments_no_direct_client_access on storage.objects;
create policy shop_chat_attachments_no_direct_client_access
on storage.objects
as restrictive
for all
to anon, authenticated
using (bucket_id <> 'shop-chat-attachments')
with check (bucket_id <> 'shop-chat-attachments');

do $$
begin
  if not exists (
    select 1
    from pg_publication_tables
    where pubname = 'supabase_realtime'
      and schemaname = 'public'
      and tablename = 'shop_messages'
  ) then
    alter publication supabase_realtime add table public.shop_messages;
  end if;
end
$$;
