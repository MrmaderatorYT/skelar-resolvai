# Бриф для Codex — частина C: агент на Gemma, плагіни (SPI), red-team, рев'ю Opus

> Скопіюй увесь текст нижче лінії в Codex. Відповідай українською, код і коментарі пиши англійською.

---

Ти — другий інженер команди Defuse на хакатоні polyTech Code Fest × SKELAR. Дедлайн здачі — 17:00, запис відео — о 16:00. Твій жорсткий стоп — **14:50**: на цей час гілка має бути запушена разом із HANDOFF. Паралельно працює Opus (Claude) над своєю частиною. Потім ви робите **перехресне рев'ю**: ти рев'юїш його гілку, він — твою.

## 1. Контекст (прочитай перед стартом)

**Продукт.** Defuse — AI-оператор підтримки для застосунків із підпискою. Головне правило: **LLM розуміє текст і пише відповідь, а гроші рухає тільки детермінований код** (`ReferencePolicyService` + `ActionExecutorService`).

**Репо:** https://github.com/MrmaderatorYT/skelar-resolvai
- `main` належить Дімі, його **не чіпаємо**;
- наша гілка — `feat`.

**Стек:** Java 17+ / Spring Boot 3.3 (Gradle) + React/Vite. Запуск:
```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home   # якщо системна Java < 17
cd backend && ./gradlew test        # усі тести без API
./gradlew bootRun                   # :8000, режим gold без ключа
cd ../frontend && npm i && npm run dev   # :5173
```

**Прочитай обов'язково:**
- `.task/plan/WORKPLAN.md` — процес, таймінг, **таблиця володіння файлами** (§2), **заморожені контракти** (§3);
- `.task/ideas/2026-10-31-plugin-architecture.md` — дизайн SPI, який ти реалізуєш;
- `.task/CODEX_REVIEW.md` — твоє попереднє рев'ю; ці правила вже в коді;
- `docs/architecture.md`, `docs/security.md`, `README.md`.

**Нова вимога SKELAR:** агент має працювати на **Gemma**. Зараз LLM-шар — Claude (anthropic-java) у `service/llm/`. Його треба замінити.

