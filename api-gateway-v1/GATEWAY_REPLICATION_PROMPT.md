# API Gateway — guía de réplica y prompt de contexto

Este documento **simula el proyecto de referencia** `api-gateway-v1`: stack, configuración, variables de entorno, paquetes Java y comportamiento esperado. Sirve para **replicar el mismo diseño en otro repositorio** o para pegarlo como **contexto único** a otro asistente o equipo.

Documentación de arquitectura (hexagonal parcial, capas `application` / `infrastructure`): **`docs/ARQUITECTURA_HEXAGONAL_PARCIAL.md`**.

---

## 1. Identidad del proyecto de referencia

| Campo | Valor en el repo de referencia |
|-------|--------------------------------|
| Artefacto Gradle | `api-gateway-v1` (`settings.gradle`) |
| `group` | `com.ezamora` |
| Paquete Java raíz | `com.ezamora.api_gateway_v1` |
| Clase de arranque | `ApiGatewayV1Application` (`@SpringBootApplication`, escanea todo el `groupId` base) |

En un clon con otro `groupId`, renombra el paquete y la clase de arranque; mantén la **estructura** bajo `…gateway.application` y `…gateway.infrastructure` si quieres conservar el mismo diseño.

---

## 2. Stack y dependencias (copiar literalmente al nuevo proyecto)

| Componente | Valor en referencia (`build.gradle`) |
|------------|--------------------------------------|
| Java | **17** (toolchain) |
| Spring Boot | **4.0.5** (`org.springframework.boot` plugin) |
| Spring Dependency Management | **1.1.7** |
| Spring Cloud BOM | **`2025.1.1`** (`ext.springCloudVersion`) |
| Build | **Gradle** (Groovy DSL), JUnit 5 |

**Dependencias `implementation` esenciales:**

- `org.springframework.cloud:spring-cloud-starter-gateway-server-webflux` — Gateway **reactivo** (WebFlux + Reactor Netty; **no** Tomcat servlet embebido como motor del gateway).

**Opcional en referencia:** Lombok (`compileOnly` + `annotationProcessor`), DevTools (`developmentOnly`).

**Tests:** `spring-boot-starter-test`, `reactor-test`, `junit-platform-launcher` (`testRuntimeOnly`).

El gateway **no** usa `@RestController` para enrutar: las rutas vienen de **`spring.cloud.gateway.server.webflux.routes`** en YAML.

---

## 3. Configuración YAML y rutas

### 3.1 Ficheros

| Fichero | Rol |
|---------|-----|
| `src/main/resources/application.yml` | Puerto `SERVER_PORT` (defecto **8020**), perfil por defecto **dev**, rutas del gateway, `gateway.*`, `api-gateway.body-encryption`, logging |
| `application-dev.yml` | Perfil **dev** (`spring.config.activate.on-profile: dev`); opcional para overrides locales |
| `application-test.yml` | Perfil **test**; backends de ejemplo en otros puertos (`3100`, `3101`) |
| `application-prod.yml` | Perfil **prod**; URIs de backend **obligatorias** vía env; logging más restrictivo; `routePaths` ampliados por defecto para `users` |

### 3.2 Ubicación de las rutas en Spring Boot 4

En el proyecto de referencia las rutas están bajo:

```yaml
spring:
  cloud:
    gateway:
      server:
        webflux:
          routes:
            - id: ...
              uri: ...
              metadata: ...
              predicates:
                - Path=...
                - Method=...
```

### 3.3 Orden de evaluación

Spring Cloud Gateway evalúa las rutas **en el orden declarado**; la **primera** que cumple los predicados gana. En el referente, la ruta **orders** va **antes** que **users** para que paths más específicos no queden absorbidos por patrones amplios (ej. `/api/order` vs `/api/**`).

### 3.4 Metadata → políticas de payload (aplicación)

Claves de `metadata` (alineadas con `GatewayRouteMetadata` en código):

| Clave YAML | Uso |
|------------|-----|
| `routeStrategy` | Una sola clave de política para la ruta (ej. `AES`). |
| `strategyByMethod` | Mapa método HTTP → clave (ej. `POST: RSA`, `GET: AES`). Tiene prioridad sobre `routeStrategy` si está presente. |

Valores especiales interpretados por `EncryptionStrategyResolver` (sin política): **`NONE`**, **`SKIP`** (ignorar mayúsculas).

Las claves efectivas (`RSA`, `AES`, …) deben coincidir con **`policyKey()`** de cada `@Component` que implementa **`PayloadEncryptionPolicyPort`**.

### 3.5 Propiedades `gateway.*` (backends y predicados)

