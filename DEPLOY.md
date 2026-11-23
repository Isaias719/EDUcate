# Guía de Despliegue en la Nube: Render + Supabase — EDUcate

Esta guía explica paso a paso cómo desplegar la base de datos PostgreSQL en **Supabase** y el backend FastAPI en **Render**, además de conectar la aplicación Android.

---

## Arquitectura en la nube

```
[ App Android (EDUcate) ]
           │ HTTPS
           ▼
[ Render Web Service (FastAPI) ]
           │ PostgreSQL (SSL)
           ▼
[ Supabase (PostgreSQL Database) ]
```

---

## PARTE 1: Configurar la Base de Datos en Supabase

### 1.1 Crear el proyecto
1. Entra a [supabase.com](https://supabase.com/) e inicia sesión (o crea cuenta con GitHub).
2. Haz clic en **"New Project"**.
3. Configura:
   - **Name:** `educate-db` (o el nombre que prefieras).
   - **Database Password:** Escribe una contraseña segura y **anótala/guárdala muy bien**.
   - **Region:** Elige una cercana (por ejemplo: `US East (North Virginia)`).
4. Haz clic en **"Create new project"** y espera 1-2 minutos a que termine de aprovisionarse.

### 1.2 Obtener la URI de conexión (Connection String)
1. En tu proyecto de Supabase, en el menú lateral izquierdo ve a:
   **Project Settings (icono de engranaje) → Database**.
2. Desplázate hacia abajo hasta la sección **"Connection string"**.
3. Selecciona la pestaña **URI**.
4. Copia la cadena. Se ve similar a esto:
   ```
   postgresql://postgres:[YOUR-PASSWORD]@db.xxxxxxx.supabase.co:5432/postgres
   ```
   *(o con el Connection Pooler en puerto 6543)*
5. **Reemplaza `[YOUR-PASSWORD]`** por la contraseña que definiste al crear el proyecto.
   > **Nota:** Si tu contraseña tiene caracteres especiales (como `@`, `#`, `%`), asegúrate de que esté codificada o usa una contraseña alfanumérica para evitar errores de conexión.

### 1.3 (Opcional) Migrar tus datos locales actuales a Supabase
Si quieres conservar los usuarios de prueba, cursos y tareas que ya tienes en tu `educacion.db` local:
1. Abre tu terminal en la carpeta `backend`:
   ```cmd
   cd backend
   venv\Scripts\python.exe migrate_to_supabase.py "TU_URI_DE_SUPABASE_CON_PASSWORD"
   ```
2. El script creará todas las tablas y transferirá automáticamente tus datos a Supabase.
   *(Si prefieres empezar desde cero en la nube, puedes omitir este paso: FastAPI creará las tablas automáticamente al iniciar).*

---

## PARTE 2: Desplegar el Backend en Render

### 2.1 Subir el código a GitHub
Render se conecta directamente a tu repositorio de GitHub para compilar y desplegar:

```cmd
git add .
git commit -m "Preparar backend para Render y Supabase"
git push
```

### 2.2 Crear el Web Service en Render
1. Entra a [render.com](https://render.com/) e inicia sesión.
2. En el panel principal, haz clic en **New + → Web Service**.
3. Conecta tu repositorio de GitHub donde está `EDUcate`.
4. Llena los siguientes campos:
   - **Name:** `educate-backend` (o el que gustes).
   - **Region:** Selecciona la misma región de tu Supabase (ej. `Ohio (US East)` o `Oregon (US West)`).
   - **Root Directory:** `backend` *(muy importante)*
   - **Runtime:** `Python 3`
   - **Build Command:** `pip install -r requirements.txt`
   - **Start Command:** `uvicorn main:app --host 0.0.0.0 --port $PORT`
   - **Instance Type:** `Free`

### 2.3 Configurar Variables de Entorno en Render
En la misma pantalla (o en la pestaña **Environment**):
Haz clic en **"Add Environment Variable"** y agrega:

| Key | Value |
|---|---|
| `DATABASE_URL` | Pega tu URI de Supabase con tu contraseña (ej. `postgresql://postgres:pass@db.xxxx.supabase.co:5432/postgres`) |
| `SECRET_KEY` | Cualquier cadena segura aleatoria (ej. `clave_secreta_super_segura_educate_2026`) |
| `PYTHON_VERSION` | `3.11.9` |

5. Haz clic en **"Create Web Service"**.
6. Render comenzará a construir el proyecto (`pip install`) y arrancará uvicorn.
7. Cuando termine, verás el estado en verde: **`Live`**.
8. Render te dará una URL pública HTTPS, por ejemplo:
   `https://educate-backend.onrender.com`

---

## PARTE 3: Probar el Backend en Render

Abre en tu navegador:
`https://TU-SERVICIO.onrender.com/health`
Debe responder:
```json
{"status": "ok"}
```

Y la documentación interactiva Swagger:
`https://TU-SERVICIO.onrender.com/docs`

---

## PARTE 4: Conectar la App Android al Nuevo Backend

1. Abre el archivo `android/app/build.gradle.kts` en tu proyecto.
2. Modifica la línea de `BASE_URL` (recuerda que DEBE terminar con `/`):
   ```kotlin
   buildConfigField("String", "BASE_URL", "\"https://TU-SERVICIO.onrender.com/\"")
   ```
3. En Android Studio:
   - Haz clic en **Sync Project with Gradle Files** (icono del elefante 🐘).
   - Ejecuta la app (**Run ▶️**).

¡Listo! Tu app ahora funcionará desde cualquier celular, con cualquier red o datos móviles, apuntando a tu backend en Render y guardando todo en tu base de datos Supabase.

| Variable | Valor |
|---|---|
| `SECRET_KEY` | Cadena aleatoria segura |
| `DATABASE_URL` | URL de tu base de datos PostgreSQL (ver paso 3) |
| `UPLOAD_DIR` | `uploads` |

### 3. Agregar PostgreSQL en Render

1. En el dashboard, clic en **New** → **PostgreSQL**.
2. Crear la base de datos y copiar la **Internal Database URL**.
3. Pegarla como valor de `DATABASE_URL` en las variables de entorno del servicio web.

### 4. Plan gratuito de Render

> **Atención:** El plan gratuito de Render pone el servicio en "sleep" tras 15 minutos de inactividad.
> La primera petición después del sleep puede tardar 30-60 segundos.
> Para producción real, considera el plan Starter ($7/mes) o usa Railway.

---

## Actualizar la URL del backend en la app Android

Una vez que el backend esté desplegado y tengas su URL pública (ej. `https://educate-backend.up.railway.app`), actualiza la app Android:

1. Abre el archivo:
   ```
   android/app/build.gradle.kts
   ```

2. Busca la línea con `BASE_URL` (dentro del bloque `buildConfigField` o `defaultConfig`) y cámbiala a tu URL de producción:
   ```kotlin
   buildConfigField("String", "BASE_URL", "\"https://TU_URL.up.railway.app/\"")
   ```
   Asegúrate de que la URL termine con `/`.

3. Reconstruye la app: **Build → Rebuild Project** en Android Studio.

4. Para pruebas locales, puedes volver a poner `http://10.0.2.2:8000/` (emulador) o la IP de tu máquina.

---

## Resumen de archivos de configuración creados

| Archivo | Propósito |
|---|---|
| `backend/requirements.txt` | Dependencias Python con versiones fijas |
| `backend/Procfile` | Comando de inicio (Heroku/Render compatible) |
| `backend/railway.toml` | Configuración específica de Railway |
| `backend/.env.example` | Plantilla de variables de entorno |
| `backend/.gitignore` | Excluye archivos sensibles del repositorio |

---

## Solución de problemas frecuentes

| Problema | Solución |
|---|---|
| `ModuleNotFoundError` al iniciar | Verifica que `requirements.txt` esté en `backend/` y que el Root Directory esté configurado correctamente |
| Error de conexión a la DB | Verifica que `DATABASE_URL` esté definida y sea una URL PostgreSQL válida |
| Health check falla | El endpoint `/health` debe responder antes del timeout (300 s). Si la DB tarda, Railway reintentará |
| `bcrypt` error de versión | Usa exactamente `bcrypt==4.2.1` como está en `requirements.txt` |
| CORS error desde la app Android | Verifica que el origen esté permitido en el middleware CORS de `main.py` |
