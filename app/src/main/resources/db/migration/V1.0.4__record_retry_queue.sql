create table record_retry_queue(
    id serial primary key,
    topic text not null,
    recordKey text not null,
    recordValue text not null,
    createdAt timestamp with time zone
);

create index retry_queue_record_key on record_retry_queue(topic);
create index retry_queue_created_at on record_retry_queue(createdAt);