Definidas en `application.yml` y sobreescribibles por perfil o env:

| Propiedad | Ejemplo env | Significado |
|-----------|--------------|-------------|
| `gateway.backends.orders` | `GATEWAY_BACKEND_ORDERS_URI` | URI upstream pedidos |
| `gateway.backends.users` | `GATEWAY_BACKEND_USERS_URI` | URI upstream usuarios |
| `gateway.routePaths.orders` | `GATEWAY_ROUTE_PATHS_ORDERS` | Valor del predicado `Path` (coma = varios patrones) |
| `gateway.routePaths.users` | `GATEWAY_ROUTE_PATHS_USERS` | Idem para users |
| `gateway.routeMethods.orders` | `GATEWAY_ROUTE_METHODS_ORDERS` | Lista para predicado `Method` (coma) |
| `gateway.routeMethods.users` | `GATEWAY_ROUTE_METHODS_USERS` | Idem |

**Prod:** en el referente, `gateway.backends.*` en `application-prod.yml` **no** tienen default HTTP: deben definirse **`GATEWAY_BACKEND_ORDERS_URI`** y **`GATEWAY_BACKEND_USERS_URI`**.

### 3.6 Secreto AES (auditoría demo)

```yaml
api-gateway:
  body-encryption:
    aes-secret: ${API_GATEWAY_AES_SECRET:01234567890123456789012345678901}
```

Debe ser **16, 24 o 32 bytes en UTF-8** (validación en `AesGcmPayloadEncryptionPolicy`). En producción, inyectar **`API_GATEWAY_AES_SECRET`** por env/secret manager.

### 3.7 Puerto del servidor

`server.port: ${SERVER_PORT:8020}` — configurable con **`SERVER_PORT`**.

---

## 4. Código Java — layout hexagonal parcial (`gateway`)

### 4.1 Árbol de paquetes (referencia)

```
com.ezamora.api_gateway_v1/
├── ApiGatewayV1Application.java
└── gateway/
    ├── application/
    │   ├── port/
    │   │   ├── PayloadEncryptionPolicyPort.java
    │   │   └── EncryptionPolicyLookupPort.java
    │   ├── support/
    │   │   └── GatewayRouteMetadata.java
    │   └── service/
    │       └── EncryptionStrategyResolver.java
    └── infrastructure/
        ├── config/
        │   └── GatewayConfiguration.java
        ├── web/
        │   ├── RequestIdFilter.java
        │   ├── MatchedGatewayRouteLoggingFilter.java
        │   └── PayloadEncryptionFilter.java
        └── policy/
            ├── SpringEncryptionPolicyLookup.java
            ├── RsaOaepPayloadEncryptionPolicy.java
            └── AesGcmPayloadEncryptionPolicy.java
```

- **`application`**: puertos y lógica **sin** dependencia de Spring en el servicio puro.
- **`infrastructure`**: `GlobalFilter`, políticas Spring, beans (`GatewayConfiguration` expone `EncryptionStrategyResolver` como `@Bean`).

**No** hay paquete `domain` en el referente.

### 4.2 `GlobalFilter` y orden

| Clase | `getOrder()` | Función |
|-------|----------------|---------|
| `RequestIdFilter` | `HIGHEST_PRECEDENCE` | `X-Request-ID`, logs de petición/respuesta y duración |
| `MatchedGatewayRouteLoggingFilter` | `HIGHEST_PRECEDENCE + 5` | Log de ruta emparejada, upstream, política efectiva, método, path |
| `PayloadEncryptionFilter` | `HIGHEST_PRECEDENCE + 20` | Lee body en POST/PUT/PATCH; audita vía política; reenvía el mismo body |

**Extensión (filtros):** nuevos `@Component` que implementen `GlobalFilter` + `Ordered` en `…gateway.infrastructure.web`; elegir `getOrder()` para encadenarlos antes o después de los existentes.

### 4.3 Añadir **otro tipo de cifrado** (u otra política de auditoría de payload)

El patrón del referente es **Strategy vía puerto**: el YAML solo conoce **cadenas** (`RSA`, `AES`, `CHACHA20`, …); Spring descubre todas las implementaciones de **`PayloadEncryptionPolicyPort`** y `SpringEncryptionPolicyLookup` las indexa por **`policyKey()`**. **No** hace falta tocar `SpringEncryptionPolicyLookup` ni `PayloadEncryptionFilter` para registrar una política nueva.

