# Промт для Codex — незалежна перевірка ідеї, даних і ТЗ

> Як запускати: відкрий Codex у папці `2026-10-polytech-code-fest-skelar/` (там лежать `idea-brief.md`, `TZ-developer.md`, `defuse-data/`) і встав усе, що нижче лінії.
> Мережа потрібна для пункту B (фактчек). Якщо мережі нема, Codex позначить ці факти як `UNVERIFIED`.

---

Ти — незалежний рецензент хакатон-проєкту. Ти **не автор** цієї роботи, тому шукай слабкі місця, а не підтвердження. Відповідай українською, технічні терміни лишай англійською.

## Контекст

Хакатон polyTech Code Fest × SKELAR (Львів, 31.10.2026, здача MVP до 17:00). SKELAR — венчур-білдер із B2C-бізнесами на підписках (wellness, mental health, edtech: Liven, Leaply та ін.).

Умова челенджу: «AI-рішення для customer support, яке не просто відповідає, а допомагає вирішувати реальні проблеми». AI має:
1. розуміти справжню мету звернення;
2. пропонувати або ініціювати дії;
3. допомагати агентам приймати рішення;
4. аналізувати взаємодії та ефективність підтримки.

Критерії оцінки:
- Operational Impact
- Innovation
- Use of AI (AI в центрі системи)
- Reasoning & Context Handling
- Technical Execution (архітектура, якість коду, **безпека й тести**)
- Presentation & Demo

Команда обрала ідею **Defuse**. Це AI-оператор, який ловить у billing-тікетах сарказм, приховану агресію та сигнали майбутнього chargeback. Він виконує найдешевшу дію, що закриває проблему (cancel / refund / pause), через **детерміновану policy**: LLM лише класифікує й пише текст, а гроші рухає код.

## Файли, які треба прочитати повністю

1. `idea-brief.md` — ідея, 4 альтернативи, ринок, факти з джерелами.
2. `TZ-developer.md` — ТЗ для розробника.
3. `defuse-data/README.md`
4. `defuse-data/reference_policy.py` — policy engine (`build_context`, `risk_score`, `decide`, `priority`).
5. `defuse-data/generate_seed.py` — генератор даних і 38 сценаріїв із gold-розміткою (`SCENARIOS`).
6. `defuse-data/evaluate.py`, `defuse-data/baseline_keywords.py`, `defuse-data/test_policy.py`
7. `defuse-data/data/*.json` — згенеровані дані.

## Правила роботи

- **Не змінюй** наявні файли: gold-мітки, policy, ТЗ. Усі зміни пропонуй як unified diff у звіті.
- Нові файли створюй тільки такі: `CODEX_REVIEW.md` (звіт) і `defuse-data/test_codex_invariants.py` (твої перевірки).
- Не викликай платні LLM API.
- Кожне твердження в звіті має спиратися на конкретику: `файл:рядок`, ID тікета, вивід команди або URL. Без «загалом виглядає добре».
- Якщо щось не можеш перевірити, пиши `UNVERIFIED` і поясни чому.

---

## A. Запусти й зафіксуй вивід

```bash
cd defuse-data
python3 generate_seed.py
python3 evaluate.py --gold-check
python3 baseline_keywords.py && python3 evaluate.py data/predictions_baseline.json; echo "exit=$?"
python3 -m pytest -q test_policy.py   # якщо pytest нема: pip install pytest
```

Перевір **детермінованість**: запусти `generate_seed.py` двічі й порівняй SHA-256 усіх `data/*.json`. Вони мають бути однаковими.

## B. Фактчек ідеї (`idea-brief.md`)

Для кожного факту знайди **першоджерело** (Visa, текст директиви EU, Gartner, сторінка цін Intercom / Zendesk). Вердикт: `CONFIRMED` / `PARTLY` (що саме відрізняється) / `WRONG` / `UNVERIFIED`. Додай URL.

1. Visa VAMP: поріг «Excessive» для мерчантів знизили з 2.2% до 1.5% з 1 квітня 2026 для US/CA/EU/AP; $8 за диспут; формула ratio = (TC40 fraud + TC15 disputes) / settled CNP transactions. Чи поширюється це на CEMEA (Україна)?
2. Directive (EU) 2023/2673, «withdrawal button» (нова стаття 11a у Consumer Rights Directive), застосовується з 19.06.2026. **Чи стосується вона скасування підписки, чи тільки права на відмову (withdrawal) протягом 14 днів?** Це критично для формулювання на слайді.
3. Gartner: до 2029 agentic AI вирішуватиме 80% типових звернень, −30% витрат (березень 2025). Окремо: «лише 24% лідерів бачать позитивний ROI» (липень 2026).
4. Ринок AI customer service ~$15B (2026) → ~$47.8B (2030), MarketsandMarkets.
5. Intercom Fin $0.99 за resolution; Zendesk $1.50 / $2.00 за automated resolution.
6. Ethoca / Verifi (CDRN, RDR): ціна алерту $15–29.
7. Медіанний refund rate підписок 2.71% (Adapty).
8. SKELAR: портфель (Liven, Leaply, Duomo…); Leaply стартував як web-платформа з підпискою; у них є вакансії Billing / Chargeback support і Reputation Manager.

