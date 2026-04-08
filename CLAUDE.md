# Eliza Gallery — CLAUDE.md

## Что за проект
Маркетплейс/галерея `eliza.gallery`. Монорепо: Django-бэкенд + Android-приложение.
Владелец: Yury Ushakov (живёт в Англии). Общение в сессиях — на русском.

## Стек
- **Backend**: Django 4.2 + SQLite + gunicorn + nginx, сервер на Яндекс Cloud (2 vCPU, 2 GB RAM, 20 GB SSD)
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
  models.py      — Category, Product, ProductImage, Comment, CommentReport
  views.py       — REST API + mobile views + mobile2 views + report/delete endpoints
  urls.py        — все маршруты каталога
  admin.py       — Django admin
  widgets.py     — ImageUploadWidget (presign → S3 прямо из admin)
users/           — кастомная модель User, авторизация по email
  models.py      — User (email как username, consent_date), EmailVerification
  views.py       — request_code, verify_code, set_name, profile, delete_account, logout, record_consent
  urls.py        — /api/auth/...
chat/            — приватные чаты между пользователем и staff
  models.py      — Chat, ChatMessage
  views.py       — API чата (пользователь + staff)
  urls.py        — /api/chat/... и /api/chats/...
main/            — главная страница
uploader/        — тестовые страницы загрузки
templates/
  admin/         — кастомный base_site.html (ссылки на mobile/mobile2/all-products)
  mobile/        — мобильный интерфейс v1
  mobile2/       — мобильный интерфейс v2 (умное добавление товаров)
  consent_ru.html — объединённые Privacy+Terms на русском (для WebView при регистрации)
static_src/      — исходники JS/CSS
android/         — Android-приложение
fix_users_migration.py — одноразовый скрипт (уже применён на сервере)
```

## Модели
```python
Category:  name, cover_key, is_hidden, created_at
Product:   categories (M2M), name, description, cover_key, is_hidden, created_at
ProductImage: product (FK), image_key, order, is_hidden, created_at
Comment:   product (FK), user (FK), text, is_read_by_staff, created_at

User:      email (unique, USERNAME_FIELD), display_name, is_active, is_staff, date_joined,
           privacy_and_terms_of_use_consent_date (DateTimeField, null=True)
           — кастомная модель, AUTH_USER_MODEL = 'users.User'
           — без пароля (set_unusable_password), вход через email-код
EmailVerification: email, code (6 цифр), created_at, is_used

Chat:      user (OneToOne FK → User), created_at, last_message_at
           — один чат на пользователя
ChatMessage: chat (FK), sender (FK → User), text, is_read, created_at
           — is_read: прочитано получателем (staff читает от user, user читает от staff)

FavoriteImage: user (FK → User), image (FK → ProductImage), created_at
           — unique_together (user, image); ordering = ['-created_at']
           — избранное привязано к конкретной фотографии, не к товару

CommentReport: comment (FK → Comment), reporter (FK → User), text (max 150), is_read, created_at
           — unique_together (comment, reporter) — один репорт на пользователя на комментарий
           — is_read: помечается True при первом GET /api/staff/reports/
```
- `cover_key` и `image_key` — ключи объектов в Yandex Storage (не полные URL)
- Полный URL строится как `YA_PUBLIC_UPLOADER_PUBLIC_BASE_URL / key`
- `is_hidden=True` у Category/Product — скрыт из API и Android, но виден в mobile/mobile2
- `is_hidden=True` у ProductImage — скрыт из API (`ProductDetailSerializer` и `image_count` фильтруют), виден в mobile2
- `display_name` — имя пользователя для комментариев; если занято, добавляется ` #xxxx`

## Загрузка изображений
Схема: браузер → presign-эндпоинт Django → PUT прямо в Yandex Storage.
- Presign-эндпоинт: `POST /api/catalog/upload/presign` (только staff)
- JS-функция `uploadImage()` в `templates/mobile/base.html`

## Тайлы изображений
После загрузки оригинала автоматически генерируются уменьшенные версии через Pillow.

**Именование:** `catalog/abc.jpg` → `catalog/abc_100.jpg`, `_200`, `_300`, `_600`

**Размеры:** 100, 200, 300, 600 px — **наибольшая сторона** (Pillow `thumbnail()` сохраняет пропорции, не кропает), LANCZOS, JPEG 85%, EXIF-ориентация исправляется через `ImageOps.exif_transpose`

