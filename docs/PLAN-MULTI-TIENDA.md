# Plan: OrderShip para varias tiendas (multi-tienda)

Estado: **en curso en la rama `multi-tienda`** (escrito el 2026-09-28, actualizado el 2026-10-10: usuarios con varias
tiendas). Hecho: paso 1 (`V22__tiendas.sql`, la tienda actual es "Al natural Py"; además se eliminaron
`/auth/register` y el CRUD de `/users`, que veían usuarios de todas las tiendas). Las columnas de `stores` que faltan
(numeración, zona horaria, secreto e ítems ignorados de Shopify) las agrega cada paso.

## Por qué

Hoy OrderShip lo usa una sola tienda. La idea es que sirva para varias: primero la tienda actual, después otra
propia y más adelante venderlo a otras tiendas. Conviene hacerlo ahora, con una sola tienda y pocos datos, en vez de
migrar cuando haya más. También va antes del bot de WhatsApp (etapa 10): su configuración (número, tokens,
plantillas) es por tienda.

Alcance: **solo tiendas**. Un producto para empresas de courier sería una app aparte (envíos de terceros, guías,
rendición a remitentes): no se mezcla acá.

## Decisiones

- **Una sola base con `store_id`** en los datos de negocio. No una base ni un esquema por tienda: alcanza de sobra para
  decenas de tiendas y es mucho más simple de operar en Railway.
- **Un usuario puede pertenecer a varias tiendas**, con un rol en cada una (ej. ADMIN en la suya y OPERATOR en otra;
  un repartidor que trabaja para dos tiendas). La pertenencia vive en `store_members (user_id, store_id, role)`.
  El rol deja de estar en `users`.
- **Se trabaja en una tienda a la vez.** El usuario la elige en la app (si tiene una sola, entra directo) y la cambia
  desde Más. No hay vistas que sumen varias tiendas (quizás más adelante, para el dueño de varias).
- **La tienda va en un header `X-Store-Id` en cada request**, no en el JWT. El token sigue siendo del usuario.
  Así, cambiar de tienda no toca los tokens: no hay que emitir uno nuevo ni rotar el refresh, que es lo más delicado de
  la sesión (un refresh reusado revoca todas las sesiones). El filtro JWT ya lee el usuario de la base en cada
  request; ahora también lee su pertenencia a esa tienda, y si se la quitan deja de entrar al instante.
- **Filtro automático con `@TenantId` de Hibernate** (multi-tenancy por discriminador): agrega `store_id` a todas las
  consultas JPQL y lo completa al guardar. Es la barrera principal para que una tienda nunca vea datos de otra.
  Hoy no hay consultas nativas (`nativeQuery`), así que todas pasan por el filtro. Regla nueva: no usar consultas
  nativas sin filtrar por tienda a mano.
- **El email del usuario sigue siendo único global** (es el login, y el mismo usuario entra a todas sus tiendas).
  El resto de las unicidades pasan a ser por tienda.
- Nada de registro público, planes, cobro ni marca propia por tienda por ahora: las tiendas y sus miembros se dan de
  alta a mano.

## Cambios en la base (una migración Flyway, la siguiente libre: hoy `V22__tiendas.sql`)

1. Tabla `stores`: `id`, `name`, `timezone` (default `America/Asuncion`), `next_order_number`, `shopify_shop_domain`
   (único, nullable), `shopify_webhook_secret` (nullable), `shopify_ignored_line_items` (texto, nullable: hoy es
   `app.shopify.ignored-line-items`, propio de la tienda actual), `active`, `created_at`.
2. Tabla `store_members`: `store_id`, `user_id`, `role` (`ADMIN` / `OPERATOR` / `DELIVERY`), `created_at`;
   PK `(store_id, user_id)`, índice por `user_id`.
3. Crear la tienda actual y asignarle todos los datos existentes:
   - Su `next_order_number` = máximo `order_number` + 1, así los números P- no cambian.
   - Su `shopify_shop_domain`, secreto e ítems ignorados = los que hoy están en las variables de Railway.
   - Cada usuario actual pasa a ser miembro de esa tienda con el rol que tiene en `users.role`; después se borra
     `users.role`.
