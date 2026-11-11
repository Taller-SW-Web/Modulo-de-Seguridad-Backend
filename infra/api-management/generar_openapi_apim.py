"""Genera el OpenAPI 3.0 reducido que importa la pasarela de API Management.

El contrato de G7 es OpenAPI 3.1 y vive en el repo de documentación. La
pasarela solo necesita saber qué operaciones publicar, así que este guion
extrae las rutas de RUTAS_PUBLICADAS y escribe un documento 3.0.3 mínimo:
rutas, métodos, operationId, resumen y parámetros de ruta. No copia esquemas.

Uso:
    python infra/api-management/generar_openapi_apim.py \
        <ruta a specs/contrato-api/openapi.yaml> infra/api-management/openapi-apim.json

Requiere PyYAML (el mismo `requirements-contract.txt` del repo de documentación).
"""
import json
import re
import sys

# Rutas que consumen los otros módulos (ADR-010). Las de administración quedan
# fuera: solo las usa la SPA de G7, que llama a la URL directa.
RUTAS_PUBLICADAS = [
    ('/auth/.well-known/jwks.json', 'get'),
    ('/auth/.well-known/openid-configuration', 'get'),
    ('/auth/token', 'post'),
    ('/auth/introspeccion', 'post'),
    ('/usuarios/{id}', 'get'),
    ('/usuarios/lote', 'post'),
    ('/usuarios/{id}/direcciones', 'get'),
    ('/roles', 'get'),
    ('/permisos', 'get'),
    ('/auth/login', 'post'),
    ('/auth/otp/solicitar', 'post'),
    ('/auth/otp/verificar', 'post'),
    ('/auth/refresh', 'post'),
    ('/auth/logout', 'post'),
    ('/auth/me', 'get'),
]

URL_BACKEND = 'https://g7-auth-service.onrender.com/api/v1'


def reducir(contrato):
    """Devuelve el documento 3.0.3 con las operaciones publicadas."""
    rutas = {}
    for ruta, metodo in RUTAS_PUBLICADAS:
        if ruta not in contrato['paths'] or metodo not in contrato['paths'][ruta]:
            raise ValueError(f'El contrato no tiene {metodo.upper()} {ruta}')
        operacion = contrato['paths'][ruta][metodo]
        reducida = {
            'operationId': operacion['operationId'],
            'summary': operacion.get('summary', operacion['operationId']),
            'responses': {'200': {'description': 'Respuesta del backend de G7, sin modificar'}},
        }
        parametros = [
            {'name': nombre, 'in': 'path', 'required': True, 'schema': {'type': 'string'}}
            for nombre in re.findall(r'\{([^}]+)\}', ruta)
        ]
        if parametros:
            reducida['parameters'] = parametros
        rutas.setdefault(ruta, {})[metodo] = reducida
    return {
        'openapi': '3.0.3',
        'info': {
            'title': 'G7 Seguridad — identidad',
            'version': contrato['info']['version'],
            'description': 'Subconjunto del contrato de G7 publicado por la pasarela de API Management '
                           '(ADR-010). El contrato completo está en el repo de documentación.',
        },
        'servers': [{'url': URL_BACKEND}],
        'paths': rutas,
    }


def main(argumentos):
    if len(argumentos) != 3:
        print(__doc__)
        return 2
    import yaml
    with open(argumentos[1], encoding='utf-8') as entrada:
        contrato = yaml.safe_load(entrada)
    documento = reducir(contrato)
    with open(argumentos[2], 'w', encoding='utf-8', newline='\n') as salida:
        json.dump(documento, salida, ensure_ascii=False, indent=2)
        salida.write('\n')
    operaciones = sum(len(metodos) for metodos in documento['paths'].values())
    print(f"Contrato {documento['info']['version']}: {operaciones} operaciones en {len(documento['paths'])} rutas")
    return 0


if __name__ == '__main__':
    sys.exit(main(sys.argv))
