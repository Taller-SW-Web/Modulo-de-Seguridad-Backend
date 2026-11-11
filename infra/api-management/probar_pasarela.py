"""Comprueba la pasarela de API Management y escribe la tabla de evidencia (FEAT-19).

Hace tres comprobaciones y nunca imprime la clave de suscripción:

1. Equivalencia: las rutas públicas responden igual por la pasarela que directo.
2. Acceso: sin clave de suscripción, la pasarela rechaza la petición.
3. Límite: una ráfaga por encima de 30 peticiones por minuto recibe 429.

Variables de entorno:
    G7_PASARELA_URL    URL de la API en la pasarela, sin barra final (obligatoria)
    G7_PASARELA_CLAVE  clave de suscripción (obligatoria)
    G7_DIRECTA_URL     por defecto https://g7-auth-service.onrender.com/api/v1

Uso:
    python infra/api-management/probar_pasarela.py [--rafaga 40]

Solo usa la biblioteca estándar. Devuelve 0 si las tres comprobaciones pasan.
"""
import argparse
import json
import os
import sys
import time
import urllib.error
import urllib.request
from datetime import datetime, timezone

CABECERA_CLAVE = 'Ocp-Apim-Subscription-Key'
UUID_INEXISTENTE = '00000000-0000-4000-8000-000000000000'

# (método, ruta, estado esperado). Ninguna necesita credenciales de G7.
RUTAS_EQUIVALENCIA = [
    ('GET', '/auth/.well-known/jwks.json', 200),
    ('GET', '/auth/.well-known/openid-configuration', 200),
    ('GET', f'/usuarios/{UUID_INEXISTENTE}', 401),
    ('GET', '/roles', 401),
]


def pedir(metodo, url, cabeceras=None):
    """Devuelve (estado, cabeceras, cuerpo). Un error HTTP no lanza excepción."""
    peticion = urllib.request.Request(url, method=metodo, headers=cabeceras or {})
    try:
        with urllib.request.urlopen(peticion, timeout=90) as respuesta:
            return respuesta.status, dict(respuesta.headers), respuesta.read()
    except urllib.error.HTTPError as error:
        return error.code, dict(error.headers), error.read()


def resumen(cuerpo):
    """Lo comparable de una respuesta: los kid de un JWKS o el code de un problema."""
    try:
        datos = json.loads(cuerpo)
    except ValueError:
        return 'no es JSON'
    if isinstance(datos, dict) and 'keys' in datos:
        return 'kid: ' + ', '.join(sorted(clave.get('kid', '?')[:8] for clave in datos['keys']))
    if isinstance(datos, dict) and 'code' in datos:
        return 'code: ' + str(datos['code'])
    if isinstance(datos, dict) and 'issuer' in datos:
        return 'issuer: ' + str(datos['issuer'])
    return 'JSON'


def equivalencia(directa, pasarela, clave):
    filas, correcto = [], True
    for metodo, ruta, esperado in RUTAS_EQUIVALENCIA:
        estado_d, _, cuerpo_d = pedir(metodo, directa + ruta)
        estado_p, _, cuerpo_p = pedir(metodo, pasarela + ruta, {CABECERA_CLAVE: clave})
        igual = estado_d == estado_p == esperado and resumen(cuerpo_d) == resumen(cuerpo_p)
        correcto = correcto and igual
        filas.append(f'| `{metodo} {ruta}` | {estado_d} · {resumen(cuerpo_d)} | '
                     f'{estado_p} · {resumen(cuerpo_p)} | {"Igual" if igual else "DISTINTO"} |')
    return filas, correcto


def acceso(pasarela):
    estado, _, _ = pedir('GET', pasarela + '/auth/.well-known/jwks.json')
    return estado, estado == 401


def limite(pasarela, clave, rafaga):
    """Espera una ventana limpia y envía la ráfaga seguida."""
    time.sleep(61)
    estados, reintento = [], None
    for _ in range(rafaga):
        estado, cabeceras, _ = pedir('GET', pasarela + '/auth/.well-known/openid-configuration',
                                     {CABECERA_CLAVE: clave})
        estados.append(estado)
        if estado == 429 and reintento is None:
            reintento = cabeceras.get('Retry-After')
    aceptadas, rechazadas = estados.count(200), estados.count(429)
    primera = estados.index(429) + 1 if 429 in estados else None
    correcto = rechazadas > 0 and aceptadas <= 30 and aceptadas + rechazadas == rafaga
    return aceptadas, rechazadas, primera, reintento, correcto


def main():
    analizador = argparse.ArgumentParser(description=__doc__.split('\n')[0])
    analizador.add_argument('--rafaga', type=int, default=40, help='peticiones de la prueba de límite')
    opciones = analizador.parse_args()
    pasarela = os.environ.get('G7_PASARELA_URL', '').rstrip('/')
    clave = os.environ.get('G7_PASARELA_CLAVE', '')
    directa = os.environ.get('G7_DIRECTA_URL', 'https://g7-auth-service.onrender.com/api/v1').rstrip('/')
    if not pasarela or not clave:
        print('Faltan G7_PASARELA_URL o G7_PASARELA_CLAVE.', file=sys.stderr)
        return 2

    # Despierta el servicio de Render antes de comparar tiempos y estados.
    pedir('GET', directa + '/auth/.well-known/jwks.json')

    filas, igual = equivalencia(directa, pasarela, clave)
    estado_sin_clave, protegido = acceso(pasarela)
    aceptadas, rechazadas, primera, reintento, limitado = limite(pasarela, clave, opciones.rafaga)

    print(f'Fecha (UTC): {datetime.now(timezone.utc):%Y-%m-%d %H:%M}')
    print(f'Directa: {directa}')
    print(f'Pasarela: {pasarela}')
    print()
    print('| Petición | Directa | Por la pasarela | Resultado |')
    print('|---|---|---|---|')
    print('\n'.join(filas))
    print()
    print('| Comprobación | Resultado |')
    print('|---|---|')
    print(f'| Sin clave de suscripción | {estado_sin_clave} ({"rechazada" if protegido else "NO RECHAZADA"}) |')
    print(f'| Ráfaga de {opciones.rafaga} peticiones | {aceptadas} con 200 y {rechazadas} con 429 |')
    print(f'| Primera petición rechazada | {primera if primera else "ninguna"} |')
    print(f'| `Retry-After` del primer 429 | {reintento if reintento else "sin cabecera"} |')
    return 0 if igual and protegido and limitado else 1


if __name__ == '__main__':
    sys.stdout.reconfigure(encoding='utf-8')  # la tabla se pega en la evidencia
    sys.stderr.reconfigure(encoding='utf-8')
    sys.exit(main())
