# Бриф для Codex — фаза 2: клієнтська база даних

> Встав увесь текст нижче лінії в Codex. Відповідай українською, код пиши англійською.

---

Ти — другий інженер Defuse. Твоя попередня частина (Gemma, плагіни, red-team) вже злита у `feat` (`ab69121`, 126 тестів). Прочитай `.task/plan/PHASE2.md`.

**Задача:** застосунок має працювати з **базою даних, схожою на ту, що у клієнтів SKELAR** (B2C підписки, web-to-app). Зараз дані йдуть з JSON у `InMemoryStore`. Паралельно Opus будує вебку з двома ролями і формулу автономності — його файли не чіпай (таблиця в `PHASE2.md` §4).

**Гілка `feat-codex2`** від `feat`. Окремий клон. **Freeze — 15:30**: на цей час запушено, тести зелені, `HANDOFF-codex2.md` готовий. `main` не чіпати.

## 1. Схема (`backend/src/main/resources/db/schema.sql`)
H2 у режимі `MODE=PostgreSQL`. **Назви таблиць і колонок — як в офіційних API клієнтських систем.** У коментарі до кожної таблиці дай URL документації.

| Таблиця | Джерело схеми | Мінімальні поля |
|---|---|---|
| `stripe_customers` | Stripe Customer object | id, email, created, metadata_app_user_id |
| `stripe_subscriptions` | Stripe Subscription | id, customer, status, current_period_start/end, cancel_at_period_end, canceled_at, price_id, unit_amount, interval, discount_percent, pause_collection_resumes_at |
| `stripe_charges` | Stripe Charge / PaymentIntent | id, customer, amount (cents), currency, status, created, payment_method_fingerprint, invoice, description, refunded, amount_refunded, disputed |
| `stripe_refunds` | Stripe Refund | id, charge, amount, status (pending/succeeded/failed), created, reason |
| `stripe_disputes` | Stripe Dispute | id, charge, amount, status, reason, created |
| `appstore_transactions` | App Store Server API JWSTransaction | original_transaction_id, transaction_id, product_id, purchase_date, expires_date, revocation_date, app_account_token |
| `appstore_notifications` | App Store Server Notifications V2 | notification_uuid, notification_type, subtype, original_transaction_id, signed_date |
| `play_subscriptions` | Google Play subscriptionsv2 | purchase_token, subscription_state, line_item_product_id, expiry_time, linked_purchase_token, obfuscated_external_account_id |
| `rc_subscribers` / `rc_entitlements` | RevenueCat subscriber / entitlements | app_user_id, original_app_user_id, entitlement_id, store, expires_date, period_type, unsubscribe_detected_at |
| `zendesk_tickets` | Zendesk Ticket | id, requester_id, subject, description, status, priority, tags, created_at, channel (via.channel) |
| `appsflyer_installs` | AppsFlyer raw data (installs) | appsflyer_id, customer_user_id, media_source, campaign, install_time, platform |
| `app_users` | наш зв'язок identity | id (`cus_XXXX`), name, country, language, quiz_goal, card_fingerprint, offers_12m, last_offer_at, contacts_last_30d |

## 2. ETL і читання
- **`DbSeeder`** під час старту (і на `POST /api/demo/reset`) заливає seed JSON (`data/*.json`) у ці таблиці з мапінгом:
  - `card` → stripe_*;
  - `app_store` → appstore_* + rc_*;
  - `google_play` → play_* + rc_*;
  - `tickets_eval` → zendesk_tickets;
  - канал залучення → appsflyer_installs.
- **`src/main/resources/db/views.sql`:** SQL-view, які збирають доменні об'єкти (`v_customers`, `v_subscriptions`, `v_payments`, `v_usage`) у форматі, що вже очікує `InMemoryStore`. **Store читає з цих view через JdbcTemplate**, а не з JSON. Нехай `DataLoader` або новий `JdbcSeedSource` дає ту саму форму даних, щоб policy, тести й API не змінилися.
- **Зворотний запис:** дії executor (refund, cancel, pause, discount, offer) пишуть у відповідні таблиці (`stripe_refunds`, `stripe_subscriptions.canceled_at` / `pause_collection_resumes_at` / `discount_percent`, `app_users.offers_12m`). Це має бути видно SQL-запитом після дії.
- **`AnalyticsRepository`** (read-only, тільки параметризовані запити) з методами для аналітики Opus:
  - `segments()` → рядки `acquisition_channel, payment_rail, root_cause, tickets, refunds_usd`;
  - `railMix()` → кількість активних підписок за rail;
  - `multiRailCustomers()` → клієнти з активними підписками на ≥ 2 rail.

  Opus викличе ці методи з `AnalyticsService`. Повертай `List<Map<String,Object>>` зі snake_case ключами.

## 3. Конфіг
- **`application.yml`:** H2 за замовчуванням (`jdbc:h2:mem:defuse;MODE=PostgreSQL;DB_CLOSE_DELAY=-1`). Профіль `postgres` бере URL / user / password **лише з env**. Твій SecretsValidator має це покрити.
- **`build.gradle`:** `spring-boot-starter-jdbc`, `com.h2database:h2` (версія з Spring Boot BOM), драйвер Postgres — лише runtimeOnly.
- **H2-консоль** (`/h2-console`) — тільки з профілем `demo-console`, за замовчуванням вимкнена.

## 4. Тести (усі без мережі)
- Після старту кількість рядків у таблицях відповідає seed (customers, charges, refunds…).
- Store, завантажений з БД, дає **ті самі** рішення policy на всіх 44 gold-тікетах, що й JSON (паритет).
- Після `analyze` T01 у `stripe_refunds` з'являється рядок на 49.99, а `stripe_subscriptions.canceled_at` не NULL. Повторний analyze не створює другий refund.
- `segments()` повертає ненульові сегменти; запити параметризовані (SQL injection через параметри неможливий — тест).
- Усі наявні 126 тестів зелені.

## 5. Обмеження
- `ClassificationFeatures`, `PolicyDecision`, `CustomerContext`, policy, `defuse-policy.yml`, `frontend/**`, `web/**`, `defuse-data/**` не чіпати.
- Не вигадувати назв полів: для кожної таблиці посилання на офіційну документацію в коментарі SQL і в `docs/features/data-layer/integrations.md`.
- Жодних реальних креденшелів. Live Gemma запускається лише через GitHub Actions secret, як ти вже налаштував.

## 6. Рев'ю Opus (після freeze, до 15:40)
Рев'ю `git diff feat...origin/feat-opus2` → `.task/reviews/REVIEW-of-opus2-by-codex.md`. Перевір:
- **Формулу автономності:** межі 3% / 15% / $50 / $1.50 з YAML; найсуворіший режим виграє; injection, диспут, безпека → завжди HUMAN; без побічних ефектів → AUTO.
- **Auth:**
  - клієнт бачить лише свої тікети;
  - клієнт не може викликати admin API;
  - токен не в URL;
  - паролі демо-акаунтів не потрапляють у фронтенд-бандл.
- **Chat:** ticket прив'язаний до customer із токена, а не з тіла запиту.
- **XSS** у чаті, **регресії** (`./gradlew test`).

Формат: severity · файл:рядок · відтворення · diff.
