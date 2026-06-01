create table outgoing_record_queue
(
    id serial primary key,
    topic text not null,
    recordKey text not null,
    recordValue text not null,
    createdAt timestamp with time zone
);

create index outgoing_record_queue_record_key on outgoing_record_queue(recordKey);
create index outgoing_record_queue_created_at on outgoing_record_queue(createdAt);
