"""
Одноразовый скрипт для первого деплоя кастомной модели User.
Запускать на сервере ОДИН РАЗ перед deploy.sh:

  python fix_users_migration.py
"""
import os
import django

os.environ.setdefault('DJANGO_SETTINGS_MODULE', 'eliza_backend.settings')
django.setup()

from django.db import connection
from django.contrib.auth import get_user_model
from users.models import EmailVerification

with connection.cursor() as c:
    c.execute(
        "INSERT OR IGNORE INTO django_migrations (app, name, applied) "
        "VALUES ('users', '0001_initial', datetime('now'))"
    )

with connection.schema_editor() as se:
    try:
        se.create_model(get_user_model())
        print("✓ Таблица users_user создана")
    except Exception as e:
        print(f"users_user: {e}")
    try:
        se.create_model(EmailVerification)
        print("✓ Таблица users_emailverification создана")
    except Exception as e:
        print(f"users_emailverification: {e}")

print("Готово. Теперь запускай deploy.sh")
