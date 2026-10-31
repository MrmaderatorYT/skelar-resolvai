# Workplan 31.10.2026 (13:00 → 16:00): Opus + Codex, перехресне рев'ю

## 0. Що змінилося і з чого стартуємо
- **Вимога SKELAR:** агент має працювати на **Gemma** (модель Google). Claude з рантайму прибираємо.
- **Діма змерджив `feat` у `main` до коміту `d3a1314`** і додав свої 2 коміти: `0c41849` «Harden approval and injection safeguards» і `f8a1840` «Bind approvals to the reviewed analysis». Наш коміт з retention (`24895fb`) у `main` ще не потрапив.
- **Правило Назара:** `main` належить Дімі, ми його не чіпаємо. Наша робота йде в `feat`, а Діма сам мерджить `feat` → `main`.

## 1. Процес

| Час | Opus | Codex |
|---|---|---|
| 13:00–13:20 | **Крок 0:** злити `origin/main` у `feat`, розв'язати конфлікти з retention, додати 3 getter-и для параметрів пропозиції (§3), прогнати тести, запушити `feat` | Перевірити Gemma API без змін у репо: які моделі доступні, чи працює JSON-режим, чи підтримується system instruction (див. CODEX-BRIEF §2.1) |
| 13:20 | гілка `feat-opus` від нового `feat` | гілка `feat-codex` від нового `feat` |
| 13:20–14:50 | Частина O (§4) | Частина C (CODEX-BRIEF) |
| 14:50 | push + `.task/handoff/HANDOFF-opus.md` | push + `.task/handoff/HANDOFF-codex.md` |
| 14:50–15:20 | рев'ю `feat-codex` → `.task/reviews/REVIEW-of-codex-by-opus.md` | рев'ю `feat-opus` → `.task/reviews/REVIEW-of-opus-by-codex.md` |
| 15:20–15:40 | виправлення за рев'ю | виправлення за рев'ю |
| 15:40–15:55 | мердж у `feat`: спершу `feat-opus`, потім `feat-codex`; повні тести + live eval на Gemma; push `feat` | підтримка під час мерджу |
| 16:00 | запис відео | |

**Кожен рев'ю-файл** — таблиця: severity (critical / major / minor) · файл:рядок · відтворення · пропонований diff. **Critical** — неправильні гроші, пропущена injection, неправдиве твердження на слайді або впале демо. Critical виправляємо до мерджу обов'язково.

## 2. Хто володіє якими файлами (щоб не було конфліктів)

| Зона | Власник | Файли |
|---|---|---|
| Еталон і дані | **Opus** | `defuse-data/**`, `backend/src/main/resources/data/**` |
| Правила policy | **Opus** | `service/policy/**`, новий `config/policy/**`, новий `src/main/resources/defuse-policy.yml` |
| Гроші й аналітика | **Opus** | `service/analytics/MoneyService.java`, `AnalyticsService.java` |
| Доказова база | **Opus** | `docs/evidence/**`, `docs/policy/**`, `docs/features/{retention,analytics,action-engine}/**` |
| Policy UI | **Opus** | новий `controller/PolicyController.java`, `frontend/src/pages/{Policy,Dashboard}.tsx`, `frontend/src/App.tsx` |
| Пітч | **Opus** | `.task/pitch/**` |
| Зміст промптів: визначення ознак і few-shot | **Opus** | `docs/evidence/LABELING.md`, `backend/src/main/resources/prompts/classifier-fewshot.json` |
| LLM на Gemma (формат промпту, виклики, парсинг) | **Codex** | `service/llm/**`, `build.gradle`, секція `defuse.llm.*` в `application.yml`, `service/analytics/EvalService.java`, `LiveEvalTest.java` |
| Захист | **Codex** | `service/guard/**`, red-team набір у `src/test/resources/redteam/**`, `docs/security.md` |
| Плагіни (SPI) | **Codex** | нові `spi/**`, `registry/**`, `plugins/**`; `service/executor/**`, `store/**`, `service/TicketPipelineService.java`, `model/dto/{Requests,TicketAnalysis}.java`, секції `defuse.datasources.*` / `defuse.billing.*` в `application.yml` |
| Docs фіч | **Codex** | `docs/features/triage/**`, новий `docs/features/plugins/**` |

**Якщо треба змінити чужий файл** — не змінюй його, а впиши запит у свій HANDOFF.

**Єдиний спільний файл — `application.yml`.** Opus додає один рядок на початок: `spring.config.import: classpath:defuse-policy.yml`. Codex змінює лише свої секції.

