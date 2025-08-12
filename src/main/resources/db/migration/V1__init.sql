-- table: orders
create table orders
(
    id        varchar(50) primary key,
    name      varchar(255) not null,
    temp      varchar(10)  not null,
    freshness int          not null,
    storage   varchar(10)  not null,
    placed_at timestamp    not null
);

comment on column orders.id is 'Unique order identifier';
comment on column orders.name is 'Name of the order item';
comment on column orders.temp is 'Storage temperature type: HOT, COLD, ROOM';
comment on column orders.freshness is 'Freshness time in seconds before expiration';
comment on column orders.storage is 'Storage location where the order is currently placed';
comment on column orders.placed_at is 'Timestamp when the order was placed in storage';

-- indexes for orders
create index idx_orders_storage on orders(storage);
create index idx_orders_placed_at on orders(placed_at);

-- table: actions
create table actions
(
    id       bigserial primary key,
    ts       timestamp   not null,
    order_id varchar(50) not null,
    action   text        not null,
    target   varchar(10) not null
);

comment on column actions.id is 'Unique action identifier';
comment on column actions.ts is 'Timestamp when the action occurred';
comment on column actions.order_id is 'Order identifier related to this action';
comment on column actions.action is 'Type of action: PLACE, MOVE, PICKUP, DISCARD';
comment on column actions.target is 'Target storage location for the action';

-- indexes for actions
create index idx_actions_order_id on actions(order_id);
create index idx_actions_ts on actions(ts);
