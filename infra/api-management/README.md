# Pasarela de API Management (FEAT-19)

**Dueño:** Sergio (PO). **Decisión:** ADR-010 del repo de documentación: Azure
API Management en plan Consumption, con Azure for Students.

**Estado al 10-nov-2026: preparado, sin ejecutar.** No había cuenta de Azure al
escribir esta carpeta. La plantilla Bicep no se validó con `az bicep build`. Los
guiones de Python sí se ejecutaron en lo que no necesita la pasarela.

La pasarela es una segunda entrada a la misma API. La URL directa
(`https://g7-auth-service.onrender.com/api/v1`) sigue funcionando y ningún
módulo está obligado a cambiar.

## Archivos

| Archivo | Para qué |
|---|---|
| `main.bicep` | Crea la instancia, importa la API, aplica la política y crea una suscripción de demostración |
| `politica-api.xml` | Clave de suscripción obligatoria y límite de 30 peticiones por minuto |
| `openapi-apim.json` | Las 15 operaciones que se publican, en OpenAPI 3.0. **Generado**: no se edita a mano |
| `generar_openapi_apim.py` | Regenera el archivo anterior desde el contrato del repo de documentación |
| `probar_pasarela.py` | Comprueba equivalencia, acceso y límite, y escribe la tabla de evidencia |

## FEAT-19-T2 — publicar

Necesitas la CLI de Azure (`az`) y una suscripción Azure for Students.

1. Inicia sesión y crea el grupo de recursos:

   ```bash
   az login
   az group create --name g7-seguridad --location eastus
   ```

2. Valida la plantilla antes de desplegar. Si falla, corrige aquí y deja el
   cambio en el PR:

   ```bash
   az bicep build --file infra/api-management/main.bicep --stdout > /dev/null
   az deployment group what-if --resource-group g7-seguridad \
     --template-file infra/api-management/main.bicep \
     --parameters nombre=g7-seguridad-apim correoPublicador=<tu correo>
   ```

3. Despliega. Crear la instancia tarda varios minutos:

   ```bash
   az deployment group create --resource-group g7-seguridad \
     --template-file infra/api-management/main.bicep \
     --parameters nombre=g7-seguridad-apim correoPublicador=<tu correo> \
     --query properties.outputs.urlPasarela.value --output tsv
   ```

   El comando imprime la URL de la pasarela. Si el nombre ya existe en Azure,
   elige otro.

4. Lee la clave de suscripción y guárdala solo en tu sesión:

   ```bash
   export G7_PASARELA_URL=<URL del paso 3>
   export G7_PASARELA_CLAVE=$(az rest --method post --query primaryKey --output tsv --url \
     "https://management.azure.com/subscriptions/<id de suscripción>/resourceGroups/g7-seguridad/providers/Microsoft.ApiManagement/service/g7-seguridad-apim/subscriptions/g7-demostracion-h5/listSecrets?api-version=2022-08-01")
   ```

**Aceptación de T2:** las rutas responden igual por la pasarela que de forma
directa. Lo comprueba la primera tabla de `probar_pasarela.py`.

## FEAT-19-T3 — seguridad, límite y monitoreo

1. Ejecuta la comprobación. Tarda algo más de un minuto, porque espera una
   ventana limpia antes de la ráfaga:

   ```bash
   python infra/api-management/probar_pasarela.py --rafaga 40
   ```

   Debe terminar con código 0: rutas iguales, `401` sin clave y, de 40
   peticiones, como mucho 30 con `200` y el resto con `429`.

2. Abre el recurso en el portal de Azure, sección **Métricas**, métrica
   **Requests**, y divide por código de respuesta. Debe verse el tráfico de la
   prueba, con los `429`. Captura esa pantalla.

3. Copia la salida del guion y la captura a `docs/evidencia/h5/FEAT-19.md` del
   repo de documentación. Anota la fecha, la versión desplegada del backend y el
   nivel de evidencia («propia desplegada»). **No copies la clave.**

4. Al terminar: `unset G7_PASARELA_CLAVE`.

## Si algo no sale

| Problema | Qué hacer |
|---|---|
| La cuenta no califica para Azure for Students | Plan B del ADR-010: AWS API Gateway. Se reutilizan `openapi-apim.json` y `probar_pasarela.py`; cambia la cabecera de la clave a `x-api-key` |
| La importación rechaza `openapi-apim.json` | Revisa el mensaje; el archivo es OpenAPI 3.0.3 y pasa `openapi-spec-validator` |
| El plan Consumption no está en la región | Usa otra región en `az group create` |
| Las métricas no muestran las peticiones | Activa Application Insights en la instancia y repite la prueba |
| La primera petición tarda mucho | Render en plan gratuito se duerme; el guion ya lo despierta antes de medir |

## Cuando cambie el contrato

Si el contrato añade o renombra una ruta de integración, actualiza
`RUTAS_PUBLICADAS` y regenera:

```bash
python infra/api-management/generar_openapi_apim.py \
  ../Modulo-de-Seguridad/specs/contrato-api/openapi.yaml infra/api-management/openapi-apim.json
```
