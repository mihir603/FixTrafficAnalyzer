drop table if exists fix_message;
drop table if exists fix_log;

create table if not exists fix_log(
    id serial primary key not null,
    date_time text,
    pid bigint,
    message_type text,
    fix_plugin text,
    log text
);

create table if not exists fix_message(
    id serial primary key not null,
    version text,
    message_type text,
    sender_comp_id text,
    target_comp_id text,
    message_seq_no bigint,
    sending_time text,
    client_order_id text,
    system_order_id text,
    symbol text,
    security_id text,
    side text,
    order_qty integer,
    price numeric(7, 2),
    exec_id text,
    exec_type text,
    order_status text,
    fix_log bigint references fix_log (id)
);