# Eliza Gallery — CLAUDE.md

## Что за проект
Маркетплейс/галерея `eliza.gallery`. Монорепо: Django-бэкенд + Android-приложение.
Владелец: Yury Ushakov (живёт в Англии). Общение в сессиях — на русском.

## Стек
- **Backend**: Django 4.2 + SQLite + gunicorn + nginx, сервер на Digital Ocean
- **Storage**: Yandex Object Storage (S3-compatible, boto3)
  - Публичный бакет `gallery-media-public` — изображения каталога
  - Приватный бакет `gallery-media-private` (KMS-шифрование) — зарезервирован под медиа в чате (V3)
- **Email**: Resend через `django-anymail` (домен `otp.eliza.gallery`, DNS в Cloudflare)
- **API**: Django REST Framework + Token Authentication
- **Android**: Kotlin + Jetpack Compose + Retrofit + Coil + Navigation Compose + Room (KSP 2.2.10-2.0.2)
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
  views.py       — request_code, verify_code, set_name, profile, delete_account
  urls.py        — /api/auth/...
chat/            — приватные чаты между пользователем и staff
  models.py      — Chat, ChatMessage
  views.py       — API чата (пользователь + staff)
  urls.py        — /api/chat/... и /api/chats/...
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

Chat:      user (OneToOne FK → User), created_at, last_message_at
           — один чат на пользователя
ChatMessage: chat (FK), sender (FK → User), text, is_read, created_at
           — is_read: прочитано получателем (staff читает от user, user читает от staff)
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
- `/mobile2/` — список категорий с счётчиком товаров и кнопкой "Удалить" (только у пустых)
- `/mobile2/categories/add/` — добавить категорию
- `/mobile2/categories/<pk>/` — товары категории (плитка 4 колонки, счётчик фото на плитке)
- `/mobile2/categories/<pk>/products/add/` — добавить товар:
  - Поля: название (авто-генерируется случайное), описание, галочка "Разбить на отдельные товары", мультизагрузка фото
  - Без галочки (или 1 фото): 1 товар, первая фото = обложка, все фото = галерея
  - С галочкой + несколько фото: N товаров с именами "название 1", "название 2"...
- `/mobile2/products/<pk>/` — карточка товара + галочки категорий + кнопка "Удалить товар"
- `/mobile2/categories/<pk>/delete/` — удалить категорию (POST, только пустые)
- `/mobile2/products/<pk>/delete/` — удалить товар (POST), редирект на категорию или главную
- Кнопка "назад" на странице товара ведёт на категорию напрямую (не `history.back()`)

## REST API
- `GET /api/categories/` — список категорий (скрытые не включаются); включает `product_count`
- `GET /api/categories/<id>/products/` — товары категории (скрытые не включаются); включает `image_count`
- `GET /api/products/<id>/` — детальная карточка товара
- `GET /api/products/<id>/comments/` — комментарии; включает `user_id` и `user_email` (для staff-навигации в чат)
- `POST /api/products/<id>/comments/` — добавить комментарий (требует токен)
- `POST /api/auth/request-code/` — отправить 6-значный код на email
- `POST /api/auth/verify-code/` — проверить код, вернуть token + has_name
- `POST /api/auth/set-name/` — сохранить display_name (требует токен)
- `GET /api/auth/profile/` — профиль пользователя; включает `is_staff`
- `DELETE /api/auth/delete-account/` — удалить аккаунт (staff защищены, 403)

### Chat API (пользователь)
- `GET /api/chat/` — инфо о чате (chat_id, last_message_id); создаёт чат если нет
- `GET /api/chat/messages/?after=<id>&before=<id>&limit=50` — сообщения
- `POST /api/chat/messages/send/` — отправить сообщение
- `POST /api/chat/mark-read/` — пометить прочитанными до `up_to_id`
- `GET /api/chat/unread/` — количество непрочитанных от staff

### Chat API (staff)
- `GET /api/chats/` — список всех чатов с `user_id`, `unread_count`, `last_message`, сортировка по `last_message_at`
- `GET /api/chats/unread/` — общее количество непрочитанных от всех пользователей
- `GET /api/chats/<user_id>/messages/?after=<id>&before=<id>&limit=50`
- `POST /api/chats/<user_id>/messages/send/`
- `POST /api/chats/<user_id>/mark-read/`

## Email
- Локально (`DJANGO_DEBUG=true`): `console` backend — письма в терминал
- На сервере: Resend через anymail (`anymail.backends.resend.EmailBackend`)
- Домен отправки: `noreply@otp.eliza.gallery` (DNS записи DKIM/SPF/DMARC/MX в Cloudflare)
- Код дублируется в gunicorn лог (logger.info) для диагностики
- Код живёт 15 минут, одноразовый

