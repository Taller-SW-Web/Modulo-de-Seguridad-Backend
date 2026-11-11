"""Comprueba la consulta de identidad con token de servicio (FEAT-06-T4, SPEC-18).

Con el cliente técnico de prueba (`modulo-prueba-h5`, scopes `usuarios:leer` y
`direcciones:leer`) comprueba en el despliegue:

    200  al emitir el token y al consultar un usuario y sus direcciones
    403  al pedir `/roles`, que exige un scope que el cliente no tiene
    401  al consultar sin token

Nunca imprime el secreto, el token ni los datos del usuario: de cada respuesta
solo muestra el estado, el `code` del error o los nombres de los campos.

Variables de entorno:
    G7_CLIENT_ID      por defecto modulo-prueba-h5
    G7_CLIENT_SECRET  secreto del cliente (obligatoria)
    G7_USUARIO_ID     UUID de una cuenta de prueba existente (obligatoria)
    G7_BASE_URL       por defecto https://g7-auth-service.onrender.com/api/v1

Uso:
    python infra/verificacion/comprobar_consulta_identidad.py

Solo usa la biblioteca estándar. Devuelve 0 si todos los estados son los esperados.
"""
import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime, timezone


def pedir(metodo, url, cabeceras=None, datos=None):
    peticion = urllib.request.Request(url, method=metodo, headers=cabeceras or {}, data=datos)
    try:
        with urllib.request.urlopen(peticion, timeout=90) as respuesta:
            return respuesta.status, respuesta.read()
    except urllib.error.HTTPError as error:
        return error.code, error.read()


def detalle(cuerpo):
    """El `code` de un error o los nombres de los campos; nunca los valores."""
    try:
        datos = json.loads(cuerpo)
    except ValueError:
        return 'sin JSON'
    if isinstance(datos, dict) and 'code' in datos:
        return '`' + str(datos['code']) + '`'
    if isinstance(datos, dict):
        return 'campos: ' + ', '.join(sorted(datos))
    if isinstance(datos, list):
        return f'lista de {len(datos)}'
    return 'JSON'


def main():
    base = os.environ.get('G7_BASE_URL', 'https://g7-auth-service.onrender.com/api/v1').rstrip('/')
    cliente = os.environ.get('G7_CLIENT_ID', 'modulo-prueba-h5')
    secreto = os.environ.get('G7_CLIENT_SECRET', '')
    usuario = os.environ.get('G7_USUARIO_ID', '')
    if not secreto or not usuario:
        print('Faltan G7_CLIENT_SECRET o G7_USUARIO_ID.', file=sys.stderr)
        return 2

    formulario = urllib.parse.urlencode({
        'grant_type': 'client_credentials', 'client_id': cliente, 'client_secret': secreto}).encode()
    estado, cuerpo = pedir('POST', base + '/auth/token',
                           {'Content-Type': 'application/x-www-form-urlencoded'}, formulario)
    filas = []
    correcto = estado == 200
    token, scopes = '', []
    if estado == 200:
        emitido = json.loads(cuerpo)
        token, scopes = emitido['accessToken'], emitido.get('scopes', [])
        filas.append(('`POST /auth/token`', 200, estado, 'scopes: ' + ', '.join(sorted(scopes))))
    else:
        filas.append(('`POST /auth/token`', 200, estado, detalle(cuerpo)))

    autorizacion = {'Authorization': 'Bearer ' + token}
    casos = [
        ('`GET /usuarios/{id}` con `usuarios:leer`', 'GET', f'/usuarios/{usuario}', autorizacion, 200),
        ('`GET /usuarios/{id}/direcciones` con `direcciones:leer`', 'GET', f'/usuarios/{usuario}/direcciones', autorizacion, 200),
        ('`GET /roles` sin `roles:leer`', 'GET', '/roles', autorizacion, 403),
        ('`GET /usuarios/{id}` sin token', 'GET', f'/usuarios/{usuario}', {}, 401),
    ]
    if token:
        for nombre, metodo, ruta, cabeceras, esperado in casos:
            estado, cuerpo = pedir(metodo, base + ruta, cabeceras)
            correcto = correcto and estado == esperado
            filas.append((nombre, esperado, estado, detalle(cuerpo)))
        if 'roles:leer' in scopes:
            correcto = False
            filas.append(('Scopes del cliente', '-', '-', 'tiene `roles:leer`: el caso 403 no es válido'))

    print(f'Fecha (UTC): {datetime.now(timezone.utc):%Y-%m-%d %H:%M}')
    print(f'Base: {base}')
    print(f'Cliente: `{cliente}`')
    print()
    print('| Petición | Esperado | Obtenido | Detalle |')
    print('|---|---|---|---|')
    for nombre, esperado, obtenido, texto in filas:
        print(f'| {nombre} | {esperado} | {obtenido} | {texto} |')
    return 0 if correcto else 1


if __name__ == '__main__':
    sys.stdout.reconfigure(encoding='utf-8')  # la tabla se pega en la evidencia
    sys.exit(main())
