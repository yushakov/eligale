# Eliza Gallery — CLAUDE.md

## Что за проект
Маркетплейс/галерея `eliza.gallery`. Монорепо: Django-бэкенд + Android-приложение.
Владелец: Yury Ushakov. Общение в сессиях — на русском.

## Стек
- **Backend**: Django 4.2 + SQLite + gunicorn + nginx, сервер на Digital Ocean
- **Storage**: Yandex Object Storage (S3-compatible, boto3)
  - Публичный бакет `gallery-media-public` — изображения каталога
  - Приватный бакет `gallery-media-private` (KMS-шифрование) — зарезервирован под V3
- **API**: Django REST Framework
- **Android**: Kotlin + Jetpack Compose + Retrofit + Coil + Navigation Compose
- **Пакет Android**: `gallery.eliza.app`

## Структура репозитория
```
eliza_backend/   — настройки Django (settings.py, urls.py)
catalog/         — основное приложение: модели, API, мобильные views
  models.py      — Category, Product, ProductImage
  views.py       — REST API + мобильные views (/mobile/...)
  urls.py        — все маршруты каталога
  admin.py       — Django admin с кастомным виджетом загрузки фото
  widgets.py     — ImageUploadWidget (presign → S3 прямо из admin)
main/            — главная страница
uploader/        — тестовые страницы загрузки (публичный и приватный бакет)
templates/
  mobile/        — мобильный интерфейс для добавления контента с телефона
static_src/      — исходники JS/CSS (собираются в static/)
android/         — Android-приложение
```

## Модели
```python
Category:  name, cover_key, is_hidden, created_at
Product:   categories (M2M), name, description, cover_key, is_hidden, created_at
ProductImage: product (FK), image_key, order, created_at
```
- `cover_key` и `image_key` — ключи объектов в Yandex Storage (не полные URL)
- Полный URL строится как `YA_PUBLIC_UPLOADER_PUBLIC_BASE_URL / key`
- `is_hidden=True` — объект скрыт из API и Android-приложения, но виден в мобильном admin-интерфейсе

## Загрузка изображений
Схема: браузер → presign-эндпоинт Django → PUT прямо в Yandex Storage (минуя сервер).
- Presign-эндпоинт: `POST /api/catalog/upload/presign` (только staff)
- В ответе: `upload_url`, `object_key`, `public_url`, `content_type`
- JS-функция `uploadImage()` в `templates/mobile/base.html`
- JS-функция `catalogUpload()` в `static_src/catalog/image_upload.js` (для Django admin)

## Мобильный интерфейс (`/mobile/`)
Защищён `@staff_member_required`. Нужно войти через `/admin/` первым.
- `/mobile/` — список категорий
- `/mobile/categories/add/` — добавить категорию
- `/mobile/categories/<pk>/` — товары категории
- `/mobile/categories/<int:category_id>/products/add/` — добавить товар
- `/mobile/products/<pk>/` — карточка товара с галереей
- `/mobile/products/<pk>/images/add/` — AJAX добавление фото (JSON POST)
- `/mobile/categories/<pk>/toggle-hidden/` — скрыть/показать категорию
- `/mobile/products/<pk>/toggle-hidden/` — скрыть/показать товар

## REST API
- `GET /api/categories/` — список категорий (скрытые не включаются)
- `GET /api/categories/<id>/products/` — товары категории (скрытые не включаются)
- `GET /api/products/<id>/` — детальная карточка товара

## Переменные окружения (.env, не в git)
```
DJANGO_SECRET_KEY
DJANGO_DEBUG             # "true" только для локальной разработки
YA_PUBLIC_UPLOADER_ENDPOINT_URL
YA_PUBLIC_UPLOADER_REGION_NAME
YA_PUBLIC_UPLOADER_ACCESS_KEY_ID
YA_PUBLIC_UPLOADER_SECRET_ACCESS_KEY
YA_PUBLIC_UPLOADER_BUCKET_NAME
YA_PUBLIC_UPLOADER_PUBLIC_BASE_URL
YA_PRIVATE_UPLOADER_REGION_NAME
YA_PRIVATE_UPLOADER_ACCESS_KEY_ID
YA_PRIVATE_UPLOADER_SECRET_ACCESS_KEY
YA_PRIVATE_UPLOADER_BUCKET_NAME
```

## Деплой
```bash
./deploy.sh
```
Скрипт выполняет на сервере: `git pull` → `pip install -r requirements.txt` → `migrate` → `collectstatic` → `systemctl restart gunicorn`

Локальный запуск:
```bash
python manage.py runserver
# или для доступа с телефона:
python manage.py runserver 0.0.0.0:8000
```

## Важные нюансы
- Приватный бакет использует KMS-шифрование — сервисный аккаунт должен иметь роль `kms.keys.encrypterDecrypter` на ключе
- CORS в Yandex Storage настроен только на `https://eliza.gallery` — локальная разработка с загрузкой файлов работает только если добавить `http://127.0.0.1:8000` в CORS (но не коммитить это в prod)
- `DEBUG=False` на сервере — статику раздаёт nginx, не Django
- При добавлении новых статических файлов нужен `collectstatic`

## Роадмап
- **V1** (текущий): каталог с изображениями, мобильный интерфейс управления
- **V2**: комментарии, регистрация пользователей
- **V3**: приватные чаты (голос/медиа через приватный бакет)
