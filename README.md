# EDUcate — plataforma de educación en línea

Dos carpetas:

- `backend/` — API en Python (FastAPI) + base de datos (SQLite por defecto, PostgreSQL con `DATABASE_URL`).
- `android/` — app Android en Kotlin (Jetpack Compose) con las 9 pantallas de los mockups.

## 1. Backend

```bash
cd backend
pip install -r requirements.txt
export ADMIN_KEY="pon-una-clave-larga"     # para aprobar docentes
uvicorn main:app --host 0.0.0.0 --port 8000
```

Documentación interactiva de la API: http://localhost:8000/docs

Variables opcionales: `DATABASE_URL` (p. ej. `postgresql+psycopg://...`), `SECRET_KEY`, `UPLOAD_DIR`.

La base de datos nace **vacía**: no hay cursos, usuarios ni calificaciones de ejemplo.
Todo lo que se ve en la app sale de datos reales de cada cuenta.

### Perfiles y Autenticación
- **Validación de correos**: Verificación estricta de formato tanto en frontend (Jetpack Compose) como en backend (Pydantic / FastAPI) con mensajes explicativos en español.
- **Registro de usuarios**: Opción para registrarse como **Estudiante** o como **Maestro / Profesor**.
- **Inicio de sesión unificado**: Detecta automáticamente el rol del usuario (`student` o `teacher`) y abre la interfaz adecuada con su barra de navegación propia.

### Aplicación del Maestro / Docente
1. **Inicio Docente**: Métricas en tiempo real (cursos activos, total de alumnos, tareas pendientes de revisión), accesos rápidos y lista de actividades por calificar.
2. **Gestión Completa de Cursos**:
   - Creación y edición de cursos: título, descripción, categoría, nivel, duración en semanas, grupo, periodo, portada e inscripción abierta.
   - Eliminación de cursos.
3. **Temarios y Módulos**:
   - Creación y eliminación de módulos/semanas.
   - Creación de contenidos: Lecturas, Videos (URL y duración) y Foros de debate.
   - Asociación de recursos y materiales.
4. **Gestión de Tareas y Calificaciones**:
   - Creación de tareas con instrucciones, puntos máximos, fecha y hora límite, tipo (archivo, cuestionario, foro) y módulo asociado.
   - Revisión de entregas de alumnos: visualización de archivos adjuntos, fecha y hora de entrega.
   - Calificación de entregas con asignación de nota numérica y retroalimentación pedagógica personalizada.
   - Lista de estudiantes que aún no han realizado su entrega.
   - Bandeja unificada de entregas por calificar de todos los cursos.
5. **Comunicados y Avisos**: Publicación y gestión de avisos oficiales para el grupo.
6. **Lista de Estudiantes**: Visualización de los alumnos matriculados en cada curso.

### Progreso por cuenta
- Contenido completado = una fila en `item_progress` (usuario + contenido).
- Un **tema/semana** cuenta como completado cuando todos sus contenidos están completos;
  el porcentaje del curso = temas completados / temas totales.
- Calificaciones = entregas evaluadas por el docente (`submissions`). El promedio solo incluye
  actividades evaluadas.

## 2. App Android

1. Abre la carpeta `android/` con Android Studio (Koala o más reciente) y deja que sincronice Gradle.
2. En `app/build.gradle.kts` ajusta `BASE_URL`:
   - emulador: `http://10.0.2.2:8000/` (ya viene así)
   - celular real: `http://IP-DE-TU-PC:8000/` (misma red Wi‑Fi)
   - producción: tu dominio con HTTPS (y quita `usesCleartextTraffic` del manifest).
3. Ejecuta en un emulador o dispositivo.

## Pendiente / fuera de esta versión
- "¿Olvidaste tu contraseña?" e inicio con Google/Microsoft.
- Cobro real de donaciones (Stripe/PayPal): hoy se registra la donación con estado `pendiente_pago`.
