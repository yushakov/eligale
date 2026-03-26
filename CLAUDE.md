# Eliza Gallery — CLAUDE.md

## Что за проект
Маркетплейс/галерея `eliza.gallery`. Монорепо: Django-бэкенд + Android-приложение.
Владелец: Yury Ushakov (живёт в Англии). Общение в сессиях — на русском.

## Стек
- **Backend**: Django 4.2 + SQLite + gunicorn + nginx, сервер на Digital Ocean
- **Storage**: Yandex Object Storage (S3-compatible, boto3)
  - Публичный бакет `gallery-media-public` — изображения каталога
  - Приватный бакет `gallery-media-private` (KMS-шифрование) — зарезервирован под V3
- **Email**: AWS SES (eu-west-2, London) через `django-anymail`
- **API**: Django REST Framework + Token Authentication
- **Android**: Kotlin + Jetpack Compose + Retrofit + Coil + Navigation Compose
- **Пакет Android**: `gallery.eliza.app`

## Структура репозитория
```
eliza_backend/   — настройки Django (settings.py, urls.py)
catalog/         — модели каталога, API, мобильные views
  models.py      — Category, Product, ProductImage, Comment
  views.py       — REST API + mobile views + mobile2 views
  urls.py        — все маршруты каталога
  admin.py       — Django admin
  widgets.py     — ImageUploadWidget (presign → S3 прямо из admin)
users/           — кастомная модель User, авторизация по email
  models.py      — User (email как username), EmailVerification
  views.py       — request_code, verify_code, set_name
  urls.py        — /api/auth/...
main/            — главная страница
uploader/        — тестовые страницы загрузки
templates/
  admin/         — кастомный base_site.html (ссылки на mobile/mobile2)
  mobile/        — мобильный интерфейс v1
  mobile2/       — мобильный интерфейс v2 (умное добавление товаров)
static_src/      — исходники JS/CSS
android/         — Android-приложение
fix_users_migration.py — одноразовый скрипт (уже применён на сервере)
```

## Модели
```python
Category:  name, cover_key, is_hidden, created_at
Product:   categories (M2M), name, description, cover_key, is_hidden, created_at
ProductImage: product (FK), image_key, order, created_at
Comment:   product (FK), user (FK), text, created_at

User:      email (unique, USERNAME_FIELD), display_name, is_active, is_staff, date_joined
           — кастомная модель, AUTH_USER_MODEL = 'users.User'
           — без пароля (set_unusable_password), вход через email-код
EmailVerification: email, code (6 цифр), created_at, is_used
```
- `cover_key` и `image_key` — ключи объектов в Yandex Storage (не полные URL)
- Полный URL строится как `YA_PUBLIC_UPLOADER_PUBLIC_BASE_URL / key`
- `is_hidden=True` — скрыт из API и Android, но виден в mobile/mobile2
- `display_name` — имя пользователя для комментариев; если занято, добавляется ` #xxxx`

## Загрузка изображений
Схема: браузер → presign-эндпоинт Django → PUT прямо в Yandex Storage.
- Presign-эндпоинт: `POST /api/catalog/upload/presign` (только staff)
- JS-функция `uploadImage()` в `templates/mobile/base.html`

## Мобильный интерфейс v1 (`/mobile/`)
Защищён `@staff_member_required`. Вход через `/admin/` первым.
- `/mobile/` — список категорий
- `/mobile/categories/add/` — добавить категорию
- `/mobile/categories/<pk>/` — товары категории
- `/mobile/categories/<pk>/products/add/` — добавить товар (с обложкой)
- `/mobile/products/<pk>/` — карточка товара с галереей + галочки категорий
- `/mobile/products/<pk>/images/add/` — AJAX добавление фото
- `/mobile/categories/<pk>/toggle-hidden/` — скрыть/показать
- `/mobile/products/<pk>/toggle-hidden/` — скрыть/показать
- `/mobile/products/<pk>/toggle-category/<cat_id>/` — назначить/снять категорию