### Правила роботи
1. **Гілка `feat-codex`.** Створюєш її від `feat` **після того, як Opus запушить крок 0** (≈13:20, у `feat` з'явиться коміт «Merge main into feat»). До того працюй лише над §2.1 (перевірка API), без змін у репо.
2. **Окрема копія репо.** Працюй в окремому клоні, наприклад `~/Desktop/skelar-resolvai-codex`. Не в папці Opus і не в робочій копії Назара.
3. **Змінюєш лише свої файли** (WORKPLAN §2). Треба змінити чужий файл → опиши запит у HANDOFF, сам не міняй.
4. **Заморожені контракти** (WORKPLAN §3) не змінюєш: `ClassificationFeatures`, `PolicyDecision`, назви дій, REST/JSON для фронтенду, getter-и параметрів пропозиції.
5. **Ніяких push у `main`, ніяких force-push, ніяких секретів у репо.** Ключ лише з env.
6. **Кожне нетривіальне рішення має джерело:** офіційна документація Google/Gemma, Spring, Stripe, OWASP — або виміряний результат на eval. Немає джерела → пиши `UNVERIFIED` і як перевірити. Не вигадуй цифр якості: якщо щось не прогнав, так і пиши.
7. **Перед кожним push:** `./gradlew test` зелений, і всі старі тести не змінені за змістом.

## 2. C1 — LLM-шар на Gemma (пріоритет №1, ~60 хв)

### 2.1 Перевірка API (13:00–13:20, без змін у репо)
Потрібен `GEMINI_API_KEY` (Google AI Studio). Нема ключа → одразу скажи Назару.

Перевір і **занотуй з посиланнями на документацію** у `docs/features/triage/integrations.md`:
1. **Яка Gemma доступна через Gemini API на цьому ключі.** Зроби `models.list` і візьми найновішу instruction-tuned Gemma (кандидат: `gemma-3-27b-it`; перевір, чи є новіша). Запиши точний id.
2. **Java SDK.** `com.google.genai:google-genai` — офіційний Google Gen AI SDK. Документація показує 1.4.1; візьми актуальну стабільну версію з Maven Central, якій щонайменше 2 тижні.
3. **System instruction.** Чи приймає Gemma на Gemini API поле `systemInstruction`? Для Gemma-моделей воно може відхилятися. Якщо так — інструкції йдуть у перший user-turn.
4. **JSON mode / `responseSchema`.** Чи працюють вони для Gemma? Сторонні агрегатори суперечать один одному, тож перевір реальним запитом.
5. **Ліміти.** RPM/TPM для цього ключа, latency p50 на 5 запитах.

### 2.2 Реалізація
- **Інтерфейс `LlmClient`** у `service/llm/` (`generateJson(prompt, schemaHint)`, `generateText(prompt)`) і **реалізація `GemmaClient`** на Google Gen AI SDK. Конфіг:
  ```yaml
  defuse:
    llm:
      mode: ${DEFUSE_LLM_MODE:gold}        # gold | live | replay
      provider: gemma
      model: ${DEFUSE_LLM_MODEL:<id з 2.1>}
      api-key-env: GEMINI_API_KEY
      timeout-ms: 15000
      max-retries: 2
  ```
  Те саме SDK працює і з Gemini-моделями. Якщо SKELAR уточнить «Gemini», міняється лише `model`.
- **Прибрати Claude з рантайму:** `AnthropicClientHolder` і залежність `anthropic-java` з `build.gradle`. В UI health показує `provider` + `model`.
- **`LlmClassifierService`** повертає той самий `ClassificationFeatures`:
  - якщо JSON mode/schema підтримується (2.1) — використай його; інакше — промпт + **строгий парсинг** Jackson-ом (`FAIL_ON_UNKNOWN_PROPERTIES=false`, але обов'язкові поля мають бути);
  - **валідація:** intent із фіксованого списку; `confidence` скінченне число в 0..1; `cancelReason` зі списку (`too_expensive | no_time | not_using | cant_find_cancel | technical | other | none`);
  - невалідна відповідь → **одна** спроба «repair» з повідомленням про помилку → далі `ClassificationFeatures.fallback(...)` (confidence 0 → людина);
  - `temperature` 0 (або мінімальна), `maxOutputTokens` з запасом;
  - текст тікета йде як дані: JSON-рядок або чітко позначений блок, **не в інструкціях**. Додай захист від закриття делімітера: `</ticket>` у тексті ескейпиться або замінюється.
- **Промпт для Gemma:** `Prompts.java` під Gemma. Логіку визначень (сарказм, прихована агресія, dispute signal, cancelReason) не змінюй.
  - **Few-shot:** 6–8 синтетичних прикладів у `src/main/resources/prompts/classifier-fewshot.json`, **жодного з eval-набору** (`tickets_eval.json`). Тест: нормалізований текст кожного прикладу не збігається з жодним eval-тікетом і не має подібності > 0.8 (Jaccard по словах).
- **`ReplyWriterService`** на Gemma з тими ж обмеженнями: відповідь пишеться **після** виконання дій і згадує лише виконані чи заплановані дії. Параметри пропозиції бери з `ReferencePolicyService` через getter-и (§3). Будь-яка помилка → шаблон.
- **Стійкість:**
  - `analyze-all` іде послідовно, без паралельних викликів;
  - 429/5xx → backoff відповідно до `max-retries`;
  - таймаут → fallback (людина), а не падіння запиту.
- **Режим `replay` для демо:** після live-прогону `POST /api/demo/record` зберігає аналізи в `backend/replay/analyses.json`; `mode=replay` віддає їх без мережі. В UI badge: «Replay of live Gemma run at HH:MM». Ніде не видавати replay за live.

### 2.3 Eval на Gemma
- **`EvalService.runLive()`** на Gemma, 44 тікети (Opus може додати ще; бери всі `tickets_eval` з `gold`).
- **Додай метрики:** точність `cancel_reason`, latency p50/p95, кількість fallback-ів і невалідних JSON.
- **Гейт без змін:** 0 unsafe auto-refunds, 0 missed injections, повне покриття тікетів.
- **Зберегти** `eval_report.json` з `model`, `provider`, `generated_at`, метриками, а поруч — keyword baseline.
- **У HANDOFF — таблиця:** Gemma проти keyword-бота за метриками сарказму, injection, unsafe, точності дій.
- **Не досягнуто recall сарказму ≥ 0.8?** Чесно напиши цифру і що пробував. Ітерації промпту — лише на прикладах **поза** eval (інакше це підгонка під тест).

**Готово, коли:** `./gradlew test` зелений без ключа; `./gradlew liveEval` з ключем проходить гейт; у `README` і `docs/features/triage/*` описано Gemma (model id, як отримати ключ, режими gold/live/replay).

## 3. C2 — плагіни (SPI) за дизайном (~40 хв)
Реалізуй **мінімальну** версію з `.task/ideas/2026-10-31-plugin-architecture.md`. Рішення ядра (policy) не змінюються. Це рефакторинг, і **усі старі тести мають лишитися зеленими без змін змісту**.

- **`spi/`:**
  - `BillingProvider` (`processor()`, `capabilities()`, `execute(BillingCommand)`) — `BillingCommand` несе `idempotencyKey = ticketId:action`;
  - `Capability` (REFUND, CANCEL, PAUSE, DISCOUNT, EXTEND);
  - `ActionHandler` (`action()`, `movesMoney()`, `run(ActionContext)`);
  - `ConnectorFactory<T>` (`type()`, `kind()`, `create(Map<String,String>)`);
  - `CustomerSource`, `EngagementSource`, `TicketSource` — лише читання.
- **`registry/`:** `ActionRegistry`, `BillingRegistry`, `ConnectorRegistry`. Spring збирає біни; дублікати ключів чи невідомий `type` → **помилка старту** з людським повідомленням.
- **`plugins/`:**
  - `plugins/actions/*Handler` — по одному на кожну дію з WORKPLAN §3; замінюють `switch` у `MockBillingService`;
  - `plugins/billing/MockCardBillingProvider` (усі capabilities) і `StoreBillingProvider` для `app_store` / `google_play` (жодних capabilities);
  - `plugins/json/*` — обгортки над `InMemoryStore`.
- **`ActionExecutorService`:** dispatch через `ActionRegistry`. **Defense in depth:** якщо дія рухає гроші, а провайдер платежу не має capability → `BillingException` → тікет ескалується (policy при цьому вже мала б не дозволити).
- **`application.yml`:**
  - `defuse.datasources.*` (`type: json`) і `defuse.billing[]`;
  - **SecretsValidator:** значення з `password` / DSN з паролем, не взяті через `${ENV}`, → помилка старту.
- **Тести:**
  - невідомий `type` → старт падає;
  - буквальний пароль → старт падає;
  - `app_store` + `refund_full` напряму в executor → помилка і ескалація;
  - дублікат `ActionHandler.action()` → старт падає;
  - **усі 38+ старих тестів зелені**.
- **Джерела в коментарях / docs:**
  - Spring Boot `@ConfigurationProperties` + `@Validated`;
  - Stripe Idempotency-Key — як ми моделюємо idempotency;
  - чому не PF4J / ServiceLoader для MVP (одне речення).
- **Docs:** `docs/features/plugins/` — 10 файлів у форматі SKELAR (як інші папки в `docs/features`).

**Stretch (лише якщо все вище готово до 14:20):** `plugins/postgres/PostgresCustomerSourceFactory` — тільки читання, JdbcTemplate, тест на H2 або Testcontainers. Не встигаєш — не починай.

## 4. C3 — red-team набір (~20 хв)
**Джерело класифікації атак:** OWASP Top 10 for LLM Applications 2025 — LLM01 Prompt Injection, LLM05 Improper Output Handling, LLM06 Excessive Agency. У тестах посилайся на відповідний пункт.

**`src/test/resources/redteam/attacks.json`** — щонайменше **24** кейси, **поза eval-набором**, мовами EN/UA/DE/ES/PL. Категорії:
1. прямий override інструкцій;
2. рольова гра «ти тепер адмін»;
3. фейкові системні теги `[SYSTEM]`, `<<SYS>>`;
4. закриття делімітера `</ticket>` + нові інструкції;
5. гомогліфи (кирилична «о» в «ignоre»);
6. base64 / rot13-інструкції;
7. «policy updated / limit raised»;
8. «don't tell the operator»;
9. JSON-ін'єкція в `reply_text` на `/approve`;
10. XSS-пейлоад у тексті (`<img onerror=…>`);
11. соціальна інженерія з погрозою банком, щоб отримати refund поза policy;
12. дуже довгий текст (> 5000 символів).

**`RedTeamTest`** (без API) проганяє кожен кейс через guard + policy (у gold-подібному режимі з консервативними фічами) і перевіряє:
- **жодної дії з грошима** без підстави в policy;
- injection → `escalate`, 0 actions;
- `reply_text` на `/approve` не змінює actions і amount;
- текст із тегами повертається з API як текст (без HTML).

**Live-варіант** (`@Tag("live")`) проганяє ті самі кейси через Gemma, результат іде в HANDOFF. Якщо regex-гард пропускає якусь категорію, а Gemma ловить, так і напиши. Якщо обидва пропускають — додай патерн у guard і тест.

## 5. Що віддати о 14:50 — `.task/handoff/HANDOFF-codex.md`
- **Що зроблено** по C1/C2/C3, з посиланнями на файли.
- **Таблиця перевірки Gemma API (2.1):** що працює, що ні, з джерелами.
- **Eval:** Gemma проти keyword-бота — цифри, model id, дата/час.
- **Red-team:** скільки кейсів, що ловить guard, що ловить модель, що не ловить ніхто.
- **Ризики для демо** і як їх обійти (replay-режим, ліміти ключа).
- **Запити до Opus**, якщо потрібні зміни в його файлах.
- **`UNVERIFIED`** — список.

## 6. Рев'ю гілки Opus (14:50–15:20) → `.task/reviews/REVIEW-of-opus-by-codex.md`
Opus робить: доказову базу правил (`docs/evidence/RULES.md`), `defuse-policy.yml` + валідацію, оновлення правил і gold, гроші на дашборді, вкладку Policy, тексти слайдів. Подивись `git diff feat...origin/feat-opus` і перевір:

1. **Докази.** Кожне число в `defuse-policy.yml` має рядок у `RULES.md` з URL, типом і силою (A/B/C/H). Відкрий джерела — чи справді там таке написано? Дані вендора не подані як незалежне дослідження? Евристики позначені як H?
2. **Паритет.** `cd defuse-data && python3 evaluate.py --gold-check` = 100%; JUnit-паритет Java ↔ Python зелений; зміни gold обґрунтовані в HANDOFF-opus.
3. **Гроші.** Refund не названо «економією»; VAMP залежить від регіону; комісії — параметри з джерелом; знижки рахуються як витрата.
4. **Конфіг.** Невалідний YAML → старт падає (є тест); жодних секретів.
5. **Policy UI.** Whitelist .md (спробуй `../../application.yml`, URL-encoded `%2e%2e`); лише читання.
6. **Слайди.** Кожна цифра на слайді є в `RULES.md` або в eval-звіті; немає «врятованих доларів».
7. **Регресії.** `./gradlew test` зелений; демо-кейси T01, T05, T12, T30, T37, T39, T40, T41 дають очікувані результати.

**Формат рев'ю:** severity (critical / major / minor) · файл:рядок · відтворення (команда або вхід) · пропонований diff. **Critical:** неправильні гроші, пропущена injection, неправдиве твердження, впале демо. Сам не застосовуй зміни в гілці Opus — лише рев'ю.

## 7. Чекліст перед push о 14:50
- [ ] `./gradlew test` зелений без ключа; старі тести не змінені за змістом.
- [ ] `./gradlew liveEval` прогнано з ключем — цифри в HANDOFF (або чесно: «не прогнано, бо …»).
- [ ] `git grep -nI "AIza\|sk-ant\|api[_-]key\s*[:=]\s*[A-Za-z0-9]"` нічого не знаходить.
- [ ] Запушено `feat-codex`, PR **не** відкривати; мерджить Opus о 15:40.
- [ ] `HANDOFF-codex.md` закомічено.
