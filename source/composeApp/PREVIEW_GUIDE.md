# Compose Previews with Koin in commonMain

## Problem
Compose previews in `commonMain` fail with `KoinApplication has not been started` error when using `koinInject()` because Koin isn't initialized in the preview environment.

Additionally, Android-specific implementations (like `fileOps`, `httpClient`) require `android.content.Context` which needs to be provided to Koin.

## Solution
Use the `PreviewKoinContext` wrapper for any preview that requires Koin dependencies.

### Usage

```kotlin
import krill.zone.preview.*
import org.jetbrains.compose.ui.tooling.preview.*

@Composable @Preview
fun MyComposablePreview() {
    PreviewKoinContext {
        MyComposable()
    }
}
```

### How it works
`PreviewKoinContext` is an `expect/actual` composable that:
1. Uses Koin's `KoinApplication` composable to create an isolated Koin context
2. Loads all required modules (`appModule` and `composeModule`)
3. **On Android**: Provides the Android Context using `LocalContext.current` and `androidContext()`
4. **On other platforms**: Simply initializes Koin with the modules
5. Provides dependencies to any composable within its scope
6. Automatically cleans up when the preview is disposed

### Platform-specific implementations
- **Android** (`androidMain`): Uses `androidContext()` to provide Android Context for platform-specific dependencies
- **Desktop** (`desktopMain`): Simple Koin initialization without platform context
- **iOS** (`iosMain`): Simple Koin initialization without platform context
- **WasmJs** (`wasmJsMain`): Simple Koin initialization without platform context

### Files
- **Common declaration**: `/composeApp/src/commonMain/kotlin/krill/zone/ui/preview/PreviewKoinContext.kt`
- **Android implementation**: `/composeApp/src/androidMain/kotlin/krill/zone/ui/preview/PreviewKoinContext.android.kt`
- **Desktop implementation**: `/composeApp/src/desktopMain/kotlin/krill/zone/ui/preview/PreviewKoinContext.desktop.kt`
- **iOS implementation**: `/composeApp/src/iosMain/kotlin/krill/zone/ui/preview/PreviewKoinContext.ios.kt`
- **WasmJs implementation**: `/composeApp/src/wasmJsMain/kotlin/krill/zone/ui/preview/PreviewKoinContext.wasmJs.kt`
- **Example usage**: `/composeApp/src/commonMain/kotlin/krill/zone/ui/dialog/DeleteNodeDialog.kt`

### When to use
- Any preview in `commonMain` that uses `koinInject()`
- Any preview that renders composables requiring Koin dependencies
- Any preview that indirectly depends on platform-specific implementations (fileOps, httpClient, etc.)

### When NOT to use
- Previews that don't use Koin dependencies (like `WelcomeDialog`)
- Previews that don't reference any composables using `koinInject()`

## Notes
- Each preview with `PreviewKoinContext` gets its own isolated Koin instance
- The context is automatically disposed when the preview is destroyed
- No need to manually stop Koin or clean up resources
- The Android implementation automatically retrieves the Android Context from the preview environment

