-- Local synthetic HTTP fixture only. Never apply remotely.
insert into auth.users(id) values
  ('90000000-0000-4000-8000-000000000004'),
  ('90000000-0000-4000-8000-000000000005'),
  ('90000000-0000-4000-8000-000000000006');
insert into public.shop_profiles(id,supabase_user_id,status) values
  ('90000000-0000-4000-8000-000000000004','90000000-0000-4000-8000-000000000004','active'),
  ('90000000-0000-4000-8000-000000000005','90000000-0000-4000-8000-000000000005','active'),
  ('90000000-0000-4000-8000-000000000006','90000000-0000-4000-8000-000000000006','active');
insert into public.shop_user_links(shop_profile_id,provider,provider_user_id,account_status) values
  ('90000000-0000-4000-8000-000000000004','supabase','qa-local-buyer-a','active'),
  ('90000000-0000-4000-8000-000000000005','supabase','qa-local-buyer-b','active'),
  ('90000000-0000-4000-8000-000000000006','supabase','qa-local-seller-b','active');
insert into public.shop_user_roles(profile_id,role_id)
select p.id,r.id from public.shop_profiles p cross join public.shop_roles r
where p.id in ('90000000-0000-4000-8000-000000000004','90000000-0000-4000-8000-000000000005') and r.code='CUSTOMER';
insert into public.shop_user_roles(profile_id,role_id)
select '90000000-0000-4000-8000-000000000006',id from public.shop_roles where code='SELLER';
update public.shop_stores set owner_profile_id='90000000-0000-4000-8000-000000000001',
  status='active',approval_status='approved' where id='92000000-0000-4000-8000-000000000001';
insert into public.shop_stores(id,owner_profile_id,name,slug,status,approval_status)
values ('92000000-0000-4000-8000-000000000002','90000000-0000-4000-8000-000000000006',
  'Synthetic Other Store','synthetic-other-store','active','approved');
insert into public.shop_carts(id,profile_id,status)
values ('95000000-0000-4000-8000-000000000001','90000000-0000-4000-8000-000000000004','active');
insert into public.shop_cart_items(id,cart_id,product_id,variant_id,quantity,unit_price_minor)
values ('96000000-0000-4000-8000-000000000001','95000000-0000-4000-8000-000000000001',
  '93000000-0000-4000-8000-000000000001','94000000-0000-4000-8000-000000000001',1,5600000);
insert into public.shop_session_families(id,profile_id)
select ('97000000-0000-4000-8000-' || lpad(right(id::text,12),12,'0'))::uuid,id
from public.shop_profiles where id in (
  '90000000-0000-4000-8000-000000000001','90000000-0000-4000-8000-000000000002',
  '90000000-0000-4000-8000-000000000003','90000000-0000-4000-8000-000000000004',
  '90000000-0000-4000-8000-000000000005','90000000-0000-4000-8000-000000000006');
insert into public.shop_sessions(profile_id,family_id,session_jti,refresh_token_hash,expires_at,refresh_expires_at)
select profile_id,id,'qa-g11-'||right(profile_id::text,1),'qa-g11-hash-'||right(profile_id::text,1),
  now()+interval '1 hour',now()+interval '1 day' from public.shop_session_families;
insert into public.shop_favorites(id,profile_id,product_id)
values ('96000000-0000-4000-8000-000000000002','90000000-0000-4000-8000-000000000004',
  '93000000-0000-4000-8000-000000000001');
insert into public.shop_addresses(id,profile_id,recipient_name,phone,city,address_line1)
values ('96000000-0000-4000-8000-000000000003','90000000-0000-4000-8000-000000000004',
  'Synthetic Buyer','0000000000','QA City','Fixture Street');
insert into public.shop_seller_applications(id,profile_id,proposed_store_name,status)
values ('96000000-0000-4000-8000-000000000004','90000000-0000-4000-8000-000000000001',
  'Synthetic QA Store','draft');
insert into public.shop_orders(id,profile_id,order_number,status,fulfillment_status,delivery_status,payment_status)
values ('97000000-0000-4000-8000-000000000011','90000000-0000-4000-8000-000000000004',
  'QA-READ-ONLY-1','pending','unfulfilled','not_started','unpaid');
insert into public.shop_order_groups(id,order_id,store_id,status,fulfillment_status)
values ('97000000-0000-4000-8000-000000000012','97000000-0000-4000-8000-000000000011',
  '92000000-0000-4000-8000-000000000001','pending','unfulfilled');