## Android
- Экраны: CategoryScreen → ProductListScreen → ProductDetailScreen
- Чат: CategoryScreen → ChatScreen (пользователь) или ChatListScreen → ChatScreen (staff)
- **CategoryScreen**: плитка 2 колонки, круглые обложки с мягкими краями (radial gradient), название + "товаров: N" в полупрозрачном прямоугольнике поверх нижней четверти круга. Кнопка "Чат" (пользователь) или "Чаты" (staff) с бейджем непрочитанных. Polling счётчика каждые 15 сек (пока CategoryScreen активен).
- **ProductListScreen**: сетка 4 колонки, квадратные фото, оверлей "N фото" снизу слева
- **ProductDetailScreen**: галерея (HorizontalPager), двойной тап → fullscreen с зумом и кнопкой "Скачать" (DownloadManager), описание, комментарии, поле ввода. Для staff: имена авторов комментариев — кликабельные ссылки в чат с этим пользователем.
- **ChatScreen**: универсальный (пользователь и staff). Polling новых сообщений каждые 5 сек пока экран открыт. Кнопка "Загрузить историю" вверху при пустой локальной БД. Сообщения хранятся в Room (локальная SQLite).
- **ChatListScreen**: только для staff. Список чатов с бейджами непрочитанных, сортировка по последнему сообщению. Polling каждые 15 сек.
- AuthDialog: 3 шага — email → код → имя (шаг 3 только при первом входе)
- TokenStorage: токен в SharedPreferences
- Room: `ChatDatabase` (ChatMessageEntity, ChatMessageDao) в `data/ChatDatabase.kt`
- Тема: белый фон, тёмно-коричневый текст (#3E2000), без dynamic color и тёмной темы
- Pull-to-refresh на всех экранах с аддитивным мёржем по ID (новые добавляются, удалённые игнорируются)
- При сетевой ошибке: экран с кнопкой "Переподключиться" вместо сырого текста

## Известные баги / TODO
- **Белый экран после сна (Samsung)**: при выходе из сна после таймаута экрана приложение показывает белый экран и не реагирует на касания. Требует расследования (возможно, проблема с `LaunchedEffect` polling или Compose recomposition после resume).
- **Нет фонового polling счётчика непрочитанных**: polling чата работает только пока `CategoryScreen` активен. Если пользователь находится на другом экране (например, смотрит товар), счётчик не обновляется. Нужен фоновый механизм (WorkManager или Service).

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
EMAIL_BACKEND            # на сервере: anymail.backends.resend.EmailBackend
RESENDCOM_API_KEY        # API ключ Resend
```

## Деплой
```bash
./deploy.sh
```
Скрипт: `git pull` → `pip install -r requirements.txt` → `migrate` → `collectstatic` → `systemctl restart gunicorn`

Локальный запуск:
```bash
source .venv/bin/activate
python manage.py runserver
# для доступа с телефона:
python manage.py runserver 0.0.0.0:8000
```

## Важные нюансы
- `fix_users_migration.py` — уже применён на сервере, повторно не нужен
- Приватный бакет: KMS-шифрование, сервисный аккаунт нужна роль `kms.keys.encrypterDecrypter`
- CORS в Yandex Storage настроен только на `https://eliza.gallery`
- `DEBUG=False` на сервере — статику раздаёт nginx
- При добавлении новых статических файлов нужен `collectstatic`
- venv называется `.venv` (не `venv`); всегда активировать: `source .venv/bin/activate`
- Chat URLs используют `user_id` (не `chat_id`) — важно при отладке

## Роадмап
- **V1** ✅: каталог с изображениями, мобильный интерфейс управления
- **V2** ✅: комментарии, регистрация через email, display_name, текстовый чат
- **V3**: медиа в чате (фото/голос через приватный бакет), шеринг товаров (App Links)

## Авторизация — важные нюансы
- Один токен на пользователя (`Token.objects.get_or_create`). Несколько устройств работают с одним токеном одновременно — друг друга не разлогинивают.
- Logout на бэкенде не реализован — кнопка "Выйти" только чистит токен локально на устройстве. Если добавить `DELETE /api/auth/logout/` с `token.delete()`, выход будет разлогинивать все устройства сразу.
- Staff-аккаунты защищены от удаления через API (403).

## Запланировано на будущее

### Фоновый polling непрочитанных
Сейчас счётчик непрочитанных сообщений обновляется только пока открыт `CategoryScreen`. Нужен механизм фонового обновления (WorkManager или foreground Service), чтобы бейдж обновлялся на любом экране.

### Шеринг товаров (App Links)
Пользователь копирует ссылку на товар и отправляет её. Если у получателя есть приложение — оно открывает товар напрямую. Если нет — браузер открывает веб-страницу товара.

Что нужно реализовать:
1. **Django**: веб-страница товара `GET /products/{id}/` — картинка, название, описание (fallback для браузера)
2. **Django**: файл `/.well-known/assetlinks.json` — верификация App Links для домена `eliza.gallery`
3. **Android**: `intent-filter` в `AndroidManifest.xml` для `https://eliza.gallery/products/{id}/` с `android:autoVerify="true"`
4. **Android**: в `MainActivity` перехватывать входящий `Intent` и навигировать на `ProductDetailScreen`
5. **Android**: кнопка "Поделиться" в `ProductDetailScreen`, шерит `https://eliza.gallery/products/{id}/`

### Медиа в чате (V3)
Фото и голосовые сообщения через приватный бакет Яндекса. Разметка в тексте: `[image:key]` / `[voice:key]`. Новые сообщения с медиа — загружаются автоматически; старые — по тапу "📷 Изображение" / "🎙 Голосовое".
