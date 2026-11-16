-- Disposable fixture only; never execute on staging or production.
insert into public.shop_profiles(id,supabase_user_id,status) values
  ('90000000-0000-4000-8000-000000000001','90000000-0000-4000-8000-000000000001','active'),
  ('90000000-0000-4000-8000-000000000002','90000000-0000-4000-8000-000000000002','suspended'),
  ('90000000-0000-4000-8000-000000000003','90000000-0000-4000-8000-000000000003','active');
insert into public.shop_user_links(shop_profile_id,provider,provider_user_id,account_status) values
  ('90000000-0000-4000-8000-000000000001','supabase','qa-local-seller','active'),
  ('90000000-0000-4000-8000-000000000002','supabase','qa-local-suspended','suspended'),
  ('90000000-0000-4000-8000-000000000003','supabase','qa-local-admin','active');
insert into public.shop_user_roles(profile_id,role_id) select
  '90000000-0000-4000-8000-000000000001',id from public.shop_roles where code='SELLER';
insert into public.shop_user_roles(profile_id,role_id) select
  '90000000-0000-4000-8000-000000000003',id from public.shop_roles where code='SHOP_ADMIN';
