-- Bucket privado para heatmaps e PDFs. Caminho: {user_id}/{survey_id}/arquivo.ext
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('survey-exports', 'survey-exports', false, 20971520,
        array['image/png','image/jpeg','application/pdf'])
on conflict (id) do nothing;

create policy "survey_exports_read_own" on storage.objects for select to authenticated
  using (bucket_id = 'survey-exports' and (storage.foldername(name))[1] = auth.uid()::text);

create policy "survey_exports_write_own" on storage.objects for insert to authenticated
  with check (bucket_id = 'survey-exports' and (storage.foldername(name))[1] = auth.uid()::text);

create policy "survey_exports_update_own" on storage.objects for update to authenticated
  using (bucket_id = 'survey-exports' and (storage.foldername(name))[1] = auth.uid()::text);

create policy "survey_exports_delete_own" on storage.objects for delete to authenticated
  using (bucket_id = 'survey-exports' and (storage.foldername(name))[1] = auth.uid()::text);