Окремо оціни, чи не надто натягнута теза «SKELAR продає через web-checkout карткою». Якщо продажі йдуть переважно через App Store / Google Play, VAMP їх майже не стосується. Як тоді переформулювати цінність?

## C. Red-team ідеї проти критеріїв

Для кожного з 6 критеріїв постав оцінку 1–5 і дай одне найсильніше заперечення, яке може сказати суддя SKELAR, та як його закрити (фічею або реченням у пітчі).

Обов'язково розглянь:
- Чи не виглядає продукт як «ще один Intercom Fin / Zendesk AI»? Що реально нове?
- Чи не є «агент, що сам повертає гроші» анти-бізнесом для компанії, яка живе з підписок? Чи переконує аргумент «refund дешевший за chargeback»? Чи нема ризику, що friendly fraud злетить, бо люди дізнаються, що AI легко повертає гроші?
- Чи достатньо AI «в центрі», якщо рішення приймає детермінований код? Як це подати як перевагу, а не слабкість?
- Етика й compliance: чи не виглядає `pause` / retention як dark pattern?
- Чи реалістичний обсяг ТЗ для одного розробника до 17:00? Що вирізати першим?

## D. Перевірка даних — інваріанти

Напиши `defuse-data/test_codex_invariants.py` (pytest, без мережі) і запусти. Мінімум такі перевірки:

1. ID унікальні (customers, subscriptions, payments); кожен `customer_id` у subscriptions / payments / usage / tickets існує; рівно 1 subscription і 1 usage на клієнта.
2. `ltv_usd` == сума `amount` платежів зі статусом `succeeded` (з точністю до центів).
3. Жоден `created_at` платежу чи тікета не пізніше `NOW` (2026-10-31T12:00Z). Для кожного eval-тікета його `created_at` пізніше за останній платіж клієнта.
4. Послідовність платежів правдоподібна: trial $0.99 перший; renewals через інтервал плану (30 / 91 / 365 днів); дублікат — того ж дня, з тією ж сумою.
5. `current_period_end` активних підписок у майбутньому. Для `canceled` є `canceled_at`, і він пізніше за останній платіж.
6. `usage.last_active_at` не пізніше NOW. Якщо `sessions_since_last_charge == 0`, то `last_active_at` раніше за останній платіж.
7. Платформа клієнта ↔ `processor` (web → card, ios → app_store, android → google_play) збігається в subscriptions і payments.
8. Email-и синтетичні (`@example.com`), без реальних брендів SKELAR у даних (Liven, Leaply).
9. `tickets_history`: усі `synthetic: true`; `resolution_hours >= first_response_hours`; `refund_amount > 0` лише для грошових root causes; `risk_band` збігається з `risk_score` за порогами policy.
10. Для кожного eval-тікета `decide(gold, build_context(...))` == gold (actions, mode, refund_amount, risk_band, priority). Це має покривати `--gold-check`; перевір, що він справді порівнює **всі** ці поля.
11. Жоден `refund_amount` у gold не перевищує суму останнього платежу.
12. Для `mode == "auto"` з refund > 0: сума ≤ 60, нема історії refund-ів / chargeback-ів, платіж не `disputed`.

Окремо покажи розподіли: мови, intents, risk bands, modes, кількість auto-refund-ів у eval; root causes і chargeback rate за bands у history. Чи нема перекосу, через який демо виглядатиме підігнаним (наприклад, high band у eval ~50%)?

## E. Сліпа перевірка gold-міток (найважливіше)

1. **Спочатку не дивись на `gold`.** Для кожного з 38 тікетів прочитай тільки `text` (+ контекст клієнта через `build_context`) і сам розмітити: `intent, sarcasm, hidden_aggression, explicit_aggression, dispute_signal, injection`. Користуйся визначеннями з `TZ-developer.md` §6.2 (`CLASSIFIER_SYSTEM`).
2. Потім порівняй із gold. Дай таблицю розбіжностей: ID, поле, gold, твоя мітка, аргумент, рекомендація (`keep gold` / `change gold` / `ambiguous → remove from eval or add a note`).
3. Перевір мовну якість UA / DE / ES / PL текстів: чи звучать вони як справжні клієнти, чи нема помилок і кальок.

