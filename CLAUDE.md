# CLAUDE.md — CheckoutKMP

Guía de arquitectura y convenciones para trabajar en este repositorio. Léela antes de tocar código.

## Qué es

App de **prácticas de pagos** en Kotlin Multiplatform. Modela un flujo de checkout real:
tokenización de tarjeta, 3D Secure (SCA), idempotencia, reintentos, taxonomía de errores y
accesibilidad. **La lógica Y la UI (Compose Multiplatform) son compartidas y testeadas**; Android
e iOS son hosts finos.

## Reglas de oro (NO negociables)

1. **El PAN (número de tarjeta) NUNCA se loguea, ni se persiste, ni aparece en el estado.**
   Solo circulan el **token** y una versión **enmascarada** (`•••• 4242`). El PAN entra en el
   `CardTokenizer`, se convierte en token + máscara, y se descarta. Ningún `PaymentState`,
   log, receipt ni evento contiene dígitos del PAN, CVV o datos sensibles en claro.
2. **Todo vive en `commonMain` / `commonTest`** salvo los hosts: dominio + data + **presentación (MVI)**
   + **UI (Compose Multiplatform)** + tests son multiplataforma. `:androidApp` e `iosApp` solo montan
   `App()` y arrancan Koin.
3. **El dominio no depende de Android** ni de ningún framework. Casos de uso **puros** y testeados.
4. **`main` siempre compila y pasa tests.**

## Arquitectura (Clean Architecture)

```
:shared / commonMain
  ui/            Compose Multiplatform: App() = KoinContext { MaterialTheme { CheckoutRoute() } },
                 CheckoutScreen, CardForm, ScaChallengeScreen, Dimens, BrandLabel,
                 Localization (tr(en, es) + expect deviceLanguageCode())
  presentation/  MVI: CheckoutState (inmutable, sin PAN), CheckoutIntent, CheckoutViewModel
  di/            initKoin() común + presentationModule
  domain/
    model/        Amount, Currency, PaymentMethod, CardToken, IdempotencyKey, CardRules,
                  PaymentRequest, Receipt, PaymentError, PaymentState
    usecase/      ProcessPaymentUseCase, CompleteScaUseCase, Luhn, caducidad
    repository/   contrato PaymentRepository
    tokenizer/    contrato CardTokenizer (+ RawCard, TokenizationResult)
    simulation/   PaymentScenario + PaymentSimulator (seam de demo)
  data/
    psp/          FakePsp (implementa Psp + PaymentSimulator), PspErrorMapper
    repository/   PaymentRepositoryImpl + RetryingPaymentRepository (backoff, solo transitorios)
    tokenizer/    FakeCardTokenizer (PCI-safe)
    di/           dataModule

:androidApp   host fino: Application (initKoin { androidContext }) + MainActivity (setContent { App() })
iosApp        host fino: ComposeView monta MainViewControllerKt.MainViewController(); Koin en App.init
```

- **Dependencias apuntan hacia dentro:** `data → domain ← presentation`. Nada apunta hacia `data`
  (la UI depende solo de `domain`/`presentation`).
- **DI con Koin multiplataforma:** `koin-core` + `koin-compose`/`koin-compose-viewmodel`. `initKoin()`
  es común; el `androidContext` lo pasa la `Application` de Android y `startKoinIos()` desde Swift.
  `koinViewModel()` resuelve el ViewModel; nunca se instancia a mano en la UI.
- **iOS activado:** `iosArm64` + `iosSimulatorArm64`, framework `Shared`. `iosMain` aporta
  `MainViewController` (Compose) + `startKoinIos`. **Compilar/enlazar Apple requiere macOS + Xcode.**
  No introducir dependencias Android-only en `commonMain`.

## UI: Compose Multiplatform + MVI (en `commonMain/ui`)

- **UI compartida** en Compose Multiplatform; Android e iOS montan `App()`. La vista solo renderiza.
- **Estado inmutable** (`data class ...State`) expuesto como `StateFlow`; **nunca contiene el PAN/CVV**.
- **Intents** (`sealed interface ...Intent`) describen las acciones del usuario.
- El ViewModel reduce `Intent + estado actual → nuevo estado`, llamando a casos de uso.
- Validación en vivo (Luhn, formateo, enmascarado): PAN/CVV solo en estado local del composable
  (`remember`, no `rememberSaveable`); salen únicamente vía el intent `Submit` y se tokenizan al enviar.
- **i18n en Kotlin**, NO Compose resources: usar `tr("English", "Español")` (el plugin KMP-library de
  AGP 9 no empaqueta los Compose resources — ver memoria `compose-resources-agp9-limitation`).
- **Accesibilidad**: `liveRegion` para anuncios, `contentDescription` limpio, headings; sin
  `traversalIndex` (los layouts son lineales). Marcas reales = nombres propios; solo `UNKNOWN` se localiza.

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
- **Sin números mágicos:** reglas de tarjeta en `domain/model/CardRules`, dimensiones UI en `ui/Dimens`.
- **Sin APIs deprecadas** (verificar con `--warning-mode all`).
- Nada de `println`/logs con datos de pago; usar tipos que no expongan el PAN.
- Los mensajes al usuario no filtran códigos técnicos (`reason` de `PaymentError` es solo diagnóstico).

## Estrategia de ramas y commits

- `main` protegida conceptualmente: siempre verde (compila + tests). **Historia lineal, sin merge commits.**
- Una rama por trabajo (`feat/...`, `refactor/...`). Las 8 fases originales ya están en `main`.
- **Conventional Commits:** `feat:`, `test:`, `refactor:`, `docs:`, `chore:`. Commits pequeños.
- Al terminar: ejecutar tests; si pasan, **pedir OK** y mergear con `git merge --ff-only` (fast-forward).
  No mergear a `main` sin confirmación explícita. `gh` no está instalado → merges por CLI, no por PR.

## Comandos

```bash
./gradlew :androidApp:assembleDebug        # compilar app Android (arrastra la UI compartida)
./gradlew :shared:testAndroidHostTest      # tests compartidos (host JVM)
./gradlew :androidApp:testDebugUnitTest    # tests de presentación/DI (JVM)
# iOS (solo macOS): abrir iosApp/iosApp.xcodeproj en Xcode
```
