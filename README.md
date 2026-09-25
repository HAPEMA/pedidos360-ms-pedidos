# Pedidos360 · ms-pedidos

Microservicio de **pedidos** del sistema Pedidos360 (tienda de fauna), evaluación EP1 de DSY1107 – Desarrollo Cloud Native I (DuocUC).

El usuario busca una especie en el catálogo, ve su ficha y hace un pedido. Este servicio registra y consulta esos pedidos. Para crear uno le pide a **ms-catalogo** el precio y el stock de la especie. El precio, el stock y el usuario **nunca** se toman del body de la petición.

## Arquitectura

```
Navegador (Angular, pedidos360-front)
   │  OAuth2 Authorization Code + PKCE  ──►  Amazon Cognito (User Pool)
   │  Authorization: Bearer <access token>
   ▼
AWS API Gateway (REST API)
   · Cognito Authorizer + scopes por método
   · integración HTTP proxy
   ▼
EC2 (Amazon Linux 2023, Corretto 17, systemd)
   ├── ms-catalogo  :8081
   └── ms-pedidos   :8082  ── token relay ──►  ms-catalogo /api/especies/{id}
                     │
                     ▼
               RDS PostgreSQL
```

Capas del código (`cl.duoc.pedidos`):

| Paquete      | Contenido |
|--------------|-----------|
| `controller` | `PedidoController`: endpoints REST |
| `service`    | `PedidoService`: reglas de negocio (dueño o ADMIN, stock, total); `UsuarioActual` |
| `repository` | `PedidoRepository` (Spring Data JPA) |
| `model`      | Entidad `Pedido` (tabla `pedido`) y enum `EstadoPedido` |
| `dto`        | `PedidoRequest`, `PedidoResponse`, `EspecieDto` (records) |
| `client`     | `CatalogoClient` (RestClient hacia ms-catalogo) |
| `config`     | `SecurityConfig`, `CognitoAuthoritiesConverter`, `CatalogoConfig` |
| `web`        | `ManejadorErrores` (`@RestControllerAdvice`) y excepciones de negocio |

### Token relay

Al crear un pedido, ms-pedidos llama a `GET {CATALOGO_URL}/api/especies/{id}` con **el mismo access token del usuario** que recibió (`Authorization: Bearer ...`). El token se toma del contexto de seguridad, es decir, solo se reenvía un token que este servicio ya validó.

Ventajas:
- ms-catalogo valida al usuario real con sus propias reglas. No hay una credencial de servicio compartida ni una llamada interna "de confianza" sin autenticar.
- La traza de auditoría conserva la identidad del usuario de punta a punta.

Traducción de errores del catálogo:

| ms-catalogo responde | ms-pedidos responde |
|----------------------|---------------------|
| 200 | continúa con la validación de stock |
| 404 | **404** `{"status":404,"error":"La especie N no existe en el catálogo"}` |
| otro 4xx/5xx, timeout (conexión 2 s, lectura 5 s) o conexión rechazada | **502** `{"status":502,"error":"ms-catalogo no está disponible"}` |

## Endpoints

Todas las respuestas son JSON, también los errores: `{"status": n, "error": "mensaje"}`.

| Método y ruta | Autorización | Respuestas |
|---------------|--------------|------------|
| `GET /api/pedidos` | scope `pedidos360-api/read` | **200**. ADMIN ve todos; el resto solo los propios. Orden: más reciente primero |
| `GET /api/pedidos/{id}` | scope `read` + dueño o ADMIN | **200** · **403** si es de otro usuario · **404** |
| `POST /api/pedidos` | scope `pedidos360-api/write` | **201** · **400** validación · **404** especie inexistente · **409** stock insuficiente · **502** catálogo caído |
| `DELETE /api/pedidos/{id}` | scope `write` **y** grupo `ADMIN` | **204** · **404** |
| `GET /actuator/health` | público | **200** |

Sin token o con un token inválido → **401**. Con token válido pero sin permisos → **403**.

### Ejemplo: crear pedido

```http
POST /api/pedidos
Authorization: Bearer <access token>
Content-Type: application/json

{"especieId": 1, "cantidad": 2}
```

```json
201 Created
Location: /api/pedidos/7
{
  "id": 7,
  "especieId": 1,
  "especieNombre": "Guepardo",
  "cantidad": 2,
  "precioUnitario": 2500000,
  "total": 5000000,
  "estado": "PENDIENTE",
  "creadoPor": "ana",
  "creadoEn": "2026-09-25T13:40:12.345Z"
}
```

Validación (`cantidad` entre 1 y 5, `especieId` obligatorio):

```json
400 Bad Request
{"status":400,"error":"Datos inválidos","detalles":{"especieId":"es obligatorio","cantidad":"no puede superar 5"}}
```

Stock insuficiente:

```json
409 Conflict
{"status":409,"error":"Solo quedan 1 ejemplares de Guepardo"}
```