## 3. Заморожені контракти (не змінює ніхто)
- **`ClassificationFeatures`:** поля й назви лишаються як є (intent, trueGoal, sarcasm, hiddenAggression, explicitAggression, disputeSignal, injection, sentiment, language, confidence, evidence, cancelReason).
- **`PolicyDecision`:** поля лишаються як є.
- **Назви дій:** `cancel`, `refund_full`, `refund_partial`, `refund_partial_goodwill`, `refund_duplicate`, `pause`, `extend_access_7d`, `store_refund_instructions`, `store_cancel_instructions`, `evidence_pack`, `ask_clarification`, `reply_only`, `offer_discount`, `offer_pause`, `apply_discount`. Нових дій у цьому раунді не додаємо.
- **REST-шляхи та JSON-форма відповідей,** на які спирається фронтенд.
- **Параметри пропозиції** беруться лише через екземпляр `ReferencePolicyService`: `retentionDiscountPercent()`, `retentionDiscountMonths()`, `retentionPauseMonths()`. Opus додає їх на кроці 0; на `feat-opus` вони стануть конфігурованими.

## 4. Частина O — Opus: правила, дані, гроші, пітч

### Стандарт доказів (обов'язковий для всіх правил)
Кожне правило має рядок у `docs/evidence/RULES.md`:

| Правило | Наше значення | Джерело (URL) | Тип | Сила | Що з цього випливає |
|---|---|---|---|---|---|

- **Тип:** закон / політика платформи / A/B-тест / звіт-бенчмарк / дані вендора / евристика.
- **Сила:** **A** — закон, правила платіжних мереж, офіційна політика, рецензоване дослідження; **B** — великий звіт з методологією (RevenueCat, Adapty), опубліковані A/B-тести компаній; **C** — дані вендора без методології, маркетинг; **H** — евристика без джерела.
- **Без джерела число не вигадуємо:** правило з силою H позначаємо саме так і пишемо, як holdout чи eval його перевірить.

### O1. Доказова база (~35 хв) → `docs/evidence/`
Перевіряю першоджерела і за потреби змінюю значення:

| Тема | Що перевірити | Кандидати джерел |
|---|---|---|
| Вікно повного refund (зараз 14 днів, ≤2 сесії) | Право на відмову в ЄС та оплата за вже надану послугу | Directive 2011/83/EU, ст. 9 і 14(3); Directive 2023/2673 |
| Частковий refund (зараз 50% до 30 днів) | Пропорційне повернення за невикористаний період замість фіксованих 50%; політики конкурентів | EU CRD ст. 14(3); публічні subscription policy Liven («30-day money-back»), Leaply (§2, §4), Calm, Headspace, Noom, BetterMe |
| Store-покупки | Хто повертає гроші і як | Apple «Request a refund» (reportaproblem), Google Play refund policy |
| Ліміт авто-refund $60, повторні refund-и → людина | Практика ризик-лімітів | Stripe docs (Radar rules, refunds), Adapty refund benchmark (2–5%, медіана 2.71% US iOS) |
| Тип і розмір пропозиції (зараз −25% × 2 міс; пауза 2 міс) | Що краще утримує: пауза чи знижка; мінімальна відчутна знижка | Churnkey «pause offer at cancellation» (54% відновлень після паузи проти 22% утримання після знижки — дані вендора, C), Churnkey «discounts» (≥30%), Skio / Userpilot (save rate 10–34%) |
| Одна пропозиція, «cancel» завжди працює | Вимога закону | California AB 2863 (Bus. & Prof. Code §17602), Venable / DWT |
| Cooldown 12 міс, кластер карток, бюджет, holdout 10% | Захист від зловживань і вимірювання ефекту | Stripe coupons (`max_redemptions`, `first_time_transaction`), Stripe card fingerprint, практика holdout у retention-вендорів |
| Hard paywall / повернення клієнтів | Чи варто взагалі робити save offer | RevenueCat State of Subscription Apps 2026 (лише ~5% тих, хто скасував річний план, повертаються) |
| Гроші: комісії й пороги диспутів | Комісія за dispute, VAMP за регіонами, Mastercard ECP | Stripe pricing (dispute fee $15 US), Visa VAMP fact sheet (1.5% US/CA/EU/AP з 01.04.2026; CEMEA 2.2%), Mastercard ECP |
| Ваги ризику | Чи є зовнішня основа | arXiv 2010.13684 (sentiment → ескалація); інакше H, перевіряємо на eval |

**Результат:** `docs/evidence/RULES.md` + по одному `.md` на тему з цитатами (≤15 слів кожна) і посиланнями.

### O2. Policy-as-config (~25 хв)
- **`defuse-policy.yml`** містить усі пороги policy, retention, ризику й грошей. Кожне значення має поле `source: evidence/<file>.md#<anchor>`.
- **`@ConfigurationProperties("defuse.policy")` + `@Validated`.** Частки в межах 0..1, ліміти > 0. Невалідне значення — застосунок не стартує.
- **`ReferencePolicyService` бере пороги з конфігу** замість `static final`. Getter-и з §3 повертають значення з конфігу.
- **Python-еталон читає той самий YAML.** Тест паритету: значення в Java і Python однакові.