| Paso | Qué hacer | Dónde |
|------|-----------|--------|
| 1 | Crear una clase que implemente **`PayloadEncryptionPolicyPort`**. | `…gateway.infrastructure.policy` (mismo paquete que RSA/AES). |
| 2 | Anotarla con **`@Component`** para que Spring la inyecte en la lista de políticas. | La clase nueva. |
| 3 | Implementar **`policyKey()`** devolviendo una cadena **única** y estable (ej. `CHACHA20`). Coincidirá con lo que pongas en YAML. | Misma clase. |
| 4 | Implementar **`auditPlainPayload(byte[] plaintext)`** con tu lógica (log, métricas, cifrado de demostración, envío a SIEM, etc.). Hoy el referente **no altera** el body hacia el upstream salvo que tú lo cambies en el filtro. | Misma clase. |
| 5 | Si la política necesita secretos o parámetros, inyectarlos con **`@Value("${…}")`** o **`@ConfigurationProperties`** y documentar las claves en `application.yml` (como `api-gateway.body-encryption.aes-secret` para AES). | Clase + YAML + (opcional) perfiles. |
| 6 | En **`application.yml`** (o perfil), en la ruta que corresponda, referenciar la clave en **`metadata.routeStrategy`** o en **`metadata.strategyByMethod`** (por método HTTP). | `spring.cloud.gateway.server.webflux.routes[].metadata` |

**No** es necesario modificar **`EncryptionStrategyResolver`** ni **`GatewayRouteMetadata`** mientras sigas usando las mismas claves YAML (`routeStrategy` / `strategyByMethod`). Solo tendrías que tocarlos si añadieras **nuevas formas de elegir** política (otro mapa en metadata, otro predicado, etc.).

**Colisión de claves:** dos `@Component` con el mismo `policyKey()` hace que el `Collectors.toMap` del lookup falle al arrancar; mantén claves únicas.

**Probar:** `./gradlew test` y una petición contra una ruta cuya `metadata` apunte a tu `policyKey()`; revisar logs de **`MatchedGatewayRouteLoggingFilter`** (estrategia efectiva) y de tu política.

---

## 5. Logs y niveles

Con el paquete base `com.ezamora.api_gateway_v1` (ajustar si cambias el paquete):

- **`RequestIdFilter`**: método, path, cliente, `X-Request-ID`, respuesta, ms.
- **`MatchedGatewayRouteLoggingFilter`**: `routeId`, URI upstream, estrategia efectiva.
- **Políticas RSA/AES**: líneas de auditoría (demo) sobre el payload.

Niveles en referencia: `application.yml` → `com.ezamora.api_gateway_v1: INFO`, `RoutePredicateHandlerMapping: DEBUG` (en **prod** el perfil pone este logger en **INFO**).

---

## 6. Tests en el referente

- `src/test/java/.../ApiGatewayV1ApplicationTests.java`: `@SpringBootTest` + `contextLoads()` para verificar que el contexto Spring (incl. gateway) arranca.

Réplica mínima: conservar al menos un test de contexto tras copiar código y YAML.

---

## 7. Checklist de réplica en un proyecto nuevo

1. **Gradle:** Java 17 toolchain, plugins Spring Boot y dependency-management, BOM Spring Cloud `2025.1.1` (o la línea que elijas), dependencia `spring-cloud-starter-gateway-server-webflux`.
2. **Copiar** el árbol `src/main/java/.../gateway/` (`application` + `infrastructure`) o reimplementarlo siguiendo el §4.
3. **Copiar** `application.yml` y `application-{dev,test,prod}.yml`; sustituir `group`/paquete en logging si aplica.
4. **Ajustar** `gateway.backends`, `gateway.routePaths`, `api-gateway.body-encryption.aes-secret` para entornos reales; en prod, exigir env para URIs y secretos.
5. **Orden de rutas:** rutas más específicas **antes** que comodines amplios.
6. **Ejecutar** `./gradlew test` y levantar la app con `--spring.profiles.active=dev|test|prod` según necesidad.
7. **Rutas en caliente** sin reinicio: no están en el referente; requerirían Config Server, Kubernetes reload u otro mecanismo.
8. **Nuevo tipo de cifrado o auditoría de payload:** seguir **§4.3** (una clase `@Component` en `infrastructure.policy` + la misma clave en `metadata` de la ruta).

---

## 8. Prompt corto (inglés) para pegar en otro sistema

