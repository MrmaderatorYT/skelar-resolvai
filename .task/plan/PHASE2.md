# Phase 2 (14:00 → 16:00): вебка з двома ролями, формула автономності, клієнтська БД

Вхідні дані: зустріч зі SKELAR (див. `.task/meeting-2026-10-31.md`) і ресерч `.task/research/Defuse_market_validation_2026-10-31.md`.

## 1. Що будуємо
- **Логін, 2 демо-акаунти:**
  - **Користувач** (`customer@calmly.demo`) бачить сайт Calmly і вікно AI-підтримки. Він пише, агент відповідає, робить дії або каже «передаю спеціалісту».
  - **Адмін** (`admin@calmly.demo`) бачить консоль підтримки:
    - **Inbox** — тікети, які агент не довів до кінця: що агент підготував, чому зупинився, кнопки Approve / Reject / «Відповісти самому»;
    - **Queue**;
    - **Analytics**: AI сам / AI + людина / лише людина; топ-5 причин; чому AI зупинився; сегменти «канал × причина × платіжна система»;
    - **Policy**;
    - **Eval**.
- **Без API-ключа** чат працює в режимі *Scripted demo*: повідомлення-підказки з eval-набору, позначені на екрані. З `GEMINI_API_KEY` — справжня Gemma.

## 2. Формула автономності (Autonomy gate)
SKELAR: «продукт сам вирішує правила: < $50 можна віддати автоматично, більша сума — людина». Ресерч: самооцінка LLM ≠ калібрована ймовірність помилки; калібрувати треба по класах дій.

Для кожного тікета після policy:

```
p_err  = max( calib_err[intent], 1 − confidence )
         calib_err[intent] = (помилки + 1) / (n + 2) з останнього розміченого прогону (Laplace);
         поки live-прогону немає — пріори з defuse-policy.yml (H)
impact = гроші, що реально рухаються: refund + вартість знижки (ціна × % × місяці)
         + продовження доступу (ціна × дні / період); cancel, pause, відповідь = $0
E      = p_err × impact          (очікувана вартість помилки)

AUTO    якщо  p_err ≤ 3%   і  impact ≤ $50  і  E ≤ $1.50
ASSIST  якщо  p_err ≤ 15%  і  impact ≤ $500       → AI готує все, людина Approve
HUMAN   інакше, або жорсткі правила (injection, диспут, безпека акаунта)
        → людина веде, AI дає чернетку й докази
Дії без побічних ефектів (reply_only, ask_clarification) — завжди AUTO.
```

- **Підсумковий режим** = найсуворіший із (policy, gate).
- **Пороги 3% / 15% / $50 / $1.50** — налаштування бізнесу в `defuse-policy.yml`. $50 — приклад SKELAR, 3/15% — наша евристика (H). Їх калібруємо на реальних розмічених тікетах пілота.
- **Адмін бачить три смужки:** p_err, impact, E — і тир.

## 3. Клієнтська БД — делегуємо Codex
- **H2 (PostgreSQL mode) у пам'яті;** профіль `postgres` для справжнього Postgres.
- **Схема повторює системи клієнтів;** назви полів беремо з офіційних API-доків:
  - Stripe: customers, subscriptions, invoices/charges, refunds, disputes;
  - Apple App Store Server API: transactions і notifications;
  - Google Play: subscriptionsv2;
  - RevenueCat: subscribers / entitlements;
  - Zendesk: tickets;
  - AppsFlyer: installs / attribution.
- **ETL під час старту:** seed JSON → таблиці. Застосунок читає домен через SQL-view, а не з JSON.

## 4. Розподіл
| Хто | Що | Файли |
|---|---|---|
| **Opus** | gate у policy + YAML + тести; auth (2 ролі); chat API; admin inbox API; аналітика сегментів; уся вебка (логін, сайт + чат-віджет, адмін-консоль); пітч | `config/**`, `service/policy/**`, новий `web/**` (auth, chat, admin), `service/analytics/AnalyticsService.java`, `frontend/**`, `defuse-data/**` |
| **Codex** | БД (§3); рев'ю гілки Opus; live Gemma через GitHub Actions | `db/**`, `store/**`, `plugins/jdbc/**`, `build.gradle`, `application.yml` (datasource), `src/main/resources/db/**` |

**Гілки:** `feat-opus2` і `feat-codex2` від `feat` (`ab69121`). Freeze — 15:30, мердж — 15:40, відео — 16:00.
