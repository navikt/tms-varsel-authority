create table varsel (
    varselId text unique primary key,
    type text not null,
    ident text not null,
    aktiv boolean not null,
    sensitivitet text not null,
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
create index varsel_opprettet on varsel(opprettet);
create index varsel_aktiv_frem_til on varsel(aktivFremTil);

create table varsel_arkiv (
    varselId text unique primary key,
    ident text not null,
    varsel jsonb not null,
    arkivert timestamp with time zone
);

create index varsel_arkiv_ident on varsel_arkiv(ident);

create table varsel_migration_log (
    type text not null,
    varselId text not null,
    duplikat boolean,
    forstBehandlet timestamp with time zone not null,
    migrert timestamp with time zone
);

create index varsel_migration_varselid on varsel_migration_log(varselId);
create index varsel_migration_forstbehandlet on varsel_migration_log(forstBehandlet);

create table arkivert_varsel_migration_log (
    type text not null,
    varselId text not null,
    duplikat boolean,
    arkivert timestamp with time zone not null,
    migrert timestamp with time zone
);

create index arkivert_varsel_migration_varselid on arkivert_varsel_migration_log(varselId);
create index arkivert_varsel_migration_arkivert on arkivert_varsel_migration_log(arkivert);
