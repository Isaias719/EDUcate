"""
Script para migrar los datos locales de SQLite (educacion.db) a Supabase (PostgreSQL).
Uso:
    python migrate_to_supabase.py "postgresql://postgres:[TU-PASSWORD]@db.[PROJECT-REF].supabase.co:5432/postgres"
"""
import sys
import os
from sqlalchemy import create_engine, select
from sqlalchemy.orm import sessionmaker

# Importar modelos del backend
from main import (
    Base, User, Course, Module, Item, Resource,
    Assignment, Enrollment, ItemProgress, Submission,
    Announcement, ForumPost, Donation, TeacherApplication
)

def migrate(supabase_url: str):
    if supabase_url.startswith("postgres://"):
        supabase_url = supabase_url.replace("postgres://", "postgresql+psycopg2://", 1)
    elif supabase_url.startswith("postgresql://") and not supabase_url.startswith("postgresql+"):
        supabase_url = supabase_url.replace("postgresql://", "postgresql+psycopg2://", 1)

    print(">> Conectando a SQLite local...")
    sqlite_engine = create_engine("sqlite:///./educacion.db")
    SqliteSession = sessionmaker(bind=sqlite_engine)
    sqlite_session = SqliteSession()

    print(">> Conectando a Supabase PostgreSQL...")
    pg_engine = create_engine(supabase_url)
    PgSession = sessionmaker(bind=pg_engine)
    pg_session = PgSession()

    print(">> Creando tablas en Supabase si no existen...")
    Base.metadata.create_all(pg_engine)

    # Orden de migración respetando Foreign Keys
    models = [
        User,
        Course,
        Module,
        Item,
        Resource,
        Assignment,
        Enrollment,
        ItemProgress,
        Submission,
        Announcement,
        ForumPost,
        Donation,
        TeacherApplication
    ]

    for model in models:
        table_name = model.__tablename__
        records = sqlite_session.execute(select(model)).scalars().all()
        print(f"   Migrando {len(records)} registros de '{table_name}'...")
        for r in records:
            # Hacer merge para insertar o actualizar conservando IDs
            pg_session.merge(r)
        pg_session.commit()

    print("\n✅ ¡Migración completada con éxito!")
    print("Todos tus datos locales ahora están en Supabase.")

if __name__ == "__main__":
    if len(sys.argv) > 1:
        target_url = sys.argv[1].strip()
    else:
        target_url = os.getenv("SUPABASE_DATABASE_URL") or input("Ingresa la URI de conexión de Supabase (URI): ").strip()

    if not target_url:
        print("❌ Error: Se requiere la URL de Supabase.")
        sys.exit(1)

    migrate(target_url)