## Мобильный интерфейс v2 (`/mobile2/`)
Оптимизирован для быстрого добавления контента с телефона (без обложки вручную).
- `/mobile2/` — список категорий
- `/mobile2/categories/add/` — добавить категорию
- `/mobile2/categories/<pk>/` — товары категории
- `/mobile2/categories/<pk>/products/add/` — добавить товар:
  - Поля: название, описание, галочка "Разбить на отдельные товары", мультизагрузка фото
  - Без галочки (или 1 фото): 1 товар, первая фото = обложка, остальные = галерея
  - С галочкой + несколько фото: N товаров с именами "название 1", "название 2"...
- `/mobile2/products/<pk>/` — карточка товара + галочки категорий

## REST API
- `GET /api/categories/` — список категорий (скрытые не включаются)
- `GET /api/categories/<id>/products/` — товары категории (скрытые не включаются)
- `GET /api/products/<id>/` — детальная карточка товара
- `GET /api/products/<id>/comments/` — комментарии (публичный)
- `POST /api/products/<id>/comments/` — добавить комментарий (требует токен)
- `POST /api/auth/request-code/` — отправить 6-значный код на email
- `POST /api/auth/verify-code/` — проверить код, вернуть token + has_name
- `POST /api/auth/set-name/` — сохранить display_name (требует токен)

## Email
- Локально (`DJANGO_DEBUG=true`): `console` backend — письма в терминал
- На сервере: AWS SES через anymail + код дублируется в gunicorn лог (logger.info)
- Код живёт 15 минут, одноразовый

## Android
- Экраны: CategoryScreen → ProductListScreen → ProductDetailScreen
- ProductDetailScreen: галерея (HorizontalPager), описание, комментарии, поле ввода
- AuthDialog: 3 шага — email → код → имя (шаг 3 только при первом входе)
- TokenStorage: токен в SharedPreferences

## Переменные окружения (.env, не в git)
```
DJANGO_SECRET_KEY
DJANGO_DEBUG             # "true" только локально
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
EMAIL_BACKEND            # на сервере: anymail.backends.amazon_ses.EmailBackend
AWS_SES_ACCESS_KEY_ID
AWS_SES_SECRET_ACCESS_KEY
AWS_SES_REGION           # eu-west-2
```

## Деплой
```bash
./deploy.sh
```
Скрипт: `git pull` → `pip install -r requirements.txt` → `migrate` → `collectstatic` → `systemctl restart gunicorn`

Локальный запуск:
```bash
python manage.py runserver
# для доступа с телефона:
python manage.py runserver 0.0.0.0:8000
```

## Важные нюансы
- `fix_users_migration.py` — уже применён на сервере, повторно не нужен
- AWS SES пока в Sandbox (eu-west-2) — pending production access
- Приватный бакет: KMS-шифрование, сервисный аккаунт нужна роль `kms.keys.encrypterDecrypter`
- CORS в Yandex Storage настроен только на `https://eliza.gallery`
- `DEBUG=False` на сервере — статику раздаёт nginx
- При добавлении новых статических файлов нужен `collectstatic`
- venv называется `.venv` (не `venv`)

## Роадмап
- **V1** ✅: каталог с изображениями, мобильный интерфейс управления
- **V2** ✅ (частично): комментарии, регистрация через email, display_name
- **V3**: приватные чаты (голос/медиа через приватный бакет)

## V2 — открытые вопросы
- Комментарии V2: только текст, или сразу лайки/ответы?
- AWS SES production access: запрос отправлен, статус "More information needed", ожидаем ответа

## Запланировано на будущее

### Шеринг товаров (App Links)
Пользователь копирует ссылку на товар и отправляет её. Если у получателя есть приложение — оно открывает товар напрямую. Если нет — браузер открывает веб-страницу товара.

Что нужно реализовать:
1. **Django**: веб-страница товара `GET /products/{id}/` — картинка, название, описание (fallback для браузера)
2. **Django**: файл `/.well-known/assetlinks.json` — верификация App Links для домена `eliza.gallery`
3. **Android**: `intent-filter` в `AndroidManifest.xml` для `https://eliza.gallery/products/{id}/` с `android:autoVerify="true"`
4. **Android**: в `MainActivity` перехватывать входящий `Intent` и навигировать на `ProductDetailScreen`
5. **Android**: кнопка "Поделиться" в `ProductDetailScreen`, шерит `https://eliza.gallery/products/{id}/`