## Seguridad (Resource Server)

El servicio **valida el JWT por sí mismo** aunque el API Gateway ya lo haya validado (*Defense in Depth*). Si alguien llega directo al EC2 saltándose el Gateway, igual necesita un token válido de nuestro User Pool.

- **JwtDecoder**: `NimbusJwtDecoder.withJwkSetUri(issuer + "/.well-known/jwks.json")`. Las llaves se cargan con el primer token, así que el servicio arranca aunque Cognito no sea alcanzable.
- **Validadores**:
  - firma, `exp`/`nbf` e `iss` igual al User Pool (`JwtValidators.createDefaultWithIssuer`);
  - `token_use == "access"`: rechaza los *id tokens*;
  - `client_id == COGNITO_CLIENT_ID`: el access token de Cognito no trae `aud`, así que `client_id` cumple el papel de audience.
- **Authorities**: claim `scope` → `SCOPE_xxx`; claim `cognito:groups` → `ROLE_xxx`. El principal es el claim `username` (en Cognito `sub` es un UUID).
- **Reglas**, en este orden:
  1. dispatcher `ERROR` permitido (si no, un 404 del controlador saldría como 403);
  2. `OPTIONS /**` permitido (preflight CORS);
  3. `/actuator/health` público;
  4. `GET /api/pedidos/**` → `SCOPE_pedidos360-api/read`;
  5. `POST /api/pedidos` → `SCOPE_pedidos360-api/write`;
  6. `DELETE /api/pedidos/**` → `SCOPE_pedidos360-api/write` **y** `ROLE_ADMIN` (`AuthorizationManagers.allOf`);
  7. cualquier otra ruta → `denyAll` (denegar por defecto).
- **Dueño o ADMIN** en `GET /api/pedidos/{id}`: se revisa en el service (control de acceso a nivel de objeto, evita IDOR). El scope `read` solo no alcanza para ver pedidos ajenos.
- **401/403 en JSON** con `authenticationEntryPoint` y `accessDeniedHandler`.
- **Stateless y sin CSRF**: no hay cookie de sesión que un sitio ajeno pueda reutilizar; la credencial viaja en el header `Authorization`.
- **CORS**: solo el origen exacto `FRONT_ORIGIN`; métodos `GET, POST, DELETE, OPTIONS`; headers `Authorization, Content-Type`.
- **Datos de confianza**: precio, stock y nombre vienen de ms-catalogo; `creadoPor` viene del JWT. Si el body trae campos extra, se ignoran.
- **Sin secretos en el repo**: todo sale de variables de entorno. `.gitignore` excluye `.env` y `*.env`.

### Matriz de seguridad esperada

| Caso | Resultado |
|------|-----------|
| Sin token (cualquier `/api/**`) | 401 |
| Token malformado, expirado, de otro pool, *id token* o de otro `client_id` | 401 |
| `GET /api/pedidos` sin scope `read` | 403 |
| `POST /api/pedidos` sin scope `write` | 403 |
| `GET /api/pedidos` como CLIENTE | 200, solo sus pedidos |
| `GET /api/pedidos` como ADMIN | 200, todos los pedidos |
| `GET /api/pedidos/{id}` de otro usuario (CLIENTE) | 403 |
| `GET /api/pedidos/{id}` de otro usuario (ADMIN) | 200 |
| `GET /api/pedidos/{id}` inexistente | 404 |
| `DELETE /api/pedidos/{id}` como CLIENTE (con `write`) | 403 |
| `DELETE /api/pedidos/{id}` como ADMIN sin `write` | 403 |
| `DELETE /api/pedidos/{id}` como ADMIN con `write` | 204 |
| `GET /actuator/health` sin token | 200 |
| `GET /actuator/env` o cualquier ruta no declarada | 401 sin token / 403 con token |
| Preflight CORS desde `FRONT_ORIGIN` | 200 con `Access-Control-Allow-Origin` |
| Preflight CORS desde otro origen | 403 |

Los tests automatizados cubren todos estos casos.

## Variables de entorno

| Variable | Obligatoria | Ejemplo / valor por defecto | Uso |
|----------|-------------|-----------------------------|-----|
| `PORT` | no | `8082` | Puerto HTTP |
| `DB_URL` | sí | `jdbc:postgresql://<endpoint-rds>:5432/pedidos360` | JDBC de RDS |
| `DB_USER` | sí | `pedidos_app` | Usuario de BD |
| `DB_PASS` | sí | — | Clave de BD |
| `CATALOGO_URL` | no | `http://localhost:8081` | Base de ms-catalogo |
| `COGNITO_POOL_ID` | sí | `us-east-1_XXXXXXXXX` | Arma el issuer `https://cognito-idp.us-east-1.amazonaws.com/<pool>` |
| `COGNITO_CLIENT_ID` | sí | — | App client esperada en el claim `client_id` |
| `FRONT_ORIGIN` | no | `http://localhost:4200` | Origen permitido por CORS |

