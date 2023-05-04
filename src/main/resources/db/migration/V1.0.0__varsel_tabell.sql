create table varsel (
    varselId text unique primary key,
    type text not null,
    ident text not null,
    aktiv boolean not null,
    innhold jsonb not null,
    produsent jsonb not null,
    eksternVarslingBestilling jsonb,
    eksternVarslingStatus jsonb,
    opprettet timestamp with time zone not null,
    aktivFremTil timestamp with time zone,
    inaktivert timestamp with time zone,
    inaktivertAv text
);

create index varsel_ident on varsel(ident);
create index varsel_innhold_id on varsel using gin ((innhold -> 'varselId'));
create index varsel_innhold_ident on varsel using gin ((innhold -> 'ident'));
create index varsel_opprettet on varsel(opprettet);
create index varsel_aktiv_frem_til on varsel(aktivFremTil);


create table varsel_archive (
    varselId text unique primary key,
    ident text not null,
    varsel jsonb not null,
    arkivert timestamp with time zone
);

create index varsel_archive_ident on varsel_archive(ident);
