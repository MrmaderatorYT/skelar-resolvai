# Архітектура плагінів Defuse (Java 17 / Spring Boot 3)

Мета: компанія підключає **свої дані й свою policy без зміни коду ядра**. Головне правило не змінюється: **плагіни постачають дані та виконують дії, а рішення про гроші приймає лише ядро** (`ReferencePolicyService` + `ActionExecutorService`).

## 1. Схема

```
                    defuse.yaml  (policy + datasources + actions)
                         │  @ConfigurationProperties + валідація під час старту
                         ▼
┌──────────────────────── CORE (не змінюється) ─────────────────────────┐
│ TicketPipelineService                                                  │
│   InjectionGuard → LlmClassifier → ContextBuilder → ReferencePolicy    │
│                                         ▲              │ decision      │
│                                         │ дані         ▼               │
│                                   ConnectorRegistry  ActionExecutor    │
│                                         │              │ (idempotency, │
│                                         │              │  audit, lock) │
└─────────────────────────────────────────┼──────────────┼───────────────┘
                                          │  SPI          │  SPI
              ┌───────────────┬───────────┴───┐     ┌─────┴──────────────┐
              ▼               ▼               ▼     ▼                    ▼
       CustomerSource  EngagementSource  TicketSource  BillingProvider  ActionHandler
       json | postgres  json | firebase  json | zendesk card(stripe) |   cancel, refund,
                                                       app_store |       pause, discount,
                                                       google_play       extend_access…
```

## 2. SPI — інтерфейси плагінів (`com.skelar.defuse.spi`)

```java
/** Профіль клієнта: звідки прийшов, скільки з нами, ідентичність для анти-абузу. */
public interface CustomerSource {
    Optional<CustomerProfile> find(String customerId);
    List<String> identityCluster(String customerId);   // ті самі card fingerprint / email / device
}

/** Активність: сесії, streak, остання активність. */
public interface EngagementSource {
    Engagement engagement(String customerId);
}

/** Звідки беруться тікети і куди йде відповідь. */
public interface TicketSource {
    List<Ticket> open();
    void reply(String ticketId, String text);
}

/** Білінг. Одна реалізація на процесор; можливості кажуть policy, що взагалі дозволено. */
public interface BillingProvider {
    String processor();                          // "card" | "app_store" | "google_play"
    Set<Capability> capabilities();              // REFUND, CANCEL, PAUSE, DISCOUNT, EXTEND
    List<Payment> payments(String customerId);
    Subscription subscription(String customerId);
    ActionResult execute(BillingCommand cmd);    // cmd несе idempotencyKey = ticketId:action
}

/** Одна дія = один бін. Нова дія не вимагає зміни switch в executor. */
public interface ActionHandler {
    String action();                             // "refund_full", "offer_discount", ...
    boolean movesMoney();
    ActionResult run(ActionContext ctx);         // викликає лише ActionExecutor після policy
}

/** Фабрика конекторів: Spring збирає всі фабрики, реєстр вибирає за `type` з YAML. */
public interface ConnectorFactory<T> {
    String type();                               // "json", "postgres", "firebase", "zendesk"
    Class<T> kind();                             // CustomerSource.class, ...
    T create(Map<String, String> settings);
}
```

**Capabilities замість захардкоджених `if store`.** Провайдер `app_store` повертає `capabilities = {}`, і policy видає «store instructions», бо REFUND не підтримується. Провайдер `card` повертає `{REFUND, CANCEL, PAUSE, DISCOUNT, EXTEND}`. Новий процесор (PayPal, Paddle) — це новий бін без змін у policy.

## 3. `defuse.yaml`

```yaml
defuse:
  policy:
    refund:    { full-max-days: 14, full-max-sessions: 2, partial-max-days: 30, partial-share: 0.5,
                 auto-limit-usd: 60, doc: policy/refunds.md }
    retention: { max-offers-per-ticket: 1, min-tenure-months: 2, cooldown-months: 12,
                 monthly-budget-share: 0.15, holdout-share: 0.10, doc: policy/retention.md,
                 offers: { too_expensive: { type: discount, percent: 25, months: 2 },
                           no_time:       { type: pause, months: 2 } } }
    risk:      { weights: { dispute: 0.40, sarcasm: 0.25, aggression: 0.20, money: 0.15, per-contact: 0.10 },
                 bands: { high: 0.5, medium: 0.3 }, doc: policy/risk.md }
  datasources:
    customers:  { type: json,     path: data/customers.json }
    engagement: { type: json,     path: data/usage.json }
    tickets:    { type: json,     path: data/tickets_eval.json }
    # customers: { type: postgres, url: ${DATABASE_URL}, user: ${DB_USER}, password: ${DB_PASSWORD}, table: customers }
    # engagement: { type: firebase, project: ${FIREBASE_PROJECT}, credentials: ${GOOGLE_APPLICATION_CREDENTIALS} }
  billing:
    - { processor: card,        type: mock }      # прод: stripe / solidgate
    - { processor: app_store,   type: store }     # лише інструкції
    - { processor: google_play, type: store }
```

