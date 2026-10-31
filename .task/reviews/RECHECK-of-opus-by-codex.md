# Повторна перевірка роботи Opus — 2026-10-31

Перевірений актуальний `origin/feat`: **96a704d**. Код виправлень — merge **90935f6**; наступний коміт додає тільки `.task/DIMA-GUIDE.md`. Сама `origin/feat-opus` залишилася на `f8d35a7`, тому перевірка лише цієї гілки пропустила б нові виправлення. Перевіряв в окремому detached worktree; код Opus не змінював.

## Результат

Попередній помилковий auto-refund для невідомого періоду усунуто, нульові refund actions прибрано, квартальну ціну нормалізовано, твердження про ефект holdout виправлено. **128 Java-тестів, 38 Python-тестів, 44/44 gold і frontend build проходять.**

Залишається відтворена помилка завершення тікета: після Approve запит на refund з невідомим періодом стає resolved, хоча суми/рішення щодо refund немає. Нижче — findings і запропоновані зміни, не застосовані до гілки Opus.

## R1 · major / P1 · невизначений refund закривається після Approve

**Місце:** `backend/src/main/java/com/skelar/defuse/service/policy/ReferencePolicyService.java:207–211`; взаємодія з `TicketPipelineService.java:133–145`.

**Відтворено на реальних Spring beans**, gold, без мережі та API. Після старту:

```java
var ticket = store.tickets.get("T04");
store.subscription(ticket.customerId).plan = "legacy_unconfigured";
var a = pipeline.analyze("T04", false);
pipeline.approve("T04", "reviewer", null, a.analysisId);
```

Фактичний результат до Approve:

```text
status=needs_approval
actions=[cancel], mode=suggest, refundAmount=0.0
reasons=[unknown_plan_period: human decides the amount]
executed=[cancel → canceled]
```

Після Approve:

```text
status=resolved
executed=[cancel → canceled, cancel → skipped: already executed]
reply=Your subscription is cancelled and you won't be charged again.
```

Тікет T04 просить cancellation і refund. Approve не дозволяє визначити суму — він лише виконує збережені actions. Виправлення C1 зупинило неправильний переказ, але залишило невирішений refund у стандартному approval-flow, який закриває тікет. Новий `missingPlanPeriodGoesToHumanInsteadOfGuessing` перевіряє лише policy, тому цього не ловить.

**Пропоноване мінімальне рішення:** невизначений refund направляти в окремий human/escalation стан без стандартного Approve. Базовий fail-closed diff policy:

```diff
@@ if (proRataRefund && refundCfg.periodDays(ctx.plan()) < 0) {
             acts.removeIf(a -> a.startsWith("refund_"));
             refund = 0;
-            mode = "suggest";
+            mode = "escalate";
             reasons.add("unknown_plan_period: human decides the amount");
```

При інтеграції зберегти окреме виконання достовірно запитаної cancellation, якщо вона має залишатися негайною: ескалація невизначеного refund не повинна закривати його як resolved. Додати pipeline-тест, що немає ані переказу з вигаданою сумою, ані успішного завершення refund через no-op Approve.

## R2 · major / P2 · Python та Java не мають спільного runtime policy

**Місце:** `defuse-data/reference_policy.py:15`; `backend/src/test/java/com/skelar/defuse/PolicyConfigTest.java:60–62`.

Попереднє M3 залишається відкритим і прямо визнане в DIMA-GUIDE. Python досі визначає власний POLICY literal; Java читає YAML. Тест `pythonReferenceUsesTheSameNumbers` порівнює Java з JSON snapshot, а не Python з ефективним YAML. Дефолтні 44 кейси узгоджені, але зміна company policy не гарантує паритету runtime.

**Відтворення:** змінити тільки YAML `retention.discount-percent` з 30 на 35 у копії для перевірки; Java отримає 35, незалежний Python literal залишиться 30. Це також видно без зміни гілки з двох незалежних визначень. Цей сценарій не видається за виконаний subprocess-тест зміненого YAML.

**Пропонований diff:** замінити POLICY literal YAML-loader-ом і перетворенням ключів, наведеними в попередньому `REVIEW-of-opus-by-codex.md`, додати PyYAML та валідацію. Новий тест має запускати обидва runtime зі зміненим тимчасовим YAML. До цього документація має називати Python mirror дефолтів, а не спільним джерелом конфігурації.