4. `store_id NOT NULL` + FK + índice en: `customers`, `customer_addresses`, `products`, `orders`, `order_items`,
   `order_discounts`, `delivery_assignments`, `carriers`, `zones`, `shopify_webhook_failures`, `stock_movements`.
   `users` y `refresh_tokens` no lo llevan: son del usuario, no de la tienda.
5. Unicidades globales → por tienda:
   - `customers (phone)` → `(store_id, phone)`
   - `products (shopify_sku)` → `(store_id, shopify_sku)`
   - `orders (shopify_order_id)` → `(store_id, shopify_order_id)`
   - `orders (order_number)` → `(store_id, order_number)`
   - `zones (name)` → `(store_id, name)`
   - `shopify_webhook_failures (shopify_order_id)` → `(store_id, shopify_order_id)`
6. Revisar los índices de V16 y V21 (pedidos y zona de pedidos): los que sirven a los listados pasan a empezar por
   `store_id`.
7. Número de pedido por tienda: quitar el `DEFAULT nextval('orders_order_number_seq')` y numerar con un trigger
   `BEFORE INSERT` que hace `UPDATE stores SET next_order_number = next_order_number + 1 ... RETURNING` (bloquea la
   fila de la tienda: sin números repetidos con pedidos simultáneos). La entidad ya lee `order_number` como
   `@Generated`, así que sigue funcionando. Borrar la secuencia vieja.

## Cambios en el código del backend

1. **Entidades `Store` y `StoreMember`**, y el campo `@TenantId private UUID storeId;` en cada entidad de negocio.
   `User` pierde `role`. `StoreMember` no lleva `@TenantId` (cruza tiendas): se consulta siempre filtrando por
   usuario y tienda a mano.
2. **Resolución de la tienda actual** (`CurrentTenantIdentifierResolver` + contexto del request):
   - Requests con usuario: el filtro JWT lee `X-Store-Id`, busca la pertenencia `(usuario, tienda)` y, si existe y la
     tienda está activa, guarda la tienda en el contexto y arma las authorities con **el rol de esa tienda**
     (`ROLE_ADMIN`, ...). Así los `hasRole` actuales siguen funcionando sin cambios.
   - Header ausente en un endpoint de datos → 400 "Elegí una tienda". Tienda ajena o desactivada → 403.
   - Endpoints sin tienda: login, refresh, logout y `GET /users/me`. Los tokens no cambian: el refresh sigue igual.
   - Webhook de Shopify: la tienda sale de `X-Shopify-Shop-Domain` (buscar en `stores`), y la firma HMAC se valida con
     el secreto **de esa tienda**. Dominio desconocido → 401. `SHOPIFY_WEBHOOK_SECRET` de Railway deja de usarse
     (queda como respaldo solo durante la transición). Los ítems ignorados salen de la tienda.
   - Sin tienda resuelta (ej. un proceso interno) → el resolver devuelve un valor que no coincide con ninguna tienda,
     nunca "todas las tiendas".
3. **Zona horaria por tienda**: `DashboardService`, `ReportService` y el filtro `from`/`to` de `OrderService` usan
   `store.timezone` en lugar de `app.timezone`.
4. **Zonas**: las de Gran Asunción (V12) se copian a cada tienda nueva como punto de partida.
5. **`GET /users/me`** (y el `user` del login) devuelve `stores: [{id, name, role}]` con las tiendas activas del
   usuario. Un usuario sin tiendas puede loguearse pero la app le muestra "No tenés tiendas asignadas".
6. **Se elimina `DataInitializer`** (creaba un admin al arrancar) y las propiedades `app.admin.*`: los usuarios y
   tiendas salen de la migración o del alta a mano.
7. **Alta de tiendas y miembros**: al principio con un script SQL documentado (crea la tienda, sus zonas por defecto
   y agrega un usuario existente o nuevo como ADMIN). Más adelante, endpoints:
   - Rol global `SUPER_ADMIN` (flag en `users`, para soporte): crear tiendas.
   - ADMIN de una tienda: agregar miembros por email (si el usuario ya existe, se le suma la tienda; si no, se crea),
     cambiarles el rol y quitarlos. Es la "gestión de usuarios" que hoy está fuera del alcance de la app.

## Cambios en la app mobile

