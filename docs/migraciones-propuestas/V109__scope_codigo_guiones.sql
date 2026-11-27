ALTER TABLE public.scope
    DROP CONSTRAINT ck_scope_codigo,
    ADD CONSTRAINT ck_scope_codigo
        CHECK (codigo ~ '^[a-z_]+(-[a-z_]+)*(:[a-z_]+(-[a-z_]+)*)+$');
