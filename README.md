# Tres

App Android de notas con tres prioridades, pantalla negra AMOLED, dictado por voz en español o inglés, fechas con repetición y un widget para la pantalla de inicio. Todo se guarda solo en el teléfono: sin cuenta, sin nube y sin notificaciones.

## Funciones
- Notas de texto y hasta tres prioridades; editar, completar y restaurar.
- Fecha opcional con repetición diaria, semanal o mensual. Al completar una tarea repetida se crea una sola siguiente fecha. Las mensuales conservan el día original (31 → 28 de febrero → 31 de marzo).
- Las notas completadas se eliminan 10 días después de completarse.
- Dictado con el servicio de voz de Android (idioma en Ajustes). Tres no guarda audio; el servicio de voz puede procesarlo por internet.
- Widget con las tres prioridades: completarlas o empezar un dictado desde la pantalla de inicio.
- Requiere Android 8.0 (API 26) o superior.

## Descargar el APK
- **Versión de prueba:** en la pestaña **Actions**, abre la última ejecución de *Android build* y descarga `tres-debug-apk` en *Artifacts*.
- **Versión firmada:** en **Releases**, una vez configurada la firma (ver abajo).

Para instalarlo, permite en el teléfono la instalación de apps de orígenes desconocidos.

## Publicar un APK firmado
1. Crea tu clave una sola vez, en tu PC (guárdala bien: sin ella no podrás actualizar la app):
   ```powershell
   keytool -genkeypair -v -keystore tres-release.jks -alias tres -keyalg RSA -keysize 4096 -validity 10000
   [Convert]::ToBase64String([IO.File]::ReadAllBytes("tres-release.jks")) | Set-Clipboard
   ```
2. En **Settings → Secrets and variables → Actions** crea `TRES_KEYSTORE_BASE64` (lo copiado), `TRES_KEYSTORE_PASSWORD` y `TRES_KEY_ALIAS` (`tres`).
3. Crea una Release con un tag como `v1.0.0` (o `git tag v1.0.0` y `git push origin v1.0.0`). GitHub compila, firma y adjunta `Tres-1.0.0.apk`. Cada versión nueva debe tener un número mayor.

## Compilar en tu PC
Requisitos: JDK 17 y Android SDK 34 (`ANDROID_HOME` o `local.properties` con `sdk.dir=...`).

```sh
./gradlew assembleDebug lintDebug     # Windows: .\gradlew.bat assembleDebug lintDebug
./scripts/test-core.sh                # Windows: .\scripts\test-core.ps1
./gradlew connectedDebugAndroidTest   # con un emulador o teléfono conectado
```
El APK queda en `app/build/outputs/apk/debug/app-debug.apk`.

## Código
- `Notebook.java`: lógica de prioridades, repetición y caducidad (probada en `tests/`).
- `Note.java`: modelo inmutable de nota.
- `NoteStore.java`: guardado atómico en JSON, compatible con el formato anterior.
- `MainActivity.java`: interfaz, editor con recuperación de borrador, fechas y dictado.
- `PriorityWidget.java`: widget de la pantalla de inicio.
