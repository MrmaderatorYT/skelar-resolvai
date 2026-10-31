# Defuse: ринкова валідація, buyer persona та стратегія для SKELAR

**Дата аналізу:** 31.10.2026  
**База:** наданий контекст Defuse (станом на 31.10.2026), транскрипт зустрічі зі стейкхолдером, офіційна документація Visa/Stripe/Apple/Google/Intercom/Zendesk, RevenueCat 2025–2026, вакансії SKELAR, публічні випадки звернень користувачів.  
**Класи доказів:** A — первинне правило, закон, офіційний опис продукту чи політика; B — статистичний звіт із методологією; C — твердження постачальника про свої результати або невідрепрезентативні кейси; H — наша робоча гіпотеза; UNVERIFIED — не підтверджено.

## Executive decision

**Рекомендація:** звузити Defuse з «AI support для всіх» до **AI Billing Resolution Engine for web-to-app subscription businesses**, з демонстраційним фокусом на wellness/fitness та другорядними ринками AI-apps і edtech. Defuse має показати три відмінності: (1) знаходить *правильне джерело платежу й об'єднує контекст* між web / app-store / кількома акаунтами; (2) виконує тільки дозволені правилами дії, з ідемпотентністю та approval для неоднозначних кейсів; (3) генерує структуровані продуктові інсайти і бачить recurring root cause. Не позиціювати просто як «бот, що може refund»: це вже роблять конкуренти.

**Якість висновку:** Problem–solution fit підкріплено статистикою та публічними сигналами, але **готовність платити, обсяг billing-черги і фактична частка web-checkout в конкретних SKELAR-продуктах не верифіковані**.

## 1. Який користувач і покупець

**Компанія (ICP, гіпотеза H):** B2C subscription/web-to-app продукт, міжнародний, продає напряму через web checkout карткою і, можливо, через Apple/Google, має високу частоту коротких trial та автопродовжень, підтримку в Zendesk/Intercom, окрему billing/payments команду, кілька цінових пакетів/checkout-потоків, достатній потік звернень, щоб вручну перевіряти контекст стало дорого. Сфери: wellness, fitness, mental health; далі AI creative/productivity і edtech.

**Хто користується:** L1/L2 Billing Support Specialist, refund/dispute analyst, team lead. **Хто купує/володіє бюджетом:** Head of Customer Support/CX або Head of Payments/Risk. **Хто погоджує:** Product, Finance, Legal/Compliance, Security/Engineering. Конкретні бюджети та середній чек цього ICP — UNVERIFIED.

**Jobs-to-be-done компанії:** «За один інцидент покажи, хто списав кошти, чи залишилась інша активна підписка, чи має людина право на refund, чи вже був refund/dispute; підготуй безпечне рішення, зменш ручну роботу, не зроби друге списання/повернення й поясни повторювані проблеми продукту».

### Чому SKELAR — точний приклад

