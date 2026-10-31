# Перехресне рев'ю feat-opus — Codex

Перевірено `origin/feat-opus` = `f8d35a7` від бази `85de9b8`, 2026-10-31. Код Opus не змінювався. Усі diff нижче — пропозиції власнику, а не застосовані виправлення. Severity відповідає CODEX-BRIEF: неправильне автоматичне переміщення грошей або неправдиве твердження на слайді — critical.

## Висновок

Поточні 44 gold-кейси проходять, але перед демо треба виправити critical C1–C3. Зелений паритет на seed не покриває пропущений період плану, нульову суму pro-rata та іншу конфігурацію. Основна продуктова архітектура збережена: LLM не визначає суму refund, policy детермінована, refund показано витратою, а не економією.

| ID | Severity | Файл:рядок | Проблема |
|---|---|---|---|
| C1 | critical | `backend/src/main/java/com/skelar/defuse/config/policy/PolicyProperties.java:37–45` | Пропущений annual приймається як 30 днів і змінює `$85.05 / suggest` на `$29.99 / auto` |
| C2 | critical | `.task/pitch/slides.md:26–27`; `docs/evidence/RULES.md:8–11` | Комерційні правила демо подано як загальний обов'язок за CRD/CJEU; Calm не підтверджує partial refund |
| C3 | critical | `.task/pitch/slides.md:28`; `docs/evidence/RULES.md:15` | Google Play неправильно прирівняно до заборони developer refunds |
| M1 | major | `backend/src/main/java/com/skelar/defuse/service/policy/ReferencePolicyService.java:187–194` | Policy планує refund на $0; executor відхиляє власне рішення policy |
| M2 | major | `backend/src/main/java/com/skelar/defuse/service/analytics/AnalyticsService.java:98–106` | Ціну квартального/річного платежу множать як місячну у вартості знижки |
| M3 | major | `defuse-data/reference_policy.py:15–51`; `backend/src/test/java/com/skelar/defuse/PolicyConfigTest.java:61–62` | Python не читає спільний YAML; тест порівнює лише згенерований JSON snapshot |
| M4 | major | `.task/pitch/slides.md:42`; `AnalyticsService.java:104–105`; `frontend/src/pages/Dashboard.tsx:28` | Acceptance названо save rate проти holdout, хоча результатів контрольної групи немає |
| m1 | minor | `backend/src/main/resources/defuse-policy.yml:10,25–33,40–57`; `PolicyProperties.java:17–22,119–141` | Не кожне число має видимий рядок evidence; посилання `#refund/#risk` не ведуть на визначені секції |

## C1 — відсутній період плану змінює гроші та режим виконання

Відтворено **на Java-класах Opus** поза Spring, без зміни файлів гілки. Конструктор приймає:

```java
var base = PolicyProperties.defaults();
var refund = new PolicyProperties.Refund(14, 2, 30, Map.of("monthly", 30), 60, 100, .85);
var incomplete = new PolicyProperties(refund, base.retention(), base.risk(), base.money(), base.sources());
```

Вхід: `cancel_refund`, confidence 1, card, active annual, останній успішний платіж $89.99 20 днів тому, 6 sessions, без попередніх refunds/disputes. З повним конфігом: `cancel + refund_partial`, **$85.05 / suggest**. З конфігом вище: ті самі actions, **$29.99 / auto**. Причина — `getOrDefault(plan, 30)`; constructor перевіряє лише непорожню map. Такий YAML синтаксично правильний, тому обіцянка «невалідний конфіг не стартує» не захищає цей випадок. Python має аналогічний fallback у `build_context():107`.

Пропонований diff:

```diff
--- a/backend/src/main/java/com/skelar/defuse/config/policy/PolicyProperties.java
+++ b/backend/src/main/java/com/skelar/defuse/config/policy/PolicyProperties.java
@@
             require(planPeriodDays != null && !planPeriodDays.isEmpty()
                     && planPeriodDays.values().stream().allMatch(d -> d != null && d > 0), "plan-period-days must be > 0");
+            require(planPeriodDays.keySet().containsAll(Set.of("monthly", "quarterly", "annual")),
+                    "plan-period-days must include monthly, quarterly and annual");
@@
-            return planPeriodDays.getOrDefault(plan, 30);
+            Integer days = planPeriodDays.get(plan);
+            require(days != null, "unknown plan period: " + plan);
+            return days;
--- a/defuse-data/reference_policy.py
+++ b/defuse-data/reference_policy.py
@@
-        "period_days": POLICY["plan_period_days"].get(sub["plan"], 30),
+        "period_days": POLICY["plan_period_days"][sub["plan"]],
```

