# CheckoutKMP

Flujo de checkout y pagos en Kotlin Multiplatform: tokenización, 3D Secure (SCA), idempotencia, reintentos, accesibilidad y tests de lógica compartida. Lógica en commonMain, UI Android en :androidApp.

> Proyecto de prácticas centrado en el **dominio de pagos**. El objetivo es demostrar
> una arquitectura limpia, testeable y segura (PCI-consciente) para un flujo de pago real,
> con la lógica compartida en `commonMain` y la UI únicamente en Android.

---

## Arquitectura

Clean Architecture con la lógica de negocio 100 % en Kotlin común (sin Android):

```
┌──────────────────────────────────────────────────────────┐
│  :androidApp   (Compose + MVI)                             │
│  UI · estado inmutable · intents · ViewModels              │
└───────────────▲──────────────────────────────────────────┘
                │  consume casos de uso
┌───────────────┴──────────────────────────────────────────┐
│  :shared / commonMain                                      │
│                                                            │
│   domain/   Modelos puros, PaymentState, casos de uso,     │
│             Luhn, caducidad (kotlinx-datetime).            │
│             No conoce Android ni frameworks.               │
│                                                            │
│   data/     PaymentRepository, FakePsp, CardTokenizer      │
│             (PCI-safe), idempotencia por IdempotencyKey.   │
└──────────────────────────────────────────────────────────┘
```

- **domain** no depende de nada de la plataforma: modelos inmutables, casos de uso puros
  y la máquina de estados del pago.
- **data** implementa los contratos del dominio (repositorios, PSP simulado, tokenizador).
- **UI** (solo Android) sigue **MVI**: estado inmutable + intents, alimentado por los casos de uso.
- **DI** con **Koin** (KMP en `:shared`, `koin-android` en la app).
- Targets **iOS preparados pero desactivados** (comentados en `shared/build.gradle.kts`);
  la lógica se escribe para que puedan activarse sin tocar `commonMain`.

## Módulos

| Módulo        | Contenido                                                            |
|---------------|---------------------------------------------------------------------|
| `:shared`     | Lógica compartida: `commonMain` (domain + data), `commonTest`, `androidMain`. |
| `:androidApp` | Aplicación Android con Compose y patrón MVI.                         |
| `iosApp`      | Punto de entrada iOS (preparado; targets desactivados por ahora).   |

### Stack

- Kotlin Multiplatform (Kotlin 2.4, AGP 9) · Gradle version catalogs (`gradle/libs.versions.toml`)
- kotlinx-coroutines · kotlinx-datetime · Koin
- `kotlin.uuid.Uuid` (stdlib) para `IdempotencyKey`
- Tests: kotlin-test · kotlinx-coroutines-test · **Turbine**

## Cómo ejecutar

Requisitos: JDK 17+, Android SDK (definido en `local.properties` → `sdk.dir`).

```bash
# Compilar la app Android
./gradlew :androidApp:assembleDebug

# Ejecutar los tests de la lógica compartida (host JVM)
./gradlew :shared:testAndroidHostTest
```

En Android Studio: usa las run configurations del widget de ejecución.

## Roadmap por fases

Cada fase vive en su propia rama (`feat/phase-N-*`) y se mergea a `main` tras pasar los tests.
`main` siempre compila y pasa tests.

1. **Dominio** — modelos (`Amount`, `Currency`, `PaymentMethod`, `CardToken`, `IdempotencyKey`,
   `PaymentRequest`, `Receipt`, `PaymentError`), `PaymentState`, `ProcessPaymentUseCase` + `completeSca`,
   Luhn, caducidad con kotlinx-datetime.
2. **Tests de dominio** — Turbine: Approved / NeedsSca / Declined / Error, Luhn, transiciones de estado.
3. **Data** — `PaymentRepository`, `FakePsp` configurable (approved/needsSca/declined/network) con
   latencia e idempotencia por `IdempotencyKey`; `CardTokenizer` PCI-safe. Tests de idempotencia y enmascarado.
4. **UI Android** — Compose + MVI: selección de método de pago y formulario de tarjeta con
   validación en vivo (Luhn, formateo, enmascarado).
5. **3D Secure** — pantalla de challenge, OTP simulado, `completeSca`; éxito, `ScaFailed`, cancelación.
6. **Accesibilidad** — `traversalIndex`, `liveRegion`, `contentDescription` en marcas de tarjeta,
   `key(...)` para evitar anuncios indebidos.
7. **Errores y resiliencia** — taxonomía completa de `PaymentError`, mapper PSP→PaymentError en el borde,
   `retryTransient` con backoff que solo reintenta transitorios reutilizando la misma `IdempotencyKey`.
8. **Pulido** — diagrama de la máquina de estados, sección "¿Qué demuestra?", verificación anti-PAN.

## Qué demuestra (orientado a pagos)

- **Seguridad PCI-consciente (regla de oro):** el **PAN nunca se loguea, ni se persiste, ni aparece
  en el estado**. Solo circula el **token** y una versión **enmascarada** (p. ej. `•••• 4242`).
- **Idempotencia:** cada intento de pago lleva una `IdempotencyKey`; reintentar no cobra dos veces.
- **Reintentos seguros:** solo se reintentan errores **transitorios** (red/timeout), nunca `Declined`
  ni `InvalidCard`, y siempre con la **misma** `IdempotencyKey`.
- **3D Secure / SCA:** máquina de estados que modela el challenge y su resolución (éxito/fallo/cancelación).
- **Validación de tarjeta:** algoritmo de **Luhn** y control de caducidad, testeados en `commonTest`.
- **Lógica compartida y testeada:** casos de uso puros, independientes de Android, verificables con Turbine.
- **Accesibilidad de verdad:** orden de foco, anuncios de errores/resultado y descripciones de contenido.

---

Aprende más sobre [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html).
