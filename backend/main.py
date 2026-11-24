import datetime as dt
import os
import secrets
import uuid
from pathlib import Path
from typing import Optional

import bcrypt
import jwt
from fastapi import (Depends, FastAPI, File, Header, HTTPException, Query,
                     Request, UploadFile)
from fastapi.exceptions import RequestValidationError
from fastapi.responses import FileResponse, JSONResponse
from pydantic import BaseModel, EmailStr, Field
from sqlalchemy import (Boolean, DateTime, Float, ForeignKey, Integer, String,
                        Text, UniqueConstraint, create_engine, func, select, text)
from sqlalchemy.orm import (DeclarativeBase, Mapped, Session, mapped_column,
                            relationship, sessionmaker)

# ================================================================ config
DATABASE_URL = os.getenv("DATABASE_URL", "sqlite:///./educacion.db")
if DATABASE_URL.startswith("postgres://"):
    DATABASE_URL = DATABASE_URL.replace("postgres://", "postgresql+psycopg2://", 1)
elif DATABASE_URL.startswith("postgresql://") and not DATABASE_URL.startswith("postgresql+"):
    DATABASE_URL = DATABASE_URL.replace("postgresql://", "postgresql+psycopg2://", 1)
ADMIN_KEY = os.getenv("ADMIN_KEY")
UPLOAD_DIR = Path(os.getenv("UPLOAD_DIR", "uploads"))
UPLOAD_DIR.mkdir(parents=True, exist_ok=True)
MAX_UPLOAD = 25 * 1024 * 1024
ALLOWED_EXT = {".pdf", ".zip", ".docx"}
TOKEN_DAYS = 30
GRADE_GOAL = 80


def _load_secret() -> str:
    env = os.getenv("SECRET_KEY")
    if env:
        return env
    f = Path(".secret_key")
    if not f.exists():
        f.write_text(secrets.token_hex(32))
    return f.read_text().strip()


SECRET_KEY = _load_secret()
engine = create_engine(
    DATABASE_URL,
    connect_args={"check_same_thread": False} if DATABASE_URL.startswith("sqlite") else {},
)
SessionLocal = sessionmaker(bind=engine, autoflush=False)


def now() -> dt.datetime:
    return dt.datetime.now(dt.timezone.utc)


def aware(d):
    if d is None:
        return None
    return d if d.tzinfo else d.replace(tzinfo=dt.timezone.utc)


def iso(d):
    d = aware(d)
    return d.isoformat() if d else None


# ================================================================ modelos
class Base(DeclarativeBase):
    pass


class User(Base):
    __tablename__ = "users"
    id: Mapped[int] = mapped_column(primary_key=True)
    name: Mapped[str] = mapped_column(String(120))
    email: Mapped[str] = mapped_column(String(255), unique=True, index=True)
    password_hash: Mapped[str] = mapped_column(String(255))
    role: Mapped[str] = mapped_column(String(10), default="student")  # student | teacher
    created_at: Mapped[dt.datetime] = mapped_column(DateTime(timezone=True), default=now)


class Course(Base):
    __tablename__ = "courses"
    id: Mapped[int] = mapped_column(primary_key=True)
    title: Mapped[str] = mapped_column(String(200))
    description: Mapped[str] = mapped_column(Text, default="")
    category: Mapped[str] = mapped_column(String(60), default="")
    level: Mapped[str] = mapped_column(String(30), default="Principiante")
    duration_weeks: Mapped[Optional[int]] = mapped_column(Integer, nullable=True)
    is_free: Mapped[bool] = mapped_column(Boolean, default=True)
    open_enrollment: Mapped[bool] = mapped_column(Boolean, default=True)
    group_name: Mapped[str] = mapped_column(String(40), default="")
    period: Mapped[str] = mapped_column(String(20), default="")
    image_url: Mapped[Optional[str]] = mapped_column(String(500), nullable=True)
    teacher_id: Mapped[int] = mapped_column(ForeignKey("users.id"), index=True)
    teacher: Mapped[User] = relationship()
    modules: Mapped[list["Module"]] = relationship(
        back_populates="course", order_by="Module.position", cascade="all, delete-orphan")
    assignments: Mapped[list["Assignment"]] = relationship(
        back_populates="course", order_by="Assignment.due_at", cascade="all, delete-orphan")


class Module(Base):
    """Un 'tema' o semana del curso."""
    __tablename__ = "modules"
    id: Mapped[int] = mapped_column(primary_key=True)
    course_id: Mapped[int] = mapped_column(ForeignKey("courses.id"), index=True)
    position: Mapped[int] = mapped_column(Integer)
    title: Mapped[str] = mapped_column(String(200))
    available_from: Mapped[Optional[dt.datetime]] = mapped_column(DateTime(timezone=True), nullable=True)
    course: Mapped[Course] = relationship(back_populates="modules")
    items: Mapped[list["Item"]] = relationship(
        back_populates="module", order_by="Item.position", cascade="all, delete-orphan")


class Item(Base):
    __tablename__ = "items"
    id: Mapped[int] = mapped_column(primary_key=True)
    module_id: Mapped[int] = mapped_column(ForeignKey("modules.id"), index=True)
    position: Mapped[int] = mapped_column(Integer)
    type: Mapped[str] = mapped_column(String(20))  # reading | video | forum
    title: Mapped[str] = mapped_column(String(200))
    subtitle: Mapped[str] = mapped_column(String(200), default="")
    description: Mapped[str] = mapped_column(Text, default="")
    video_url: Mapped[Optional[str]] = mapped_column(String(500), nullable=True)
    duration_min: Mapped[Optional[int]] = mapped_column(Integer, nullable=True)
    transcript: Mapped[str] = mapped_column(Text, default="")
    module: Mapped[Module] = relationship(back_populates="items")
    resources: Mapped[list["Resource"]] = relationship(cascade="all, delete-orphan")


class Resource(Base):
    __tablename__ = "resources"
    id: Mapped[int] = mapped_column(primary_key=True)
    item_id: Mapped[int] = mapped_column(ForeignKey("items.id"), index=True)
    title: Mapped[str] = mapped_column(String(200))
    url: Mapped[str] = mapped_column(String(500))
    pages: Mapped[Optional[int]] = mapped_column(Integer, nullable=True)
    size_kb: Mapped[Optional[int]] = mapped_column(Integer, nullable=True)


class Assignment(Base):
    __tablename__ = "assignments"
    id: Mapped[int] = mapped_column(primary_key=True)
    course_id: Mapped[int] = mapped_column(ForeignKey("courses.id"), index=True)
    module_id: Mapped[Optional[int]] = mapped_column(ForeignKey("modules.id"), nullable=True)
    title: Mapped[str] = mapped_column(String(200))
    instructions: Mapped[str] = mapped_column(Text, default="")
    kind: Mapped[str] = mapped_column(String(20), default="tarea")  # tarea | cuestionario | foro
    points: Mapped[int] = mapped_column(Integer, default=10)
    due_at: Mapped[dt.datetime] = mapped_column(DateTime(timezone=True))
    quiz_questions: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    rubric: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    publish_at: Mapped[Optional[dt.datetime]] = mapped_column(DateTime(timezone=True), nullable=True)
    course: Mapped[Course] = relationship(back_populates="assignments")