Для невідомого плану з зовнішнього конектора policy має окремо повернути `escalate`, а не спричиняти HTTP 500; перевірку розмістити після injection/store-routing, перед розрахунком card refund. Додати startup-тест відсутнього annual та тест unknown plan → 0 money actions. Перевіряти всі плани реального конектора, якщо їх більше за три seed-плани.

## C2 — слайд про закон і partial refund не підтверджується джерелами

Відкрите первинне джерело [CJEU C-641/19, пп. 26–32](https://eur-lex.europa.eu/legal-content/en/TXT/?uri=celex%3A62019CJ0641) розглядає компенсацію за сервіс, який споживач прямо попросив почати **під час застосовного періоду відмови**. Звідси не випливає загальне право на pro-rata refund на 15–30 день після кожного renewal у всіх країнах. Поточні customer/context поля не містять юрисдикції, згоди на початок виконання чи правового статусу renewal, тож код не може перевірити застосовність цього правила. Це висновок зі співставлення умов рішення з моделлю даних, а не окремий юридичний висновок для конкретного клієнта.

[Calm, чинна refund policy](https://support.calm.com/hc/en-us/articles/360008240974-Calm-App-Refund-Policy-How-to-Get-Your-Money-Back) підтверджує 30-денне вікно для придатних web-покупок, але в FAQ прямо описує **повні**, а не часткові повернення. Отже, Calm підтримує приклад вікна, а не нашу формулу. Поріг 2 sessions залишається H, не критерієм закону.

Пропонований diff тексту; алгоритм можна залишити комерційним вибором компанії:

```diff
--- a/.task/pitch/slides.md
+++ b/.task/pitch/slides.md
@@
- - Повний refund до 14 днів, якщо сервісом майже не користувались — право на відмову в ЄС (CRD ст. 9).
- - Після цього — пропорційно невикористаним дням до 30 днів (CRD ст. 14(3), CJEU C-641/19; Calm теж повертає гроші за web-покупки до 30 днів).
+ - Демо-політика компанії: повний refund до 14 днів і до 2 sessions; далі — pro-rata до 30 днів. Пороги та застосування до renewal — наші припущення H.
+ - CRD/CJEU пояснюють withdrawal і пропорційність за визначених умов. Calm — приклад 30-денного вікна для повного web-refund; наша partial-формула потребує затвердження компанією.
--- a/backend/src/main/resources/defuse-policy.yml
+++ b/backend/src/main/resources/defuse-policy.yml
@@
-      refund-partial: "RULES.md#refund (A: CRD art. 14(3), CJEU C-641/19)"
-      refund-partial-max-days: "RULES.md#refund (A: Calm refund policy; C: Liven)"
+      refund-partial: "RULES.md#refund (H: commercial policy; conditional legal analogy CJEU C-641/19)"
+      refund-partial-max-days: "RULES.md#refund (H: chosen window; comparator Calm offers full refunds)"
```

Так само змінити тип/пояснення рядків `refund.full-max-days`, `refund.partial`, `refund.partial-max-days` у RULES і коментарі YAML/Python. Не подавати demo heuristics як реалізовану перевірку законних прав клієнта.

## C3 — Google Play: обмеження нашого конектора видано за обмеження платформи

[Офіційний Google Play `orders.refund`](https://developers.google.com/android-publisher/api-ref/rest/v3/orders/refund) дозволяє авторизованому developer refund підписки; `revoke` також може припинити майбутні платежі. [Calm](https://support.calm.com/hc/en-us/articles/360008240974-Calm-App-Refund-Policy-How-to-Get-Your-Money-Back) направляє Google Play покупців до **Calm Support**, App Store — до Apple. MVP без store capabilities цілком допустимий; пояснення має описувати саме цей scope.

```diff
--- a/.task/pitch/slides.md
+++ b/.task/pitch/slides.md
@@
- - Store-покупки повертають Apple / Google — ми даємо точні кроки.
+ - У MVP store-покупки направляємо до відповідного refund-workflow; інтеграцію Google Play developer refunds ще не підключено.
--- a/docs/evidence/RULES.md
+++ b/docs/evidence/RULES.md
@@
-| store purchases → instructions only | on | official policy · A | Calm support: App Store purchases are refunded by Apple; Apple/Google own store refunds | We cannot refund or cancel what Apple/Google billed |
+| store purchases → instructions only | on | MVP scope · H | Google Play orders.refund; Calm refund support routing | Our store connector has no write capabilities; Google Play developer refunds are possible with an authorized integration |
--- a/backend/src/main/java/com/skelar/defuse/service/policy/ReferencePolicyService.java
+++ b/backend/src/main/java/com/skelar/defuse/service/policy/ReferencePolicyService.java
@@
-                reasons.add("store_billing: only Apple/Google can refund or cancel");
+                reasons.add("store_billing: use the store refund/cancellation workflow; this connector has no write capabilities");
```

У Codex-owned `ReplyWriterService` аналогічне старе формулювання вже виправлене у `feat-codex`; перелічені файли Opus не змінювалися. `store_refund_instructions` та інші заморожені назви дій не змінюються.

## M1 — нульова сума залишається billing action

Java-відтворення: active monthly, amount $29.99, 6 sessions, `cancel_refund`, confidence 1, dispute signal true, інші прапорці false. `daysAgo=30` → `cancel + refund_partial`, **$0 / auto**; `daysAgo=40` → `cancel + refund_partial_goodwill`, **$0 / suggest**. `MockBillingService.java:57` відхиляє amount ≤ 0. Після cancel/Approve цей план закінчується billing error і зайвою ескалацією.

```diff
--- a/backend/src/main/java/com/skelar/defuse/service/policy/ReferencePolicyService.java
+++ b/backend/src/main/java/com/skelar/defuse/service/policy/ReferencePolicyService.java
@@
-                acts.add("refund_partial");
                 refund = proRata(lp.amount(), days, refundCfg.periodDays(ctx.plan()));
+                if (refund > 0) acts.add("refund_partial");
+                else reasons.add("billing_period_fully_consumed");
@@
-                acts.add("refund_partial_goodwill");
                 refund = proRata(lp.amount(), days, refundCfg.periodDays(ctx.plan()));
-                mode = "suggest";
-                reasons.add("outside_window_but_high_dispute_risk: goodwill needs a human");
+                if (refund > 0) {
+                    acts.add("refund_partial_goodwill");
+                    mode = "suggest";
+                    reasons.add("outside_window_but_high_dispute_risk: goodwill needs a human");
+                } else reasons.add("billing_period_fully_consumed");
```

Дзеркально змінити Python і додати boundary-тести `days == period`, `days > period`: cancellation зберігається, refund action відсутня. Якщо business policy хоче goodwill після повністю використаного періоду, потрібне окреме правило суми, а не виклик refund(0).

## M2 — оцінка discount cost припускає місячну ціну

Відтворено викликом справжнього `AnalyticsService.summary()`: T39 subscription змінено лише в пам'яті на quarterly / $49.99, audit містить `offer_accepted / apply_discount`. Dashboard повертає **$44.99**, бо рахує `49.99 × .30 × 3`. $49.99 — ціна одного квартального платежу. Якщо потрібна 90-денна оцінка рівномірної вартості, вона становить **$14.83** за поточним 91-day approximation; якщо йдеться про discounted invoices, треба визначити графік і порахувати їх. Прийнята пропозиція ще не є фактично понесеною витратою.

Пропонований мінімальний варіант — явно позначена прогнозна оцінка. Додати `PolicyProperties props` до конструктора AnalyticsService і зберегти поле:

```diff
--- a/backend/src/main/java/com/skelar/defuse/service/analytics/AnalyticsService.java
+++ b/backend/src/main/java/com/skelar/defuse/service/analytics/AnalyticsService.java
@@
-                    return t == null ? 0 : store.subscription(t.customerId).price * retentionPercent / 100.0 * retentionMonths;
+                    if (t == null) return 0;
+                    var sub = store.subscription(t.customerId);
+                    double monthlyEquivalent = sub.price * 30.0 / props.refund().periodDays(sub.plan);
+                    return monthlyEquivalent * retentionPercent / 100.0 * retentionMonths;
--- a/frontend/src/pages/Dashboard.tsx
+++ b/frontend/src/pages/Dashboard.tsx
@@
-        <KpiCard label="Discount cost" value={usd(s.retention.discount_cost_usd)} hint="accepted discounts × price × % × months" />
+        <KpiCard label="Estimated discount cost" value={usd(s.retention.discount_cost_usd)} hint="30-day monthly equivalent × discount × months; projected" />
```

Це модель H, не точна сума invoice ledger. Додати monthly/quarterly/annual приклади, а для фактичної витрати пізніше використовувати billing events. Зберегти чинний REST key.

## M3 — спільне джерело policy для Java/Python не реалізоване

`reference_policy.py` сам визначає POLICY dict і не імпортує YAML. `PolicyConfigTest` читає `backend/src/main/resources/data/policy.json`, тобто snapshot генератора, а не запускає Python з ефективним YAML. Зміна YAML після генерації змінить Java, але не Python. Поточні дефолти збігаються; це не перевірка configurable parity.

Відтворення: у тимчасовій копії змінити тільки `retention.discount-percent` YAML 30 → 35. Spring binding дає 35, `python3 -c 'import reference_policy as p; print(p.POLICY["retention"]["discount_percent"])'` продовжує давати 30. На перевіреній гілці цей висновок також безпосередньо випливає з незалежних визначень, без запису у файли Opus.

Пропонований diff завантаження замість дубльованого POLICY literal (потрібні PyYAML у requirements і дзеркальна валідація структури/діапазонів):

```diff
--- a/defuse-data/reference_policy.py
+++ b/defuse-data/reference_policy.py
@@
+import yaml
+POLICY_FILE = Path(os.environ.get("DEFUSE_POLICY_FILE",
+    Path(__file__).resolve().parents[1] / "backend/src/main/resources/defuse-policy.yml"))
+_cfg = yaml.safe_load(POLICY_FILE.read_text())["defuse"]["policy"]
+_r, _k = _cfg["refund"], _cfg["risk"]
+POLICY = {
+    "auto_refund_limit_usd": _r["auto-limit-usd"],
+    "full_refund_max_days": _r["full-max-days"],
+    "full_refund_max_sessions": _r["full-max-sessions"],
+    "partial_refund_max_days": _r["partial-max-days"],
+    "plan_period_days": _r["plan-period-days"],
+    "goodwill_ltv_usd": _r["goodwill-ltv-usd"],
+    "min_auto_confidence": _r["min-auto-confidence"],
+    "risk_weights": {
+        "base": _k["base"], "dispute_signal": _k["dispute"],
+        "sarcasm": _k["sarcasm"], "aggression": _k["aggression"],
+        "money": _k["money"], "per_recent_contact": _k["per-contact"],
+        "max_contacts": _k["max-contacts"]},
+    "risk_bands": {"high": _k["band-high"], "medium": _k["band-medium"]},
+    "retention": {k.replace("-", "_"): v for k, v in _cfg["retention"].items()},
+    "money": {k.replace("-", "_"): v for k, v in _cfg["money"].items()},
+}
```

Додати subprocess-тест на **змінений** тимчасовий YAML, не тільки дефолтний JSON. Якщо це не встигає до демо, у HANDOFF прямо позначити shared-config requirement незавершеним; згенеровані snapshots не називати спільним runtime source.

## M4 — offer acceptance не вимірює incremental retention

`save_rate = accepted / sent`, а `holdout_customers` — лише кількість клієнтів без offer. Немає однакового outcome в обох групах, горизонту observation, renewal/retained подій чи оцінки різниці. Тому слайд «save rate проти контрольної групи» описує відсутню метрику. Сама наявність bucket — корисна підготовка до експерименту.

```diff
--- a/frontend/src/pages/Dashboard.tsx
+++ b/frontend/src/pages/Dashboard.tsx
@@
-          hint={`accepted ${s.retention.offers_accepted} · declined ${s.retention.offers_declined} · save rate ${s.retention.save_rate ?? "—"}% · holdout ${s.retention.holdout_customers}`} />
+          hint={`accepted ${s.retention.offers_accepted} · declined ${s.retention.offers_declined} · offer acceptance ${s.retention.save_rate ?? "—"}% · holdout ${s.retention.holdout_customers}`} />
--- a/.task/pitch/slides.md
+++ b/.task/pitch/slides.md
@@
- - Дашборд окремо показує: суму під ризиком chargeback · витрати на повернення (це витрата, а не економія) · очікувану економію на комісіях · витрати на знижки · save rate проти контрольної групи.
+ - Дашборд окремо показує: суму під ризиком chargeback · витрати на повернення · очікувані уникнені комісії · прогнозну вартість знижок · прийняття пропозицій. Holdout підготовлено; retention uplift ще не виміряний.
```

REST `save_rate` залишити для сумісності; уточнити його визначення у docs. Audit-події прийняття також слід відрізняти від успішного застосування та подальшого фактичного продовження.

## m1 — evidence coverage і посилання

`refund.plan-period-days` (30/91/365), `risk.base` (.10), `risk.max-contacts` (3) не мають власного видимого рядка Policy UI / RULES; bands є в UI, але не наведені у рядку RULES з числами. YAML source містить `#refund`, `#risk`, хоча відповідних секцій/явних anchors у RULES немає. `PolicyConfigTest` шукає назву групи як підрядок, а не валідність anchor. Окремо: 90 днів з моменту offer — наше наближення, а [Churnkey](https://docs.churnkey.co/cancel-flows/offers/discounts/) пояснює, що загальне cooldown залежить і від тривалості coupon, і від cooldown period; не приписувати наш точний алгоритм джерелу.

Пропонований мінімальний diff RULES; додати ці ключі в REQUIRED_SOURCES/rules() і перевірку повного coverage числових leaves:

```diff
--- a/docs/evidence/RULES.md
+++ b/docs/evidence/RULES.md
@@
+| `refund.plan-period-days` | monthly 30, quarterly 91, annual 365 | approximation · H | — | Demo day-based periods; use real billing-period boundaries in production |
+| `risk.base` / `risk.max-contacts` | .10 / 3 | heuristic · H | — | Uncalibrated seed defaults |
+| `risk.bands` | high .50, medium .30 | heuristic · H | — | Seed cutoffs; not measured probabilities |
@@
+<a id="refund"></a><a id="retention"></a><a id="risk"></a><a id="money"></a>
```

Краще згрупувати таблицю в реальні секції з цими anchors. Зміна `cooldown` source на H з поясненням vendor inspiration — точніша за C для нашого іншого алгоритму.

## Що реально перевірено

- Чистий Opus snapshot: `./gradlew test --no-daemon` — **86 tests, 0 failures/errors/skips**. Java gold-parity охоплює всі 44 тікети, включно з T01/T05/T12/T30/T37/T39/T40/T41.
- Python: `uv run --no-project --with pytest python -B -m pytest -q test_policy.py test_codex_invariants.py` — **38 passed** в ізольованому середовищі; системний Python не мав pytest. `python3 -B defuse-data/evaluate.py --gold-check` — **44/44**. T04/T10 нові суми та T40 60-day previous offer пояснені HANDOFF; зміни відповідають новим дефолтам, але не замінюють перевірки обґрунтованості правил.
- `frontend: npm ci && npm run build` — успішно. Є попередження Vite про bundle >500 kB; воно не блокує збірку.
- PolicyApiTest перевіряє readonly whitelist RULES/LABELING, traversal та URL-encoded traversal отримують 4xx; packaged docs дорівнюють джерелу. Регресії контракту тут не знайдено.
- У тимчасовій інтеграційній копії Opus + Codex: **127 tests, 0 failures/errors/skips**. Потрібно було поєднати один YAML conflict: залишити Opus `spring.config.import`, додати Codex LLM/datasources/billing. У власному EvalService Codex прибрав залежність від конструктора ContextBuilder і зберігає immutable seed contexts при старті; новий regression test перевіряє ізоляцію eval від demo mutations.
- Java repro вище фактично виконано: missing annual, zero refunds, quarterly discount cost. Це нові boundary/config сценарії поза gold; вони пояснюють, чому зелений seed suite не спростовує findings.
- Первинне [Stripe pricing](https://stripe.com/pricing) підтверджує $15 dispute received і окрему $15 manual counter fee з умовами повернення. Значення 15 правдоподібне для обраного pricing scope; краще посилатися прямо на Stripe, а не на переказ.
- Первинний [Visa VAMP fact sheet](https://corporate.visa.com/content/dam/VCOM/corporate/visa-perspectives/security-and-trust/documents/visa-acquirer-monitoring-program-fact-sheet-2025.pdf) підтримує регіональні ratio defaults; код не вигадує універсальний $8 scheme fee. Ratio саме по собі не визначає вступ до програми: існують також count/volume та інші умови.

## Межі перевірки

- Gemma live eval, live red-team, latency/quotas — **UNVERIFIED**, немає ключа. Offline gold/parity не є вимірюванням Gemma.
- Точний актуальний Mastercard ECM threshold / період кваліфікації: **UNVERIFIED**. Відкритий [Mastercard Security Rules, 4 August 2026, §8.3.1](https://www.mastercard.com/content/dam/mccom/shared/business/support/rules-pdfs/SPME-Manual.pdf) відсилає визначення ECM до Data Integrity Monitoring Program; не достатньо просто повторити vendor summary 1.5%/100 як повну умову.
- AB 2863: офіційний bill endpoint повернув 403. Точний правовий висновок **UNVERIFIED**; prompt cancellation і повідомлення про можливість продовжити cancellation не доводять, що закон вимагає рівно одну offer. На слайді 33 позначити «одна пропозиція» як наш H/product choice до первинної перевірки.
- Vendor Churnkey цифри позначені C і не є виміряним ефектом Defuse. На seed немає незалежного causal proof зниження chargebacks або incremental retention.
