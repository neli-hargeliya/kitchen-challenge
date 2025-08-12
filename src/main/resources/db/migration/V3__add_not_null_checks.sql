-- add enum-like constraints for orders.temp
alter table orders
    add constraint chk_orders_temp
        check (temp in ('HOT', 'COLD', 'ROOM'));

comment on constraint chk_orders_temp on orders is 'Ensures temp is one of: HOT, COLD, ROOM';

-- add enum-like constraints for orders.storage
alter table orders
    add constraint chk_orders_storage
        check (storage in ('HEATER', 'COOLER', 'SHELF'));

comment on constraint chk_orders_storage on orders is 'Ensures storage is one of: HEATER, COOLER, SHELF';

-- add enum-like constraints for actions.action
alter table actions
    add constraint chk_actions_action
        check (action in ('PLACE', 'MOVE', 'PICKUP', 'DISCARD'));

comment on constraint chk_actions_action on actions is 'Ensures action is one of: PLACE, MOVE, PICKUP, DISCARD';

-- add enum-like constraints for actions.target
alter table actions
    add constraint chk_actions_target
        check (target in ('HEATER', 'COOLER', 'SHELF'));

comment on constraint chk_actions_target on actions is 'Ensures target is one of: HEATER, COOLER, SHELF';