## R3 · major / P2 · Policy UI досі називає бізнес-правило законом

**Місце:** `backend/src/main/resources/defuse-policy.yml:40–43`; `frontend/src/pages/Policy.tsx:5–9`.

RULES уже правильно містить H для demo refund rules, але YAML sources не змінено. Фактичне значення з `PolicyProperties.rules()`:

```text
key=refund.partial
source=RULES.md#refund (A: CRD art. 14(3), CJEU C-641/19)
```

UI бере першу літеру в дужках і показує **A · law / official**. Таким чином, виправлене пояснення справа суперечить badge зліва. Це внутрішня неузгодженість реалізації; первинні джерела й межі правової аналогії вже описані в попередньому рев'ю.

```diff
@@
-      refund-full-max-days: "RULES.md#refund (A: Directive 2011/83/EU art. 9)"
+      refund-full-max-days: "RULES.md#refund (H: business rule; legal context CRD art. 9)"
-      refund-partial: "RULES.md#refund (A: CRD art. 14(3), CJEU C-641/19)"
+      refund-partial: "RULES.md#refund (H: business rule; conditional legal context CJEU C-641/19)"
-      refund-partial-max-days: "RULES.md#refund (A: Calm refund policy; C: Liven)"
+      refund-partial-max-days: "RULES.md#refund (H: chosen window; benchmark Calm full web refunds)"
```

Додати перевірку типу доказу з API, а не тільки наявності слова RULES. Уточнити також коментарі YAML і Javadoc proRata.

## R4 · major / P2 · README дає неправильний ключ для live-запуску

**Місце:** `README.md:22`; аналогічно `docs/architecture.md:10–23`.

README досі пропонує `DEFUSE_LLM_MODE=live ANTHROPIC_API_KEY=... ./gradlew bootRun` і описує Claude. Фактичний `GemmaClient.java:25–26` читає налаштований `GEMINI_API_KEY`; ключ Anthropic не задовольнить перевірку. Той, хто йде за основним README, отримує fallback без live Gemma. Новий DIMA-GUIDE описує правильний ключ, але не виправляє основну точку входу.

```diff
--- a/README.md
+++ b/README.md
@@
-DEFUSE_LLM_MODE=live ANTHROPIC_API_KEY=... ./gradlew bootRun   # real Claude
+# Inject GEMINI_API_KEY into the backend environment first.
+DEFUSE_LLM_MODE=live ./gradlew bootRun                     # live Gemma
```

Замінити також Claude model names у README/architecture і додати gold/live/replay інструкції з `docs/features/triage/integrations.md`. Це був запит до власника у HANDOFF, а не зміна Codex-owned файлів.

## R5 · minor / P2 · Google Play у слайді направляється людині, у коді — auto-resolved

**Місце:** `.task/pitch/slides.md:28`; `ReferencePolicyService.java:155`.

Слайд каже «у демо це йде людині». Фактично на T16 через Spring pipeline:

```text
status=resolved
actions=[store_refund_instructions], mode=auto, refundAmount=0
reasons=[store_billing: only Apple/Google can refund or cancel]
executed=[{noop=true, action=store_refund_instructions}]
```

Коректне твердження про можливість developer refunds додано в RULES/слайд, але старе неправдиве пояснення залишилось у decision reasons. Instructions-only є допустимим MVP scope; опис має збігатися з ним.

```diff
--- a/.task/pitch/slides.md
+++ b/.task/pitch/slides.md
@@
-у демо це йде людині
+у демо надаємо інструкції; developer refund integration ще не підключено
--- a/backend/src/main/java/com/skelar/defuse/service/policy/ReferencePolicyService.java
+++ b/backend/src/main/java/com/skelar/defuse/service/policy/ReferencePolicyService.java
@@
-                reasons.add("store_billing: only Apple/Google can refund or cancel");
+                reasons.add("store_billing: this connector supports instructions only");
```

Якщо бажана саме human queue, потрібно змінити routing та перевірити status, а не тільки текст слайду.

## R6 · major / P2 · аналітика знову вгадує 30 днів для невідомого плану

**Місце:** `backend/src/main/java/com/skelar/defuse/service/analytics/AnalyticsService.java:105–107`.

