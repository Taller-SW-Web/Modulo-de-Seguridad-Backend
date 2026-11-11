// Pasarela de API Management de G7 (FEAT-19-T2 y T3, ADR-010).
// Crea una instancia de Azure API Management en plan Consumption, importa el
// subconjunto publicado del contrato, aplica la política de acceso y de límite,
// y deja una suscripción de demostración.
//
// Validada y desplegada el 10-nov-2026 en South Central US (despliegue
// «feat19-apim», 1 min 45 s). La suscripción de estudiante de la UNMSM solo
// admite las regiones listadas en README.md.

@description('Nombre de la instancia. Debe ser único en Azure, por ejemplo g7-seguridad-apim.')
param nombre string

@description('Región. La suscripción de estudiante de la UNMSM solo admite southcentralus, northcentralus, canadacentral, westus y chilecentral.')
param ubicacion string = resourceGroup().location

@description('Correo de quien administra la instancia. Azure le envía los avisos.')
param correoPublicador string

@description('Nombre que muestra la plataforma como publicador.')
param nombrePublicador string = 'G7 Seguridad'

@description('URL base del backend de G7. La URL directa sigue funcionando.')
param urlBackend string = 'https://g7-auth-service.onrender.com/api/v1'

resource apim 'Microsoft.ApiManagement/service@2022-08-01' = {
  name: nombre
  location: ubicacion
  sku: {
    name: 'Consumption'
    capacity: 0
  }
  properties: {
    publisherEmail: correoPublicador
    publisherName: nombrePublicador
  }
}

resource api 'Microsoft.ApiManagement/service/apis@2022-08-01' = {
  parent: apim
  name: 'g7-seguridad'
  properties: {
    displayName: 'G7 Seguridad — identidad'
    path: 'seguridad/v1'
    protocols: [
      'https'
    ]
    serviceUrl: urlBackend
    subscriptionRequired: true
    format: 'openapi+json'
    value: loadTextContent('openapi-apim.json')
  }
}

resource politica 'Microsoft.ApiManagement/service/apis/policies@2022-08-01' = {
  parent: api
  name: 'policy'
  properties: {
    format: 'rawxml'
    value: loadTextContent('politica-api.xml')
  }
}

resource producto 'Microsoft.ApiManagement/service/products@2022-08-01' = {
  parent: apim
  name: 'g7-demostracion'
  properties: {
    displayName: 'G7 — demostración H5'
    description: 'Acceso a la API de identidad de G7 por la pasarela, con límite de 30 peticiones por minuto.'
    subscriptionRequired: true
    approvalRequired: false
    state: 'published'
  }
}

resource productoApi 'Microsoft.ApiManagement/service/products/apis@2022-08-01' = {
  parent: producto
  name: api.name
}

resource suscripcion 'Microsoft.ApiManagement/service/subscriptions@2022-08-01' = {
  parent: apim
  name: 'g7-demostracion-h5'
  properties: {
    displayName: 'Demostración H5'
    scope: producto.id
    state: 'active'
  }
}

@description('URL de la API por la pasarela. La clave de suscripción se lee aparte y no se imprime aquí.')
output urlPasarela string = '${apim.properties.gatewayUrl}/seguridad/v1'
