# El Itacate VdC

- `app/` → app Android (Java) con mapa y distancia al local.
- `web/` → página web (para Render como Static Site).
- `.github/workflows/build-apk.yml` → GitHub compila el APK solo.

## APK (sin Android Studio)
1. Sube todo a un repositorio de GitHub (rama `main`).
2. Pestaña **Actions** → "Build APK" → espera a que termine (palomita verde).
3. Entra a esa ejecución → abajo, **Artifacts** → descarga `app-debug-apk`.
4. Descomprime y pasa `app-debug.apk` al celular para instalarlo.

## Página web en Render
New → Static Site → conecta el repositorio →
Build Command: (vacío) · Publish Directory: `web`
