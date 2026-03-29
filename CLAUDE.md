# Eliza Gallery — CLAUDE.md

## Что за проект
Маркетплейс/галерея `eliza.gallery`. Монорепо: Django-бэкенд + Android-приложение.
Владелец: Yury Ushakov (живёт в Англии). Общение в сессиях — на русском.

## Стек
- **Backend**: Django 4.2 + SQLite + gunicorn + nginx, сервер на Digital Ocean
- **Storage**: Yandex Object Storage (S3-compatible, boto3)
  - Публичный бакет `gallery-media-public` — изображения каталога + медиафайлы чата (папка `chat/`)
  - Приватный бакет `gallery-media-private` (KMS-шифрование) — зарезервирован под голосовые и др.
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
  views.py       — request_code, verify_code, set_name, profile, delete_account, logout
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
- `GET /api/search/?q=...` — поиск по комментариям и сообщениям чата (требует токен); сниппет ±80 символов вокруг совпадения; макс. 100 результатов, сортировка по дате; staff видит все чаты, пользователь — только свой
- `GET /api/comments/my/` — комментарии текущего пользователя, сортировка `-created_at`; включает `product_id`, `product_name`
- `POST /api/auth/request-code/` — отправить 6-значный код на email
- `POST /api/auth/verify-code/` — проверить код, вернуть token + has_name
- `POST /api/auth/set-name/` — сохранить display_name (требует токен)
- `GET /api/auth/profile/` — профиль пользователя; включает `is_staff`
- `POST /api/auth/logout/` — удалить токен из БД (разлогинивает все устройства)
- `DELETE /api/auth/delete-account/` — удалить аккаунт (staff защищены, 403)

### Chat API (пользователь)
- `GET /api/chat/` — инфо о чате (chat_id, last_message_id); создаёт чат если нет
- `GET /api/chat/messages/?after=<id>&before=<id>&limit=50` — сообщения
- `POST /api/chat/messages/send/` — отправить сообщение
- `POST /api/chat/mark-read/` — пометить прочитанными до `up_to_id`
- `GET /api/chat/unread/` — количество непрочитанных от staff
- `POST /api/chat/media/presign/` — presign для загрузки медиа в публичный бакет (папка `chat/`); доступен любому авторизованному пользователю и staff; возвращает `upload_url` и `public_url`

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
- **Навигация**: на всех экранах кроме CategoryScreen — кнопка домика (🏠) в TopAppBar, ведёт сразу на CategoryScreen (`popBackStack("categories", false)`).
- **CategoryScreen**: плитка 2 колонки, круглые обложки с мягкими краями (radial gradient), название + "товаров: N" в полупрозрачном прямоугольнике поверх нижней четверти круга. TopAppBar содержит кнопку "Меню" (dropdown): для staff — Чаты (с красной точкой), Комменты (с красной точкой), Поиск, Аккаунт; для обычного пользователя — Чат (с красной точкой), Комменты (свои), Поиск, Аккаунт; для незалогиненных — Войти. Красная точка на "Меню" если хоть что-то непрочитано. Polling счётчиков каждые 15 сек (пока CategoryScreen активен).
- **ProductListScreen**: сетка 4 колонки, квадратные фото, оверлей "N фото" снизу слева
- **ProductDetailScreen**: название товара над галереей (жирно, мелко). TopAppBar показывает название категории. Кнопка "В чат" на каждом фото галереи: для пользователя — диалог "Вас интересует данный продукт?" (Да/Нет, просто в чат), "Да" отправляет сообщение `"Интересует товар «name» (фото N)\n[product:id:page]"` и переходит в чат; для staff — диалог "В чат с этим товаром?" (Да/Нет), "Да" предзаполняет `[product:id:page]` в поле ввода выбранного чата. Двойной тап → fullscreen с зумом, кнопки "Скачать" и "Копировать ссылку" внизу (`navigationBarsPadding`). Для staff: имена авторов комментариев — кликабельные ссылки в чат. Кнопка "Назад" ведёт в категорию (не в чат).
- **ChatScreen**: универсальный (пользователь и staff). Polling новых сообщений каждые 5 сек. Кнопка "Загрузить историю" вверху (показывается если первый запрос вернул ≥50 сообщений); после загрузки всей истории — текст "Начало переписки". Сообщения в Room `chat_messages` с полем `chat_user_id` (0 = свой чат, >0 = staff смотрит чат юзера X — изоляция по пользователю). Поддержка медиа: кнопка 🖼 открывает галерею, фото сжимается в JPEG 85%, загружается PUT напрямую в Яндекс (presign), отправляется как `[image:url]`. Сообщения с `[image:url]` рендерятся как изображение (Coil), двойной тап → fullscreen. Сообщения с `[product:id:page]` рендерятся с тапабельной ссылкой "→ Открыть товар". URL в тексте (http/https) — кликабельные ссылки (открывают браузер). Долгое нажатие на пузырёк → меню "Скопировать" (копирует `msg.text` целиком). `initialText` предзаполняет поле ввода. `targetMessageId` — прокручивает к нужному сообщению после загрузки (используется при переходе из поиска). Поле ввода находится внутри `Column` в контент-области Scaffold (не в `bottomBar`), без ручного `imePadding` — Scaffold сам управляет клавиатурой через дефолтный `contentWindowInsets` (`safeDrawing`).
- **ChatListScreen**: только для staff. Список чатов с бейджами непрочитанных, сортировка по последнему сообщению. Polling каждые 15 сек.
- **MyCommentsScreen**: список своих комментариев (только для обычных пользователей). Сортировка от новых к старым. Каждый элемент — название товара + текст + дата, клик → ProductDetailScreen с прокруткой к комментарию. Долгое нажатие на комментарий в ProductDetailScreen → меню "Скопировать" (копирует `"Автор\nТекст"`).
- **SearchScreen**: поиск по комментариям и сообщениям чата (только для залогиненных). Поле ввода + кнопка "Искать" (BrownDark). Результаты — карточки со сниппетом (±80 символов вокруг совпадения), совпадение выделено жирным (`buildAnnotatedString`). Тип результата (коммент/чат), подзаголовок (товар·автор или email), дата. Клик по комментарию → ProductDetailScreen с прокруткой к комментарию; клик по сообщению → ChatScreen с прокруткой к сообщению. Макс. 100 результатов, сортировка по дате (новые сверху).
- AuthDialog: 3 шага — email → код → имя (шаг 3 только при первом входе)
- TokenStorage: токен в SharedPreferences
- Room: `ChatDatabase` (ChatMessageEntity, ChatMessageDao) в `data/ChatDatabase.kt`, версия 2, `fallbackToDestructiveMigration()`
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