У фінансовій policy fallback на 30 днів прибрано, але новий discount-cost розрахунок додає такий самий `periodDays.getOrDefault(sub.plan, 30)`. У Spring harness accepted-discount event + subscription `plan=legacy_unconfigured`, `price=89.99` дає **discount_cost_usd=80.99**. Період невідомий, тому підстав для цієї числової оцінки немає. Для відомого quarterly плану нормалізація працює правильно ($14.83).

**Пропонований diff концепції:** прибрати fallback, повертати відомий subtotal з явним переліком непрорахованих планів; UI має показати, що сума неповна. Не відображати пропущений випадок як нульову реальну витрату.

```diff
@@ before discountCost calculation
+        Set<String> unpricedPlans = new LinkedHashSet<>();
@@
-                    int period = periodDays.getOrDefault(sub.plan, 30);
+                    Integer period = periodDays.get(sub.plan);
+                    if (period == null) { unpricedPlans.add(sub.plan); return 0; }
@@ after discountCost calculation
+        retention.put("discount_cost_unpriced_plans", List.copyOf(unpricedPlans));
```

Додати імпорти, additive frontend type та видиме попередження про неповну оцінку. Альтернатива — не допускати неповний plan map на старті й невідомі плани в offer-flow. Додати boundary-тест unknown plan; поточний новий тест C1 не перевіряє аналітику.

## Перевірені виправлення попереднього рев'ю

| Попередній пункт | Поточний результат |
|---|---|
| C1: annual → 30 days → неправильний auto refund | Вигаданий період прибрано; тепер cancel/suggest/$0 з причиною unknown_plan_period. Завершення approval має R1 вище |
| C2: CRD/Calm як загальний обов'язок | RULES і слайди уточнено. YAML/Policy badge залишилися старими — R3 |
| C3: Google developer refunds неможливі | RULES/слайд визнають можливість. Decision reason і фактичний routing потребують R5 |
| M1: refund action на $0 | Прибрано. Java boundary-виклик monthly day 30/40 більше не повертає refund action; на day 40 залишився зайвий suggest через goodwill |
| M2: квартальна ціна рахується як місячна | Фактичний `AnalyticsService.summary()` для quarterly $49.99 і accepted 30% × 3 months повертає **$14.83**, раніше $44.99. Це прогноз за 30/91-day approximation; для unknown плану залишається R6, UI варто назвати Estimated discount cost |
| M3: shared YAML | Не реалізовано — R2 |
| M4: acceptance видається за holdout uplift | Слайд виправлено; UI показує acceptance, backend дає acceptance_rate. Поточний frontend узгоджений із backend |
| m1: anchors/кожне число має джерело | Відкладено; точні anchors і повний coverage досі відсутні |

`save_rate` не було у frozen base `85de9b8`, а frontend/types оновлені разом із новим backend. Тому перейменування у `acceptance_rate` **не включене у поточні дефекти**. Для зовнішніх клієнтів, які вже споживали проміжний snapshot, варто тимчасово залишити alias.

## Верифікація та межі

- Java: `JAVA_HOME=...openjdk@21... GRADLE_USER_HOME=/tmp/defuse-codex-gradle ./gradlew test --no-daemon` — **128 tests, 0 failures, 0 errors, 0 skipped**.
- Python: `uv run --no-project --with pytest python -B -m pytest -q test_policy.py test_codex_invariants.py` — **38 passed**; `python3 -B evaluate.py --gold-check` — **44/44**.
- Frontend: `npm ci && npm run build` — успішно. Перевірка була збіркою; browser E2E у цьому повторному проході не запускалось.
- Додатковий зовнішній Java harness зі справжнім Spring context (`WebApplicationType.NONE`) фактично виконав unknown-plan analyze → approve, T16 store routing, quarterly discount calculation і перевірку evidence source. Harness не змінював source файли гілки.
- `96a704d` після перевіреного code snapshot додає лише guide; Java/frontend/Python source і test results відповідають цьому актуальному стану.
- HANDOFF requests на provider/model/time у badge і README/architecture досі виконані не повністю: App показує лише `LLM mode`. Для replay немає запитаного timestamp записаного run.
- Live Gemma, latency/quotas, model safety/quality та live red-team залишаються **UNVERIFIED** через відсутність ключа. Успішний offline suite не вимірює ці властивості.
- Phase 2 у нових документах — план. Наявність CODEX-BRIEF-2 не використана як інструкція виконувати іншу задачу під час цього рев'ю.
