-- Security hardening: enable Row Level Security on the billing tables.
--
-- Without RLS, the public `anon` key (which ships in the GitHub APK) could read
-- and write these tables directly via PostgREST — allowing anyone to forge a
-- "completed" purchase and grant themselves premium, or read/delete others'
-- billing records. Enabling RLS with NO policies makes the tables default-deny
-- for the anon/authenticated roles. The Edge Functions access them with the
-- service_role key, which bypasses RLS, so their behavior is unchanged.

alter table public.purchases enable row level security;
alter table public.subscriptions enable row level security;

-- Belt and suspenders: revoke direct table privileges from the public API roles.
revoke all on public.purchases from anon, authenticated;
revoke all on public.subscriptions from anon, authenticated;
