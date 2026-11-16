-- LOCAL SYNTHETIC SEED PROPOSAL ONLY. Does not link to existing Auth users.
-- All products remain draft/pending and are absent from the reviewed public view.
begin;
insert into public.shop_categories(id,name,slug,code)
values ('91000000-0000-4000-8000-000000000001','QA Laptops','qa-laptops','QA_LAPTOPS'),
       ('91000000-0000-4000-8000-000000000002','QA Phones','qa-phones','QA_PHONES');
insert into public.shop_stores(id,name,slug,city)
values ('92000000-0000-4000-8000-000000000001','Synthetic QA Store','synthetic-qa-store','Test City');
insert into public.shop_products(id,store_id,category_id,title,slug,category_slug,price_minor)
values ('93000000-0000-4000-8000-000000000001','92000000-0000-4000-8000-000000000001',
        '91000000-0000-4000-8000-000000000001','Synthetic QA Laptop','synthetic-qa-laptop','qa-laptops',5600000);
insert into public.shop_product_variants(id,product_id,sku,title,options,price_minor)
values ('94000000-0000-4000-8000-000000000001','93000000-0000-4000-8000-000000000001',
        'QA-LAP-8-256','8GB / 256GB','{"ram":"8GB","storage":"256GB"}'::jsonb,5600000),
       ('94000000-0000-4000-8000-000000000002','93000000-0000-4000-8000-000000000001',
        'QA-LAP-16-512','16GB / 512GB','{"ram":"16GB","storage":"512GB"}'::jsonb,6300000);
insert into public.shop_inventory(product_id,variant_id,quantity_available)
values ('93000000-0000-4000-8000-000000000001','94000000-0000-4000-8000-000000000001',3),
       ('93000000-0000-4000-8000-000000000001','94000000-0000-4000-8000-000000000002',3);
commit;