## Тесты

### Backend (Django)
Запуск: `source .venv/bin/activate && python manage.py test catalog users chat`

| Файл | Классы | Что покрыто |
|---|---|---|
| `catalog/tests.py` | 15 классов | CategoryList, ProductList, ProductDetail, CommentList (включая `user_id`/`user_email`), MyComments, Search (изоляция user/staff), StaffComments CRUD, Mobile2 views, Presign |
| `users/tests.py` | 10 классов | RequestCode, VerifyCode, SetName, UniqueDisplayName, Profile (включая `is_staff`), Logout (удаление токена, последующий 401), DeleteAccount |
| `chat/tests.py` | 13 классов | ChatInfo, Messages (пагинация after/before), Send, MarkRead, Unread, StaffChatList/Messages/Send/MarkRead, StaffUnread, ChatMediaPresign |

### Android (Kotlin + Robolectric)
Запуск: `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" && ./gradlew :app:testDebugUnitTest`

| Файл | Что покрыто |
|---|---|
| `ui/screens/ProductGalleryTest.kt` | Счётчик страниц, кнопка "В чат", колбэк передаёт текущую страницу |
| `ui/screens/MessageBubbleTest.kt` | Обычный текст; `[product:id:page]` → кнопка + правильные id/page в колбэке; без колбэка кнопки нет; сырые теги не просачиваются в UI; текст рядом с тегом отображается |
| `data/ChatMessageEntityTest.kt` | `toModel()` / `toEntity()` маппинг всех полей, `chat_user_id`, round-trip |
| `data/TokenStorageTest.kt` | save/get/clear/overwrite |
| `data/ChatDaoTest.kt` | CRUD, изоляция по `chat_user_id`, порядок ASC, `minId`/`maxId`, `markReadUpTo` (только staff, только до upToId, без cross-chat эффектов), OnConflict.REPLACE |

**Нюанс Robolectric:** `DropdownMenu` рендерится в `Popup` — `assertIsDisplayed()` не работает (нода есть, но не "displayed"). Меню "Скопировать" не тестируется через Robolectric.

**Нюанс Search (SQLite + кириллица):** `icontains` в SQLite не чувствителен к регистру только для ASCII. Тестовые данные должны быть в том же регистре, что и поисковый запрос.

## Роадмап
- **V1** ✅: каталог с изображениями, мобильный интерфейс управления
- **V2** ✅: комментарии, регистрация через email, display_name, текстовый чат
- **V2.5** ✅: фото в чате (публичный бакет, папка `chat/`), кнопка "В чат" на товарах, fullscreen фото в чате, server-side logout
- **V2.6** ✅: копирование сообщений/комментариев (долгое нажатие), активные ссылки в чате, "Меню" dropdown, кнопка домой на всех экранах, "Копировать ссылку" в fullscreen, экран поиска, экран "Мои комментарии"
- **V3**: голосовые в чате (приватный бакет), шеринг товаров (App Links)

## Авторизация — важные нюансы
- Один токен на пользователя (`Token.objects.get_or_create`). Несколько устройств работают с одним токеном одновременно.
- `POST /api/auth/logout/` удаляет токен из БД — разлогинивает все устройства сразу. Android вызывает его перед очисткой локального токена.
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

### Голосовые сообщения в чате
Фото в чате реализованы (V2.5, публичный бакет папка `chat/`). Голосовые — ещё нет. Разметка: `[voice:key]`. Загрузка через приватный бакет. По тапу "🎙 Голосовое" загружать и воспроизводить.

### Навигационные маршруты Android (важно для расширения)
- `"categories"` — главный экран
- `"products/{categoryId}?name={name}"`
- `"product/{productId}?categoryId={categoryId}&categoryName={categoryName}&commentId={commentId}&imageIndex={imageIndex}"`
- `"chat?targetMessageId={targetMessageId}"` — чат пользователя, опциональный скролл к сообщению
- `"chats?pendingText={pendingText}"` — staff список чатов с опциональным предзаполненным текстом
- `"chat_staff/{userId}?email={email}&initialText={initialText}&targetMessageId={targetMessageId}"` — чат staff
- `"comments"` — список комментариев (staff)
- `"my_comments"` — мои комментарии (обычный пользователь)
- `"search"` — экран поиска (только залогиненные)
