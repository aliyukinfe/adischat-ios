-- AdisGebeya production category hierarchy and integer selling units.
-- Existing root category ids remain unchanged for legacy product compatibility.

alter table public.shop_categories
  add column if not exists code text,
  add column if not exists level integer not null default 0,
  add column if not exists is_leaf boolean not null default true,
  add column if not exists seller_selectable boolean not null default true,
  add column if not exists buyer_visible boolean not null default true,
  add column if not exists search_terms text[] not null default '{}',
  add column if not exists review_required boolean not null default false;

update public.shop_categories
set code = upper(replace(slug, '-', '_'))
where code is null;

alter table public.shop_categories alter column code set not null;
create unique index if not exists shop_categories_code_uidx on public.shop_categories(code);
create index if not exists shop_categories_parent_browse_idx
  on public.shop_categories(parent_id, buyer_visible, is_active, display_order);
create index if not exists shop_categories_seller_leaf_idx
  on public.shop_categories(seller_selectable, is_leaf, is_active, display_order);
create index if not exists shop_categories_search_terms_gin_idx
  on public.shop_categories using gin(search_terms);

create table if not exists public.shop_selling_units (
  code text primary key,
  name text not null,
  symbol text not null,
  quantity_scale integer not null default 0 check (quantity_scale = 0),
  is_active boolean not null default true,
  display_order integer not null default 0,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

alter table public.shop_selling_units enable row level security;
revoke all on table public.shop_selling_units from anon, authenticated;
grant select on table public.shop_selling_units to anon, authenticated;
grant all on table public.shop_selling_units to service_role;
drop policy if exists public_read_active_selling_units on public.shop_selling_units;
create policy public_read_active_selling_units on public.shop_selling_units
  for select to anon, authenticated using (is_active = true);

insert into public.shop_selling_units(code,name,symbol,display_order) values
  ('PIECE','Piece','pc',10),('PAIR','Pair','pair',20),('SET','Set','set',30),
  ('PACK','Pack','pack',40),('DOZEN','Dozen','dozen',50),('GRAM','Gram','g',60),
  ('KILOGRAM','Kilogram','kg',70),('MILLILITER','Milliliter','ml',80),
  ('LITER','Liter','L',90),('METER','Meter','m',100),('CENTIMETER','Centimeter','cm',110),
  ('YARD','Yard','yd',120),('ROLL','Roll','roll',130),('BOX','Box','box',140),
  ('CARTON','Carton','carton',150),('BAG','Bag','bag',160),('BUNDLE','Bundle','bundle',170),
  ('SHEET','Sheet','sheet',180),('SQUARE_METER','Square Meter','sq m',190),
  ('CUBIC_METER','Cubic Meter','cu m',200),('TON','Ton','t',210),
  ('BOTTLE','Bottle','bottle',220),('CAN','Can','can',230),('TUBE','Tube','tube',240),
  ('JAR','Jar','jar',250)
on conflict(code) do update set name=excluded.name,symbol=excluded.symbol,
  display_order=excluded.display_order,is_active=true,updated_at=now();

alter table public.shop_products
  add column if not exists selling_unit_code text references public.shop_selling_units(code),
  add column if not exists minimum_order_quantity integer not null default 1 check (minimum_order_quantity >= 1),
  add column if not exists category_review_required boolean not null default false,
  add column if not exists legacy_category_assignment boolean not null default false,
  add column if not exists product_type_description text;
update public.shop_products p set legacy_category_assignment=true
where p.category_id in (select id from public.shop_categories where parent_id is null);
update public.shop_products set selling_unit_code='PIECE' where selling_unit_code is null;
alter table public.shop_products alter column selling_unit_code set default 'PIECE';
alter table public.shop_products alter column selling_unit_code set not null;

alter table public.shop_cart_items add column if not exists unit_snapshot jsonb not null default '{}'::jsonb;
alter table public.shop_order_items add column if not exists unit_snapshot jsonb not null default '{}'::jsonb;

-- Keep original ids/slugs so existing products and old links remain valid.
update public.shop_categories set name='Electronics & Technology',code='ELECTRONICS_TECHNOLOGY',
  is_leaf=false,seller_selectable=false,search_terms=array['electronics','technology','tech'] where slug='electronics';
update public.shop_categories set name='Fashion & Clothing',code='FASHION_CLOTHING',
  is_leaf=false,seller_selectable=false,search_terms=array['fashion','clothing','apparel'] where slug='fashion';
update public.shop_categories set name='Home, Furniture & Garden',code='HOME_FURNITURE_GARDEN',
  is_leaf=false,seller_selectable=false,search_terms=array['home','furniture','garden'] where slug='home';
update public.shop_categories set name='Beauty & Personal Care',code='BEAUTY_PERSONAL_CARE',
  is_leaf=false,seller_selectable=false,search_terms=array['beauty','personal care'] where slug='beauty';
update public.shop_categories set name='Groceries & Food',code='GROCERIES_FOOD',
  is_leaf=false,seller_selectable=false,search_terms=array['groceries','food'] where slug='groceries';
update public.shop_categories set name='Books, Media & Entertainment',code='BOOKS_MEDIA_ENTERTAINMENT',
  is_leaf=false,seller_selectable=false,search_terms=array['books','media','entertainment'] where slug='books';
update public.shop_categories set name='Sports & Outdoors',code='SPORTS_OUTDOORS',
  is_leaf=false,seller_selectable=false,search_terms=array['sports','outdoors'] where slug='sports';
update public.shop_categories set name='Toys, Kids & Baby',code='TOYS_KIDS_BABY',
  is_leaf=false,seller_selectable=false,search_terms=array['toys','kids','baby'] where slug='toys';

create or replace function public.adisgebeya_category_id(p_slug text)
returns uuid language sql immutable strict set search_path=public as $$
  select (substr(md5('adisgebeya-category:'||p_slug),1,8)||'-'||substr(md5('adisgebeya-category:'||p_slug),9,4)||'-4'||substr(md5('adisgebeya-category:'||p_slug),14,3)||'-8'||substr(md5('adisgebeya-category:'||p_slug),18,3)||'-'||substr(md5('adisgebeya-category:'||p_slug),21,12))::uuid
$$;

with roots(name,slug,code,icon,display_order) as (values
 ('Jewelry & Watches','jewelry-watches','JEWELRY_WATCHES','Watch',8),
 ('Crafts, Sewing & Accessories','crafts-sewing-accessories','CRAFTS_SEWING_ACCESSORIES','Palette',9),
 ('Fabric, Textile & Leather','fabric-textile-leather','FABRIC_TEXTILE_LEATHER','Texture',10),
 ('Shoes, Bags & Accessories','shoes-bags-accessories','SHOES_BAGS_ACCESSORIES','ShoppingBag',11),
 ('Automotive & Motorcycle','automotive-motorcycle','AUTOMOTIVE_MOTORCYCLE','DirectionsCar',12),
 ('Tools & Hardware','tools-hardware','TOOLS_HARDWARE','Build',13),
 ('Industrial & Manufacturing','industrial-manufacturing','INDUSTRIAL_MANUFACTURING','PrecisionManufacturing',14),
 ('Packaging & Printing','packaging-printing','PACKAGING_PRINTING','Inventory2',15),
 ('Office, School & Stationery','office-school-stationery','OFFICE_SCHOOL_STATIONERY','Edit',16),
 ('Health & Wellness','health-wellness','HEALTH_WELLNESS','HealthAndSafety',17),
 ('Pet Supplies','pet-supplies','PET_SUPPLIES','Pets',18),
 ('Agriculture & Farming','agriculture-farming','AGRICULTURE_FARMING','Agriculture',19),
 ('Construction & Building Materials','construction-building-materials','CONSTRUCTION_BUILDING_MATERIALS','Construction',20),
 ('Appliances','appliances','APPLIANCES','Kitchen',21),
 ('Gifts, Party & Events','gifts-party-events','GIFTS_PARTY_EVENTS','Celebration',23),
 ('Other / Specialized Products','other-specialized-products','OTHER_SPECIALIZED_PRODUCTS','Category',24)
)
insert into public.shop_categories(id,name,slug,code,icon,level,is_leaf,seller_selectable,buyer_visible,display_order,is_seed,search_terms,review_required)
select public.adisgebeya_category_id(slug),name,slug,code,icon,0,false,false,true,display_order,true,array[lower(name)],slug='other-specialized-products'
from roots on conflict(slug) do update set name=excluded.name,code=excluded.code,icon=excluded.icon,
 level=0,is_leaf=false,seller_selectable=false,buyer_visible=true,display_order=excluded.display_order,is_active=true;

-- Root slug, leaf name, leaf slug. These are deliberately data-driven rather than Android constants.
with leaves(root_slug,name,slug,sort_order) as (values
 ('electronics','Mobile Phones','mobile-phones',10),('electronics','Tablets','tablets',20),('electronics','Laptops','laptops',30),('electronics','Desktop Computers','desktop-computers',40),('electronics','Computer Accessories','computer-accessories',50),('electronics','Phone Accessories','phone-accessories',60),('electronics','Chargers & Cables','chargers-cables',70),('electronics','Earphones & Headphones','earphones-headphones',80),('electronics','Smart Watches','smart-watches',90),('electronics','Cameras','cameras',100),('electronics','TVs','tvs',110),('electronics','Networking','networking',120),('electronics','Gaming','gaming',130),('electronics','Storage Devices','storage-devices',140),('electronics','Power Banks','power-banks',150),
 ('fashion','Men','mens-clothing',10),('fashion','Women','womens-clothing',20),('fashion','Kids','kids-clothing',30),('fashion','Traditional Clothing','traditional-clothing',40),('fashion','Uniforms','uniforms',50),('fashion','Jackets','jackets',60),('fashion','Shirts','shirts',70),('fashion','T-Shirts','t-shirts',80),('fashion','Trousers','trousers',90),('fashion','Dresses','dresses',100),('fashion','Underwear','underwear',110),('fashion','Sportswear','sportswear',120),
 ('beauty','Makeup','makeup',10),('beauty','Skincare','skincare',20),('beauty','Hair Care','hair-care',30),('beauty','Perfume','perfume',40),('beauty','Personal Care','personal-care',50),('beauty','Beauty Tools','beauty-tools',60),('beauty','Salon Supplies','salon-supplies',70),
 ('home','Furniture','furniture',10),('home','Kitchen','kitchen',20),('home','Bedding','bedding',30),('home','Home Decor','home-decor',40),('home','Lighting','lighting',50),('home','Storage','home-storage',60),('home','Bathroom','bathroom',70),('home','Cleaning','cleaning',80),('home','Garden','garden',90),
 ('groceries','Coffee','coffee',10),('groceries','Tea','tea',20),('groceries','Beverages','beverages',30),('groceries','Snacks','snacks',40),('groceries','Grains','grains',50),('groceries','Spices','spices',60),('groceries','Cooking Ingredients','cooking-ingredients',70),('groceries','Packaged Food','packaged-food',80),('groceries','Bakery','bakery',90),('groceries','Dairy','dairy',100),
 ('sports','Fitness','fitness',10),('sports','Football','football',20),('sports','Running','running',30),('sports','Cycling','cycling',40),('sports','Camping','camping',50),('sports','Outdoor Gear','outdoor-gear',60),
 ('toys','Baby Clothing','baby-clothing',10),('toys','Baby Care','baby-care',20),('toys','Feeding','baby-feeding',30),('toys','Diapers','diapers',40),('toys','Strollers','strollers',50),('toys','Educational Toys','educational-toys',60),('toys','General Toys','general-toys',70),
 ('jewelry-watches','Rings','rings',10),('jewelry-watches','Necklaces','necklaces',20),('jewelry-watches','Earrings','earrings',30),('jewelry-watches','Bracelets','bracelets',40),('jewelry-watches','Fashion Jewelry','fashion-jewelry',50),('jewelry-watches','Watches','watches',60),('jewelry-watches','Jewelry Accessories','jewelry-accessories',70),
 ('fabric-textile-leather','Cotton Fabric','cotton-fabric',10),('fabric-textile-leather','Polyester Fabric','polyester-fabric',20),('fabric-textile-leather','Denim','denim',30),('fabric-textile-leather','Canvas','canvas-fabric',40),('fabric-textile-leather','Lining Fabric','lining-fabric',50),('fabric-textile-leather','Knit Fabric','knit-fabric',60),('fabric-textile-leather','Leather','leather',70),('fabric-textile-leather','Synthetic Leather','synthetic-leather',80),('fabric-textile-leather','Textile Rolls','textile-rolls',90),('fabric-textile-leather','Industrial Fabric','industrial-fabric',100),
 ('shoes-bags-accessories','Men''s Shoes','mens-shoes',10),('shoes-bags-accessories','Women''s Shoes','womens-shoes',20),('shoes-bags-accessories','Kids Shoes','kids-shoes',30),('shoes-bags-accessories','Sports Shoes','sports-shoes',40),('shoes-bags-accessories','Handbags','handbags',50),('shoes-bags-accessories','Backpacks','backpacks',60),('shoes-bags-accessories','Wallets','wallets',70),('shoes-bags-accessories','Belts','belts',80),('shoes-bags-accessories','Luggage','luggage',90),
 ('automotive-motorcycle','Auto Parts','auto-parts',10),('automotive-motorcycle','Motorcycle Parts','motorcycle-parts',20),('automotive-motorcycle','Batteries','vehicle-batteries',30),('automotive-motorcycle','Tires','tires',40),('automotive-motorcycle','Oils','vehicle-oils',50),('automotive-motorcycle','Accessories','vehicle-accessories',60),('automotive-motorcycle','Vehicle Electronics','vehicle-electronics',70),('automotive-motorcycle','Tools','vehicle-tools',80),
 ('tools-hardware','Hand Tools','hand-tools',10),('tools-hardware','Power Tools','power-tools',20),('tools-hardware','Fasteners','fasteners',30),('tools-hardware','Screws','screws',40),('tools-hardware','Locks','locks',50),('tools-hardware','Plumbing','hardware-plumbing',60),('tools-hardware','Electrical Hardware','electrical-hardware',70),('tools-hardware','Welding Supplies','welding-supplies',80),
 ('industrial-manufacturing','Industrial Machinery','industrial-machinery',10),('industrial-manufacturing','Motors','motors',20),('industrial-manufacturing','Pumps','pumps',30),('industrial-manufacturing','Compressors','compressors',40),('industrial-manufacturing','Factory Components','factory-components',50),('industrial-manufacturing','Spare Parts','industrial-spare-parts',60),('industrial-manufacturing','Manufacturing Supplies','manufacturing-supplies',70),
 ('packaging-printing','Boxes','boxes',10),('packaging-printing','Shopping Bags','shopping-bags',20),('packaging-printing','Bottles','packaging-bottles',30),('packaging-printing','Containers','containers',40),('packaging-printing','Labels','labels',50),('packaging-printing','Packaging Film','packaging-film',60),('packaging-printing','Wrapping','wrapping',70),('packaging-printing','Printing Materials','printing-materials',80),('packaging-printing','Printer Supplies','printer-supplies',90),
 ('office-school-stationery','Pens','pens',10),('office-school-stationery','Notebooks','notebooks',20),('office-school-stationery','Paper','paper',30),('office-school-stationery','Office Equipment','office-equipment',40),('office-school-stationery','School Supplies','school-supplies',50),('office-school-stationery','Art Supplies','art-supplies',60),('office-school-stationery','Printer Supplies','office-printer-supplies',70),
 ('health-wellness','First Aid','first-aid',10),('health-wellness','Mobility Products','mobility-products',20),('health-wellness','Personal Health Devices','personal-health-devices',30),('health-wellness','Health Accessories','health-accessories',40),('health-wellness','Permitted Wellness Products','permitted-wellness-products',50),
 ('pet-supplies','Pet Food','pet-food',10),('pet-supplies','Pet Accessories','pet-accessories',20),('pet-supplies','Beds','pet-beds',30),('pet-supplies','Cages','pet-cages',40),('pet-supplies','Grooming','pet-grooming',50),
 ('agriculture-farming','Farming Tools','farming-tools',10),('agriculture-farming','Irrigation','irrigation',20),('agriculture-farming','Greenhouse Supplies','greenhouse-supplies',30),('agriculture-farming','Animal Equipment','animal-equipment',40),('agriculture-farming','Agricultural Machinery','agricultural-machinery',50),('agriculture-farming','Permitted Seeds','permitted-seeds',60),
 ('construction-building-materials','Tiles','tiles',10),('construction-building-materials','Doors','doors',20),('construction-building-materials','Windows','windows',30),('construction-building-materials','Plumbing','construction-plumbing',40),('construction-building-materials','Electrical Materials','electrical-materials',50),('construction-building-materials','Sanitary Ware','sanitary-ware',60),('construction-building-materials','Building Supplies','building-supplies',70),('construction-building-materials','Paint & Finishes','paint-finishes',80),
 ('appliances','Refrigerators','refrigerators',10),('appliances','Washing Machines','washing-machines',20),('appliances','Ovens','ovens',30),('appliances','Microwaves','microwaves',40),('appliances','Air Conditioners','air-conditioners',50),('appliances','Fans','fans',60),('appliances','Small Appliances','small-appliances',70),
 ('books','Books','physical-books',10),('books','Educational Materials','educational-materials',20),('books','Music Equipment','music-equipment',30),('books','Instruments','instruments',40),('books','Physical Media','physical-media',50),
 ('gifts-party-events','Gifts','gifts',10),('gifts-party-events','Balloons','balloons',20),('gifts-party-events','Wedding Supplies','wedding-supplies',30),('gifts-party-events','Party Decorations','party-decorations',40),('gifts-party-events','Event Accessories','event-accessories',50)
)
insert into public.shop_categories(id,parent_id,name,slug,code,level,is_leaf,seller_selectable,buyer_visible,display_order,is_seed,search_terms)
select public.adisgebeya_category_id(l.slug),r.id,l.name,l.slug,upper(replace(l.slug,'-','_')),1,true,true,true,l.sort_order,true,array[lower(l.name),replace(l.slug,'-',' ')]
from leaves l join public.shop_categories r on r.slug=l.root_slug
on conflict(slug) do update set parent_id=excluded.parent_id,name=excluded.name,code=excluded.code,
 level=1,is_leaf=true,seller_selectable=true,buyer_visible=true,display_order=excluded.display_order,is_active=true;

-- Crafts uses a real three-level path for the required rhinestone case.
insert into public.shop_categories(id,parent_id,name,slug,code,level,is_leaf,seller_selectable,buyer_visible,display_order,is_seed,search_terms)
select public.adisgebeya_category_id('garment-accessories'),id,'Garment Accessories','garment-accessories','GARMENT_ACCESSORIES',1,false,false,true,10,true,array['garment accessories','sewing accessories']
from public.shop_categories where slug='crafts-sewing-accessories'
on conflict(slug) do update set parent_id=excluded.parent_id,level=1,is_leaf=false,seller_selectable=false,is_active=true;

with craft(name,slug,sort_order,terms) as (values
 ('Rhinestones & Decorative Stones','rhinestones-decorative-stones',10,array['rhinestone','rhinestones','decorative stone','garment stone','crystal stone']),
 ('Beads','beads',20,array['bead','beads']),('Sequins','sequins',30,array['sequin','sequins']),
 ('Buttons','buttons',40,array['button','buttons']),('Zippers','zippers',50,array['zipper','zippers']),
 ('Lace','lace',60,array['lace']),('Ribbon','ribbon',70,array['ribbon']),('Thread','thread',80,array['thread']),
 ('Elastic','elastic',90,array['elastic']),('Embroidery Supplies','embroidery-supplies',100,array['embroidery']),
 ('Sewing Accessories','sewing-accessories',110,array['sewing accessories']),('Garment Trims','garment-trims',120,array['garment trims']),
 ('Craft Tools','craft-tools',130,array['craft tools'])
)
insert into public.shop_categories(id,parent_id,name,slug,code,level,is_leaf,seller_selectable,buyer_visible,display_order,is_seed,search_terms)
select public.adisgebeya_category_id(c.slug),g.id,c.name,c.slug,upper(replace(c.slug,'-','_')),2,true,true,true,c.sort_order,true,c.terms
from craft c join public.shop_categories g on g.slug='garment-accessories'
on conflict(slug) do update set parent_id=excluded.parent_id,name=excluded.name,level=2,is_leaf=true,
 seller_selectable=true,buyer_visible=true,display_order=excluded.display_order,search_terms=excluded.search_terms,is_active=true;

-- Copy legacy root schemas to descendants, then enrich the key production leaves.
with recursive descendants(root_id,child_id) as (
  select id,id from public.shop_categories where parent_id is null
  union all select d.root_id,c.id from descendants d join public.shop_categories c on c.parent_id=d.child_id
)
insert into public.shop_category_attributes(category_id,attribute_definition_id,required,variant_capable,filterable,sort_order)
select d.child_id,ca.attribute_definition_id,ca.required,ca.variant_capable,ca.filterable,ca.sort_order
from descendants d join public.shop_category_attributes ca on ca.category_id=d.root_id
where d.child_id<>d.root_id
on conflict(category_id,attribute_definition_id) do nothing;

insert into public.shop_attribute_definitions(code,name,display_name,data_type,input_type,is_variant_option,is_filterable,is_searchable,allows_multiple,suggestions,sort_order)
values
 ('SHAPE','shape','Shape','choice','chips',true,true,true,true,'["Round","Square","Oval","Teardrop"]',200),
 ('STONE_TYPE','stone_type','Stone Type','choice','select',false,true,true,false,'["Crystal","Glass","Acrylic","Resin"]',210),
 ('GRADE','grade','Grade','text','text',false,true,true,false,'[]',220),
 ('PACKAGING','packaging','Packaging','choice','select',false,true,false,false,'["Bag","Box","Jar","Bulk"]',230),
 ('FABRIC_WIDTH','fabric_width','Width','number','number',false,true,true,false,'[]',240),
 ('GSM','gsm','Weight / GSM','number','number',false,true,true,false,'[]',250),
 ('ROLL_LENGTH','roll_length','Roll Length','number','number',false,true,false,false,'[]',260)
on conflict(code) do update set display_name=excluded.display_name,data_type=excluded.data_type,input_type=excluded.input_type,
 is_variant_option=excluded.is_variant_option,is_filterable=excluded.is_filterable,is_searchable=excluded.is_searchable,
 allows_multiple=excluded.allows_multiple,suggestions=excluded.suggestions,status='active',updated_at=now();

with rules(category_slug,attribute_code,required,variant_capable,filterable,sort_order) as (values
 ('rhinestones-decorative-stones','MATERIAL',true,false,true,10),('rhinestones-decorative-stones','STONE_TYPE',false,false,true,20),
 ('rhinestones-decorative-stones','COLOR',true,true,true,30),('rhinestones-decorative-stones','SIZE',true,true,true,40),
 ('rhinestones-decorative-stones','SHAPE',false,true,true,50),('rhinestones-decorative-stones','GRADE',false,false,true,60),
 ('rhinestones-decorative-stones','PACKAGING',false,false,true,70),
 ('mobile-phones','BRAND',true,false,true,10),('mobile-phones','MODEL',true,false,true,20),
 ('mobile-phones','STORAGE',false,true,true,30),('mobile-phones','RAM',false,true,true,40),
 ('mobile-phones','COLOR',false,true,true,50),('mobile-phones','SCREEN_SIZE',false,false,true,60),
 ('mobile-phones','NETWORK',false,false,true,70),('mobile-phones','BATTERY_CAPACITY',false,false,true,80),
 ('cotton-fabric','MATERIAL',true,false,true,10),('cotton-fabric','FABRIC_WIDTH',false,false,true,20),
 ('cotton-fabric','GSM',false,false,true,30),('cotton-fabric','COLOR',false,true,true,40),
 ('cotton-fabric','PATTERN',false,true,true,50),('cotton-fabric','ROLL_LENGTH',false,false,false,60)
)
insert into public.shop_category_attributes(category_id,attribute_definition_id,required,variant_capable,filterable,sort_order)
select c.id,a.id,r.required,r.variant_capable,r.filterable,r.sort_order
from rules r join public.shop_categories c on c.slug=r.category_slug
join public.shop_attribute_definitions a on a.code=r.attribute_code
on conflict(category_id,attribute_definition_id) do update set required=excluded.required,
 variant_capable=excluded.variant_capable,filterable=excluded.filterable,sort_order=excluded.sort_order;

create or replace function public.adisgebeya_validate_product_classification()
returns trigger language plpgsql set search_path=public as $$
declare c public.shop_categories%rowtype; u public.shop_selling_units%rowtype;
begin
  if new.category_id is not null and (tg_op='INSERT' or old.category_id is distinct from new.category_id) then
    select * into c from public.shop_categories where id=new.category_id;
    if not found or not c.is_active then raise exception 'category-not-found'; end if;
    if not c.is_leaf or not c.seller_selectable then raise exception 'category-leaf-required'; end if;
    new.category_slug:=c.slug;
    new.category_review_required:=c.review_required;
    new.legacy_category_assignment:=false;
    if c.review_required and nullif(trim(coalesce(new.product_type_description,'')),'') is null then
      raise exception 'product-type-description-required';
    end if;
  end if;
  select * into u from public.shop_selling_units where code=new.selling_unit_code and is_active=true;
  if not found then raise exception 'invalid-selling-unit'; end if;
  return new;
end $$;
drop trigger if exists shop_products_validate_classification on public.shop_products;
create trigger shop_products_validate_classification before insert or update of category_id,selling_unit_code,product_type_description
on public.shop_products for each row execute function public.adisgebeya_validate_product_classification();

create or replace function public.adisgebeya_validate_product_submission()
returns trigger language plpgsql set search_path=public as $$
declare c public.shop_categories%rowtype; missing_count integer;
begin
  if new.status='submitted' and old.status is distinct from new.status then
    select * into c from public.shop_categories where id=new.category_id;
    if not found or not c.is_active then raise exception 'category-not-found'; end if;
    if (not c.is_leaf or not c.seller_selectable) and not new.legacy_category_assignment then raise exception 'category-leaf-required'; end if;
    select count(*) into missing_count
    from public.shop_category_attributes ca
    where ca.category_id=new.category_id and ca.required=true
      and not exists(select 1 from public.shop_product_attribute_values pav
        where pav.product_id=new.id and pav.attribute_definition_id=ca.attribute_definition_id
          and (nullif(trim(pav.value_text),'') is not null or pav.value_number is not null or pav.value_boolean is not null or pav.value_json is not null));
    if missing_count>0 then raise exception 'required-category-attribute-missing'; end if;
  end if;
  return new;
end $$;
drop trigger if exists shop_products_validate_submission on public.shop_products;
create trigger shop_products_validate_submission before update of status on public.shop_products
for each row execute function public.adisgebeya_validate_product_submission();

create or replace function public.adisgebeya_snapshot_selling_unit()
returns trigger language plpgsql set search_path=public as $$
begin
  select jsonb_build_object('code',u.code,'name',u.name,'symbol',u.symbol,'minimumOrderQuantity',p.minimum_order_quantity)
  into new.unit_snapshot from public.shop_products p join public.shop_selling_units u on u.code=p.selling_unit_code
  where p.id=new.product_id;
  new.unit_snapshot:=coalesce(new.unit_snapshot,'{}'::jsonb);
  return new;
end $$;
drop trigger if exists shop_cart_items_unit_snapshot on public.shop_cart_items;
create trigger shop_cart_items_unit_snapshot before insert or update of product_id on public.shop_cart_items
for each row execute function public.adisgebeya_snapshot_selling_unit();
drop trigger if exists shop_order_items_unit_snapshot on public.shop_order_items;
create trigger shop_order_items_unit_snapshot before insert or update of product_id on public.shop_order_items
for each row execute function public.adisgebeya_snapshot_selling_unit();

-- Backfill snapshots without changing quantities, prices, stock, or order state.
update public.shop_cart_items ci set unit_snapshot=jsonb_build_object('code',u.code,'name',u.name,'symbol',u.symbol,'minimumOrderQuantity',p.minimum_order_quantity)
from public.shop_products p join public.shop_selling_units u on u.code=p.selling_unit_code
where ci.product_id=p.id and ci.unit_snapshot='{}'::jsonb;
update public.shop_order_items oi set unit_snapshot=jsonb_build_object('code',u.code,'name',u.name,'symbol',u.symbol,'minimumOrderQuantity',p.minimum_order_quantity)
from public.shop_products p join public.shop_selling_units u on u.code=p.selling_unit_code
where oi.product_id=p.id and oi.unit_snapshot='{}'::jsonb;

drop policy if exists public_read_active_categories on public.shop_categories;
create policy public_read_active_categories on public.shop_categories for select to anon, authenticated
using (is_active=true and buyer_visible=true);

revoke all on function public.adisgebeya_category_id(text) from public,anon,authenticated;
revoke all on function public.adisgebeya_validate_product_classification() from public;
revoke all on function public.adisgebeya_validate_product_submission() from public;
revoke all on function public.adisgebeya_snapshot_selling_unit() from public;
grant execute on function public.adisgebeya_category_id(text) to service_role;

-- Extend the established seller update RPC without adding an Edge Function.
alter function public.adishop_seller_update_product(uuid,uuid,jsonb,text)
  rename to adishop_seller_update_product_category_core;
create or replace function public.adishop_seller_update_product(
  p_profile_id uuid,p_product_id uuid,p_payload jsonb,p_request_id text default null
) returns jsonb language plpgsql security definer set search_path=public as $$
declare result jsonb;
begin
  result:=public.adishop_seller_update_product_category_core(p_profile_id,p_product_id,p_payload,p_request_id);
  update public.shop_products p set
    selling_unit_code=case when p_payload ? 'sellingUnitCode' then upper(trim(p_payload->>'sellingUnitCode')) else p.selling_unit_code end,
    minimum_order_quantity=case when p_payload ? 'minimumOrderQuantity' then greatest(1,(p_payload->>'minimumOrderQuantity')::integer) else p.minimum_order_quantity end,
    product_type_description=case when p_payload ? 'productTypeDescription' then nullif(trim(p_payload->>'productTypeDescription'),'') else p.product_type_description end,
    updated_at=now()
  where p.id=p_product_id and exists(
    select 1 from public.shop_stores s where s.id=p.store_id and s.owner_profile_id=p_profile_id
  );
  if not found then raise exception 'seller-product-not-found'; end if;
  return public.adishop_seller_get_product(p_profile_id,p_product_id);
end $$;
revoke all on function public.adishop_seller_update_product(uuid,uuid,jsonb,text) from public,anon,authenticated;
grant execute on function public.adishop_seller_update_product(uuid,uuid,jsonb,text) to service_role;

create or replace function public.adishop_seller_product_summary(p public.shop_products)
returns jsonb language sql stable set search_path=public as $$
  select jsonb_build_object(
    'id',p.id,'store_id',p.store_id,'category_id',p.category_id,'brand',p.brand,
    'title',p.title,'slug',p.slug,'category_slug',p.category_slug,
    'selling_unit_code',p.selling_unit_code,'minimum_order_quantity',p.minimum_order_quantity,
    'product_type_description',p.product_type_description,
    'short_description',p.short_description,'description',p.description,
    'status',p.status,'moderation_status',p.moderation_status,'currency',p.currency,
    'price_minor',p.price_minor,'compare_at_price_minor',p.compare_at_price_minor,
    'discount_percent',p.discount_percent,'stock_status',p.stock_status,
    'cash_on_delivery_enabled',p.cash_on_delivery_enabled,'product_type',p.product_type,
    'seller_sku',p.seller_sku,'specifications',p.specifications,
    'shipping_details',p.shipping_details,'return_policy',p.return_policy,
    'validation_errors',p.validation_errors,'submitted_version',p.submitted_version,
    'submitted_at',p.submitted_at,'reviewed_at',p.reviewed_at,'published_at',p.published_at,
    'created_at',p.created_at,'updated_at',p.updated_at,
    'images',coalesce((select jsonb_agg(to_jsonb(i) order by i.is_primary desc,i.display_order) from public.shop_product_images i where i.product_id=p.id and i.deleted_at is null),'[]'::jsonb),
    'specificationValues',coalesce((select jsonb_agg(jsonb_build_object(
      'attributeCode',a.code,'displayName',a.display_name,'valueText',pav.value_text,
      'valueNumber',pav.value_number,'valueBoolean',pav.value_boolean,'valueJson',pav.value_json,
      'unit',coalesce(pav.unit,a.unit)) order by a.sort_order)
      from public.shop_product_attribute_values pav join public.shop_attribute_definitions a on a.id=pav.attribute_definition_id
      where pav.product_id=p.id),'[]'::jsonb),
    'productOptions',coalesce((select jsonb_agg(jsonb_build_object(
      'id',po.id,'attributeCode',po.code,'name',po.name,'position',po.position,'status',po.status,
      'values',coalesce((select jsonb_agg(jsonb_build_object('id',pov.id,'code',pov.code,'value',pov.value,
        'displayValue',pov.display_value,'colorHex',pov.color_hex,'position',pov.position,'status',pov.status) order by pov.position)
        from public.shop_product_option_values pov where pov.product_option_id=po.id),'[]'::jsonb)) order by po.position)
      from public.shop_product_options po where po.product_id=p.id and po.status='active'),'[]'::jsonb),
    'variants',coalesce((select jsonb_agg(to_jsonb(v)||jsonb_build_object(
      'inventory',coalesce((select to_jsonb(inv) from public.shop_inventory inv where inv.variant_id=v.id limit 1),'{}'::jsonb),
      'imageIds',coalesce((select jsonb_agg(vm.product_image_id order by vm.position) from public.shop_variant_media vm where vm.variant_id=v.id),'[]'::jsonb)) order by v.created_at)
      from public.shop_product_variants v where v.product_id=p.id and v.status<>'archived'),'[]'::jsonb),
    'inventory',coalesce((select jsonb_agg(to_jsonb(inv)) from public.shop_inventory inv where inv.product_id=p.id),'[]'::jsonb),
    'store',(select to_jsonb(s)-'owner_profile_id' from public.shop_stores s where s.id=p.store_id)
  )
$$;

alter function public.adishop_product_catalog_payload(uuid) rename to adishop_product_catalog_payload_category_core;
revoke all on function public.adishop_product_catalog_payload_category_core(uuid) from public,anon,authenticated;
grant execute on function public.adishop_product_catalog_payload_category_core(uuid) to service_role;
create or replace function public.adishop_product_catalog_payload(p_product_id uuid)
returns jsonb language sql stable security definer set search_path=public as $$
  select case when payload is null then null else payload || jsonb_build_object(
    'sellingUnitCode',p.selling_unit_code,
    'minimumOrderQuantity',p.minimum_order_quantity,
    'sellingUnit',jsonb_build_object('code',u.code,'name',u.name,'symbol',u.symbol,'quantityScale',u.quantity_scale)
  ) end
  from (select public.adishop_product_catalog_payload_category_core(p_product_id) payload) x
  left join public.shop_products p on p.id=p_product_id
  left join public.shop_selling_units u on u.code=p.selling_unit_code
$$;
revoke all on function public.adishop_product_catalog_payload(uuid) from public;
grant execute on function public.adishop_product_catalog_payload(uuid) to anon,authenticated,service_role;