- Modelo `User` con `stores: [{id, name, role}]`. El rol que importa para la UI (ocultar acciones a DELIVERY,
  Reportes solo ADMIN/OPERATOR) pasa a ser **el de la tienda actual**, no el del usuario.
- `SessionStorage` guarda la tienda actual (y la recuerda para el próximo inicio). El `AuthInterceptor` agrega
  `X-Store-Id` junto con el `Authorization`; el refresh y el logout no lo necesitan.
- Después del login: con una tienda, entra directo; con varias, pantalla "Elegí la tienda"; si la recordada ya no
  está en la lista (le quitaron el acceso), también la pide.
- Más: nombre de la tienda actual y, si tiene varias, "Cambiar de tienda".
- **Al cambiar de tienda se reinicia todo el estado** como al cambiar de usuario (listas paginadas, dashboard,
  zonas, repartidores, contadores de Más) y se vuelve a Inicio. Las respuestas que lleguen de la tienda anterior se
  descartan: la base de `PaginatedNotifier` ya lo hace al cambiar de usuario; hay que extenderlo a la tienda.
- Un 403 por tienda ajena (le quitaron el acceso mientras usaba la app) → refrescar `/users/me` y pedir otra tienda.
- La agenda ya manda "hoy" desde el celular: no cambia.

## Tests imprescindibles

Backend:
- **Aislamiento**: con dos tiendas en la base, un usuario de la tienda A no ve ni puede modificar pedidos, clientes,
  productos, repartidores ni zonas de la tienda B (listar, buscar por id → 404, actualizar). Uno por recurso.
- **Usuario con dos tiendas**: con `X-Store-Id` de A ve solo lo de A y con el de B solo lo de B; su rol es el de cada
  tienda (ej. ADMIN en A puede ver reportes, DELIVERY en B no).
- `X-Store-Id` de una tienda a la que no pertenece → 403; sin header → 400; tienda desactivada → 403.
- Quitarle la tienda a un usuario corta su acceso en el siguiente request, sin esperar a que venza el token.
- Mismo teléfono o SKU en dos tiendas: se puede. Repetido dentro de una tienda: no.
- Numeración P- independiente por tienda y sin repetidos con inserts simultáneos.
- Webhook: se enruta por dominio, rechaza firma de otra tienda y dominio desconocido, usa los ítems ignorados de su
  tienda.
- Dashboard, reportes y agregados (`salesSince`, contadores) solo cuentan la tienda propia, con su zona horaria.
- Verificar que las 4 actualizaciones `@Modifying` (`OrderItemRepository.fillMissingUnitCost`, `ProductRepository`,
  `CustomerAddressRepository`, `RefreshTokenRepository`) respetan el filtro de tienda; si no, agregar `store_id` a mano.

App:
- El interceptor manda `X-Store-Id` y no lo manda en refresh/logout.
- Login con una tienda entra directo; con varias muestra el selector; la tienda recordada se usa al reabrir.
- Cambiar de tienda reinicia las listas y descarta respuestas viejas.

## Despliegue

1. Probar la migración en la base local (tiene datos parecidos a los reales) y revisar que la app funcione igual.
2. **Backup de la base de Railway** antes de desplegar.
3. Cargar el dominio, el secreto y los ítems ignorados de Shopify de la tienda actual en la migración (o justo
   después) para que los webhooks no fallen. Si alguno falla igual, queda en "Pedidos con falla" y se puede revisar.
4. **Transición de la app**: la versión instalada no manda `X-Store-Id`. Durante la transición, si el usuario tiene
   **una sola** tienda y falta el header, el backend usa esa. Desplegar el backend, publicar la app nueva y, cuando
   todos la tengan, volver obligatorio el header.

## Orden sugerido (un PR por paso)

1. Migración (`stores`, `store_members`, `store_id`, unicidades) + entidades + `@TenantId` + `X-Store-Id` en el filtro
   con el rol por tienda + `stores` en `/users/me` + tests de aislamiento.
2. Numeración P- por tienda.
3. Webhook de Shopify por tienda (dominio, secreto e ítems ignorados).
4. Zona horaria por tienda (dashboard, reportes, filtro de fechas).
5. App: `X-Store-Id`, selector de tienda, "Cambiar de tienda" en Más y reinicio del estado.
6. Más adelante: `SUPER_ADMIN`, alta de tiendas y gestión de miembros desde la app.