class Enrollment(Base):
    __tablename__ = "enrollments"
    __table_args__ = (UniqueConstraint("user_id", "course_id"),)
    id: Mapped[int] = mapped_column(primary_key=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id"), index=True)
    course_id: Mapped[int] = mapped_column(ForeignKey("courses.id"), index=True)
    created_at: Mapped[dt.datetime] = mapped_column(DateTime(timezone=True), default=now)


class ItemProgress(Base):
    """Una fila = ese usuario completó ese contenido."""
    __tablename__ = "item_progress"
    __table_args__ = (UniqueConstraint("user_id", "item_id"),)
    id: Mapped[int] = mapped_column(primary_key=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id"), index=True)
    item_id: Mapped[int] = mapped_column(ForeignKey("items.id"), index=True)
    completed_at: Mapped[dt.datetime] = mapped_column(DateTime(timezone=True), default=now)


class Submission(Base):
    __tablename__ = "submissions"
    __table_args__ = (UniqueConstraint("assignment_id", "user_id"),)
    id: Mapped[int] = mapped_column(primary_key=True)
    assignment_id: Mapped[int] = mapped_column(ForeignKey("assignments.id"), index=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id"), index=True)
    filename: Mapped[str] = mapped_column(String(255))
    path: Mapped[str] = mapped_column(String(500))
    size_bytes: Mapped[int] = mapped_column(Integer)
    submitted_at: Mapped[dt.datetime] = mapped_column(DateTime(timezone=True), default=now)
    score: Mapped[Optional[float]] = mapped_column(Float, nullable=True)
    feedback: Mapped[str] = mapped_column(Text, default="")
    graded_at: Mapped[Optional[dt.datetime]] = mapped_column(DateTime(timezone=True), nullable=True)
    assignment: Mapped[Assignment] = relationship()
    user: Mapped[User] = relationship()


class Announcement(Base):
    __tablename__ = "announcements"
    id: Mapped[int] = mapped_column(primary_key=True)
    course_id: Mapped[int] = mapped_column(ForeignKey("courses.id"), index=True)
    title: Mapped[str] = mapped_column(String(200))
    body: Mapped[str] = mapped_column(Text, default="")
    created_at: Mapped[dt.datetime] = mapped_column(DateTime(timezone=True), default=now)
    course: Mapped[Course] = relationship()


class ForumPost(Base):
    __tablename__ = "forum_posts"
    id: Mapped[int] = mapped_column(primary_key=True)
    item_id: Mapped[int] = mapped_column(ForeignKey("items.id"), index=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id"))
    body: Mapped[str] = mapped_column(Text)
    created_at: Mapped[dt.datetime] = mapped_column(DateTime(timezone=True), default=now)
    user: Mapped[User] = relationship()


class Donation(Base):
    __tablename__ = "donations"
    id: Mapped[int] = mapped_column(primary_key=True)
    user_id: Mapped[Optional[int]] = mapped_column(ForeignKey("users.id"), nullable=True)
    amount: Mapped[int] = mapped_column(Integer)
    currency: Mapped[str] = mapped_column(String(3), default="MXN")
    email: Mapped[str] = mapped_column(String(255))
    status: Mapped[str] = mapped_column(String(20), default="pendiente_pago")
    created_at: Mapped[dt.datetime] = mapped_column(DateTime(timezone=True), default=now)


class TeacherApplication(Base):
    __tablename__ = "teacher_applications"
    id: Mapped[int] = mapped_column(primary_key=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id"), index=True)
    motivation: Mapped[str] = mapped_column(Text, default="")
    status: Mapped[str] = mapped_column(String(12), default="pendiente")
    created_at: Mapped[dt.datetime] = mapped_column(DateTime(timezone=True), default=now)
    user: Mapped[User] = relationship()


# ================================================================ SQLite migrations (add new columns to existing DBs)
_new_columns = [
    "ALTER TABLE assignments ADD COLUMN quiz_questions TEXT",
    "ALTER TABLE assignments ADD COLUMN rubric TEXT",
    "ALTER TABLE assignments ADD COLUMN publish_at DATETIME",
]
try:
    with engine.connect() as _conn:
        for _col_def in _new_columns:
            try:
                _conn.execute(text(_col_def))
                _conn.commit()
            except Exception:
                pass
    Base.metadata.create_all(engine)
except Exception as _e:
    print(f">> Advertencia al conectar con la base de datos: {_e}")


# ================================================================ esquemas
class RegisterIn(BaseModel):
    name: str = Field(min_length=2, max_length=120)
    email: EmailStr
    password: str = Field(min_length=8, max_length=128)
    role: str = Field(default="student", pattern="^(student|teacher)$")


class LoginIn(BaseModel):
    email: EmailStr
    password: str = Field(min_length=1, max_length=128)


class CourseIn(BaseModel):
    title: str
    description: str = ""
    category: str = ""
    level: str = "Principiante"
    duration_weeks: Optional[int] = None
    is_free: bool = True
    open_enrollment: bool = True
    group_name: str = ""
    period: str = ""
    image_url: Optional[str] = None


class ModuleIn(BaseModel):
    title: str
    position: Optional[int] = None
    available_from: Optional[dt.datetime] = None


class ItemIn(BaseModel):
    type: str = Field(pattern="^(reading|video|forum)$")
    title: str
    subtitle: str = ""
    description: str = ""
    video_url: Optional[str] = None
    duration_min: Optional[int] = None
    transcript: str = ""


class ResourceIn(BaseModel):
    title: str
    url: str
    pages: Optional[int] = None
    size_kb: Optional[int] = None


class AssignmentIn(BaseModel):
    title: str
    instructions: str = ""
    kind: str = Field(default="tarea", pattern="^(tarea|cuestionario|foro)$")
    points: int = Field(default=10, gt=0)
    due_at: dt.datetime
    module_id: Optional[int] = None


class AnnouncementIn(BaseModel):
    title: str
    body: str = ""


class GradeIn(BaseModel):
    score: float = Field(ge=0)
    feedback: str = ""
    rubric_scores: Optional[list[int]] = None


class QuizQuestionIn(BaseModel):
    question: str
    options: list[str]
    correct_index: int


class QuizSubmitIn(BaseModel):
    answers: list[int]


class RubricCriterionIn(BaseModel):
    name: str
    description: str
    max_points: int


class DuplicateCourseIn(BaseModel):
    group_name: str
    period: str


class PostIn(BaseModel):
    body: str = Field(min_length=1, max_length=4000)


class DonationIn(BaseModel):
    amount: int = Field(gt=0, le=1_000_000)
    email: EmailStr


class ApplyIn(BaseModel):
    motivation: str = ""


# ================================================================ auth
def hash_pw(p: str) -> str:
    return bcrypt.hashpw(p.encode(), bcrypt.gensalt()).decode()


def check_pw(p: str, h: str) -> bool:
    return bcrypt.checkpw(p.encode(), h.encode())


def make_token(user_id: int) -> str:
    return jwt.encode({"sub": str(user_id), "exp": now() + dt.timedelta(days=TOKEN_DAYS)},
                      SECRET_KEY, algorithm="HS256")


def get_db():
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()


def _user_from_header(authorization: str, db: Session) -> Optional[User]:
    if not authorization.startswith("Bearer "):
        return None
    try:
        data = jwt.decode(authorization[7:], SECRET_KEY, algorithms=["HS256"])
        return db.get(User, int(data["sub"]))
    except Exception:
        return None


def current_user(authorization: str = Header(default=""), db: Session = Depends(get_db)) -> User:
    user = _user_from_header(authorization, db)
    if not user:
        raise HTTPException(401, "Sesión inválida o expirada")
    return user


def optional_user(authorization: str = Header(default=""), db: Session = Depends(get_db)):
    return _user_from_header(authorization, db)


def teacher_only(user: User = Depends(current_user)) -> User:
    if user.role != "teacher":
        raise HTTPException(403, "Solo para docentes")
    return user


def admin_only(x_admin_key: str = Header(default="")):
    if not ADMIN_KEY or not secrets.compare_digest(x_admin_key, ADMIN_KEY):
        raise HTTPException(403, "No autorizado")


def initials(name: str) -> str:
    p = name.split()
    return ((p[0][0] + (p[-1][0] if len(p) > 1 else "")) if p else "?").upper()


def user_out(u: User) -> dict:
    return {"id": u.id, "name": u.name, "email": u.email, "role": u.role,
            "initials": initials(u.name)}


# ================================================================ helpers
def is_enrolled(db: Session, user_id: int, course_id: int) -> bool:
    return db.scalar(select(Enrollment.id).where(
        Enrollment.user_id == user_id, Enrollment.course_id == course_id)) is not None


def course_for(db: Session, user: User, course_id: int) -> Course:
    course = db.get(Course, course_id)
    if not course:
        raise HTTPException(404, "Curso no encontrado")
    if course.teacher_id != user.id and not is_enrolled(db, user.id, course_id):
        raise HTTPException(403, "No estás inscrito en este curso")
    return course


def own_course(db: Session, user: User, course_id: int) -> Course:
    course = db.get(Course, course_id)
    if not course:
        raise HTTPException(404, "Curso no encontrado")
    if course.teacher_id != user.id:
        raise HTTPException(403, "Este curso no es tuyo")
    return course


def enrolled_course_ids(db: Session, user_id: int) -> list[int]:
    return list(db.scalars(select(Enrollment.course_id).where(Enrollment.user_id == user_id)))


def assignment_status(sub: Optional[Submission]) -> str:
    if sub is None:
        return "pendiente"
    return "evaluada" if sub.score is not None else "entregada"


def course_view(db: Session, user: User, course: Course) -> dict:
    item_ids = [i.id for m in course.modules for i in m.items]
    done = set(db.scalars(select(ItemProgress.item_id).where(
        ItemProgress.user_id == user.id, ItemProgress.item_id.in_(item_ids)))) if item_ids else set()
    subs = {s.assignment_id: s for s in db.scalars(
        select(Submission).where(Submission.user_id == user.id))}
    counts = dict(db.execute(
        select(ForumPost.item_id, func.count()).where(ForumPost.item_id.in_(item_ids))
        .group_by(ForumPost.item_id)).all()) if item_ids else {}

    modules, completed, current_found, forums = [], 0, False, []
    for m in course.modules:
        complete = bool(m.items) and all(i.id in done for i in m.items)
        available = m.available_from is None or aware(m.available_from) <= now()
        if complete:
            status = "completed"
            completed += 1
        elif not available:
            status = "upcoming"
        elif not current_found:
            status, current_found = "in_progress", True
        else:
            status = "available"
        items = []
        for i in m.items:
            pc = counts.get(i.id, 0)
            items.append({"id": i.id, "type": i.type, "title": i.title, "subtitle": i.subtitle,
                          "completed": i.id in done, "posts_count": pc})
            if i.type == "forum":
                forums.append({"id": i.id, "title": i.title, "posts_count": pc})
        asg = []
        for a in course.assignments:
            if a.module_id == m.id:
                # Hide unpublished assignments from students
                if user.role == "student" and a.publish_at is not None and aware(a.publish_at) > now():
                    continue
                s = subs.get(a.id)
                asg.append({"id": a.id, "title": a.title, "kind": a.kind, "points": a.points,
                            "due_at": iso(a.due_at), "status": assignment_status(s),
                            "score": s.score if s else None})
        modules.append({"id": m.id, "position": m.position, "title": m.title, "status": status,
                        "available_from": iso(m.available_from), "items": items,
                        "assignments": asg,
                        "resources_count": len(items) + len(asg)})
    total = len(course.modules)
    return {
        "id": course.id, "title": course.title, "description": course.description,
        "category": course.category, "level": course.level,
        "teacher_name": course.teacher.name, "group_name": course.group_name,
        "period": course.period, "total_modules": total, "completed_modules": completed,
        "percent": round(100 * completed / total, 1) if total else 0.0,
        "modules": modules, "forums": forums,
    }


def progress_of(db: Session, user: User, course: Course) -> dict:
    v = course_view(db, user, course)
    return {"completed_modules": v["completed_modules"], "total_modules": v["total_modules"],
            "percent": v["percent"]}


def grades_view(db: Session, user: User, course: Course) -> dict:
    subs = {s.assignment_id: s for s in db.scalars(
        select(Submission).where(Submission.user_id == user.id))}
    prefix = {"tarea": "T", "cuestionario": "C", "foro": "F"}
    n: dict[str, int] = {}
    rows = []
    for a in course.assignments:
        n[a.kind] = n.get(a.kind, 0) + 1
        s = subs.get(a.id)
        rows.append({"id": a.id, "code": f"{prefix[a.kind]}{n[a.kind]}", "title": a.title,
                     "kind": a.kind, "points": a.points,
                     "score": s.score if s else None, "status": assignment_status(s)})
    ev = [r for r in rows if r["score"] is not None]
    earned = sum(r["score"] for r in ev)
    possible = sum(r["points"] for r in ev)
    percent = round(100 * earned / possible, 1) if possible else None
    label = None if percent is None else ("Desempeño alto" if percent >= 80
                                           else "Desempeño medio" if percent >= 60
                                           else "Desempeño bajo")
    return {
        "course_id": course.id, "title": course.title, "period": course.period,
        "percent": percent, "earned": earned, "possible": possible,
        "evaluated_count": len(ev), "level_label": label, "goal": GRADE_GOAL,
        "rows": rows,
        "previous": [{"code": r["code"], "percent": round(100 * r["score"] / r["points"])} for r in ev],
        "progress": progress_of(db, user, course),
        "has_pending": any(r["status"] != "evaluada" for r in rows),
    }


def assignment_detail(db: Session, user: User, a: Assignment) -> dict:
    s = db.scalar(select(Submission).where(
        Submission.assignment_id == a.id, Submission.user_id == user.id))
    return {
        "id": a.id, "title": a.title, "instructions": a.instructions, "kind": a.kind,
        "points": a.points, "due_at": iso(a.due_at), "course_id": a.course_id,
        "course_title": a.course.title, "status": assignment_status(s),
        "closed": aware(a.due_at) < now(),
        "submission": None if not s else {
            "filename": s.filename, "size_bytes": s.size_bytes, "submitted_at": iso(s.submitted_at),
            "score": s.score, "feedback": s.feedback},
    }


def item_detail(db: Session, user: User, item: Item) -> dict:
    course = item.module.course
    done = db.scalar(select(ItemProgress.id).where(
        ItemProgress.user_id == user.id, ItemProgress.item_id == item.id)) is not None
    nxt = None
    sibs = item.module.items
    idx = sibs.index(item)
    if idx + 1 < len(sibs):
        nxt = sibs[idx + 1]
    else:
        for m in course.modules:
            if m.position > item.module.position and m.items:
                nxt = m.items[0]
                break
    return {
        "id": item.id, "type": item.type, "title": item.title, "description": item.description,
        "module_title": item.module.title, "module_position": item.module.position,
        "total_modules": len(course.modules), "course_id": course.id,
        "course_title": course.title, "teacher_name": course.teacher.name,
        "video_url": item.video_url, "duration_min": item.duration_min,
        "transcript": item.transcript, "completed": done,
        "resources": [{"id": r.id, "title": r.title, "url": r.url, "pages": r.pages,
                       "size_kb": r.size_kb} for r in item.resources],
        "next": None if not nxt else {"id": nxt.id, "title": nxt.title, "type": nxt.type},
    }


def item_for(db: Session, user: User, item_id: int) -> Item:
    item = db.get(Item, item_id)
    if not item:
        raise HTTPException(404, "Contenido no encontrado")
    course_for(db, user, item.module.course_id)
    return item


def overall_average(db: Session, user: User, course_ids: list[int]) -> Optional[float]:
    if not course_ids:
        return None
    rows = db.execute(
        select(Submission.score, Assignment.points)
        .join(Assignment, Assignment.id == Submission.assignment_id)
        .where(Submission.user_id == user.id, Assignment.course_id.in_(course_ids),
               Submission.score.is_not(None))).all()
    possible = sum(p for _, p in rows)
    return round(100 * sum(s for s, _ in rows) / possible, 1) if possible else None


def pending_assignments(db: Session, user: User, course_ids: list[int]):
    if not course_ids:
        return []
    submitted = set(db.scalars(select(Submission.assignment_id).where(Submission.user_id == user.id)))
    asg = db.scalars(select(Assignment).where(
        Assignment.course_id.in_(course_ids), Assignment.due_at >= now())
        .order_by(Assignment.due_at)).all()
    return [a for a in asg if a.id not in submitted]


# ================================================================ API
app = FastAPI(title="EDUcate API")


@app.exception_handler(RequestValidationError)
async def validation_exception_handler(request: Request, exc: RequestValidationError):
    errors = exc.errors()
    msg = "Por favor revisa los datos ingresados."
    if errors:
        first = errors[0]
        loc = first.get("loc", [])
        field = loc[-1] if loc else ""
        if field == "email":
            msg = "El correo electrónico no es válido. Ingresa un correo correcto (ej. usuario@ejemplo.com)"
        elif field == "password":
            msg = "La contraseña debe tener al menos 8 caracteres"
        elif field == "name":
            msg = "El nombre debe tener al menos 2 caracteres"
        else:
            msg = first.get("msg", msg)
    return JSONResponse(status_code=422, content={"detail": msg})


# ---------------------------------------------------------------- cuenta
@app.post("/auth/register", status_code=201)
def register(body: RegisterIn, db: Session = Depends(get_db)):
    email = body.email.strip().lower()
    if db.scalar(select(User).where(User.email == email)):
        raise HTTPException(409, "Ese correo ya está registrado")
    role = body.role if body.role in ("student", "teacher") else "student"
    user = User(name=body.name.strip(), email=email, password_hash=hash_pw(body.password), role=role)
    db.add(user)
    db.commit()
    return {"token": make_token(user.id), "user": user_out(user)}


@app.post("/auth/login")
def login(body: LoginIn, db: Session = Depends(get_db)):
    user = db.scalar(select(User).where(User.email == body.email.strip().lower()))
    if not user or not check_pw(body.password, user.password_hash):
        raise HTTPException(401, "Correo o contraseña incorrectos")
    return {"token": make_token(user.id), "user": user_out(user)}


@app.get("/me")
def me(user: User = Depends(current_user)):
    return user_out(user)


class RoleUpdate(BaseModel):
    role: str = Field(pattern="^(student|teacher)$")


@app.put("/me/role")
def update_my_role(body: RoleUpdate, user: User = Depends(current_user), db: Session = Depends(get_db)):
    user.role = body.role
    db.commit()
    return user_out(user)


# ---------------------------------------------------------------- estudiante
@app.get("/home")
def home(user: User = Depends(current_user), db: Session = Depends(get_db)):
    ids = enrolled_course_ids(db, user.id)
    cont = None
    if ids:
        last = db.scalar(select(ItemProgress).where(ItemProgress.user_id == user.id)
                         .order_by(ItemProgress.completed_at.desc()).limit(1))
        course = None
        if last:
            c = db.get(Item, last.item_id).module.course
            course = c if c.id in ids else None
        if course is None:
            course = db.get(Course, db.scalar(select(Enrollment.course_id).where(
                Enrollment.user_id == user.id).order_by(Enrollment.created_at.desc()).limit(1)))
        p = progress_of(db, user, course)
        cont = {"course_id": course.id, "title": course.title,
                "teacher_name": course.teacher.name, "group_name": course.group_name, **p}
    ann = db.scalars(select(Announcement).where(Announcement.course_id.in_(ids))
                     .order_by(Announcement.created_at.desc()).limit(5)).all() if ids else []
    pend = pending_assignments(db, user, ids)
    return {
        "user": user_out(user),
        "stats": {"active_courses": len(ids), "pending": len(pend),
                  "average": overall_average(db, user, ids)},
        "continue_learning": cont,
        "announcements": [{"id": a.id, "title": a.title, "course_title": a.course.title,
                           "teacher_name": a.course.teacher.name, "created_at": iso(a.created_at)}
                          for a in ann],
        "upcoming": [{"assignment_id": a.id, "title": a.title, "course_title": a.course.title,
                      "due_at": iso(a.due_at), "kind": a.kind} for a in pend[:10]],
    }


@app.get("/courses/mine")
def my_courses(user: User = Depends(current_user), db: Session = Depends(get_db)):
    out = []
    for cid in enrolled_course_ids(db, user.id):
        c = db.get(Course, cid)
        out.append({"id": c.id, "title": c.title, "teacher_name": c.teacher.name,
                    "group_name": c.group_name, "period": c.period, **progress_of(db, user, c)})
    return out


@app.get("/catalog")
def catalog(q: str = "", category: str = "", user: User = Depends(current_user),
            db: Session = Depends(get_db)):
    stmt = select(Course).where(Course.open_enrollment.is_(True))
    if q.strip():
        stmt = stmt.where(Course.title.ilike(f"%{q.strip()}%"))
    if category.strip():
        stmt = stmt.where(Course.category == category.strip())
    mine = set(enrolled_course_ids(db, user.id))
    cats = [c for c in db.scalars(select(Course.category).where(
        Course.open_enrollment.is_(True)).distinct()) if c]
    return {"categories": sorted(cats), "courses": [
        {"id": c.id, "title": c.title, "description": c.description, "category": c.category,
         "level": c.level, "duration_weeks": c.duration_weeks, "is_free": c.is_free,
         "teacher_name": c.teacher.name, "image_url": c.image_url, "enrolled": c.id in mine}
        for c in db.scalars(stmt.order_by(Course.id.desc()))]}


@app.post("/courses/{course_id}/enroll", status_code=201)
def enroll(course_id: int, user: User = Depends(current_user), db: Session = Depends(get_db)):
    c = db.get(Course, course_id)
    if not c or not c.open_enrollment:
        raise HTTPException(404, "Curso no disponible para inscripción")
    if c.teacher_id == user.id:
        raise HTTPException(400, "Eres el docente de este curso")
    if not is_enrolled(db, user.id, course_id):
        db.add(Enrollment(user_id=user.id, course_id=course_id))
        db.commit()
    return {"enrolled": True, "course_id": course_id}


@app.get("/courses/{course_id}")
def get_course(course_id: int, user: User = Depends(current_user), db: Session = Depends(get_db)):
    return course_view(db, user, course_for(db, user, course_id))


@app.get("/courses/{course_id}/grades")
def get_grades(course_id: int, user: User = Depends(current_user), db: Session = Depends(get_db)):
    return grades_view(db, user, course_for(db, user, course_id))


@app.get("/items/{item_id}")
def get_item(item_id: int, user: User = Depends(current_user), db: Session = Depends(get_db)):
    return item_detail(db, user, item_for(db, user, item_id))


@app.post("/items/{item_id}/complete")
def complete_item(item_id: int, user: User = Depends(current_user), db: Session = Depends(get_db)):
    item = item_for(db, user, item_id)
    if not db.scalar(select(ItemProgress.id).where(
            ItemProgress.user_id == user.id, ItemProgress.item_id == item_id)):
        db.add(ItemProgress(user_id=user.id, item_id=item_id))
        db.commit()
    return item_detail(db, user, item)


@app.delete("/items/{item_id}/complete")
def uncomplete_item(item_id: int, user: User = Depends(current_user), db: Session = Depends(get_db)):
    item = item_for(db, user, item_id)
    row = db.scalar(select(ItemProgress).where(
        ItemProgress.user_id == user.id, ItemProgress.item_id == item_id))
    if row:
        db.delete(row)
        db.commit()
    return item_detail(db, user, item)


@app.get("/items/{item_id}/posts")
def list_posts(item_id: int, user: User = Depends(current_user), db: Session = Depends(get_db)):
    item_for(db, user, item_id)
    posts = db.scalars(select(ForumPost).where(ForumPost.item_id == item_id)
                       .order_by(ForumPost.created_at)).all()
    return [{"id": p.id, "author": p.user.name, "initials": initials(p.user.name),
             "body": p.body, "created_at": iso(p.created_at)} for p in posts]


@app.post("/items/{item_id}/posts", status_code=201)
def create_post(item_id: int, body: PostIn, user: User = Depends(current_user),
                db: Session = Depends(get_db)):
    item = item_for(db, user, item_id)
    if item.type != "forum":
        raise HTTPException(400, "Este contenido no es un foro")
    db.add(ForumPost(item_id=item_id, user_id=user.id, body=body.body.strip()))
    db.commit()
    return {"ok": True}


@app.get("/assignments/{assignment_id}")
def get_assignment(assignment_id: int, user: User = Depends(current_user),
                   db: Session = Depends(get_db)):
    a = db.get(Assignment, assignment_id)
    if not a:
        raise HTTPException(404, "Actividad no encontrada")
    course_for(db, user, a.course_id)
    return assignment_detail(db, user, a)


@app.post("/assignments/{assignment_id}/submit")
async def submit_assignment(assignment_id: int, file: UploadFile = File(...),
                            user: User = Depends(current_user), db: Session = Depends(get_db)):
    a = db.get(Assignment, assignment_id)
    if not a:
        raise HTTPException(404, "Actividad no encontrada")
    if a.course.teacher_id == user.id or not is_enrolled(db, user.id, a.course_id):
        raise HTTPException(403, "No puedes entregar en este curso")
    if aware(a.due_at) < now():
        raise HTTPException(400, "La fecha límite ya pasó")
    ext = Path(file.filename or "").suffix.lower()
    if ext not in ALLOWED_EXT:
        raise HTTPException(400, "Formato no permitido (PDF, ZIP o DOCX)")
    prev = db.scalar(select(Submission).where(
        Submission.assignment_id == a.id, Submission.user_id == user.id))
    if prev and prev.score is not None:
        raise HTTPException(400, "La entrega ya fue evaluada")
    dest = UPLOAD_DIR / f"{uuid.uuid4().hex}{ext}"
    size = 0
    with dest.open("wb") as out:
        while chunk := await file.read(1024 * 1024):
            size += len(chunk)
            if size > MAX_UPLOAD:
                out.close()
                dest.unlink(missing_ok=True)
                raise HTTPException(400, "El archivo supera los 25 MB")
            out.write(chunk)
    if prev:
        Path(prev.path).unlink(missing_ok=True)
        prev.filename, prev.path, prev.size_bytes, prev.submitted_at = (
            file.filename, str(dest), size, now())
    else:
        db.add(Submission(assignment_id=a.id, user_id=user.id, filename=file.filename,
                          path=str(dest), size_bytes=size))
    db.commit()
    return assignment_detail(db, user, a)


@app.get("/submissions/{submission_id}/file")
def download_submission(submission_id: int, user: User = Depends(current_user),
                        db: Session = Depends(get_db)):
    s = db.get(Submission, submission_id)
    if not s:
        raise HTTPException(404, "Entrega no encontrada")
    if s.user_id != user.id and s.assignment.course.teacher_id != user.id:
        raise HTTPException(403, "No autorizado")
    return FileResponse(s.path, filename=s.filename)


@app.get("/agenda")
def agenda(year: int, month: int, user: User = Depends(current_user),
           db: Session = Depends(get_db)):
    if not 1 <= month <= 12:
        raise HTTPException(400, "Mes inválido")
    ids = enrolled_course_ids(db, user.id)
    if not ids:
        return []
    start = dt.datetime(year, month, 1, tzinfo=dt.timezone.utc) - dt.timedelta(days=1)
    end = (dt.datetime(year + (month == 12), month % 12 + 1, 1, tzinfo=dt.timezone.utc)
           + dt.timedelta(days=1))
    submitted = set(db.scalars(select(Submission.assignment_id).where(Submission.user_id == user.id)))
    asg = db.scalars(select(Assignment).where(
        Assignment.course_id.in_(ids), Assignment.due_at >= start, Assignment.due_at < end)
        .order_by(Assignment.due_at)).all()
    return [{"assignment_id": a.id, "title": a.title, "course_title": a.course.title,
             "kind": a.kind, "due_at": iso(a.due_at), "submitted": a.id in submitted} for a in asg]


# ---------------------------------------------------------------- docente
@app.post("/teacher/apply", status_code=201)
def teacher_apply(body: ApplyIn, user: User = Depends(current_user), db: Session = Depends(get_db)):
    if user.role == "teacher":
        raise HTTPException(400, "Ya eres docente")
    if db.scalar(select(TeacherApplication.id).where(
            TeacherApplication.user_id == user.id, TeacherApplication.status == "pendiente")):
        raise HTTPException(409, "Ya tienes una postulación pendiente")
    db.add(TeacherApplication(user_id=user.id, motivation=body.motivation))
    db.commit()
    return {"status": "pendiente"}


@app.get("/teacher/panel")
def teacher_panel(user: User = Depends(teacher_only), db: Session = Depends(get_db)):
    courses = db.scalars(select(Course).where(Course.teacher_id == user.id)
                         .order_by(Course.id)).all()
    ids = [c.id for c in courses]
    counts = dict(db.execute(select(Enrollment.course_id, func.count()).where(
        Enrollment.course_id.in_(ids)).group_by(Enrollment.course_id)).all()) if ids else {}
    pend = db.scalars(select(Submission).join(Assignment, Assignment.id == Submission.assignment_id)
                      .where(Assignment.course_id.in_(ids), Submission.score.is_(None))
                      .order_by(Submission.submitted_at.desc())).all() if ids else []
    return {
        "user": user_out(user), "courses_count": len(courses),
        "students_count": sum(counts.values()), "to_review_count": len(pend),
        "courses": [{"id": c.id, "title": c.title, "group_name": c.group_name,
                     "students": counts.get(c.id, 0), "modules": len(c.modules)} for c in courses],
        "to_review": [{"submission_id": s.id, "student_name": s.user.name,
                       "initials": initials(s.user.name), "assignment_title": s.assignment.title,
                       "points": s.assignment.points, "submitted_at": iso(s.submitted_at)}
                      for s in pend[:20]],
    }


@app.get("/teacher/courses")
def teacher_courses_list(user: User = Depends(teacher_only), db: Session = Depends(get_db)):
    courses = db.scalars(select(Course).where(Course.teacher_id == user.id).order_by(Course.id.desc())).all()
    ids = [c.id for c in courses]
    student_counts = dict(db.execute(select(Enrollment.course_id, func.count()).where(
        Enrollment.course_id.in_(ids)).group_by(Enrollment.course_id)).all()) if ids else {}
    asg_counts = dict(db.execute(select(Assignment.course_id, func.count()).where(
        Assignment.course_id.in_(ids)).group_by(Assignment.course_id)).all()) if ids else {}
    pend_counts = dict(db.execute(select(Assignment.course_id, func.count()).join(
        Submission, Submission.assignment_id == Assignment.id).where(
        Assignment.course_id.in_(ids), Submission.score.is_(None)).group_by(Assignment.course_id)).all()) if ids else {}
    return [{
        "id": c.id, "title": c.title, "description": c.description,
        "category": c.category, "level": c.level, "duration_weeks": c.duration_weeks,
        "is_free": c.is_free, "open_enrollment": c.open_enrollment,
        "group_name": c.group_name, "period": c.period, "image_url": c.image_url,
        "students_count": student_counts.get(c.id, 0),
        "modules_count": len(c.modules),
        "assignments_count": asg_counts.get(c.id, 0),
        "to_review_count": pend_counts.get(c.id, 0)
    } for c in courses]


@app.post("/teacher/courses", status_code=201)
def create_course(body: CourseIn, user: User = Depends(teacher_only), db: Session = Depends(get_db)):
    c = Course(teacher_id=user.id, **body.model_dump())
    db.add(c)
    db.commit()
    return {"id": c.id, "title": c.title}


@app.get("/teacher/courses/{course_id}")
def teacher_course_detail(course_id: int, user: User = Depends(teacher_only), db: Session = Depends(get_db)):
    c = own_course(db, user, course_id)
    modules = []
    for m in c.modules:
        items = []
        for i in m.items:
            items.append({
                "id": i.id, "position": i.position, "type": i.type, "title": i.title,
                "subtitle": i.subtitle, "description": i.description,
                "video_url": i.video_url, "duration_min": i.duration_min,
                "transcript": i.transcript,
                "resources": [{"id": r.id, "title": r.title, "url": r.url, "pages": r.pages, "size_kb": r.size_kb} for r in i.resources]
            })
        modules.append({
            "id": m.id, "position": m.position, "title": m.title,
            "available_from": iso(m.available_from), "items": items
        })

    assignments = []
    for a in c.assignments:
        subs = db.scalars(select(Submission).where(Submission.assignment_id == a.id)).all()
        graded = sum(1 for s in subs if s.score is not None)
        assignments.append({
            "id": a.id, "module_id": a.module_id, "title": a.title, "instructions": a.instructions,
            "kind": a.kind, "points": a.points, "due_at": iso(a.due_at),
            "submissions_count": len(subs), "graded_count": graded, "pending_count": len(subs) - graded
        })

    enrollments = db.scalars(select(Enrollment).where(Enrollment.course_id == c.id).order_by(Enrollment.created_at.desc())).all()
    students = []
    for en in enrollments:
        u = db.get(User, en.user_id)
        if u:
            students.append({
                "id": u.id, "name": u.name, "email": u.email, "initials": initials(u.name),
                "enrolled_at": iso(en.created_at)
            })

    announcements = db.scalars(select(Announcement).where(Announcement.course_id == c.id).order_by(Announcement.created_at.desc())).all()
    ann_list = [{"id": an.id, "title": an.title, "body": an.body, "created_at": iso(an.created_at)} for an in announcements]

    return {
        "id": c.id, "title": c.title, "description": c.description,
        "category": c.category, "level": c.level, "duration_weeks": c.duration_weeks,
        "is_free": c.is_free, "open_enrollment": c.open_enrollment,
        "group_name": c.group_name, "period": c.period, "image_url": c.image_url,
        "modules": modules,
        "assignments": assignments,
        "students": students,
        "announcements": ann_list
    }


@app.put("/teacher/courses/{course_id}")
def update_course(course_id: int, body: CourseIn, user: User = Depends(teacher_only), db: Session = Depends(get_db)):
    c = own_course(db, user, course_id)
    for k, v in body.model_dump().items():
        setattr(c, k, v)
    db.commit()
    return {"id": c.id, "title": c.title}


@app.delete("/teacher/courses/{course_id}")
def delete_course(course_id: int, user: User = Depends(teacher_only), db: Session = Depends(get_db)):
    c = own_course(db, user, course_id)
    db.delete(c)
    db.commit()
    return {"ok": True}


@app.post("/teacher/courses/{course_id}/modules", status_code=201)
def create_module(course_id: int, body: ModuleIn, user: User = Depends(teacher_only),
                  db: Session = Depends(get_db)):
    c = own_course(db, user, course_id)
    pos = body.position or ((db.scalar(select(func.max(Module.position)).where(
        Module.course_id == c.id)) or 0) + 1)
    m = Module(course_id=c.id, position=pos, title=body.title, available_from=body.available_from)
    db.add(m)
    db.commit()
    return {"id": m.id, "position": m.position, "title": m.title}


@app.delete("/teacher/modules/{module_id}")
def delete_module(module_id: int, user: User = Depends(teacher_only), db: Session = Depends(get_db)):
    m = db.get(Module, module_id)
    if not m:
        raise HTTPException(404, "Módulo no encontrado")
    own_course(db, user, m.course_id)
    db.delete(m)
    db.commit()
    return {"ok": True}


@app.post("/teacher/modules/{module_id}/items", status_code=201)
def create_item(module_id: int, body: ItemIn, user: User = Depends(teacher_only),
                db: Session = Depends(get_db)):
    m = db.get(Module, module_id)
    if not m:
        raise HTTPException(404, "Módulo no encontrado")
    own_course(db, user, m.course_id)
    pos = (db.scalar(select(func.max(Item.position)).where(Item.module_id == m.id)) or 0) + 1
    i = Item(module_id=m.id, position=pos, **body.model_dump())
    db.add(i)
    db.commit()
    return {"id": i.id}


@app.delete("/teacher/items/{item_id}")
def delete_item(item_id: int, user: User = Depends(teacher_only), db: Session = Depends(get_db)):
    i = db.get(Item, item_id)
    if not i:
        raise HTTPException(404, "Contenido no encontrado")
    own_course(db, user, i.module.course_id)
    db.delete(i)
    db.commit()
    return {"ok": True}


@app.post("/teacher/items/{item_id}/resources", status_code=201)
def add_resource(item_id: int, body: ResourceIn, user: User = Depends(teacher_only),
                 db: Session = Depends(get_db)):
    i = db.get(Item, item_id)
    if not i:
        raise HTTPException(404, "Contenido no encontrado")
    own_course(db, user, i.module.course_id)
    r = Resource(item_id=i.id, **body.model_dump())
    db.add(r)
    db.commit()
    return {"id": r.id}


@app.post("/teacher/courses/{course_id}/assignments", status_code=201)
def create_assignment(course_id: int, body: AssignmentIn, user: User = Depends(teacher_only),
                      db: Session = Depends(get_db)):
    c = own_course(db, user, course_id)
    if body.module_id and not db.scalar(select(Module.id).where(
            Module.id == body.module_id, Module.course_id == c.id)):
        raise HTTPException(400, "El módulo no pertenece a este curso")
    a = Assignment(course_id=c.id, **body.model_dump())
    db.add(a)
    db.commit()
    return {"id": a.id}


@app.delete("/teacher/assignments/{assignment_id}")
def delete_assignment(assignment_id: int, user: User = Depends(teacher_only), db: Session = Depends(get_db)):
    a = db.get(Assignment, assignment_id)
    if not a:
        raise HTTPException(404, "Actividad no encontrada")
    own_course(db, user, a.course_id)
    db.delete(a)
    db.commit()
    return {"ok": True}


@app.get("/teacher/assignments/{assignment_id}/submissions")
def get_assignment_submissions(assignment_id: int, user: User = Depends(teacher_only), db: Session = Depends(get_db)):
    a = db.get(Assignment, assignment_id)
    if not a:
        raise HTTPException(404, "Actividad no encontrada")
    own_course(db, user, a.course_id)
    subs = db.scalars(select(Submission).where(Submission.assignment_id == a.id).order_by(Submission.submitted_at.desc())).all()
    enrolled_users = db.scalars(select(User).join(Enrollment, Enrollment.user_id == User.id).where(Enrollment.course_id == a.course_id)).all()
    submitted_user_ids = {s.user_id for s in subs}
    pending_students = [
        {"id": u.id, "name": u.name, "email": u.email, "initials": initials(u.name)}
        for u in enrolled_users if u.id not in submitted_user_ids
    ]
    return {
        "assignment": {
            "id": a.id, "course_id": a.course_id, "course_title": a.course.title,
            "title": a.title, "instructions": a.instructions, "kind": a.kind,
            "points": a.points, "due_at": iso(a.due_at)
        },
        "submissions": [{
            "id": s.id, "user_id": s.user_id, "student_name": s.user.name,
            "student_email": s.user.email, "initials": initials(s.user.name),
            "filename": s.filename, "size_bytes": s.size_bytes, "submitted_at": iso(s.submitted_at),
            "score": s.score, "feedback": s.feedback, "graded_at": iso(s.graded_at)
        } for s in subs],
        "pending_students": pending_students
    }


@app.post("/teacher/courses/{course_id}/announcements", status_code=201)
def create_announcement(course_id: int, body: AnnouncementIn, user: User = Depends(teacher_only),
                        db: Session = Depends(get_db)):
    c = own_course(db, user, course_id)
    db.add(Announcement(course_id=c.id, title=body.title, body=body.body))
    db.commit()
    return {"ok": True}


@app.delete("/teacher/announcements/{announcement_id}")
def delete_announcement(announcement_id: int, user: User = Depends(teacher_only), db: Session = Depends(get_db)):
    an = db.get(Announcement, announcement_id)
    if not an:
        raise HTTPException(404, "Aviso no encontrado")
    own_course(db, user, an.course_id)
    db.delete(an)
    db.commit()
    return {"ok": True}


@app.put("/teacher/submissions/{submission_id}/grade")
def grade_submission(submission_id: int, body: GradeIn, user: User = Depends(teacher_only),
                     db: Session = Depends(get_db)):
    s = db.get(Submission, submission_id)
    if not s:
        raise HTTPException(404, "Entrega no encontrada")
    own_course(db, user, s.assignment.course_id)
    # If rubric_scores provided, sum them to get the score
    if body.rubric_scores is not None:
        computed = float(sum(body.rubric_scores))
        if computed > s.assignment.points:
            raise HTTPException(400, f"La calificación máxima es {s.assignment.points}")
        s.score = computed
    else:
        if body.score > s.assignment.points:
            raise HTTPException(400, f"La calificación máxima es {s.assignment.points}")
        s.score = body.score
    s.feedback, s.graded_at = body.feedback, now()
    db.commit()
    return {"ok": True, "score": s.score}


# ---------------------------------------------------------------- donaciones
@app.post("/donations", status_code=201)
def donate(body: DonationIn, user: Optional[User] = Depends(optional_user),
           db: Session = Depends(get_db)):
    """Registra la intención de donación. El cobro real (Stripe/PayPal) se conecta
    aquí cuando haya credenciales; hasta entonces el estado queda 'pendiente_pago'."""
    d = Donation(user_id=user.id if user else None, amount=body.amount, email=body.email.lower())
    db.add(d)
    db.commit()
    return {"id": d.id, "amount": d.amount, "currency": d.currency, "status": d.status}


# ---------------------------------------------------------------- administración
@app.get("/admin/teacher-applications", dependencies=[Depends(admin_only)])
def list_applications(db: Session = Depends(get_db)):
    apps = db.scalars(select(TeacherApplication).where(
        TeacherApplication.status == "pendiente")).all()
    return [{"id": a.id, "name": a.user.name, "email": a.user.email,
             "motivation": a.motivation, "created_at": iso(a.created_at)} for a in apps]


# ---------------------------------------------------------------- health check
@app.get("/health")
def health():
    return {"status": "ok"}


@app.post("/admin/teacher-applications/{app_id}/approve", dependencies=[Depends(admin_only)])
def approve_application(app_id: int, db: Session = Depends(get_db)):
    a = db.get(TeacherApplication, app_id)
    if not a:
        raise HTTPException(404, "Postulación no encontrada")
    a.status, a.user.role = "aprobada", "teacher"
    db.commit()
    return {"ok": True}


import json

# ================================================================ CAMBIO 1 — Quiz/Cuestionarios automáticos

@app.post("/teacher/assignments/{assignment_id}/questions")
def set_quiz_questions(assignment_id: int, questions: list[QuizQuestionIn],
                       user: User = Depends(teacher_only), db: Session = Depends(get_db)):
    a = db.get(Assignment, assignment_id)
    if not a:
        raise HTTPException(404, "Actividad no encontrada")
    own_course(db, user, a.course_id)
    a.quiz_questions = json.dumps([q.model_dump() for q in questions], ensure_ascii=False)
    db.commit()
    return {"ok": True, "questions_count": len(questions)}


@app.get("/assignments/{assignment_id}/quiz")
def get_quiz(assignment_id: int, user: User = Depends(current_user), db: Session = Depends(get_db)):
    a = db.get(Assignment, assignment_id)
    if not a:
        raise HTTPException(404, "Actividad no encontrada")
    course_for(db, user, a.course_id)
    if not a.quiz_questions:
        raise HTTPException(404, "Esta actividad no tiene preguntas de quiz")
    questions = json.loads(a.quiz_questions)
    # Return questions without correct_index
    return [
        {"index": idx, "question": q["question"], "options": q["options"]}
        for idx, q in enumerate(questions)
    ]


@app.post("/assignments/{assignment_id}/quiz/submit")
def submit_quiz(assignment_id: int, body: QuizSubmitIn, user: User = Depends(current_user),
                db: Session = Depends(get_db)):
    a = db.get(Assignment, assignment_id)
    if not a:
        raise HTTPException(404, "Actividad no encontrada")
    course_for(db, user, a.course_id)
    if a.course.teacher_id == user.id:
        raise HTTPException(403, "El docente no puede entregar quiz")
    if not a.quiz_questions:
        raise HTTPException(400, "Esta actividad no tiene preguntas de quiz")
    questions = json.loads(a.quiz_questions)
    if len(body.answers) != len(questions):
        raise HTTPException(400, f"Se esperaban {len(questions)} respuestas, se recibieron {len(body.answers)}")
    correct = sum(1 for i, q in enumerate(questions) if body.answers[i] == q["correct_index"])
    total = len(questions)
    score = round(a.points * correct / total, 2) if total else 0.0
    percent = round(100 * correct / total, 1) if total else 0.0

    # Update or create submission
    sub = db.scalar(select(Submission).where(
        Submission.assignment_id == a.id, Submission.user_id == user.id))
    if sub:
        sub.score, sub.graded_at = score, now()
    else:
        sub = Submission(
            assignment_id=a.id, user_id=user.id,
            filename="quiz_automatico", path="", size_bytes=0,
            score=score, graded_at=now()
        )
        db.add(sub)
    db.commit()
    return {"score": score, "total": total, "correct": correct, "percent": percent}


# ================================================================ CAMBIO 2 — Duplicar cursos

@app.post("/teacher/courses/{course_id}/duplicate", status_code=201)
def duplicate_course(course_id: int, body: DuplicateCourseIn, user: User = Depends(teacher_only),
                     db: Session = Depends(get_db)):
    src = own_course(db, user, course_id)
    new_course = Course(
        teacher_id=user.id,
        title=src.title,
        description=src.description,
        category=src.category,
        level=src.level,
        duration_weeks=src.duration_weeks,
        is_free=src.is_free,
        open_enrollment=src.open_enrollment,
        image_url=src.image_url,
        group_name=body.group_name,
        period=body.period,
    )
    db.add(new_course)
    db.flush()  # get new_course.id

    for m in src.modules:
        new_mod = Module(
            course_id=new_course.id,
            position=m.position,
            title=m.title,
            available_from=m.available_from,
        )
        db.add(new_mod)
        db.flush()
        for i in m.items:
            new_item = Item(
                module_id=new_mod.id,
                position=i.position,
                type=i.type,
                title=i.title,
                subtitle=i.subtitle,
                description=i.description,
                video_url=i.video_url,
                duration_min=i.duration_min,
                transcript=i.transcript,
            )
            db.add(new_item)
            db.flush()
            for r in i.resources:
                db.add(Resource(
                    item_id=new_item.id,
                    title=r.title,
                    url=r.url,
                    pages=r.pages,
                    size_kb=r.size_kb,
                ))

    db.commit()
    db.refresh(new_course)
    return {
        "id": new_course.id, "title": new_course.title,
        "group_name": new_course.group_name, "period": new_course.period,
        "modules_count": len(src.modules),
    }


# ================================================================ CAMBIO 3 — Analíticas del grupo

@app.get("/teacher/courses/{course_id}/analytics")
def course_analytics(course_id: int, user: User = Depends(teacher_only), db: Session = Depends(get_db)):
    c = own_course(db, user, course_id)
    enrollments = db.scalars(select(Enrollment).where(Enrollment.course_id == c.id)).all()
    total_students = len(enrollments)
    student_ids = [e.user_id for e in enrollments]

    # Module completion: student has completed all items in a module
    module_stats = []
    for m in c.modules:
        item_ids = [i.id for i in m.items]
        if not item_ids:
            completed_count = 0
        else:
            completed_count = 0
            for uid in student_ids:
                done = set(db.scalars(select(ItemProgress.item_id).where(
                    ItemProgress.user_id == uid, ItemProgress.item_id.in_(item_ids))))
                if set(item_ids) <= done:
                    completed_count += 1
        percent = round(100 * completed_count / total_students, 1) if total_students else 0.0
        module_stats.append({
            "module_id": m.id, "title": m.title, "position": m.position,
            "completed_count": completed_count, "percent": percent,
        })

    # At-risk: no ItemProgress in the last 7 days
    cutoff = now() - dt.timedelta(days=7)
    at_risk = []
    for uid in student_ids:
        u = db.get(User, uid)
        if not u:
            continue
        last_prog = db.scalar(
            select(ItemProgress).where(ItemProgress.user_id == uid)
            .order_by(ItemProgress.completed_at.desc()).limit(1))
        if last_prog is None or aware(last_prog.completed_at) < cutoff:
            last_activity = iso(last_prog.completed_at) if last_prog else None
            at_risk.append({
                "user_id": uid, "name": u.name, "email": u.email,
                "last_activity": last_activity,
            })

    # Assignment stats
    assignment_stats = []
    for a in c.assignments:
        subs = db.scalars(select(Submission).where(Submission.assignment_id == a.id)).all()
        submitted_count = len(subs)
        graded = [s for s in subs if s.score is not None]
        graded_count = len(graded)
        avg_score = round(sum(s.score for s in graded) / graded_count, 2) if graded_count else None
        assignment_stats.append({
            "assignment_id": a.id, "title": a.title,
            "submitted_count": submitted_count,
            "graded_count": graded_count,
            "avg_score": avg_score,
        })

    return {
        "total_students": total_students,
        "modules": module_stats,
        "at_risk": at_risk,
        "assignment_stats": assignment_stats,
    }


# ================================================================ CAMBIO 4 — Calificación por rúbrica

@app.post("/teacher/assignments/{assignment_id}/rubric")
def set_rubric(assignment_id: int, criteria: list[RubricCriterionIn],
               user: User = Depends(teacher_only), db: Session = Depends(get_db)):
    a = db.get(Assignment, assignment_id)
    if not a:
        raise HTTPException(404, "Actividad no encontrada")
    own_course(db, user, a.course_id)
    a.rubric = json.dumps([c.model_dump() for c in criteria], ensure_ascii=False)
    db.commit()
    return {"ok": True, "criteria_count": len(criteria)}


@app.get("/assignments/{assignment_id}/rubric")
def get_rubric(assignment_id: int, user: User = Depends(current_user), db: Session = Depends(get_db)):
    a = db.get(Assignment, assignment_id)
    if not a:
        raise HTTPException(404, "Actividad no encontrada")
    course_for(db, user, a.course_id)
    if not a.rubric:
        raise HTTPException(404, "Esta actividad no tiene rúbrica")
    return json.loads(a.rubric)


# ================================================================ CAMBIO 5 — publish_at: filter assignments by publish_at in student view
# The assignment_detail helper already used in /assignments/{id} needs to hide
# assignments whose publish_at is in the future. We patch the student-facing
# assignment endpoint and the course_view helper.

_original_assignment_detail = assignment_detail


def assignment_detail(db: Session, user: User, a: Assignment) -> dict:
    # Hide unpublished assignments from students
    if user.role == "student" and a.publish_at is not None and aware(a.publish_at) > now():
        raise HTTPException(404, "Actividad no disponible aún")
    return _original_assignment_detail(db, user, a)