La plantilla está en [`deploy/ms-pedidos.env.example`](deploy/ms-pedidos.env.example).

## Correr en local

Requisitos: JDK 17 o superior. No hace falta Maven instalado porque se usa `./mvnw`.

El perfil `local` usa H2 en memoria y valores por defecto para Cognito, así que arranca sin variables de entorno:

```bash
# Terminal 1: ms-catalogo (repo pedidos360-ms-catalogo) en el puerto 8081
./mvnw spring-boot:run -Dspring-boot.run.profiles=local

# Terminal 2: ms-pedidos en el puerto 8082
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
curl http://localhost:8082/actuator/health   # {"status":"UP",...}
```

Para llamar a `/api/pedidos` con un token real de Cognito, define el pool y el client correctos:

```bash
COGNITO_POOL_ID=us-east-1_AbCdEf123 COGNITO_CLIENT_ID=<app-client-id> \
  ./mvnw spring-boot:run -Dspring-boot.run.profiles=local

curl -H "Authorization: Bearer $ACCESS_TOKEN" -H "Content-Type: application/json" \
     -d '{"especieId":1,"cantidad":1}' http://localhost:8082/api/pedidos
```

El mismo token se reenvía a ms-catalogo, así que este también tiene que estar configurado con el mismo pool.

## Construir y probar

```bash
./mvnw clean package          # compila, corre los tests y genera target/ms-pedidos.jar
```

Los tests no llaman a internet ni a ms-catalogo real:
- `MsPedidosApplicationTests`: el contexto arranca con el perfil `local`.
- `CatalogoClientTest` (`MockRestServiceServer`): reenvía `Authorization`, 404 → 404, 5xx o timeout → 502.
- `SecurityConfigTest`: los validadores rechazan *id token*, otro `client_id`, otro issuer y token expirado; mapeo de scopes, grupos y `username`.
- `PedidoControllerTest` (MockMvc + `jwt()`, con `CatalogoClient` mockeado y H2): la matriz de seguridad completa y los códigos 201/400/404/409/502.

## Despliegue en EC2

En la instancia (Amazon Linux 2023 con Corretto 17):

```bash
# 1. Directorios
sudo mkdir -p /opt/pedidos360 /etc/pedidos360

# 2. Copiar el jar (desde tu equipo)
scp target/ms-pedidos.jar ec2-user@<ip-ec2>:/tmp/
sudo mv /tmp/ms-pedidos.jar /opt/pedidos360/ms-pedidos.jar
sudo chown ec2-user:ec2-user /opt/pedidos360/ms-pedidos.jar

# 3. Variables de entorno (solo root las puede leer; systemd las carga antes de cambiar a ec2-user)
sudo cp deploy/ms-pedidos.env.example /etc/pedidos360/ms-pedidos.env
sudo vi /etc/pedidos360/ms-pedidos.env
sudo chmod 600 /etc/pedidos360/ms-pedidos.env

# 4. Servicio systemd
sudo cp deploy/ms-pedidos.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now ms-pedidos

# 5. Verificar
systemctl status ms-pedidos
journalctl -u ms-pedidos -f
curl http://localhost:8082/actuator/health
```

Para actualizar, reemplaza el jar y ejecuta `sudo systemctl restart ms-pedidos`.

Con `ddl-auto=update`, Hibernate crea la tabla `pedido` en el primer arranque.

## Limitaciones y evolución

- **El stock se valida pero no se descuenta.** ms-pedidos comprueba `stock >= cantidad` al crear el pedido, pero no modifica el stock en ms-catalogo. Dos pedidos simultáneos podrían pasar la validación con el mismo stock. Hay dos formas de evolucionar esto:
  - **Reserva síncrona**: un endpoint en ms-catalogo, por ejemplo `POST /api/especies/{id}/reservas`, que descuente de forma atómica (`UPDATE ... SET stock = stock - :n WHERE stock >= :n`) y responda 409 si no alcanza. Si falla la creación del pedido, se compensa liberando la reserva (patrón Saga).
  - **Evento asíncrono**: publicar `PedidoCreado` en SNS/SQS o EventBridge para que ms-catalogo descuente el stock y emita `StockReservado` o `StockInsuficiente`, que cambian el pedido a `CONFIRMADO` o `CANCELADO`. Por eso el pedido nace en estado `PENDIENTE`.
- No hay endpoint para cambiar el estado del pedido (confirmar o cancelar). El enum ya lo contempla.
- `ddl-auto=update` es cómodo para la evaluación. En producción convendría usar migraciones versionadas (Flyway o Liquibase).
- El token relay depende de que el access token siga vigente durante la llamada. Para procesos en segundo plano se usaría client credentials con un scope de servicio.
- No hay reintentos ni circuit breaker hacia ms-catalogo (solo timeouts). Se podría agregar Resilience4j.
