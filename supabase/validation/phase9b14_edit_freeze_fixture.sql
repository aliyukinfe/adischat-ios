-- Disposable PostgreSQL 17 fixture only. Never use against a linked project.
create schema if not exists storage;
create table if not exists storage.objects (
  id bigint generated always as identity primary key,
  bucket_id text not null,
  name text not null
);
insert into storage.objects(bucket_id,name)
values ('shop-product-images','synthetic/review.jpg'),
       ('shop-store-assets','synthetic/logo.jpg'),
       ('shop-category-images','synthetic/category.jpg');
