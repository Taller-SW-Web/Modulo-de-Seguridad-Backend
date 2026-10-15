-- Solo datos de prueba INCOHERENTES, antes de V102; V102 debe abortar con ellos.
-- Una familia_id repartida entre dos usuarios: el backfill no elige ninguno.
INSERT INTO usuario(id, correo, nombres, apellidos, estado) VALUES
 ('00000000-0000-0000-0000-0000000000c1', 'c@ejemplo.test', 'Cliente', 'C', 'ACTIVO'),
 ('00000000-0000-0000-0000-0000000000d1', 'd@ejemplo.test', 'Cliente', 'D', 'ACTIVO');

INSERT INTO token_refresco(jti, usuario_id, familia_id, hash_token, revocado, usado, expiracion, creado_en) VALUES
 ('00000000-0000-0000-0000-0000000000c2', '00000000-0000-0000-0000-0000000000c1',
  '00000000-0000-0000-0000-0000000000cc', repeat('d',64), false, false,
  '2026-10-19 20:00:00+00', '2026-10-12 20:00:00+00'),
 ('00000000-0000-0000-0000-0000000000d2', '00000000-0000-0000-0000-0000000000d1',
  '00000000-0000-0000-0000-0000000000cc', repeat('e',64), false, false,
  '2026-10-19 20:00:00+00', '2026-10-12 20:00:00+00');
