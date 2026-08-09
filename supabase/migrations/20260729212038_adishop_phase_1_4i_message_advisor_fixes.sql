create index if not exists shop_conversations_product_id_idx
  on public.shop_conversations(product_id)
  where product_id is not null;

create index if not exists shop_conversations_order_group_id_idx
  on public.shop_conversations(order_group_id)
  where order_group_id is not null;

create index if not exists shop_conversations_last_message_id_idx
  on public.shop_conversations(last_message_id)
  where last_message_id is not null;

create index if not exists shop_conversation_state_last_read_message_id_idx
  on public.shop_conversation_participant_state(last_read_message_id)
  where last_read_message_id is not null;

drop policy if exists shop_conversations_participant_read on public.shop_conversations;
create policy shop_conversations_participant_read
on public.shop_conversations
for select
to authenticated
using (
  (select auth.jwt())->>'iss' = 'https://securetoken.google.com/adischat-f4a77'
  and (select auth.jwt())->>'aud' = 'adischat-f4a77'
  and (
    exists (
      select 1
      from public.shop_profiles p
      where p.id = customer_profile_id
        and p.adischat_user_id = (select auth.jwt())->>'sub'
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
      left join public.shop_stores s on s.id = shop_conversations.store_id
      where p.adischat_user_id = (select auth.jwt())->>'sub'
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
      and p.adischat_user_id = (select auth.jwt())->>'sub'
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
            and p.adischat_user_id = (select auth.jwt())->>'sub'
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
          where p.adischat_user_id = (select auth.jwt())->>'sub'
            and p.status = 'active'
            and p.deleted_at is null
            and (s.owner_profile_id = p.id or sm.id is not null)
        )
      )
  )
);
