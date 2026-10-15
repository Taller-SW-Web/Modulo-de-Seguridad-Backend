-- Solo datos de prueba, antes de V103; no son semillas de runtime ni de Supabase.
INSERT INTO usuario(id, correo, nombres, apellidos, estado)
VALUES ('00000000-0000-0000-0000-000000000091', 'historico@ejemplo.test', 'Historico', 'Prueba', 'ACTIVO');
INSERT INTO desafio_mfa(id, usuario_id, hash_token, expiracion, consumido, creado_en)
VALUES ('00000000-0000-0000-0000-000000000092', '00000000-0000-0000-0000-000000000091',
        repeat('f',64), '2020-01-01 00:05:00+00', false, '2020-01-01 00:00:00+00');
INSERT INTO otp(id, usuario_id, hash_codigo, canal, expiracion, usado, intentos, creado_en)
VALUES ('00000000-0000-0000-0000-000000000093', '00000000-0000-0000-0000-000000000091',
        repeat('f',64), 'EMAIL', '2020-01-01 00:05:00+00',
        false, 0, '2020-01-01 00:00:00+00');