**Триггеры (фоновый поток, не блокирует ответ):**
- `ProductImage` создан → `post_save` → генерирует тайлы для `image_key`
- `Category.cover_key` изменился → `pre_save`/`post_save` → тайлы для `cover_key`
- `Product.cover_key` изменился → `pre_save`/`post_save` → тайлы для `cover_key`

**Android использует:**
| Место | Размер |
|---|---|
| CategoryScreen (обложка, круг) | `cover_url_600` → фолбэк `cover_url` |
| ProductListScreen (4-col плитка) | `cover_url_300` → фолбэк `cover_url` |
| ProductGallery (3-col сетка) | `image_url_300` → фолбэк `image_url` |
| FullscreenImageViewer | `image_url` (оригинал) |

**API поля:** `CategorySerializer` добавляет `cover_url_600`; `ProductListSerializer` — `cover_url_300`; `ProductImageSerializer` — `image_url_100/200/300`.

**Страховочная команда** (найти и сгенерировать пропущенные; использует наибольший размер как индикатор):
```bash
python manage.py generate_missing_thumbnails          # только пропущенные
python manage.py generate_missing_thumbnails --force  # пересоздать все
```

**Резервная копия БД:**
```bash
python manage.py backup_db
```
Gzip-сжимает `db.sqlite3`, загружает в приватный бакет (`YA_PRIVATE_UPLOADER_*`) с ключом `db-backup/db_YYYY-MM-DD_HH-MM-SS.sqlite3.gz`. Удаляет бэкапы старше 7 дней. Настроен в cron (`/etc/cron.d/eliza-backup`) на запуск каждый день в 03:00 через полный путь к python venv.

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
- `/mobile2/products/<pk>/` — карточка товара + галочки категорий + кнопка "Удалить товар" + массовое управление фото
  - Тап по фото — выбор; появляется sticky action bar с кнопками: Скрыть / Показать / Удалить / Вынести в новый товар
  - Удаление фото: удаляет из S3 оригинал + все 4 тайла; обновляет обложку товара если она была удалена/скрыта
  - "Вынести в новый товар": создаёт новый товар со случайным именем, первое фото = обложка; редирект на страницу нового товара для переименования
  - Скрытые фото (is_hidden=True): отображаются в mobile2 с полупрозрачным оверлеем; не попадают в API и Android
- `/mobile2/products/<pk>/images/action/` — POST эндпоинт для bulk-действий (hide/show/delete/move)
- `/mobile2/products/<pk>/edit/` — редактировать название и описание товара
- `/mobile2/categories/<pk>/edit/` — редактировать название категории
- `/mobile2/categories/<pk>/delete/` — удалить категорию (POST, только пустые)
- `/mobile2/products/<pk>/delete/` — удалить товар (POST), редирект на категорию или главную
- Кнопка "назад" на странице товара ведёт на категорию напрямую (не `history.back()`)
- Ссылки "Редактировать" в topbar на страницах товара и категории (через `{% block topbar_extra %}`)

## REST API
- `GET /api/categories/` — список категорий (скрытые не включаются); включает `product_count`
- `GET /api/categories/<id>/products/` — товары категории (скрытые не включаются); включает `image_count`
- `GET /api/products/<id>/` — детальная карточка товара
- `GET /api/products/<id>/comments/` — комментарии; включает `user_id` и `user_email` (для staff-навигации в чат)
- `POST /api/products/<id>/comments/` — добавить комментарий (требует токен)
- `POST /api/comments/<id>/report/` — пожаловаться на комментарий (требует токен; не staff; не свой; max 150 символов; один репорт на комментарий)
- `DELETE /api/comments/<id>/delete/` — удалить свой комментарий (требует токен; только свой — иначе 403)
- `GET /api/search/?q=...` — поиск по комментариям и сообщениям чата (требует токен); сниппет ±80 символов вокруг совпадения; макс. 100 результатов, сортировка по дате; staff видит все чаты, пользователь — только свой
- `GET /api/comments/my/` — комментарии текущего пользователя, сортировка `-created_at`; включает `product_id`, `product_name`
- `POST /api/auth/request-code/` — отправить 6-значный код на email
- `POST /api/auth/verify-code/` — проверить код, вернуть `token` + `has_name` + `has_consent`
- `POST /api/auth/set-name/` — сохранить display_name (требует токен)
- `POST /api/auth/record-consent/` — записать дату согласия с политикой и правилами (требует токен; идемпотентно)
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

