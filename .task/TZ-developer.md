# ТЗ для розробника — Defuse (Java 17+ / Spring Boot 3)
### polyTech Code Fest × SKELAR (31.10.2026)

> **Дедлайн 17:00.** Здаємо: презентацію (≤10 слайдів) з відеодемо, ZIP репозиторію і посилання на GitHub.
> Стек: **Java 17 / Spring Boot 3.3+ (Gradle або Maven) + Spring AI / Anthropic HTTP Client** + **React/Vite Frontend**.
> Дані, правила політики та тестові сценарії беруться з `defuse-data/`.

---

## 0. TL;DR

**Defuse** — це AI-оператор підтримки для застосунку з підпискою (**Calmly**).
На кожен тікет бекенд на Spring Boot:
1. Перевіряє вхідне повідомлення через **Security Guard** на Prompt Injection (Regex/Heuristic filter);
2. Розуміє **справжню мету (Intent)** і помічає **сарказм та приховану агресію** через LLM Classifier (Claude / Spring AI);
3. Дістає факти з білінгу (Customer 360: історія платежів, тріал, активність);
4. **Детермінована Java Policy (`ReferencePolicyService`)** вирішує, що можна зробити;
5. Система **автоматично виконує дію** (`AUTO`) або готує її людині на **1 клік** (`SUGGEST`), або передає складні кейси (`ESCALATE`);
6. Генерує чернетку персоналізованої відповіді;
7. Усі дії записуються в **Audit Log** з ідемпотентністю та транслюються на аналітичний дашборд.

> **Головне архітектурне правило:** LLM розуміє контекст і пише текст, але **гроші та підписки змінює виключно детермінований Java-код.**

---

## 1. Архітектура Spring Boot додатку

