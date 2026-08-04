-- =========================================================
-- Wi Ton — auth por email/senha + liberacao manual pelo admin
-- =========================================================

create schema if not exists private;
grant usage on schema private to authenticated;

create type public.user_role      as enum ('admin', 'user');
create type public.account_status as enum ('pending', 'approved', 'rejected', 'suspended');

alter table public.profiles
  add column role             public.user_role      not null default 'user',
  add column status           public.account_status not null default 'pending',
  add column phone            text,
  add column company          text,
  add column city             text,
  add column requested_at     timestamptz not null default now(),
  add column reviewed_at      timestamptz,
  add column reviewed_by      uuid references auth.users(id) on delete set null,
  add column rejection_reason text;

create index profiles_pending_idx on public.profiles (requested_at) where status = 'pending';

-- Helpers de RLS no schema private: em public virariam endpoints REST publicos.
create or replace function private.is_admin()
returns boolean language sql stable security definer set search_path = public, pg_temp as $$
  select exists (select 1 from public.profiles
                 where id = auth.uid() and role = 'admin' and status = 'approved');
$$;

create or replace function private.is_approved()
returns boolean language sql stable security definer set search_path = public, pg_temp as $$
  select exists (select 1 from public.profiles
                 where id = auth.uid() and status = 'approved');
$$;

revoke execute on function private.is_admin()    from public, anon;
revoke execute on function private.is_approved() from public, anon;
grant  execute on function private.is_admin()    to authenticated;
grant  execute on function private.is_approved() to authenticated;

-- Signup cria perfil pendente. O admin e semeado por email.
create or replace function public.handle_new_user()
returns trigger language plpgsql security definer set search_path = public, pg_temp as $$
declare
  v_role   public.user_role      := 'user';
  v_status public.account_status := 'pending';
begin
  if lower(coalesce(new.email, '')) = 'tonbeppu@gmail.com' then
    v_role := 'admin'; v_status := 'approved';
  end if;

  insert into public.profiles (id, email, full_name, phone, company, city, role, status, reviewed_at)
  values (
    new.id, new.email,
    nullif(btrim(coalesce(new.raw_user_meta_data->>'full_name', '')), ''),
    nullif(btrim(coalesce(new.raw_user_meta_data->>'phone', '')), ''),
    nullif(btrim(coalesce(new.raw_user_meta_data->>'company', '')), ''),
    nullif(btrim(coalesce(new.raw_user_meta_data->>'city', '')), ''),
    v_role, v_status,
    case when v_status = 'approved' then now() end
  )
  on conflict (id) do nothing;
  return new;
end; $$;

revoke execute on function public.handle_new_user() from public, anon, authenticated;

create trigger on_auth_user_created after insert on auth.users
  for each row execute function public.handle_new_user();

-- Sem isto, bastava um PATCH no proprio perfil para virar admin.
create or replace function public.guard_profile_privileges()
returns trigger language plpgsql security definer set search_path = public, pg_temp as $$
begin
  if (new.role is distinct from old.role or new.status is distinct from old.status)
     and not private.is_admin() then
    raise exception 'Somente administradores podem alterar role ou status do perfil';
  end if;
  return new;
end; $$;

revoke execute on function public.guard_profile_privileges() from public, anon, authenticated;

create trigger profiles_guard_privileges before update on public.profiles
  for each row execute function public.guard_profile_privileges();

create policy "profiles_select_admin" on public.profiles
  for select to authenticated using (private.is_admin());
create policy "profiles_update_admin" on public.profiles
  for update to authenticated using (private.is_admin()) with check (private.is_admin());

-- Gravacao de leituras exige conta aprovada (checado no banco, nao so na UI).
create policy "surveys_insert_approved" on public.surveys
  for insert to authenticated
  with check (auth.uid() = user_id and private.is_approved());

create policy "surveys_update_approved" on public.surveys
  for update to authenticated
  using (auth.uid() = user_id and private.is_approved())
  with check (auth.uid() = user_id and private.is_approved());

create policy "survey_points_insert_approved" on public.survey_points
  for insert to authenticated with check (
    private.is_approved()
    and exists (select 1 from public.surveys s where s.id = survey_id and s.user_id = auth.uid()));

create policy "survey_walls_insert_approved" on public.survey_walls
  for insert to authenticated with check (
    private.is_approved()
    and exists (select 1 from public.surveys s where s.id = survey_id and s.user_id = auth.uid()));

create policy "survey_exports_insert_approved" on public.survey_exports
  for insert to authenticated with check (
    private.is_approved()
    and exists (select 1 from public.surveys s where s.id = survey_id and s.user_id = auth.uid()));

-- Admin ve os METADADOS das leituras. Os pontos e paredes ficam fora de proposito:
-- na pratica eles sao a planta baixa da casa do usuario.
create policy "surveys_select_admin" on public.surveys
  for select to authenticated using (private.is_admin());

-- ---------- RPCs de moderacao ----------
create or replace function public.approve_user(p_user_id uuid)
returns public.profiles language plpgsql security invoker set search_path = public, pg_temp as $$
declare r public.profiles;
begin
  update public.profiles
     set status='approved', reviewed_at=now(), reviewed_by=auth.uid(), rejection_reason=null
   where id = p_user_id returning * into r;
  if r.id is null then raise exception 'Perfil nao encontrado ou sem permissao para aprovar'; end if;
  return r;
end; $$;

create or replace function public.reject_user(p_user_id uuid, p_reason text default null)
returns public.profiles language plpgsql security invoker set search_path = public, pg_temp as $$
declare r public.profiles;
begin
  update public.profiles
     set status='rejected', reviewed_at=now(), reviewed_by=auth.uid(), rejection_reason=p_reason
   where id = p_user_id returning * into r;
  if r.id is null then raise exception 'Perfil nao encontrado ou sem permissao para rejeitar'; end if;
  return r;
end; $$;

create or replace function public.suspend_user(p_user_id uuid, p_reason text default null)
returns public.profiles language plpgsql security invoker set search_path = public, pg_temp as $$
declare r public.profiles;
begin
  update public.profiles
     set status='suspended', reviewed_at=now(), reviewed_by=auth.uid(), rejection_reason=p_reason
   where id = p_user_id returning * into r;
  if r.id is null then raise exception 'Perfil nao encontrado ou sem permissao para suspender'; end if;
  return r;
end; $$;

create or replace function public.my_access_status()
returns table (status public.account_status, role public.user_role, rejection_reason text)
language sql stable security invoker set search_path = public, pg_temp as $$
  select p.status, p.role, p.rejection_reason from public.profiles p where p.id = auth.uid();
$$;