### Favorites API
- `GET /api/favorites/` — список избранного (требует токен); поля: `image_id`, `product_id`, `product_name`, `image_url`, `image_url_100`, `created_at`
- `POST /api/favorites/` — добавить `{"image_id": N}`; 201 при создании, 200 если уже есть; скрытые товары → 404
- `DELETE /api/favorites/<image_id>/` — удалить; 204 в любом случае (идемпотентно)

### Chat API (staff)
- `GET /api/chats/` — список всех чатов с `user_id`, `unread_count`, `last_message`, сортировка по `last_message_at`
- `GET /api/chats/unread/` — общее количество непрочитанных от всех пользователей
- `GET /api/chats/<user_id>/messages/?after=<id>&before=<id>&limit=50`
- `POST /api/chats/<user_id>/messages/send/`
- `POST /api/chats/<user_id>/mark-read/`

### Staff Comment API
- `GET /api/staff/comments/` — последние 50 комментариев всех пользователей
- `GET /api/staff/comments/unread/` — счётчик непрочитанных комментариев
- `POST /api/staff/comments/<id>/mark-read/` — пометить комментарий прочитанным
- `DELETE /api/staff/comments/<id>/delete/` — удалить любой комментарий

### Staff Reports API (жалобы)
- `GET /api/staff/reports/` — список жалоб с полями `id`, `comment_id`, `comment_text`, `comment_author`, `reporter_email`, `text`, `is_read`, `created_at`; **при каждом запросе все непрочитанные автоматически помечаются прочитанными** (поэтому `is_read` в ответе отражает состояние до пометки)
- `GET /api/staff/reports/unread/` — счётчик непрочитанных жалоб (`{"unread": N}`)
- `POST /api/staff/reports/<id>/dismiss/` — отклонить жалобу (удалить жалобу, комментарий остаётся)
- `POST /api/staff/reports/<id>/delete-comment/` — удалить комментарий вместе с жалобой (каскадно)

## Email
- Локально (`DJANGO_DEBUG=true`): `console` backend — письма в терминал
- На сервере: Resend через anymail (`anymail.backends.resend.EmailBackend`)
- Домен отправки: `noreply@otp.eliza.gallery` (DNS записи DKIM/SPF/DMARC/MX в Cloudflare)
- Код дублируется в gunicorn лог (logger.info) для диагностики
- Код живёт 15 минут, одноразовый

## Статические страницы сайта
- `GET /` — главная (лендинг с тайлами); фиксированный футер с "Политика конфиденциальности" и "Правила использования" (ссылки на русские версии)
- `GET /privacy/` и `/privacy-ru/` — Политика конфиденциальности (EN/RU), с перекрёстными ссылками
- `GET /termsofuse/` и `/termsofuse-ru/` — Правила использования (EN/RU), с перекрёстными ссылками
- `GET /consent-ru/` — объединённая страница (оба документа подряд) для показа при регистрации в WebView; без внешних ссылок
- `GET /all-products` — все товары для staff: sticky топбар со счётчиком, карточка каждого товара (название, категории, бейдж "скрыт", ссылка "открыть в Admin →", описание, фото сеткой 3 колонки с естественными пропорциями). Ссылка в кастомном admin topbar.