**Як завантажується:**
- `@ConfigurationProperties("defuse")` → records `PolicyProperties`, `DataSourceProperties`;
- `@Validated` перевіряє межі: частки в 0..1, ліміти > 0, ваги ≥ 0;
- невідомий `type` → застосунок не стартує, з повідомленням «no ConnectorFactory for type=…»;
- секрети тільки як `${ENV}`: валідатор відхиляє буквальний `password:` або DSN з паролем.

## 4. Реєстр

```java
@Component
public class ConnectorRegistry {
    // Spring інжектить усі ConnectorFactory-біни; ключ = kind + type
    public ConnectorRegistry(List<ConnectorFactory<?>> factories, DataSourceProperties cfg) { ... }
    public <T> T get(String name, Class<T> kind) { ... }   // name з YAML: "customers", "engagement"
}

@Component
public class BillingRegistry {
    public BillingRegistry(List<BillingProvider> providers) { ... }   // ключ = processor()
    public BillingProvider forProcessor(String processor) { ... }    // невідомий → escalate
}

@Component
public class ActionRegistry {
    public ActionRegistry(List<ActionHandler> handlers) { ... }       // ключ = action()
}
```

**Плагін = Spring-бін.** Щоб додати Postgres, треба один клас `PostgresCustomerSourceFactory implements ConnectorFactory<CustomerSource>` (JdbcTemplate, лише читання) і рядок у YAML. Динамічне завантаження jar-ів (ServiceLoader / PF4J) свідомо не робимо: для MVP це крихко, а архітектурний ефект той самий.

## 5. Правила безпеки для плагінів
1. **Конектори даних лише читають.** Писати в білінг може тільки `BillingProvider.execute`, і лише його викликає `ActionExecutor` після `ReferencePolicyService.decide`.
2. **Кожна команда несе `idempotencyKey = ticketId:action`.** У проді це заголовок `Idempotency-Key` в Stripe. Executor тримає lock на платіж і повторно перевіряє його стан перед дією.
3. **Плагін не може розширити policy.** Ліміти й вікна беруться лише з `defuse.yaml`. Якщо capability немає, дія переходить у `escalate`.
4. **Збій плагіна (таймаут чи exception) → `escalate`**, а не «resolved». Відповідь клієнту пишеться після виконання (вже так працює).
5. **Policy в UI лише для читання.** Редагування грошових правил з UI — після MVP, з approval, версіонуванням і audit.

## 6. UI: «Policy» (ListView ↔ .md)
- `GET /api/policy` повертає `[{ "key": "refund.auto-limit-usd", "value": 60, "doc": "policy/refunds.md" }, ...]` з `PolicyProperties`.
- `GET /api/policy/docs/{name}` віддає Markdown з `resources/policy/` за whitelist імен, без довільних шляхів.
- Нова вкладка **Policy**: зліва список правил, справа .md з поясненням «чому так» і посиланням на тікети, де правило спрацювало (з audit).

## 7. Структура пакетів

```
com.skelar.defuse
├── spi/            CustomerSource, EngagementSource, TicketSource, BillingProvider,
│                   ActionHandler, ConnectorFactory, Capability, BillingCommand, ActionResult
├── config/         DefuseProperties (policy, datasources, billing), PolicyValidator, SecretsValidator
├── plugins/
│   ├── json/       JsonCustomerSourceFactory, JsonEngagementSourceFactory, JsonTicketSourceFactory
│   ├── billing/    MockCardBillingProvider, StoreBillingProvider (app_store, google_play)
│   ├── actions/    CancelHandler, RefundHandler, DuplicateRefundHandler, PauseHandler,
│   │               ExtendAccessHandler, OfferDiscountHandler, EvidencePackHandler, NoopHandlers
│   └── postgres/   PostgresCustomerSourceFactory          (stretch)
├── registry/       ConnectorRegistry, BillingRegistry, ActionRegistry
├── service/        (як зараз) pipeline, guard, llm, policy, executor, audit, analytics
└── controller/     + PolicyController
```

## 8. Обсяг на хакатон (~1.5 год)

| Крок | Що | Час |
|---|---|---|
| 1 | `defuse.yaml` + `DefuseProperties` + валідація; `ReferencePolicyService` читає пороги з конфігу (дефолти = поточні, 38 тестів зелені) | 25 хв |
| 2 | SPI + `BillingRegistry` з capabilities; `MockBillingService` розбити на `ActionHandler`-біни | 30 хв |
| 3 | `ConnectorRegistry` + JSON-фабрики (`InMemoryStore` переходить за інтерфейси) | 20 хв |
| 4 | `PolicyController` + вкладка Policy в UI + 3 `.md` | 20 хв |
| 5 | Тести: невідомий `type` → старт падає; буквальний пароль → старт падає; `app_store` без capabilities → інструкції; 38 parity | 15 хв |
| stretch | Postgres `CustomerSource` (лише читання) | 30 хв |

Firebase не робимо: service account і SDK — зайвий ризик на демо. У YAML показуємо закоментований приклад.

## 9. Що сказати на пітчі
> «Defuse — це ядро з детермінованою policy і плагіни: компанія описує свої правила в `defuse.yaml` і підключає свої дані — JSON, Postgres, Firebase, Stripe — через інтерфейси. Плагіни можуть лише читати дані й виконувати дозволені дії. Рішення про гроші завжди ухвалює ядро, і це покрито тестами.»
