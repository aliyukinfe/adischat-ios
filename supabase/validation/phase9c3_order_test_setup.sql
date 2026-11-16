-- Synthetic helpers for disposable PostgreSQL only. Not production RPC parity.
create trigger shop_order_groups_prepare_seller_response_sla
before insert or update of fulfillment_status on public.shop_order_groups
for each row execute function public.adishop_prepare_seller_response_sla();

create function public.phase9c3_new_order(
  p_age interval default interval '0 seconds',
  p_payment_status text default 'unpaid',
  p_quantity integer default 1
) returns jsonb language plpgsql as $$
declare
  order_id uuid := gen_random_uuid();
  group_id uuid := gen_random_uuid();
  buyer_id uuid := '40000000-0000-4000-8000-000000000001';
begin
  insert into public.shop_orders(id,profile_id,order_number,status,fulfillment_status,delivery_status,
    payment_status,subtotal_minor,total_minor,created_at)
  values(order_id,buyer_id,order_id::text,'pending','awaiting_seller_confirmation','not_shipped',
    p_payment_status,1000,1000,clock_timestamp()-p_age);
  insert into public.shop_order_groups(id,order_id,store_id,status,fulfillment_status,payment_status,
    subtotal_minor,store_name_snapshot)
  values(group_id,order_id,'10000000-0000-4000-8000-000000000001','created',
    'awaiting_seller_confirmation',p_payment_status,1000,'Synthetic Store');
  insert into public.shop_order_items(order_group_id,product_id,quantity)
  values(group_id,'30000000-0000-4000-8000-000000000001',p_quantity);
  update public.shop_inventory set quantity_available=quantity_available-p_quantity
  where product_id='30000000-0000-4000-8000-000000000001' and variant_id is null;
  insert into public.shop_payments(order_id,profile_id,provider,method_code,status,amount_minor)
  values(order_id,buyer_id,'synthetic','test',p_payment_status,1000);
  return jsonb_build_object('orderId',order_id,'groupId',group_id);
end;
$$;

create function public.phase9c3_accept(p_group_id uuid) returns text language plpgsql as $$
declare g public.shop_order_groups%rowtype;
begin
  select * into g from public.shop_order_groups where id=p_group_id for update;
  if not found or g.fulfillment_status not in ('awaiting_seller_confirmation','created')
     or g.seller_response_status <> 'pending'
     or g.seller_response_required_at <= clock_timestamp() then return 'denied'; end if;
  perform 1 from public.shop_orders where id=g.order_id for update;
  update public.shop_order_groups set fulfillment_status='accepted',status='accepted',
    seller_response_status='accepted',seller_responded_at=clock_timestamp() where id=p_group_id;
  update public.shop_orders set fulfillment_status='accepted' where id=g.order_id;
  return 'accepted';
end;
$$;

create function public.phase9c3_expire(p_group_id uuid) returns jsonb language plpgsql as $$
declare
  g public.shop_order_groups%rowtype;
  o public.shop_orders%rowtype;
  item public.shop_order_items%rowtype;
  payment_result jsonb;
begin
  select * into g from public.shop_order_groups where id=p_group_id for update;
  if not found then return jsonb_build_object('ok',false,'code','not_found'); end if;
  if g.seller_response_status <> 'pending' or g.fulfillment_status not in ('awaiting_seller_confirmation','created') then
    return jsonb_build_object('ok',true,'idempotent',true);
  end if;
  if g.seller_response_required_at > clock_timestamp() then
    return jsonb_build_object('ok',false,'code','not_due');
  end if;
  select * into o from public.shop_orders where id=g.order_id for update;
  payment_result := public.adishop_prepare_group_payment_reversal(
    g,o,'seller_response_expired','Seller did not respond.','seller-response-expired');
  if g.inventory_released_at is null then
    for item in select * from public.shop_order_items where order_group_id=g.id order by product_id,variant_id,id loop
      update public.shop_inventory set quantity_available=quantity_available+item.quantity
      where product_id=item.product_id and variant_id is not distinct from item.variant_id;
      if not found then raise exception 'inventory-release-missing'; end if;
    end loop;
  end if;
  update public.shop_order_groups set status='expired',fulfillment_status='expired',
    seller_response_status='expired',seller_response_expired_at=clock_timestamp(),
    seller_response_refund_status=payment_result->>'status',
    seller_response_refund_request_id=nullif(payment_result->>'refundRequestId','')::uuid,
    inventory_released_at=coalesce(inventory_released_at,clock_timestamp()) where id=g.id;
  update public.shop_orders set status='cancelled',fulfillment_status='expired' where id=o.id;
  return jsonb_build_object('ok',true,'status','expired','refundStatus',payment_result->>'status');
end;
$$;
