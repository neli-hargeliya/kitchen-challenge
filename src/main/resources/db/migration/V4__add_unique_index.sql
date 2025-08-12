-- add unique index to prevent duplicate actions for the same order and timestamp
create unique index uq_actions_order_action_ts
    on actions(order_id, action, ts);

comment on index uq_actions_order_action_ts is
'Prevents duplicate action entries for the same order and timestamp';