## Android
- Экраны: CategoryScreen → ProductListScreen → ProductDetailScreen
- Чат: CategoryScreen → ChatScreen (пользователь) или ChatListScreen → ChatScreen (staff)
- **Навигация**: на всех экранах кроме CategoryScreen — кнопка домика (🏠) в TopAppBar, ведёт сразу на CategoryScreen (`popBackStack("categories", false)`).
- **CategoryScreen**: плитка 2 колонки, круглые обложки с мягкими краями (radial gradient), название + "товаров: N" в полупрозрачном прямоугольнике поверх нижней четверти круга. Обложки загружаются с `SubcomposeAsyncImage` — серый фон + 32dp спиннер пока грузится. TopAppBar содержит кнопку "Меню" (dropdown): для staff — Чаты, Комментарии, **Жалобы**, Избранное, Поиск, Аккаунт; для обычного пользователя — Чат, Комментарии (свои), Избранное, Поиск, Аккаунт; для незалогиненных — Войти. У каждого пункта — красная точка если есть непрочитанные. Красная точка на кнопке "Меню" если хоть что-то непрочитано. В самом низу меню — версия приложения (`BuildConfig.VERSION_NAME`, `labelSmall`). Polling счётчиков (чат/комменты/жалобы) каждые 15 сек (пока CategoryScreen активен). При смене токена загружает `DataCache.favoriteImageIds`.
- **ProductListScreen**: сетка 4 колонки, квадратные фото, оверлей "N фото" снизу слева. Обложки загружаются с `SubcomposeAsyncImage` — серый фон + 24dp спиннер пока грузится.
- **ProductDetailScreen**: название товара над галереей (жирно, мелко). TopAppBar показывает название категории и счётчик позиции `"X / N"` когда открыт из категории. Свайп влево/вправо по экрану — циклическая навигация между товарами категории (порог 80dp; отключается когда fullscreen открыт). Навигация: pop текущего экрана + navigate на новый (backstack не растёт). Галерея — сетка 3 колонки (квадратные тайлы, `ContentScale.Crop`), тап → fullscreen. Если переход из чата по `[product:id:page]` — fullscreen открывается автоматически на нужном фото. Кнопка "В чат" в fullscreen viewer (правый нижний угол, рядом со "Скачать" и "Ссылка"): для пользователя — диалог "Вас интересует данный продукт?" (Да/Нет, просто в чат), "Да" отправляет сообщение `"Интересует товар «name» (фото N)\n[product:id:page]"` и переходит в чат; для staff — диалог "В чат с этим товаром?" (Да/Нет), "Да" предзаполняет `[product:id:page]` в поле ввода выбранного чата. Для staff: имена авторов комментариев — кликабельные ссылки в чат. Кнопка "Назад" ведёт в категорию (не в чат).
- **ProductDetailScreen — сердечко на тайлах**: каждая фотография в галерее независимо добавляется в избранное. Иконка сердечка в левом нижнем углу тайла на полупрозрачном тёмном фоне. Оптимистичное обновление с откатом при сетевой ошибке. Для незалогиненных открывает AuthDialog.
- **ProductDetailScreen — меню комментария** (долгое нажатие → DropdownMenu):
  | Кто | Чей комментарий | Пункты меню |
  |---|---|---|
  | Незалогиненный | любой | Скопировать |
  | Обычный пользователь | чужой | Скопировать + **Пожаловаться** |
  | Обычный пользователь | свой | Скопировать + **Удалить** (с подтверждением) |
  | Staff | любой | Скопировать |
  "Свой" определяется сравнением `comment.user_email == currentUserEmail` (загружается из `getProfile` при старте). Staff удаляет чужие комментарии через экран Жалоб.
