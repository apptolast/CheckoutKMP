# CLAUDE.md — CheckoutKMP

Guía de arquitectura y convenciones para trabajar en este repositorio. Léela antes de tocar código.

## Qué es

App de **prácticas de pagos** en Kotlin Multiplatform. Modela un flujo de checkout real:
tokenización de tarjeta, 3D Secure (SCA), idempotencia, reintentos, taxonomía de errores y
accesibilidad. La lógica es compartida y testeada; la UI es solo Android.

## Reglas de oro (NO negociables)

1. **El PAN (número de tarjeta) NUNCA se loguea, ni se persiste, ni aparece en el estado.**
   Solo circulan el **token** y una versión **enmascarada** (`•••• 4242`). El PAN entra en el
   `CardTokenizer`, se convierte en token + máscara, y se descarta. Ningún `PaymentState`,
   log, receipt ni evento contiene dígitos del PAN, CVV o datos sensibles en claro.
2. **La lógica va en `commonMain` / `commonTest`.** Dominio + data + tests son multiplataforma.
   Solo la **UI** vive en `:androidApp`.
3. **El dominio no depende de Android** ni de ningún framework. Casos de uso **puros** y testeados.
4. **`main` siempre compila y pasa tests.**

## Arquitectura (Clean Architecture)

```
:shared / commonMain
  domain/
    model/       Amount, Currency, PaymentMethod, CardToken, IdempotencyKey,
                 PaymentRequest, Receipt, PaymentError, PaymentState
    usecase/     ProcessPaymentUseCase (+ completeSca), Luhn, validación de caducidad
    repository/  contratos (interfaces) que implementa la capa data
  data/
    psp/         FakePsp (approved/needsSca/declined/network), CardTokenizer PCI-safe
    repository/  PaymentRepository (implementación), idempotencia por IdempotencyKey

:androidApp   (solo UI)
  Compose + MVI: estado inmutable + intents, ViewModels que consumen los casos de uso de :shared
```

- **Dependencias apuntan hacia dentro:** `data` depende de `domain`; `domain` no depende de nadie.
- **DI con Koin:** módulos de Koin en `:shared` (`koin-core`); `koin-android` + `koin-androidx-compose`
  en la app. Los casos de uso y repositorios se proveen por Koin, no se instancian a mano en la UI.
- **iOS:** targets **preparados pero desactivados** en `shared/build.gradle.kts` (comentados).
  No introducir dependencias Android-only en `commonMain`.

## UI: MVI

- **Estado inmutable** (`data class ...State`) expuesto como flujo; la UI solo lo renderiza.
- **Intents** (`sealed interface ...Intent`) describen las acciones del usuario.
- El ViewModel reduce `Intent + estado actual → nuevo estado`, llamando a casos de uso.
- Validación en vivo (Luhn, formateo, enmascarado) sin exponer nunca el PAN en el estado.

## Dominio de pagos: invariantes

- **IdempotencyKey** (`kotlin.uuid.Uuid`): un intento de pago = una clave. Reintentar un transitorio
  **reutiliza la misma clave** para no cobrar dos veces. El `FakePsp` cachea por clave.
- **Reintentos:** `retryTransient` con backoff solo reintenta errores **transitorios**
  (`Network`, `Timeout`, `RateLimited`). **Nunca** `Declined` ni `InvalidCard`.
- **Mapper PSP → PaymentError centralizado en el borde** (capa data). El dominio solo ve `PaymentError`.
- **Máquina de estados** del pago: Idle → Processing → (Approved | NeedsSca → (Approved | ScaFailed | Cancelled) | Declined | Error).

## Convenciones de código

- Kotlin oficial (`kotlin.code.style=official`). Modelos inmutables (`data class`, `val`).
- Errores modelados como tipos (`sealed`), no excepciones para el flujo de negocio esperado.
- Dependencias vía **version catalog** (`gradle/libs.versions.toml`). No hardcodear versiones.
- Nada de `println` con datos de pago; usar tipos que no expongan el PAN.

## Estrategia de ramas y commits

- `main` protegida conceptualmente: siempre verde (compila + tests).
- Una rama por fase: `feat/phase-1-domain`, `feat/phase-2-tests`, `feat/phase-3-data`,
  `feat/phase-4-ui`, `feat/phase-5-3ds`, `feat/phase-6-a11y`, `feat/phase-7-errors`, `feat/phase-8-polish`.
- **Conventional Commits:** `feat:`, `test:`, `refactor:`, `docs:`, `chore:`. Commits pequeños.
- Al terminar una fase: ejecutar tests; si pasan, abrir PR (`gh pr create`) y **pedir OK antes de mergear**.
  No mergear a `main` sin confirmación explícita del usuario.

## Comandos

```bash
./gradlew :androidApp:assembleDebug        # compilar app
./gradlew :shared:testAndroidHostTest      # tests de lógica compartida (host JVM)
```
