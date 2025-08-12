-- add foreign key from actions to orders
alter table actions
    add constraint fk_actions_orders
        foreign key (order_id) references orders (id)
            on delete cascade;

comment on constraint fk_actions_orders on actions is 'Foreign key to orders table. Deletes related actions when order is removed.';