1. Офіційний перелік SKELAR містить Liven, Leaply, Brighterly та інші B2C продукти (A, https://www.skelar.tech/).
2. Вакансія Support Specialist (Billing & Payments) описує refunds, failed payments, fraud, disputes, chargeback threats, root cause, зв'язок із product/payments/compliance (A для заявлених job-to-be-done; https://jobs.dou.ua/companies/skelar/vacancies/375385/).
3. Вакансія Billing Support Specialist в SKELAR прямо згадує Zendesk, скасування і refund (A, https://jobs.dou.ua/companies/skelar/vacancies/333901/).
4. Публічна policy Leaply від 21.09.2026 підтверджує web purchases, автооновлення та правила скасування (A, https://theleaply.com/en/subscription-policy/).
5. У публічних скаргах BBB на Liven є опублікована відповідь компанії про одночасну in-app і web підписки, коли скасування першої не завершило другу; компанія повідомила про повернення списань за web-підпискою (C: одиничний випадок, не репрезентативний рівень проблеми; https://www.bbb.org/us/nv/las-vegas/profile/self-improvement-coach/liven-1086-90093506/complaints).

**Не стверджувати:** «SKELAR точно має високий chargeback ratio», «всі його продукти використовують Stripe», «Х% його підписок оплачені на web» — UNVERIFIED.

## 2. Неочевидні болі, які дійсно варто вирішувати

| Ситуація | Чому типовий FAQ/chatbot недостатній | Що робить Defuse | Статус доказу |
|---|---|---|---|
| **Дві підписки на різних rails (App Store + web)** | «Я вже скасував» може означати скасування лише однієї підписки | Об'єднує billing identity, перевіряє обидва контракти, показує дві активні підписки, обробляє/маршрутизує кожну за її провайдером | C: BBB/Liven; A: RevenueCat multi-store docs |
| **Невидимий для покупця статус refund** | `refunded` у PSP не означає, що гроші вже на рахунку; бувають reversal, pending, ARN/STAN | Зчитує статус, пояснює ETA й трасування, надсилає доказ та не створює повторний refund | A: Stripe refund statuses |
| **Клієнт каже «це шахрайство», але має інший email/акаунт** | Пошук одного ID не знаходить платіж і створює хибне «оплати немає» | Верифікує особу, здійснює дозволений пошук по безпечних ідентифікаторах, збирає evidence pack, ескалує підозріле | H, потребує верифікації інтерв'ю і privacy review |
| **Скасування в UI, але автопродовження лишилось у web** | Технічний стан підписки відрізняється від стану, який бачить клієнт | Перевіряє webhook / renewal state / cancellation state, відкриває product bug, не обіцяє помилкову відміну | C: BBB; A: store/RevenueCat docs |
| **Повторне звернення після неефективного refund-бота** | Повторні шаблонні відповіді можуть передувати банківському диспуту | Розрізняє невдоволення й реальну погрозу, перевіряє старі тікети, пріоритезує SLA й просить approval | H: причинний ефект на chargebacks не доведено |
| **Невідповідність policy / реальна правова підстава** | ЄС, Каліфорнія, store billing та локальна політика мають різні правила | Вибирає policy за contract type, payment rail, юрисдикцією та версією політики; в неоднозначному випадку людина | A: закони й офіційні правила |
| **Refund abuse через повторні акаунти** | Кожен акаунт поодинці виглядає як перший goodwill | Перевіряє дозволені anti-abuse signals, ставить на ручну перевірку; не прирівнює збіг картки до доведеного fraud | A: Stripe fingerprint concept; H: конкретні пороги |
| **Продукт сам породжує системний billing-біль** | Тікети закриваються, а причина множиться після змін paywall/checkout | Кластеризує причини, співвідносить їх з channel, offer, locale, release, payment rail, передає product action | H: ефект до A/B тесту не доведений |

## 3. Ринок: що виміряно, а що ні

**Макроорієнтир, НЕ TAM Defuse:** у 2025 Sensor Tower оцінив глобальні in-app покупки/платні застосунки на iOS і Google Play у **$167 млрд** (+10% р/р); non-gaming apps перевищили games. Ці гроші не дорівнюють billing support або web-to-app TAM і не включають усі web-платежі (B, https://sensortower.com/press/press-release-boosted-by-gen-ai-services-consumers-spent-more-money-in-apps-than-games-for-first-time).

**Дані ближче до задачі:** RevenueCat 2025 повідомляє про refunds у першому періоді оплати: education **4,86%**, health & fitness **4,71%**, gaming/business близько **2,67–2,88%**, travel **1,51%**. Це НЕ частка тікетів і НЕ Visa disputes (B, https://www.revenuecat.com/state-of-subscription-apps-2025). За RevenueCat 2026, median refund серед AI apps — **4,2%**, non-AI — **3,5%**; у productivity median **4,7%**. Визначення report — частка paid subscriptions, refunded during first billing period, *не* частка всіх платежів; висновки здебільшого стосуються store subscription datasets (B, https://www.revenuecat.com/state-of-subscription-apps).

**Структурне зростання:** AppsFlyer на агрегованих даних своєї вибірки зафіксував **+105% р/р** зростання store subscription revenue у Q1 2026; це НЕ світове зростання ринку (B, методологія вибірки, https://www.appsflyer.com/resources/reports/app-marketing-monetization-report/).

**Невідомо з відкритих надійних джерел:** частка cancel/refund/billing серед усіх тікетів окремо для кожної вертикалі, репрезентативна chargeback rate саме web-to-app wellness бізнесів, кількість B2C-компаній із багатоканальним billing, їхній середній budget. Цифри з маркетингових статей платіжних сервісів не приймати за незалежний ринковий benchmark. Власне синтетичне демо Defuse — не ринок.

### Порівняння ніш (fit 1–5 = H, аналітична оцінка)

| Ніша | Доказ болю | Наявна статистика | Фокус для Defuse | Fit |
|---|---|---|---|---:|
| Wellness / mental health | SKELAR/Liven, web+store, recurring | H&F refund 4,71% (2025; суміжна категорія) | cross-rail cancellation, authorization, refund context | **5/5** |
| Fitness / nutrition | Trial, сезонність, recurring | H&F 4,71% (2025) | non-use / pause, late cancellation, renewal | **5/5** |
| AI creativity / productivity | Дорожчий experiment/expectation gap | AI 4,2% vs non-AI 3,5%; productivity 4,7% (2026) | buyer's remorse, usage-based refund, high-price exceptions | **4,5/5** |
| Edtech / languages | Мотиваційні підписки, рівні/пакети | Education 4,86% (2025) | plan/lesson access, duplicate entitlements, renewal | **4,5/5** |
| Dating | Складна EU нормативка, reputation | Вертикальний достовірний benchmark UNVERIFIED | cancellation, partial withdrawal, evidence | **3,5/5** |
| VPN / utilities | Короткі trials, кросплатформа | Категорійний chargeback benchmark UNVERIFIED | refund & entitlement mismatch | **3,5/5** |
| Streaming / media | Великі обсяги recurring, кілька каналів | Надійна частка billing-ticket UNVERIFIED | account family, bundle mismatch, billing confusion | **3/5** |
| SMB SaaS | Seats/proration/invoice, бюджети | Business median refund із 2025 нижча за wellness | прорутація, plan, invoice та VAT | **3/5** |
| Ecommerce subscriptions | Replenishment/shipping + billing | Чітка частка billing-ticket UNVERIFIED | pause/skip/return, але Gorgias/Shopify зрілі | **2,5/5** |
| Telecom / ISP | Сильна операційна складність | Конкретні rates UNVERIFIED | довга інтеграція, voice, CRM, регуляція | **2/5** |

**Топ-3 go-to-market:** 1. wellness/fitness web-to-app; 2. AI creativity/productivity; 3. edtech. Для пітчу SKELAR — лише **перший** як чіткий beachhead.

## 4. Конкуренти: що не можна називати унікальним

| Конкурент | Підтверджена функція | Диференціація Defuse (гіпотеза, а не доведений moat) |
|---|---|---|
| Intercom Fin | External API, cancel/refund Procedures, HITL approvals, аналітика; **$0,99/outcome** | спеціалізована reconciliation web+store+payment policies і policy explainability |
| Zendesk AI agents | Читати/писати в зовнішні системи, auto-assist approval; **$1,50 committed, $2 pay-as-you-go resolution** | вертикальні конектори й SKU/chargeback context, plug-in до наявного Zendesk |
| Decagon | Публічний кейс Substack: AI робить refunds/cancellations і Voice-of-Customer insights | саме subscription billing / risk incident ledger, не generic AI agent |
| Lorikeet / Sierra | Складні workflow та backend actions, billing і retention | не конкурувати по «агент може діяти» |
| Chargeflow, Verifi RDR/CDRN, Ethoca | Pre-dispute alert / авто-кредит / chargeback prevention **після** звернення до банківського каналу | Defuse діє **до** звернення в банк — на рівні support + product fixes; має інтегруватися, а не заявляти заміну мережевих рішень |
| Churnkey / ProsperStack | Cancel flows, offers, pause / retention | Defuse — передусім investigation & resolution; retention тільки опціональний модуль |

Офіційні джерела: https://www.intercom.com/help/en/articles/10257113-how-to-set-up-fin-tasks ; https://www.intercom.com/help/en/articles/14468561-human-in-the-loop-approvals-for-fin-procedures ; https://www.intercom.com/pricing ; https://www.zendesk.com/pricing/ ; https://support.zendesk.com/hc/en-us/articles/9174548349978-About-actions-for-auto-assist-and-action-flows ; https://decagon.ai/case-studies/substack ; https://verifi.review.visa.com/solutions-sellers/resolve-disputes.html ; https://help.chargeflow.io/en/articles/9477790-intro-to-chargeflow-alerts . **Доказ: A** для власних функцій/цін, **C** для описів результатів.

**Особлива загроза:** Fin має навіть затвердження refunds людиною. Отже “human-in-the-loop” — стандартна вимога, не власна перевага. Щоби зробити moat, покажіть integration depth, reconciliation, owner-of-action, версіоноване правило, ідентичність фактичної транзакції, інцидентний feedback loop.

## 5. Валідація тез зустрічі

| Порада стейкхолдера | Оцінка | Практичний висновок |
|---|---|---|
| «Знайдіть конкретний домен, а не всіх» | **Так**: ринок конкурентний | Wellness web-to-app — перша vertical, 1–2 складні сценарії |
| «Backend rules і гейти кожного бізнесу» | **Так**, safety-critical | Config policy + event hooks + approval: не hardcode за словами LLM |
| «$50 повернення можна auto, більше — людина» | **Частково**: приклад, не ринкове правило | Розмір refund, юрисдикція, клієнт/платіж, наявність dispute, сума refund цього клієнта за період |
| «Оцінювати відсоток помилки AI (3% / 0,85 confidence)» | **Як принцип так; числа не валідовані** | Не покладатись на self-confidence LLM; empirical calibration по класах дій + abstain |
| «AI підготує 95%, людина натисне yes/no» | **Правильний workflow; 95% UNVERIFIED** | Показати evidence pack, точний original payment, why decision, one-click approve |
| «Потрібна аналітика, топ-5 проблем» | **Так, але не унікально** | Root-cause + cohort/rail/offer/release + fix owner + confirmed outcome |
| «Алерти» | **Так, якщо action-oriented** | Dispute-threat SLA, duplicate subscription, rising refund-pending, policy failure, VAMP exposure |
| «Зробіть у вебі» | **Так як demo UX** | Client web chat/widget + separate support web-console; backend APIs |
| «AI сам фіксить баги» | **Не зараз**: великий scope/security | Створювати Jira-ready bug/evidence; не змінювати production code |
| «Відеочат з AI» | **Не підтверджено потребу ICP** | Виключити з MVP |
| «Шукайте складні ручні кейси» | **Так** | cross-rail duplicate, refund-not-arrived, wrong merchant, cancellation mismatch |

## 6. Платіжна й правова валідація: термінові поправки

1. **Visa VAMP.** Visa рахує не просто “chargebacks / payments”, а **(TC40 fraud + TC15 disputes) / settled card-not-present transactions**. Для AP/Canada/EU/US з 01.04.2026 “Excessive Merchant” — **1,5%** і щомісячний обсяг **≥1500** fraud+dispute випадків; для CEMEA — **2,2%** із додатковими вимогами **≥150 випадків і ≥$75k**. Не застосовувати поріг за місцем проживання клієнта — верифікувати регіон мерчанта/еквайрингу. **A:** https://corporate.visa.com/content/dam/VCOM/corporate/visa-perspectives/security-and-trust/documents/visa-acquirer-monitoring-program-fact-sheet-2025.pdf
2. **Chargeback refunds ≠ автоматична економія.** Stripe US: **$15 dispute received** і **$15 disputed countered** (останній повертають, якщо виграли). Ранній refund означає відмову від виручки. Приклад H: якщо платіж $49, диспут коштує $15 і кожен диспут програється, refund зараз економічно вигідний лише за ймовірності диспуту **понад 49/(49+15) = 76,6%**, якщо ігнорувати операційні й репутаційні витрати та мережеві пороги. Не оцінювати «saved principal» як економію. **A:** https://stripe.com/pricing ; https://support.stripe.com/questions/dispute-fees-faq
3. **Завдання Defuse ≠ Verifi.** У Verifi RDR/CDRN уже є pre-dispute rule-based refund, а у Chargeflow alerts — отримання сигналу з мереж. Ви маєте шукати проблему **раніше**, коли користувач ще контактує із сапортом. **A:** https://verifi.review.visa.com/solutions-sellers/resolve-disputes.html
4. **“14 днів і ≤2 сесії ⇒ повний refund за законом ЄС” — НЕ можна називати A.** Withdrawal right за Consumer Rights Directive залежить від типу цифрового продукту/послуги та правил express consent/втрати права, а також конкретної національної імплементації. «≤2 сесії» — вигадане продуктове правило, не норма ЄС. Високоризикові рішення за jurisdiction+product type+consent evidence → human. **A:** https://europa.eu/youreurope/citizens/consumers/shopping/shopping-consumer-rights/index_en.htm ; https://www.europe-consommateurs.eu/en/topics/guarantees/withdrawal/
5. **EU withdrawal function** застосовується із 19.06.2026 до відповідних дистанційних онлайн-контрактів протягом строку withdrawal. Не плутати «withdrawal button» із кнопкою скасування будь-якого auto-renew у будь-який момент. **A:** https://eur-lex.europa.eu/eli/dir/2023/2673/oj/eng
6. **California AB 2863** діє до контрактів, укладених/змінених/подовжених з 01.07.2025; online retention offer не може блокувати явно доступний «click to cancel». **A:** https://legiscan.com/CA/text/AB2863/id/3019477
7. **App Store і Google Play різні.** Apple часто маршрутизує refund користувача через reportaproblem; Google дозволяє developer-assisted refunds у деяких випадках. Не встановлювати одну універсальну політику «store = лише інструкція». **A:** https://support.apple.com/en-gb/118223 ; https://support.google.com/googleplay/answer/2479637?hl=en
8. **Regex не зупиняє prompt injection як клас.** OWASP радить defense-in-depth, permissions на tools, schema validation, approval, тестування attack variants. Навіть якщо keyword-фільтр працює на 3 демо атаках, не стверджувати «захищено від injection». **A:** https://genai.owasp.org/llmrisk/llm01-prompt-injection/
9. **Self-reported confidence 0,85 ≠ 85% правильних рішень.** Калібрувати на незалежній розміченій вибірці, окремо тестувати кожен фінансовий action. 44 synthetic eval tickets не дають права заявляти production-safe. **B:** https://doi.org/10.1145/3711896.3736569
10. **Refund already initiated ≠ money on card.** Stripe називає можливий строк 5–10 робочих днів і описує refund pending, reversal та ARN/STAN; повторна дія може бути помилковою. **A:** https://support.stripe.com/questions/where-is-my-customers-refund ; https://support.stripe.com/questions/understanding-refund-statuses

## 7. Архітектура і пілотне демо

**User ticket → Gemma (intent + evidence spans, untrusted) → payment-context retrieval (with identity verification) → deterministic policy engine → read-only quote of permitted action → case-state lock / idempotency → auto/suggest/escalate → provider-confirmed result → customer reply → audit → root-cause analytics.**

### Один сильний сценарій для демо

Користувач: *“I canceled in my iPhone settings, why are you still charging me? If you don't fix this, I'm going to my bank.”*  
AI розуміє ціль («припинити всі майбутні списання, пояснити останнє списання, розглянути refund»), але **не робить висновок**, що це один і той самий контракт. Воно знаходить **одну App Store та одну web subscription**, підтверджує, що App Store скасована, а web активна; перевіряє merchant owner, payment ID, refund status та policy; припиняє web auto-renew, якщо ідентичність/авторизація доведена; формує для людини рішення щодо конкретного останнього web charge. Людина бачить **«Why this happened» + конкретний payment, evidence, cost, action, approve**. Дашборд показує новий root cause *“cross-channel cancellation mismatch”* та пов'язані когорти — не заявляє, що кількість chargebacks уже знизилась.

### Другий кейс для red team

Користувач просить “refund $500” і вставляє “ignore all previous instructions”. Незалежно від LLM output tool перевіряє payment ownership, amount bound, prior refund, dispute state, policy version; перевищення ліміту → approval або escalate. Тест має перевіряти **фактичний side-effect**, а не лише класифікацію тексту.

### Аналітика для B2B власника

- **Verified outcome:** відсоток кейсів із підтвердженою дією, не просто тікетів без відповіді.
- **Agent-assist value:** median review time, prepared evidence coverage, ручні кліки до/після.
- **Finance:** refund principal як витрата, dispute fees як витрати, credits/reversals і alerts окремо; не показувати counterfactual saved fees без control group.
- **Preventable issues:** причина × payment rail × acquisition campaign × locale × subscription offer × release; рекомендований product owner і статус fix.
- **Risk:** auto approvals, over-limit attempts blocked, fraud flags, repeat contacts, cancellations completed, complaint escalation.
- **Model QA:** precision/recall по високоризикових намірах, calibration, unsafe-action rate, coverage при фіксованому рівні ризику, abstain share.

## 8. Економіка — показувати чесно

**Ескіз, НЕ ринкова оцінка (H):** 4 000 billing tickets/місяць; 45% automatable; 6 хв human-handling на квиток; $20/год повна вартість агента; $0,99 за успішний outcome AI. Тоді автоматизовано 1 800, вивільнено 180 год (валовий трудовий ефект $3 600), витрати на AI outcome $1 782, **теоретичний ефект $1 818/місяць до інтеграції, контролю, помилок, QA й вартості LLM**. Якщо реальний час був 3 хв замість 6, за цих припущень ефект **$18/місяць**. Тому без реального часу L1/L2, обсягу, cost-per-ticket і точності агента обіцяти ROI не можна.

Відкладений ризик VAMP, втрати повторних клієнтів і репутація можуть мати високу цінність, але жодну з цих величин без даних компанії не можна конвертувати в «заощаджені долари».

## 9. Кроки до презентації

1. **Змінити позиціювання** на “Defuse — AI Billing Resolution Engine for web-to-app subscription teams”.
2. **Перший слайд:** проблема *“One user, two subscriptions, three systems, one angry message.”*
3. **Market evidence:** RevenueCat 2025 wellness/education refund benchmarks, 2026 AI refund spread, Visa VAMP з правильною виноскою про 1,500 cases, SKELAR Billing/Payments vacancy.
4. **Demo:** об'єднання web + in-app, safe cancellation, conditional refund approval, audit trail, root-cause dashboard.
5. **Differentiation:** не “action not chatbot”; Intercom/Decagon роблять те саме. Ваше — exact transaction/rail + domain policy + reconciliation + evidence + product feedback loop.
6. **Proof:** чітко назвати 44 tickets synthetic, окремо baseline keyword 61%, LIVE Gemma result **TBD**, доки не отримано справжній тест; показати red-team failures blocked.
7. **Комерція:** не вигадувати ARR або TAM; показати чіткий buyer, поточні витрати та гіпотезу pilot pricing, яку має підтвердити інтерв'ю.

## 10. Критичні питання для customer discovery

1. Яка частка звернень за останні 30 днів — refund, cancel, fraud, duplicate charge, pending refund? Скільки реальних тікетів?
2. Які 5 кейсів потребують більше двох систем і понад 5 хв роботи людини?
3. Скільки subscription rails і PSP використовуєте, чи може один клієнт мати одночасно web і store plans?
4. Чи траплялося, що cancel підтверджене в app, але web renewal не зупинився? Скільки випадків на місяць?
5. Скільки тікетів із погрозою банком призводять до спору? Як зараз це вимірюєте?
6. Яка фактична частка дискусій із клієнтом, у яких refund уже initiated/pending і не дійшов?
7. Де approval обов'язкове і як формулюються policy: risk bands, суми, ринки, клієнтські категорії?
8. Які системи зараз використовують Support/Billing/Risk? Хто owner цих інтеграцій?
9. Скільки коштує один billing resolution (час + SLA + licensed software), скільки коштує помилка?
10. Що має статися за 30-денний пілот, щоб ви заплатили? Хто бюджетодержатель і який security review?

## 11. Що лишається UNVERIFIED

- Revenue, billing ticket volume, частка web-to-app і chargeback rate будь-якого конкретного бізнесу SKELAR.
- Частка multi-rail cases у підтримці SKELAR та інших компаній.
- Реальна готовність платити за Defuse, pricing, конкурентна цінність порівняно із вже купленими Intercom/Zendesk/Chargeflow.
- Точність, калібрування, latency, витрати реальної Gemma та безпека на зовнішніх датасетах.
- Достовірні category-specific chargeback-rate і share-of-billing-tickets для кожної з 10 вертикалей.
- Чи можуть обрані PSP/Apple/Google API дозволити конкретну дію у потрібному authorization контексті.
- Доведений causal effect Defuse на реальні disputes та витрати бізнесу.

**Позиція для журі:** «Ми не заявляємо, що винайшли AI support, refund API чи HITL. Ми сфокусували агентну автоматизацію на одній дорогій для subscription-бізнесу операційній проблемі: правильне визначення транзакції та законної, безпечної дії при неповному платіжному контексті. І показуємо, як наступний продуктовий реліз може прибрати саму причину повторних звернень».
