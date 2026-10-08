# Plan: OrderShip para varias tiendas (multi-tienda)

Estado: **planificado, sin empezar** (2026-09-28).

## Por qué

Hoy OrderShip lo usa una sola tienda. La idea es que sirva para varias: primero la tienda actual, después otra
propia y más adelante venderlo a otras tiendas. Conviene hacerlo ahora, con una sola tienda y pocos datos, en vez de
migrar cuando haya más.

Alcance: **solo tiendas**. Un producto para empresas de courier sería una app aparte (envíos de terceros, guías,
rendición a remitentes): no se mezcla acá.

## Decisiones

- **Una sola base con `store_id`** en los datos de negocio. No una base ni un esquema por tienda: alcanza de sobra para
  decenas de tiendas y es mucho más simple de operar en Railway.
- **Cada usuario pertenece a una tienda.** El login decide qué tienda se ve; la app mobile casi no cambia.
- **Filtro automático con `@TenantId` de Hibernate** (multi-tenancy por discriminador): agrega `store_id` a todas las
  consultas JPQL y lo completa al guardar. Es la barrera principal para que una tienda nunca vea datos de otra.
  Hoy no hay consultas nativas (`nativeQuery`), así que todas pasan por el filtro. Regla nueva: no usar consultas
  nativas sin filtrar por tienda a mano.
- **El email del usuario sigue siendo único global** (es el login). El resto de las unicidades pasan a ser por tienda.
- Nada de registro público, planes, cobro ni marca propia por tienda por ahora: las tiendas se dan de alta a mano.

## Cambios en la base (una migración Flyway, la siguiente libre; ej. `V17__tiendas.sql`)

1. Tabla `stores`: `id`, `name`, `timezone` (default `America/Asuncion`), `next_order_number`, `shopify_shop_domain`
   (único, nullable), `shopify_webhook_secret` (nullable), `active`, `created_at`.
2. Crear la tienda actual y asignarle todos los datos existentes. Su `next_order_number` = máximo `order_number` + 1,
   así los números P- no cambian. Su `shopify_shop_domain` y secreto = los que hoy están en las variables de Railway.
3. `store_id NOT NULL` + FK + índice en: `users`, `customers`, `customer_addresses`, `products`, `orders`,
   `order_items`, `delivery_assignments`, `carriers`, `zones`, `shopify_webhook_failures`, `stock_movements`.
   (`refresh_tokens` cuelga del usuario: no lo necesita.)
4. Unicidades globales → por tienda:
   - `customers (phone)` → `(store_id, phone)`
   - `products (shopify_sku)` → `(store_id, shopify_sku)`
   - `orders (shopify_order_id)` → `(store_id, shopify_order_id)`
   - `orders (order_number)` → `(store_id, order_number)`
   - `zones (name)` → `(store_id, name)`
   - `shopify_webhook_failures (shopify_order_id)` → `(store_id, shopify_order_id)`
5. Número de pedido por tienda: quitar el `DEFAULT nextval('orders_order_number_seq')` y numerar con un trigger
   `BEFORE INSERT` que hace `UPDATE stores SET next_order_number = next_order_number + 1 ... RETURNING` (bloquea la
   fila de la tienda: sin números repetidos con pedidos simultáneos). La entidad ya lee `order_number` como
   `@Generated`, así que sigue funcionando. Borrar la secuencia vieja.

## Cambios en el código del backend

1. **Entidad `Store`** y campo `@TenantId private UUID storeId;` en cada entidad de negocio.
2. **Resolución de la tienda actual** (`CurrentTenantIdentifierResolver`):
   - Requests con usuario: la tienda sale del usuario. Agregar el claim `storeId` al JWT en `JwtProvider` y que el
     filtro JWT la guarde en el contexto del request. Los tokens viejos sin el claim → 401 → la app renueva con el
     refresh y obtiene uno nuevo (verificar que el refresh no dependa del claim).
   - Login y refresh: se buscan por email o token sin filtro de tienda (el email es global).
   - Webhook de Shopify: la tienda sale de `X-Shopify-Shop-Domain` (buscar en `stores`), y la firma HMAC se valida con
     el secreto **de esa tienda**. Dominio desconocido → 401. `SHOPIFY_WEBHOOK_SECRET` de Railway deja de usarse
     (queda como respaldo solo durante la transición).
   - Sin tienda resuelta (ej. un proceso interno) → error, nunca "todas las tiendas".
3. **Zona horaria por tienda**: `DashboardService` y el filtro `from`/`to` de `OrderService` usan `store.timezone` en
   lugar de `app.timezone`.
4. **Zonas**: las de Gran Asunción (V12) se copian a cada tienda nueva como punto de partida.
5. **Alta de tienda**: endpoint solo para un rol nuevo `SUPER_ADMIN` (o script SQL documentado al principio) que crea la
   tienda, su usuario ADMIN y sus zonas por defecto.
6. `GET /users/me` devuelve también `store {id, name}`.

## Cambios en la app mobile (mínimos)

- Modelo `User` con `store` y el nombre de la tienda visible en Más.
- Nada más: todos los filtros ocurren en el backend. La agenda ya manda "hoy" desde el celular.

## Tests imprescindibles

- **Aislamiento**: con dos tiendas en la base, un usuario de la tienda A no ve ni puede modificar pedidos, clientes,
  productos, repartidores ni zonas de la tienda B (listar, buscar por id → 404, actualizar). Uno por recurso.
- Mismo teléfono o SKU en dos tiendas: se puede. Repetido dentro de una tienda: no.
- Numeración P- independiente por tienda y sin repetidos con inserts simultáneos.
- Webhook: se enruta por dominio, rechaza firma de otra tienda y dominio desconocido.
- Dashboard y agregados (`salesSince`, contadores) solo cuentan la tienda propia.
- Verificar que las actualizaciones masivas JPQL (ej. `OrderItemRepository.fillMissingUnitCost`) respetan el filtro
  de tienda; si no, agregar `store_id` a mano en la consulta.

## Despliegue

1. Probar la migración en la base local (tiene datos parecidos a los reales) y revisar que la app funcione igual.
2. **Backup de la base de Railway** antes de desplegar.
3. Cargar el dominio y el secreto de Shopify de la tienda actual en la migración (o justo después) para que los
   webhooks no fallen. Si alguno falla igual, queda en "Pedidos con falla" y se puede revisar.
4. Desplegar backend; la app vieja sigue funcionando (el `store` en `/users/me` es opcional para ella).

## Orden sugerido (un PR por paso)

1. Migración + entidad `Store` + `@TenantId` + claim en el JWT + tests de aislamiento.
2. Numeración P- por tienda.
3. Webhook de Shopify por tienda.
4. Zona horaria por tienda, alta de tiendas (`SUPER_ADMIN`) y `store` en `/users/me` + la app.
