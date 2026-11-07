#!/usr/bin/env bash
set -euo pipefail
# Solo para una base desechable del job de CI, nunca para Render o Supabase.
: "${PGHOST:?}" "${PGPORT:?}" "${PGUSER:?}" "${PGDATABASE:?}"
[[ "$PGHOST" == 127.0.0.1 && "$PGDATABASE" == g7_e2e ]] || {
  echo 'Base de pruebas inesperada; se cancela la inicialización.' >&2
  exit 1
}
psql -X -v ON_ERROR_STOP=1 -1 -f src/test/resources/mfa/base-observada/schema.sql
for version in 101 102 103 104 105 106 108; do
  files=(src/main/resources/db/migration/V${version}__*.sql)
  [[ ${#files[@]} == 1 && -f ${files[0]} ]] || exit 1
  psql -X -v ON_ERROR_STOP=1 -1 -f "${files[0]}"
done
psql -X -v ON_ERROR_STOP=1 -1 -f src/main/resources/db/autorizacion/R__catalogo_autorizacion.sql
