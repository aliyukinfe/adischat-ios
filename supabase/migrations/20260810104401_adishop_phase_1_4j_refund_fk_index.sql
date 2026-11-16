create index if not exists shop_order_groups_seller_response_refund_request_idx
  on public.shop_order_groups(seller_response_refund_request_id)
  where seller_response_refund_request_id is not null;