- **FullscreenImageViewer**: пинч-зум (до 8×), панорамирование при zoom > 1, одиночный тап — закрыть. Свайп влево/вправо — листать фото (циклически, `Int.MAX_VALUE` виртуальных страниц). Свайп отключается при zoom > 1 чтобы не конфликтовать с панорамированием. Снизу: иконка сердечка "Избранное" (всегда видна), "В чат" (всегда — для незалогиненных открывает AuthDialog), "Скачать", "Ссылка". Оригинал грузится через `SubcomposeAsyncImage` — белый `CircularProgressIndicator` по центру пока загружается.
- **FavoritesScreen**: список избранных фотографий (72px обложка + название товара). Тап на фото → fullscreen этой фотографии (закрытие fullscreen → обратно в список). Тап на название товара → ProductDetailScreen. Свайп влево → красный фон с иконкой корзины → диалог "Удалить из избранного?" (Удалить/Отмена). `SwipeToDismissBox` с `confirmValueChange = false`.
- **ReportsScreen**: только для staff. Список жалоб, сортировка от новых к старым. Непрочитанные — жирным. Тап → диалог с текстом комментария и текстом жалобы; кнопки: "Отклонить жалобу" (удалить жалобу, комментарий остаётся), "Удалить комментарий" (удалить и жалобу и комментарий), "Ещё подумать" (закрыть диалог, ничего не делать). Pull-to-refresh.
- **ChatScreen**: универсальный (пользователь и staff). Polling новых сообщений каждые 5 сек. Кнопка "Загрузить историю" вверху (показывается если первый запрос вернул ≥50 сообщений); после загрузки всей истории — текст "Начало переписки". Сообщения в Room `chat_messages` с полем `chat_user_id` (0 = свой чат, >0 = staff смотрит чат юзера X — изоляция по пользователю). Поддержка медиа: кнопка 🖼 открывает галерею, фото сжимается в JPEG 85%, загружается PUT напрямую в Яндекс (presign), отправляется как `[image:url]`. Сообщения с `[image:url]` рендерятся как изображение (Coil), двойной тап → fullscreen. Сообщения с `[product:id:page]` рендерятся с тапабельной ссылкой "→ Открыть товар". URL в тексте (http/https) — кликабельные ссылки (открывают браузер). Долгое нажатие на пузырёк → меню "Скопировать" (копирует `msg.text` целиком). `initialText` предзаполняет поле ввода. `targetMessageId` — прокручивает к нужному сообщению после загрузки (используется при переходе из поиска). Layout: Scaffold с `contentWindowInsets = WindowInsets(0)` (инсеты не делегируем Scaffold); контент — `Box`. `LazyColumn` занимает весь Box (`fillMaxSize()`), `contentPadding(bottom=80.dp)` освобождает место под поле ввода. Поле ввода — плавающий `Column` с `Alignment.BottomCenter` и `windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))`: смещается вверх при открытии клавиатуры, учитывает навигационную панель (Samsung и др.) когда клавиатура скрыта.
- **ChatListScreen**: только для staff. Список чатов с бейджами непрочитанных, сортировка по последнему сообщению. Polling каждые 15 сек.
- **MyCommentsScreen**: список своих комментариев (только для обычных пользователей). Сортировка от новых к старым. Каждый элемент — название товара + текст + дата, клик → ProductDetailScreen с прокруткой к комментарию.
- **SearchScreen**: поиск по комментариям и сообщениям чата (только для залогиненных). Поле ввода + кнопка "Искать" (BrownDark). Результаты — карточки со сниппетом (±80 символов вокруг совпадения), совпадение выделено жирным (`buildAnnotatedString`). Тип результата (коммент/чат), подзаголовок (товар·автор или email), дата. Клик по комментарию → ProductDetailScreen с прокруткой к комментарию; клик по сообщению → ChatScreen с прокруткой к сообщению. Макс. 100 результатов, сортировка по дате (новые сверху).
- **AccountDialog**: диалог "Аккаунт" (из меню). Вверху — две ссылки: "Конфиденциальность" и "Правила". При нажатии открывается `LegalPageDialog` (WebView, 95% ширины и 85% высоты), загружает `/privacy-ru/` или `/termsofuse-ru/`; навигация разрешена только между страницами `/privacy*` и `/termsofuse*`; внешние ссылки заблокированы; внизу кнопка "Закрыть".
- **AuthDialog → ConsentDialog**: после успешной проверки кода (`verify-code`) проверяется `has_consent`. Если `false` — показывается `ConsentDialog` (WebView с `/consent-ru/`, все переходы заблокированы). Диалог содержит чекбокс "Соглашаюсь" и кнопку "Подтвердить" (вызывает `POST /api/auth/record-consent/`, затем продолжает flow: шаг имени или завершение). Показывается только один раз — при первой регистрации. Повторно авторизованным пользователям (у которых уже есть `consent_date`) диалог не показывается.
- TokenStorage: токен в SharedPreferences
- `currentUserEmail`: загружается в `MainActivity` через `getProfile` при входе/старте, передаётся в `ProductDetailScreen` для определения собственных комментариев
- Room: `ChatDatabase` (ChatMessageEntity, ChatMessageDao) в `data/ChatDatabase.kt`, версия 2, `fallbackToDestructiveMigration()`
- Тема: белый фон, тёмно-коричневый текст (#3E2000), без dynamic color и тёмной темы
- Pull-to-refresh на всех экранах с аддитивным мёржем по ID (новые добавляются, удалённые игнорируются)
- При сетевой ошибке: экран с кнопкой "Переподключиться" вместо сырого текста
- **Кэш (stale-while-revalidate)**: `DataCache` (object-синглтон) хранит данные в памяти (categories, products, productDetail, comments, `favoriteImageIds: MutableSet<Int>`); при возврате назад данные показываются мгновенно, сетевой запрос идёт в фоне. `DiskCache` сериализует кэш в JSON-файлы в `cacheDir` — переживает перезапуск приложения. `DiskCache.init()` вызывается в `MainActivity.onCreate()` до `setContent`. `CachePolicy` — чистые функции `shouldShowSpinner(hasCachedData)` и `errorMessageForDisplay(hasCachedData, exception)`.
- OkHttp `connectTimeout` = 5 сек (уменьшен с 10 для ускорения retry-цикла у пользователей с нестабильным соединением)
- AuthDialog: шаг email содержит онбординг-текст "Чат и Комментирование доступны при регистрации..."; 3 шага — email → код → имя (шаг 3 только при первом входе)

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
DJANGO_EXTRA_ALLOWED_HOSTS  # доп. хосты/IP через запятую (например, IP нового сервера при тестировании до смены DNS)
APP_FOLDER               # абсолютный путь к папке приложения на сервере (читается deploy.sh)
```

## Деплой
```bash
./deploy.sh
```
Скрипт: читает `APP_FOLDER` из `.env` → `git pull` → активирует `$APP_FOLDER/.venv` → `pip install` → `migrate` → `collectstatic` → `systemctl restart gunicorn`

Локальный запуск:
```bash
source .venv/bin/activate
python manage.py runserver
# для доступа с телефона:
python manage.py runserver 0.0.0.0:8000
```

## Важные нюансы
- `fix_users_migration.py` — уже применён на сервере, повторно не нужен
- **Gunicorn**: 2 воркера (3 вызывали OOM на 2GB RAM; каждый воркер ~200MB). Настройка в systemd unit.
- **Swap**: 1-2 GB swap-файл добавлен для защиты от OOM-kill при пиковой нагрузке.
- Приватный бакет: KMS-шифрование, сервисный аккаунт нужна роль `kms.keys.encrypterDecrypter`
- CORS в Yandex Storage настроен только на `https://eliza.gallery`
- `DEBUG=False` на сервере — статику раздаёт nginx
- При добавлении новых статических файлов нужен `collectstatic`
- venv называется `.venv` (не `venv`); всегда активировать: `source .venv/bin/activate`
- Chat URLs используют `user_id` (не `chat_id`) — важно при отладке
- `CSRF_TRUSTED_ORIGINS = ["https://eliza.gallery"]` в settings.py — обязательно при `SECURE_PROXY_SSL_HEADER`
- `DJANGO_EXTRA_ALLOWED_HOSTS` — удобен для тестирования нового сервера по IP до смены DNS

## Тесты

### Backend (Django)
Запуск: `source .venv/bin/activate && python manage.py test catalog users chat`

| Файл | Классы | Что покрыто |
|---|---|---|
| `catalog/tests.py` | 22 класса | CategoryList, ProductList, ProductDetail, CommentList (включая `user_id`/`user_email`), MyComments, Search (изоляция user/staff), StaffComments CRUD, Mobile2 views, Presign, **FavoriteAPI** (list/add/duplicate/hidden/delete/auth/isolation/fields), **ReportComment** (создание, not-staff, not-own, unique, max_length, auth), **DeleteOwnComment** (успех, чужой 403, auth, каскад), **StaffReportList** (список, auto-mark-read, пустой), **StaffReportUnread** (счётчик, после mark-read), **StaffReportDismiss** (удаление репорта, комментарий остаётся), **StaffReportDeleteComment** (удаление комментария каскадно) |
| `users/tests.py` | 12 классов | RequestCode, VerifyCode, SetName, UniqueDisplayName, Profile (включая `is_staff`), Logout (удаление токена, последующий 401), DeleteAccount, **VerifyCodeConsent** (has_consent false/true), **RecordConsent** (записывает дату, ok, auth, идемпотентно) |
| `chat/tests.py` | 13 классов | ChatInfo, Messages (пагинация after/before), Send, MarkRead, Unread, StaffChatList/Messages/Send/MarkRead, StaffUnread, ChatMediaPresign |

### Android (Kotlin + Robolectric)
Запуск: `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" && ./gradlew :app:testDebugUnitTest`

| Файл | Что покрыто |
|---|---|
| `ui/screens/ProductGalleryTest.kt` | Наличие всех фото в сетке по `contentDescription`, тап передаёт правильный индекс в `onPhotoTap` |
| `ui/screens/MessageBubbleTest.kt` | Обычный текст; `[product:id:page]` → кнопка + правильные id/page в колбэке; без колбэка кнопки нет; сырые теги не просачиваются в UI; текст рядом с тегом отображается |
| `data/ChatMessageEntityTest.kt` | `toModel()` / `toEntity()` маппинг всех полей, `chat_user_id`, round-trip |
| `data/TokenStorageTest.kt` | save/get/clear/overwrite |
| `data/ChatDaoTest.kt` | CRUD, изоляция по `chat_user_id`, порядок ASC, `minId`/`maxId`, `markReadUpTo` (только staff, только до upToId, без cross-chat эффектов), OnConflict.REPLACE |
| `ui/screens/FullscreenImageViewerTest.kt` | "В чат" видна при non-null callback, скрыта при null; клик передаёт правильный page; "Скачать" и "Ссылка" всегда видны |
| `util/CachePolicyTest.kt` | `shouldShowSpinner`: true без кэша, false с кэшем; `errorMessageForDisplay`: null с кэшем, сообщение без кэша, fallback при null message |
| `data/DiskCacheTest.kt` | categories/products/productDetail/comments восстанавливаются после рестарта; изоляция по categoryId; `clearAll` чистит диск и память |

**Нюанс Robolectric:** `DropdownMenu` рендерится в `Popup` — `assertIsDisplayed()` не работает (нода есть, но не "displayed"). Меню "Скопировать" не тестируется через Robolectric.

### e2e (Python, против реального сервера)
Запуск: `source .venv/bin/activate && python e2e_test.py --email you@example.com --base-url https://eliza.gallery --staff-token TOKEN`

Покрывает: Public API, Auth+Email (Resend, интерактивный ввод кода), Authenticated user (комменты, чат, поиск, presign, **favorites**), Staff API, Logout+отзыв токена, Yandex Object Storage (upload/download/public URL/delete).

**Нюанс:** `requests.Response` с 4xx/5xx кодом является `falsy` в Python — для проверки таких статусов использовать `r is not None and r.status_code == 401`, а не `r and ...`.

- `--base-url` можно направить на IP нового сервера для тестирования до смены DNS
- Staff-тесты запускаются **до** logout — иначе auth-flow токен staff-пользователя будет отозван (`Token.objects.get_or_create`)
- `/api/catalog/upload/presign` не тестируется (session auth + CSRF, несовместимо с Token auth)

**Нюанс Search (SQLite + кириллица):** `icontains` в SQLite не чувствителен к регистру только для ASCII. Тестовые данные должны быть в том же регистре, что и поисковый запрос.

## Роадмап
- **V1** ✅: каталог с изображениями, мобильный интерфейс управления
- **V2** ✅: комментарии, регистрация через email, display_name, текстовый чат
- **V2.5** ✅: фото в чате (публичный бакет, папка `chat/`), кнопка "В чат" на товарах, fullscreen фото в чате, server-side logout
- **V2.6** ✅: копирование сообщений/комментариев (долгое нажатие), активные ссылки в чате, "Меню" dropdown, кнопка домой на всех экранах, экран поиска, экран "Мои комментарии"
- **V2.7** ✅: галерея товара плиткой 3×N, fullscreen со свайпом (циклический) и зумом, тайлы изображений (100/200/300/600px, Pillow, фоновый поток, EXIF-коррекция)
- **V2.8** ✅: stale-while-revalidate кэш (мгновенный показ при возврате назад), disk cache (переживает перезапуск), "В чат" для незалогиненных, e2e-тест скрипт, переезд на Яндекс-сервер
- **V2.9** ✅: Избранное (server-side, иконка под галереей + в fullscreen, экран со свайп-удалением, пункт меню), версия приложения в меню, deploy.sh через APP_FOLDER
- **V3.0** ✅: Жалобы на комментарии (ReportsScreen для staff, диалог отклонить/удалить), удаление собственных комментариев, правовые страницы (privacy/terms/consent в WebView), согласие пользователя при первой регистрации (ConsentDialog), `has_consent` в verify-code, `record-consent` эндпоинт
- **V3.1** ✅: Bulk-управление фото в mobile2 (скрыть/показать/удалить/вынести), редактирование названий товаров и категорий, is_hidden для ProductImage, свайп между товарами в Android, спиннеры при загрузке (CategoryScreen/ProductListScreen/FullscreenImageViewer), страница /all-products, бэкапы БД в S3, gunicorn 2 воркера + swap, Яндекс Cloud сервер
- **V3.2**: голосовые в чате (приватный бакет), шеринг товаров (App Links)

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
- `"favorites"` — избранное (только залогиненные)
- `"reports"` — жалобы (только staff)