### O3. Правила за доказами + еталон (~20 хв)
- Змінюю значення, де докази кажуть інакше. Найімовірніше: частковий refund стає пропорційним; знижка 25% → 30%, якщо підтвердиться.
- **Зміни переносяться всюди разом:** Python-еталон, Java, gold-мітки, перегенерація даних.
- **Нові eval-тікети** на нові межі: пропорційний refund, store-пауза, знижка для клієнта з кластером.
- **Гейт:** `evaluate.py --gold-check` і JUnit-паритет — 100%.

### O4. Гроші й дашборд (~10 хв)
- **Комісії й пороги** з `RULES.md`. VAMP залежить від регіону мерджанта (US/EU 1.5%, CEMEA 2.2%); ставка за диспут — параметр контракту, а не «штраф Visa».
- **Дашборд окремо показує:** chargeback exposure · витрати на refund · очікувану економію на комісіях · витрати на знижки (offers × discount × price × months) · save rate проти holdout.

### O5. Вкладка Policy в UI (~10 хв)
- **`GET /api/policy`** → правила зі значеннями та `source`.
- **`GET /api/policy/docs/{name}`** → .md за whitelist (без path traversal).
- **Вкладка Policy:** список правил ↔ .md з джерелом і силою доказу. Лише для читання.

### O6. Пітч (~паралельно, 15 хв) → `.task/pitch/`
- **`slides.md`:** 10 слайдів під новий пітч («складні billing-звернення з контрольованими витратами»), кожна цифра з посиланням на `RULES.md`.
- **`demo-script.md`:** 3 хв. T01 → T05 → T12 (Approve) → T30 → T37 → T39 (пропозиція) → T40/T41 (зловживання) → нове повідомлення наживо → дашборд → eval.
- **`qa.md`:** відповіді на «чим ви кращі за Fin?», «навіщо платити за швидше скасування?», «чому Gemma?», «де докази?».

### O7. Зміст промптів AI (~15 хв, віддати Codex до 13:45)
- **`docs/evidence/LABELING.md` — визначення ознак із джерелами:** сарказм (похвала зі зворотним змістом), прихована та відкрита агресія, dispute signal, справжня мета, `cancel_reason`. Опора: дослідження про сарказм і sentiment у підтримці, OWASP LLM01 для injection. Ті самі визначення — інструкція для розмітки eval.
- **`prompts/classifier-fewshot.json`:** 8 синтетичних прикладів, жодного з eval, усі 5 мов. Покривають: сарказм ↔ щира похвала з емодзі, приховану погрозу, `cancel_reason`, ситуацію «питає як скасувати, а хоче гроші».
- **Codex** вбудовує це в Gemma-промпт і пише тест «few-shot ≠ eval». Сам зміст визначень Codex не змінює: якщо Gemma погано їх розуміє — запит у HANDOFF.

### Мапа «наш внесок» → крок плану
| Блок | Що робимо в цьому раунді | Крок |
|---|---|---|
| Дані для демо | Перегенерація під нові правила, профілі клієнтів за бенчмарками | O3 |
| Правила policy | Доказова база + `defuse-policy.yml` + оновлені значення | O1, O2, O3 |
| Правила знижок і пауз | Глибина, тривалість, eligibility, анти-абуз — з джерелами | O1, O3 |
| Тестовий набір | Нові eval-тікети на змінені межі, паритет Python ↔ Java | O3 |
| Промпти AI | Визначення ознак і few-shot (зміст), Codex — формат під Gemma | O7 |
| Формули грошей | Комісії, VAMP за регіоном, витрати на знижки, save rate проти holdout | O4 |

### Definition of done для Opus
- [ ] Кожне число в policy має рядок у `RULES.md` з типом і силою.
- [ ] Gold-check 100%, JUnit зелений, паритет Python ↔ Java.
- [ ] Невалідний `defuse-policy.yml` → застосунок не стартує (є тест).
- [ ] Вкладка Policy працює; path traversal заблоковано (є тест).
- [ ] Слайди не містять жодного числа без джерела.
- [ ] `LABELING.md` і `classifier-fewshot.json` запушені в `feat-opus` до 13:45.
- [ ] `HANDOFF-opus.md`: що зроблено, що змінено в правилах і чому, запити до Codex.

## 5. Що Opus перевіряє в рев'ю `feat-codex`
- **Gemma:** модель і параметри мають джерело (документація Google, model card). Немає непрогнаних тверджень про якість; eval-звіт містить model id, дату й час.
- **Невалідна відповідь моделі** (не JSON, невідомий intent, NaN) → fallback з confidence 0 → людина. Не повинно бути шляху до auto-refund.
- **Few-shot приклади** не збігаються з eval-тікетами (є тест).
- **Red-team:** жодна атака не дає дії з грошима; є кейси з `</ticket>`, гомогліфами, base64, reply_text.
- **SPI:** поведінка 1:1 з поточною (усі старі тести без змін зелені); невідомий `type` або буквальний пароль у конфігу → застосунок не стартує; дія без потрібної capability → ескалація.
- **Секретів у репо немає** (`git grep -i "AIza\|api_key"`).
- **Docs** відповідають коду.