```
Replicate this Spring Cloud Gateway reference project:

- Java 17, Gradle, Spring Boot 4.0.x, Spring Cloud BOM 2025.1.1, dependency spring-cloud-starter-gateway-server-webflux
- Routes under spring.cloud.gateway.server.webflux.routes; placeholders gateway.backends.*, gateway.routePaths.*, gateway.routeMethods.*; env vars GATEWAY_BACKEND_*_URI, GATEWAY_ROUTE_PATHS_*, GATEWAY_ROUTE_METHODS_*, SERVER_PORT, API_GATEWAY_AES_SECRET
- Route metadata: routeStrategy and/or strategyByMethod; policy keys must match policyKey() on @Component classes implementing PayloadEncryptionPolicyPort; NONE/SKIP disable policy resolution
- api-gateway.body-encryption.aes-secret: 16/24/32 UTF-8 bytes
- Profiles: default dev; application-dev.yml, application-test.yml, application-prod.yml (prod requires backend URIs via env)
- Partial hexagonal package layout: <base>.gateway.application (ports PayloadEncryptionPolicyPort, EncryptionPolicyLookupPort; EncryptionStrategyResolver; GatewayRouteMetadata) and <base>.gateway.infrastructure (web: RequestIdFilter, MatchedGatewayRouteLoggingFilter, PayloadEncryptionFilter as GlobalFilter+Ordered; policy: RSA/AES policies + SpringEncryptionPolicyLookup; config: GatewayConfiguration registers EncryptionStrategyResolver bean)
- No domain package; policies audit/log only, forward same body/headers unless extended
- To add a new encryption/audit type: new @Component in infrastructure.policy implementing PayloadEncryptionPolicyPort with unique policyKey(); reference that key in route metadata (routeStrategy or strategyByMethod); no change to SpringEncryptionPolicyLookup or EncryptionStrategyResolver unless metadata contract changes
- Smoke test: @SpringBootTest contextLoads

See docs/ARQUITECTURA_HEXAGONAL_PARCIAL.md for Spanish architecture rationale.
```

---

## 9. Cómo seguir creciendo el programa (evolución sobre el referente)

Además de **§4.3** (nueva política de payload), estas son líneas de crecimiento coherentes con el layout **application / infrastructure**:

| Dirección | Idea práctica |
|-----------|----------------|
| **Nuevas rutas / backends** | Añadir entradas bajo `spring.cloud.gateway.server.webflux.routes` y propiedades en `gateway.backends.*`, `gateway.routePaths.*`, `gateway.routeMethods.*` (y env asociadas). Sin Java salvo que cambie la semántica global. |
| **Nuevos filtros transversales** | Clases `@Component` + `GlobalFilter` + `Ordered` en `…infrastructure.web` (tracing, rate limit, auth, cabeceras). Definir orden respecto a `RequestIdFilter` / `PayloadEncryptionFilter`. |
| **Cambiar el cuerpo hacia el upstream** | Hoy `PayloadEncryptionFilter` reinyecta el mismo body tras auditar. Si en el futuro **transformáis** el payload, la lógica puede seguir en políticas o en un caso de uso en `application` llamado desde el filtro, manteniendo los puertos estables. |
| **Nuevo “motor” de resolución de clave** | Si dejáis de basaros solo en `routeStrategy` / `strategyByMethod`, ampliad **`GatewayRouteMetadata`**, **`EncryptionStrategyResolver`** y la documentación YAML; los filtros pueden seguir igual si siguen recibiendo una clave string. |
| **Catálogo de rutas dinámico** | Puerto en `application` (ej. contrato de lectura de rutas) + adaptador en `infrastructure` (`RouteDefinitionRepository`, JDBC, API interna). El YAML quedaría como fallback o se sustituiría por completo. |
| **Observabilidad** | Puertos de aplicación para métricas/eventos e implementaciones Micrometer/OpenTelemetry en `infrastructure` sin mezclar con el resolver puro. |
| **Calidad** | Tests unitarios del `EncryptionStrategyResolver` sin Spring; tests de integración con `@SpringBootTest` y rutas de prueba; contratos sobre `PayloadEncryptionPolicyPort` con dobles de prueba. |
| **Repositorio** | Multi-módulo Gradle (core vs boot), imagen Docker, pipeline CI que ejecute `./gradlew test` y análisis estático. |

Lo que **no** encaja con el patrón actual: meter lógica de cifrado “a mano” dentro de `PayloadEncryptionFilter` sin pasar por **`PayloadEncryptionPolicyPort`**; duplicaría el rol de las políticas y rompería la extensión por clave YAML.

El detalle paso a paso para **un nuevo algoritmo o política de auditoría** está en **§4.3**.

---

*Documento alineado con el repositorio `api-gateway-v1` (Spring Boot 4.0.5, Spring Cloud 2025.1.1). Actualizar versiones en tabla §2 y en §8 al migrar de línea.*
