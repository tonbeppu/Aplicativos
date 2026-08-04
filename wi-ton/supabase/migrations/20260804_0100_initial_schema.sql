-- =========================================================
-- Wi Ton — schema inicial (aplicada em ylysgozabadglznclmpn)
-- =========================================================

create table public.profiles (
  id          uuid primary key references auth.users(id) on delete cascade,
  email       text,
  full_name   text,
  avatar_url  text,
  created_at  timestamptz not null default now(),
  updated_at  timestamptz not null default now()
);

alter table public.profiles enable row level security;

create policy "profiles_select_own" on public.profiles
  for select to authenticated using (auth.uid() = id);
create policy "profiles_insert_own" on public.profiles
  for insert to authenticated with check (auth.uid() = id);
create policy "profiles_update_own" on public.profiles
  for update to authenticated using (auth.uid() = id) with check (auth.uid() = id);

create type public.survey_status as enum ('recording', 'processing', 'finished', 'failed');

create table public.surveys (
  id             uuid primary key default gen_random_uuid(),
  user_id        uuid not null references auth.users(id) on delete cascade,
  name           text not null,
  status         public.survey_status not null default 'recording',
  ssid           text,
  bssid          text,
  frequency_mhz  integer,
  device_model    text,
  android_version text,
  floor_y        real,
  area_m2        real,
  points_count   integer not null default 0,
  rssi_min       integer,
  rssi_max       integer,
  rssi_avg       real,
  started_at     timestamptz not null default now(),
  ended_at       timestamptz,
  created_at     timestamptz not null default now(),
  updated_at     timestamptz not null default now(),
  constraint surveys_name_not_blank check (length(btrim(name)) > 0)
);

create index surveys_user_id_started_at_idx on public.surveys (user_id, started_at desc);
alter table public.surveys enable row level security;

create policy "surveys_select_own" on public.surveys
  for select to authenticated using (auth.uid() = user_id);
create policy "surveys_delete_own" on public.surveys
  for delete to authenticated using (auth.uid() = user_id);

create table public.survey_points (
  id                bigserial primary key,
  survey_id         uuid not null references public.surveys(id) on delete cascade,
  x real not null, y real not null, z real not null,
  rssi              integer not null,
  raw_rssi          integer,
  link_speed_mbps   integer,
  frequency_mhz     integer,
  tracking_quality  text,
  seq               integer,
  captured_at       timestamptz not null default now(),
  constraint survey_points_rssi_range check (rssi between -120 and 0)
);

create index survey_points_survey_id_seq_idx on public.survey_points (survey_id, seq);
alter table public.survey_points enable row level security;

create policy "survey_points_select_own" on public.survey_points
  for select to authenticated using (
    exists (select 1 from public.surveys s where s.id = survey_id and s.user_id = auth.uid()));
create policy "survey_points_delete_own" on public.survey_points
  for delete to authenticated using (
    exists (select 1 from public.surveys s where s.id = survey_id and s.user_id = auth.uid()));

create table public.survey_walls (
  id         bigserial primary key,
  survey_id  uuid not null references public.surveys(id) on delete cascade,
  x1 real not null, z1 real not null,
  x2 real not null, z2 real not null,
  height     real,
  confidence real,
  created_at timestamptz not null default now()
);

create index survey_walls_survey_id_idx on public.survey_walls (survey_id);
alter table public.survey_walls enable row level security;

create policy "survey_walls_select_own" on public.survey_walls
  for select to authenticated using (
    exists (select 1 from public.surveys s where s.id = survey_id and s.user_id = auth.uid()));
create policy "survey_walls_delete_own" on public.survey_walls
  for delete to authenticated using (
    exists (select 1 from public.surveys s where s.id = survey_id and s.user_id = auth.uid()));

create type public.export_kind as enum ('heatmap_png', 'floorplan_png', 'report_pdf');

create table public.survey_exports (
  id           uuid primary key default gen_random_uuid(),
  survey_id    uuid not null references public.surveys(id) on delete cascade,
  kind         public.export_kind not null,
  storage_path text not null,
  size_bytes   bigint,
  created_at   timestamptz not null default now()
);

create index survey_exports_survey_id_idx on public.survey_exports (survey_id);
alter table public.survey_exports enable row level security;

create policy "survey_exports_select_own" on public.survey_exports
  for select to authenticated using (
    exists (select 1 from public.surveys s where s.id = survey_id and s.user_id = auth.uid()));
create policy "survey_exports_delete_own" on public.survey_exports
  for delete to authenticated using (
    exists (select 1 from public.surveys s where s.id = survey_id and s.user_id = auth.uid()));

create or replace function public.touch_updated_at()
returns trigger language plpgsql set search_path = public as $$
begin
  new.updated_at = now();
  return new;
end; $$;

create trigger profiles_touch_updated_at before update on public.profiles
  for each row execute function public.touch_updated_at();
create trigger surveys_touch_updated_at before update on public.surveys
  for each row execute function public.touch_updated_at();

-- Fecha a leitura e consolida as estatisticas numa chamada so.
create or replace function public.finish_survey(p_survey_id uuid, p_area_m2 real default null)
returns public.surveys
language plpgsql security invoker set search_path = public, pg_temp as $$
declare result public.surveys;
begin
  update public.surveys s
  set status       = 'finished',
      ended_at     = coalesce(s.ended_at, now()),
      area_m2      = coalesce(p_area_m2, s.area_m2),
      points_count = (select count(*) from public.survey_points p where p.survey_id = s.id),
      rssi_min     = (select min(p.rssi) from public.survey_points p where p.survey_id = s.id),
      rssi_max     = (select max(p.rssi) from public.survey_points p where p.survey_id = s.id),
      rssi_avg     = (select avg(p.rssi) from public.survey_points p where p.survey_id = s.id)
  where s.id = p_survey_id
  returning s.* into result;
  return result;
end; $$;

revoke execute on function public.touch_updated_at() from public, anon, authenticated;