Тікети, які автор сам вважає спірними — перевір їх особливо:
- T06 — `dispute_signal=true` лише через «keeping a record of all correspondence for whoever needs to see it».
- T07 — `sarcasm=true` через лапки у «сервіс».
- T17 — intent `cancel_refund` (справжня мета), хоча буквально питають лише «як скасувати».
- T10 — `explicit_aggression=false` при прямій погрозі банком.
- T19 — «zum Ende des Zeitraums»: policy робить `cancel` одразу, а клієнт просить у кінці періоду. Чи це баг даних або policy?
- T24 — `technical_issue` → `extend_access_7d`, хоча людина просить повернути streak.
- T25 — сарказм про баг дає `risk_band=medium`. Чи логічно?
- T38 — питання про $0.99, хоча останнім був renewal $29.99 26 днів тому.
- Межа ризику: сарказм + гроші = рівно 0.50 = high. Чи не надто крихко? Покажи, які тікети змінять band при зсуві порогу на ±0.05.

## F. Перевірка policy (`reference_policy.py`)

Пройдись по логіці й знайди баги та дірки. Для кожної знахідки дай конкретний вхід → неправильний вихід → diff. Мінімум перевір:

1. `mode="suggest"`, коли в `actions` є `cancel`: ТЗ каже, що скасування ніколи не чекає людину (§6, `execute_cancel_only`). Чи це послідовно відображено в policy, gold і ТЗ? Чи треба розділити режим для cancel і для грошей?
2. Скасування для `status == "canceled"` / `"paused"`; refund для `cancel` без refund-у; `duplicate_charge`, коли дубліката насправді нема (`duplicate_payment_id is None`) — що повертається?
3. `duplicate_charge` на iOS / Android.
4. Refund, якщо останній платіж уже `refunded`; trial-платіж $0.99 як останній; `days_ago` від'ємний.
5. `confidence < 0.85` при `escalate` / `ask_clarification`.
6. `_floor_cents` — точність float для 29.99 / 49.99 / 89.99 × 0.5.
7. Чи може LLM через `features` якось вплинути на суму refund-у? Які поля `features` небезпечні, якщо модель поверне сміття (наприклад, intent поза списком)?
8. `priority()` — чи логічно, що `technical_issue` без дій має P3, а `question` — P4?
9. Що станеться з `build_context`, якщо в клієнта нема платежів?

## G. ТЗ ↔ код ↔ дані

Перевір, що ТЗ не обіцяє того, чого нема:
- функції й поля, на які посилається ТЗ, існують з такими самими іменами (`build_context`, `decide`, `duplicate_payment_id`, `cancel_flow_abandoned`, `DEFUSE_DATA_DIR`…);
- приклад `Analysis` у §7 узгоджений зі схемою;
- цифри в ТЗ і README (7/9 сарказмів, 2 unsafe, 61% дій, P(dispute) high ≈ 0.235) збігаються з реальним виводом команд із пункту A;
- синтаксис Anthropic SDK у §6.2 / §6.5: `client.messages.parse(..., output_format=PydanticModel)` → `resp.parsed_output`; `output_config={"effort": "low"}`; моделі `claude-haiku-5-5`, `claude-sonnet-5-5`. Якщо маєш доступ до документації (github.com/anthropics/anthropic-sdk-python), звір; інакше `UNVERIFIED`;
- безпека: чи є в ТЗ дірки (ключ у фронтенді, CORS, injection через поле `reply_text` в `approve`, XSS у `HighlightedText`, якщо підсвічувати через `dangerouslySetInnerHTML`)?
- чи реально встигнути план із §14. Дай свій «мінімальний шлях до демо» на 3 години.

## Формат звіту `CODEX_REVIEW.md`

```
# Codex review — Defuse
## 0. Вердикт (5 рядків): чи йти з цією ідеєю, топ-3 ризики, топ-3 фікси до 17:00
## 1. Вивід команд (A) + детермінованість
## 2. Фактчек (B) — таблиця: факт | вердикт | першоджерело | що виправити в тексті
## 3. Red-team (C) — таблиця критеріїв + заперечення + як закрити
## 4. Інваріанти даних (D) — pass/fail + розподіли
## 5. Сліпа розмітка (E) — таблиця розбіжностей + мовні правки
## 6. Policy (F) — знахідки: severity (critical/major/minor) | вхід → вихід | diff
## 7. ТЗ ↔ код (G) — невідповідності + мінімальний шлях до демо
## 8. Усі запропоновані diff-и одним блоком (не застосовані)
```

Сортуй знахідки за severity. **Critical** — усе, що може призвести до неправильного автоматичного refund-у, пропущеної injection, неправдивого факту на слайді або падіння демо.
