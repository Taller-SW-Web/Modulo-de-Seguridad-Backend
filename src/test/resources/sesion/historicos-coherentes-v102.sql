-- Solo datos de prueba coherentes, antes de V102; no son semillas de runtime ni de Supabase.
-- Familia A: dos tokens del mismo usuario, uno revocado -> la raíz backfill queda revocada.
-- Familia B: un token vigente -> la raíz backfill queda activa.
INSERT INTO usuario(id, correo, nombres, apellidos, estado) VALUES
 ('00000000-0000-0000-0000-0000000000a1', 'a@ejemplo.test', 'Cliente', 'A', 'ACTIVO'),
 ('00000000-0000-0000-0000-0000000000b1', 'b@ejemplo.test', 'Cliente', 'B', 'ACTIVO');

INSERT INTO token_refresco(jti, usuario_id, familia_id, hash_token, revocado, usado, expiracion, creado_en) VALUES
 ('00000000-0000-0000-0000-0000000000a2', '00000000-0000-0000-0000-0000000000a1',
  '00000000-0000-0000-0000-0000000000aa', repeat('a',64), true, true,
  '2026-10-19 20:00:00+00', '2026-10-12 20:00:00+00'),
 ('00000000-0000-0000-0000-0000000000a3', '00000000-0000-0000-0000-0000000000a1',
  '00000000-0000-0000-0000-0000000000aa', repeat('b',64), false, false,
  '2026-10-19 20:01:00+00', '2026-10-12 20:01:00+00'),
 ('00000000-0000-0000-0000-0000000000b2', '00000000-0000-0000-0000-0000000000b1',
  '00000000-0000-0000-0000-0000000000bb', repeat('c',64), false, false,
  '2026-10-19 20:00:00+00', '2026-10-12 20:00:00+00');
