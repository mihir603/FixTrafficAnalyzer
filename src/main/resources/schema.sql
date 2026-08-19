-- Idempotent schema, portable across H2 and PostgreSQL.

create table if not exists import_batch (
    id               bigint generated always as identity primary key,
    filename         varchar(512),
    imported_at      timestamp,
    total_lines      int,
    parsed_messages  int,
    event_lines      int,
    skipped_lines    int
);

create table if not exists fix_log (
    id          bigint generated always as identity primary key,
    import_id   bigint not null references import_batch (id),
    line_no     int,
    log_time    timestamp,
    direction   varchar(16),
    plugin      varchar(256),
    pid         bigint,
    msg_type    varchar(16),
    raw         text
);

create table if not exists fix_message (
    id             bigint generated always as identity primary key,
    fix_log_id     bigint not null references fix_log (id),
    import_id      bigint not null,
    version        varchar(16),
    msg_type       varchar(8),
    msg_name       varchar(64),
    sender_comp_id varchar(64),
    target_comp_id varchar(64),
    msg_seq_num    bigint,
    sending_time   varchar(32),
    cl_ord_id      varchar(64),
    order_id       varchar(64),
    symbol         varchar(64),
    security_id    varchar(128),
    side           varchar(8),
    order_qty      bigint,
    price          double precision,
    exec_id        varchar(64),
    exec_type      varchar(8),
    ord_status     varchar(8)
);

create index if not exists idx_fix_message_import on fix_message (import_id);
create index if not exists idx_fix_message_type on fix_message (msg_type);
create index if not exists idx_fix_message_clordid on fix_message (cl_ord_id);
create index if not exists idx_fix_message_symbol on fix_message (symbol);
create index if not exists idx_fix_log_import on fix_log (import_id);
