# Despliegue en Railway

Guía para levantar OrderShip en Railway y conectarlo con Shopify.

El repo ya trae todo lo necesario: `Dockerfile` (compilación y arranque), `railway.toml` (health check y reinicios)
y la configuración de producción en `application.properties`.

---

## 1. Antes de empezar

- El código tiene que estar en GitHub (`git push`). Railway despliega desde ahí.
- Cuenta en [railway.com](https://railway.com). La prueba gratuita da 5 USD de crédito por 30 días, sin tarjeta.
  Después conviene el plan **Hobby** (5 USD/mes, incluye 5 USD de consumo).

## 2. Crear el proyecto y la base de datos

1. En Railway: **New Project → Deploy from GitHub repo** y elegí el repo de OrderShip.
   Railway detecta el `railway.toml` y construye con el `Dockerfile`.
   El primer despliegue **va a fallar**: todavía faltan las variables de entorno. Es normal.
2. En el mismo proyecto: **+ New → Database → PostgreSQL**.
   Dejá el nombre del servicio como **`Postgres`** (las variables del paso siguiente lo usan).

## 3. Variables de entorno

En el servicio de la app → pestaña **Variables** → **Raw Editor**, pegá esto y completá los valores marcados:

```
SPRING_DATASOURCE_URL=jdbc:postgresql://${{Postgres.PGHOST}}:${{Postgres.PGPORT}}/${{Postgres.PGDATABASE}}
SPRING_DATASOURCE_USERNAME=${{Postgres.PGUSER}}
SPRING_DATASOURCE_PASSWORD=${{Postgres.PGPASSWORD}}
JWT_SECRET=<clave aleatoria de 64 caracteres, ver abajo>
ADMIN_EMAIL=<tu email>
ADMIN_PASSWORD=<contraseña fuerte para el usuario administrador>
CORS_ALLOWED_ORIGINS=https://example.com
SHOPIFY_WEBHOOK_SECRET=<se completa en el paso 6>
```

- Las líneas con `${{Postgres...}}` se copian tal cual: Railway las reemplaza por los datos de la base.
  La URL se arma a mano porque Spring necesita el formato `jdbc:postgresql://...`, distinto al `DATABASE_URL` de Railway.
- **`JWT_SECRET`**: generá uno en PowerShell con
  `-join ((48..57) + (65..90) + (97..122) | Get-Random -Count 64 | ForEach-Object { [char]$_ })`.
  Tiene que ser distinto al de desarrollo.
- **`ADMIN_PASSWORD`**: solo se usa para crear el administrador en el primer arranque. Guardala en un lugar seguro.
- **`CORS_ALLOWED_ORIGINS`**: todavía no hay frontend; cualquier URL válida sirve. Cuando exista, va la URL del frontend.
- **No** definas `SPRING_PROFILES_ACTIVE`: sin perfil, la app usa la configuración de producción.
- Opcional: `SHOPIFY_IGNORED_LINE_ITEMS` con los nombres de los ítems que no son productos (extras del
  formulario), separados por coma. Por defecto: `Envio Prioritario y Garantia Extendida`. Solo hace falta
  definirla si Releasit agrega otros extras.
- `SHOPIFY_WEBHOOK_SECRET` se puede dejar vacío por ahora: la app arranca igual, y mientras falte rechaza los
  webhooks con 503 (Shopify los reintenta, no se pierden).

Al guardar las variables, Railway vuelve a desplegar solo.

## 4. Dominio público

Servicio de la app → **Settings → Networking → Generate Domain**.
Railway da una URL del tipo `https://ordership-production.up.railway.app` con HTTPS incluido.

## 5. Verificar que la app está funcionando

1. Abrí `https://<tu-dominio>/actuator/health` → tiene que responder `{"status":"UP"}`.
   Si responde `DOWN` o 503, la app no llega a la base: revisá las variables `SPRING_DATASOURCE_*`.
2. En los logs del despliegue (pestaña **Deployments → View logs**) tienen que aparecer:
   - las migraciones: `Migrating schema "app" to version "1 - estructura inicial"` ... hasta la versión 4,
   - `Usuario administrador creado: <tu email>`,
   - `Started OrdershipApplication`.
3. `https://<tu-dominio>/swagger-ui.html` → probá `POST /api/auth/login` con el email y contraseña del admin.

## 6. Conectar Shopify

1. En Shopify Admin: **Configuración → Notificaciones → Webhooks** (al final de la página).
2. Copiá la clave que aparece en esa sección ("tus webhooks se firmarán con…") y cargala en Railway como
   `SHOPIFY_WEBHOOK_SECRET`. Esperá a que termine el nuevo despliegue.
3. **Crear webhook**:
   - Evento: **Creación de pedido** (`orders/create`)
   - Formato: **JSON**
   - URL: `https://<tu-dominio>/api/webhooks/shopify/orders`
   - Versión de API: la más reciente
4. **Enviar notificación de prueba**. Shopify manda un pedido ficticio que normalmente no trae teléfono, así que lo
   esperable es que quede en `GET /api/shopify/webhook-failures` con el motivo "no trae un teléfono de contacto".
   Eso confirma que la conexión y la firma funcionan. Si en los logs aparece `firma HMAC inválida`, la clave del
   paso 2 no coincide.

### Antes de las compras de prueba (en Shopify)

- Hacer **obligatorio el teléfono** en el checkout (Configuración → Checkout).
- Revisar que **todas las variantes tengan SKU**.
- Activar el **modo de prueba de pagos** para comprar sin cobrar.
- En OrderShip, los productos deberían llamarse como en Shopify: `Producto - Variante` (ej. `Remera - Azul / M`),
  o tener cargado su `shopifySku`. Si no, se crean automáticamente marcados `needsReview`.

### Qué revisar después de cada compra de prueba

| Dónde | Qué mirar |
|---|---|
| `GET /api/orders?source=SHOPIFY` | Que el pedido exista con total y cantidades correctos |
| `GET /api/customers` | Nombre, email y teléfono. Ojo con clientes duplicados por teléfono escrito distinto |
| `GET /api/products` | Productos vinculados o creados (`needsReview`), stock descontado |
| `GET /api/shopify/webhook-failures` | Tiene que estar vacío (salvo la notificación de prueba) |
| Logs | Buscar `Shopify` para ver el recorrido de cada pedido |

## 7. Monitoreo: aviso si la app se cae

El health check de Railway solo se usa durante los despliegues. Para enterarte si la app se cae:

1. Cuenta gratuita en [UptimeRobot](https://uptimerobot.com).
2. **New Monitor → HTTP(s)**, URL `https://<tu-dominio>/actuator/health`, intervalo de 5 minutos.
3. Alerta por email.

## 8. Backups de la base

Servicio **Postgres → pestaña Backups**: activá el backup **diario** (se conserva 6 días) y, si querés,
el **semanal** (27 días). Verificá que tu plan lo incluya; si no aparece la opción, avisame y armamos un backup propio.

## 9. Logs

- Se conservan **7 días** en el plan Hobby.
- Son JSON, así que se pueden filtrar: `@level:error` muestra solo errores, `@level:warn` las advertencias
  (por ejemplo stock negativo).
- Lo importante no depende de los logs: los pedidos están en la base y los que fallan quedan en
  `shopify_webhook_failures` con el payload completo.

## 10. Costos

La app ocupa unos 335 MB de RAM (medido en el contenedor, limitada en el `Dockerfile` con `JAVA_OPTS`).
Con el plan Hobby el consumo estimado es de 5 a 6 USD/mes, casi todo cubierto por los 5 USD incluidos. El volumen de pedidos casi no
influye: se paga principalmente por tener la app encendida. El consumo real se ve en **Usage** del proyecto.

## Problemas comunes

| Síntoma | Causa probable |
|---|---|
| El despliegue falla y el log dice `Could not resolve placeholder 'X'` | Falta la variable de entorno `X` |
| `No existe el usuario administrador y ADMIN_PASSWORD no está configurado` | Falta `ADMIN_PASSWORD` en el primer arranque |
| `/actuator/health` responde 503 | La app no llega a la base: revisar `SPRING_DATASOURCE_*` |
| Webhooks rechazados con 401 | `SHOPIFY_WEBHOOK_SECRET` no coincide con la clave de Shopify |
| Webhooks rechazados con 503 | `SHOPIFY_WEBHOOK_SECRET` vacío |
| La app se reinicia sola con `OutOfMemoryError` | Subir `-Xmx` con la variable `JAVA_OPTS` (ej. `-Xmx384m ...`) |