```
┌───────────────── Frontend (React + Vite + TypeScript + Tailwind) ──────────────────┐
│   Queue   │   TicketView (Customer 360 · Policy Decision · Actions)   │  Dashboard  │
└───────────────────────────────────────┬────────────────────────────────────────────┘
                                        │ REST API (JSON)
┌──────────────────────── Backend (Spring Boot 3 / Java 17+) ────────────────────────┐
│                                                                                    │
│  [Web Layer]                                                                       │
│   ├── TicketController        (/api/tickets, /api/tickets/{id}/analyze)            │
│   ├── ActionController        (/api/tickets/{id}/execute, /approve)                │
│   ├── AnalyticsController     (/api/analytics/summary, /api/analytics/vamp)        │
│   └── DemoController          (/api/demo/reset, /api/demo/simulate)                │
│                                                                                    │
│  [Security & Guard Layer]                                                          │
│   └── InjectionGuardService   (фільтрація перед викликом LLM, regex pattern match)│
│                                                                                    │
│  [AI / LLM Integration]                                                            │
│   ├── LlmClassifierService    (Haiku/Sonnet structured output, JSON-parsing)       │
│   └── ReplyWriterService      (генерація відповіді мовою звернення)                │
│                                                                                    │
│  [Core Business Engine]                                                            │
│   ├── ContextBuilderService   (збирання Customer 360 з in-memory сховища)           │
│   ├── ReferencePolicyService  (детермінований розрахунок дій: REFUND, CANCEL...)   │
│   ├── ActionExecutorService   (виконання дій з перевіркою ідемпотентності)         │
│   └── AuditService            (журнал аудиту операцій)                             │
│                                                                                    │
│  [Data Layer]                                                                      │
│   └── InMemoryStore           (завантаження defuse-data/*.json при старті, reset)  │
│                                                                                    │
└────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Модель даних та DTO (Java Records / Classes)

### 2.1. Вхідні ознаки від LLM (`ClassificationFeatures`)
```java
public record ClassificationFeatures(
    String intent,               // cancel_and_refund, cancel_only, refund_only, technical, etc.
    String emotion,              // anger, frustration, neutral, happy
    boolean sarcasm,             // виявлення сарказму ("Love being charged 🙂")
    boolean hiddenAggression,     // прихована пасивна агресія
    boolean explicitAggression,   // відкритий мат / погрози
    boolean disputeSignal,       // згадки "bank", "chargeback", "scam", "lawyer"
    boolean injection,           // спроби взлому промпта
    String disputeRiskBand,      // HIGH, MEDIUM, LOW
    int disputeRiskScore,        // 0..100
    String priority,             // CRITICAL, HIGH, NORMAL, LOW
    List<String> evidenceSpans   // цитати з повідомлення, що вплинули на рішення
) {}
```

### 2.2. Рішення Policy Engine (`PolicyDecision`)
```java
public record PolicyDecision(
    String mode,                 // "auto", "suggest", "escalate"
    List<String> actions,        // ["cancel_subscription", "refund_charge", "extend_access"]
    Map<String, Object> params,  // {"refund_amount": 49.99, "extend_days": 7}
    String primaryReason,        // Пояснення бізнес-правила
    boolean isSafe,
    double moneyAtRisk
) {}
```

### 2.3. Повний стан аналізу (`TicketAnalysis`)
```java
public record TicketAnalysis(
    String ticketId,
    ClassificationFeatures features,
    PolicyDecision decision,
    CustomerContext context,
    String draftReply,
    Instant analyzedAt
) {}
```

---

## 3. Ключові сервіси Spring Boot

### 3.1. `InjectionGuardService`
Захищає від Jailbreak та Prompt Injection перед відправкою у модель:
* Перевіряє вхідний текст за переліком регулярних виразів:
  `ignore previous instructions`, `system prompt`, `you are now in developer mode`, `refund \$\d+ to (card|crypto)`.
* Якщо виявлено атаку: маркує `injection = true`, блокує автоматичні дії та виставляє `mode = escalate`.

### 3.2. `ReferencePolicyService` (Порт логіки з `reference_policy.py`)
Повністю детермінована бізнес-логіка:
* **Критерії Auto-Refund:**
  - Тріал закінчився ≤ 7 днів тому;
  - Кількість сесій у застосунку після списання == 0;
  - Сума рефанду ≤ ліміту політики ($50.00);
  - Відсутні ознаки зловживань у минулому (chargeback history == 0).
* **Критерії Suggest:**
  - Якщо був вхід у застосунок або термін після списання від 8 до 14 днів — оператор має підтвердити дію у 1 клік.
* **Критерії Escalate:**
  - Спроба ін'єкції, невідомий користувач, сума перевищує ліміт або підозра на шахрайство.

### 3.3. `ActionExecutorService`
Виконує дії над `InMemoryStore`:
* `CANCEL_SUBSCRIPTION` -> змінює статус підписки користувача на `CANCELED`.
* `REFUND_CHARGE` -> оновлює платіж на `REFUNDED`, перераховує LTV.
* `EXTEND_ACCESS` -> додає дні до терміну дії підписки.
* **Idempotency Check:** використовує токен `ticketId + action`, щоб дія не виконувалася двічі при повторних натисканнях.
* Записує подію в `AuditService`.

### 3.4. `LlmClassifierService`
* Підтримує 2 режими роботи через `application.yml` (`defuse.llm.mode`):
  1. `LIVE`: надсилає запит до Anthropic API (Claude 3.5 Haiku) або OpenAI через Spring HTTP Client з очікуванням валідного JSON.
  2. `FALLBACK / GOLD`: швидкий детермінований режим з передбачених JSON для стабільного демо офлайн або без витрати квот.

---

## 4. REST API Ендпоінти

| Метод | Шлях | Опис |
|---|---|---|
| `GET` | `/api/tickets` | Отримання черги тікетів (відсортованих за пріоритетом: Risk × Amount) |
| `GET` | `/api/tickets/{id}` | Деталі тікета та Customer 360 (платежі, активність) |
| `POST` | `/api/tickets/{id}/analyze` | Запуск конвеєра аналізу (Guard -> LLM -> Policy -> Draft) |
| `POST` | `/api/tickets/{id}/execute` | Виконання дії (для авто-режиму або кліку "Approve") |
| `POST` | `/api/tickets/simulate` | Створення нового тікета в реальному часі для Live-демо на пітчі |
| `GET` | `/api/analytics/dashboard` | Метрики: VAMP ratio, врятовані гроші ($), відвернуті диспути, Root Causes |
| `POST` | `/api/demo/reset` | Скидання стану БД до початкових seed-даних за 1 клік |

---

## 5. Структура проєкту (Spring Boot)

```
backend/
├── pom.xml / build.gradle
├── src/main/java/com/skelar/defuse/
│   ├── DefuseApplication.java
│   ├── config/
│   │   ├── AppConfig.java
│   │   └── CorsConfig.java
│   ├── controller/
│   │   ├── TicketController.java
│   │   ├── ActionController.java
│   │   ├── AnalyticsController.java
│   │   └── DemoController.java
│   ├── service/
│   │   ├── guard/InjectionGuardService.java
│   │   ├── llm/LlmClassifierService.java
│   │   ├── llm/ReplyWriterService.java
│   │   ├── policy/ReferencePolicyService.java
│   │   ├── executor/ActionExecutorService.java
│   │   └── audit/AuditService.java
│   ├── store/
│   │   ├── InMemoryStore.java
│   │   └── DataLoader.java
│   └── model/
│       ├── dto/
│       └── domain/
└── src/main/resources/
    ├── application.yml
    └── data/ (customers.json, payments.json, subscriptions.json, usage.json, tickets_eval.json)
```

---

## 6. План валідації та оцінювання
1. **JUnit 5 / AssertJ тести:**
   * Перевірка `ReferencePolicyService` на відповідність 38 сценаріям із `defuse-data/test_policy.py`.
   * **0 Unsafe Refunds** (гарантія, що жодна ін'єкція чи невалідний клієнт не отримає авто-рефанд).
2. **End-to-End тест живого сценарію:**
   * Клієнт надсилає саркастичний тікет -> статус автоматично змінюється на `RESOLVED`, гроші повернено, на дашборді додається зекономлена сума $49.99 + $23 fee + $8 VAMP.
