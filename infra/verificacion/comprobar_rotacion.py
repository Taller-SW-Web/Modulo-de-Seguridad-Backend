"""Comprueba la rotación de la clave de firma (FEAT-05-T4, RF-17.3, ESC-17.2).

Se ejecuta dos veces: antes de rotar, para anotar el `kid` vigente, y después,
para comprobar que el JWKS publica dos claves y que un token emitido antes
sigue validando.

Variables de entorno:
    G7_BASE_URL        por defecto https://g7-auth-service.onrender.com/api/v1
    G7_TOKEN_ANTERIOR  opcional: un accessToken de usuario emitido ANTES de rotar.
                       No se imprime; solo se lee su `kid` y se usa en /auth/me.
                       Debe ser un JWS de 3 partes, de un POST /auth/login SIN
                       `formatoAcceso`. El de la SPA es un JWE (ADR-009): su
                       cabecera no lleva `kid` y no sirve para esta prueba.

Uso:
    python infra/verificacion/comprobar_rotacion.py [--esperadas 2]

Solo usa la biblioteca estándar. Devuelve 0 si el JWKS tiene las claves
esperadas y, cuando se da un token anterior, este sigue validando.
"""
import argparse
import base64
import json
import os
import sys
import urllib.error
import urllib.request
from datetime import datetime, timezone


def pedir(url, cabeceras=None):
    peticion = urllib.request.Request(url, headers=cabeceras or {})
    try:
        with urllib.request.urlopen(peticion, timeout=90) as respuesta:
            return respuesta.status, respuesta.read()
    except urllib.error.HTTPError as error:
        return error.code, error.read()


def kid_del_token(token):
    """Lee el `kid` de la cabecera del JWS sin validar nada."""
    partes = token.split('.')
    if len(partes) == 5:
        raise ValueError('El token es un JWE de 5 partes (acceso cifrado, ADR-009) y su cabecera no '
                         'lleva `kid`. Inicia sesión con POST /auth/login sin `formatoAcceso`.')
    if len(partes) != 3:
        raise ValueError(f'El token tiene {len(partes)} partes; se esperaba un JWS de 3.')
    cabecera = partes[0]
    cabecera += '=' * (-len(cabecera) % 4)
    return json.loads(base64.urlsafe_b64decode(cabecera)).get('kid')


def main():
    analizador = argparse.ArgumentParser(description=__doc__.split('\n')[0])
    analizador.add_argument('--esperadas', type=int, default=2, help='claves que debe publicar el JWKS')
    opciones = analizador.parse_args()
    base = os.environ.get('G7_BASE_URL', 'https://g7-auth-service.onrender.com/api/v1').rstrip('/')
    token = os.environ.get('G7_TOKEN_ANTERIOR', '')

    estado, cuerpo = pedir(base + '/auth/.well-known/jwks.json')
    if estado != 200:
        print(f'El JWKS respondió {estado}.', file=sys.stderr)
        return 1
    claves = json.loads(cuerpo)['keys']
    kids = [clave.get('kid', '?') for clave in claves]
    correcto = len(claves) == opciones.esperadas

    print(f'Fecha (UTC): {datetime.now(timezone.utc):%Y-%m-%d %H:%M}')
    print(f'Base: {base}')
    print()
    print('| Comprobación | Resultado |')
    print('|---|---|')
    print(f'| Claves en el JWKS | {len(claves)} (esperadas: {opciones.esperadas}) |')
    for kid, clave in zip(kids, claves):
        print(f'| `kid` | `{kid}` · {clave.get("alg")} · {clave.get("use")} |')

    if token:
        try:
            kid = kid_del_token(token)
        except ValueError as error:
            print(error, file=sys.stderr)
            return 2
        estado_me, _ = pedir(base + '/auth/me', {'Authorization': 'Bearer ' + token})
        print(f'| `kid` del token anterior | `{kid}` ({"publicado" if kid in kids else "NO publicado"}) |')
        print(f'| `GET /auth/me` con el token anterior | {estado_me} |')
        correcto = correcto and kid in kids and estado_me == 200
    else:
        print('| Token anterior | no se dio `G7_TOKEN_ANTERIOR`; no se comprobó |')
    return 0 if correcto else 1


if __name__ == '__main__':
    sys.stdout.reconfigure(encoding='utf-8')  # la tabla se pega en la evidencia
    sys.stderr.reconfigure(encoding='utf-8')
    sys.exit(main())
