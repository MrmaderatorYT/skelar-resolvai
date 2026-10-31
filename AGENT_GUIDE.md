# 🤖 AGENT_GUIDE: Defuse Team Guidelines (Java Spring Boot 3 Edition)

## 🎯 Проєкт: Defuse (polyTech Code Fest × SKELAR)
**Ціль:** Створити надійний, типізований Enterprise AI-оператор підтримки на **Java 17+ / Spring Boot 3**, який запобігає чарджбекам (Visa VAMP 1.5%), розпізнає сарказм та автоматизує білінг-дії.

---

## 🏛 Архітектурні Заповіді

1. **Сувора типізація та Детермінованість:**
   * Всі сутності описуються через Java Records / DTO (`ClassificationFeatures`, `PolicyDecision`, `CustomerContext`).
   * Всі бізнес-рішення щодо грошей та статусів акаунтів приймає суто `ReferencePolicyService.java`, а не LLM.
2. **Security & Zero Unsafe Actions:**
   * Перед відправкою в LLM запит проходить `InjectionGuardService` (фільтрація системних інструкцій та спроб злому).
   * Кожна операція фінансового рефанду або зміни підписки захищена токеном ідемпотентності (`ticketId + action`).
3. **Готовність до офлайн / Fallback режиму:**
   * Якщо ключ LLM відсутній або вичерпано ліміт, сервіс прозоро перемикається на `Gold/Baseline` режим через `application.yml` (`defuse.llm.mode=live|fallback`). Демо на пітчі не повинно зламатися за жодних умов!

---

## 👥 Розподіл завдань для агентів

### 🛠 Агент 1: Java Spring Boot Engineer
* Створення проєкту Spring Boot 3 на Gradle (Java 17/21).
* Реалізація `InMemoryStore` з автоматичним завантаженням JSON даних із `resources/data/`.
* Портування `reference_policy.py` у типізований сервіс `ReferencePolicyService.java`.
* Реалізація `InjectionGuardService`, `ActionExecutorService` та REST контролерів.
* Написання JUnit 5 тестів на 38 валідаційних сценаріїв (гарантія 0 Unsafe Actions).

### 🎨 Агент 2: Frontend Engineer
* Інтерфейс на React + Vite + Tailwind + Lucide Icons.
* Пріоритезована черга з ризиками сарказму / диспуту.
* Картка тікета з блоком Customer 360 і кнопкою One-Click Approve.
* Live Simulator для введення тексту під час виступу.
* Дашборд із метриками VAMP та збереженими коштами.

### 📊 Агент 3: QA, Docs & Presentation Engineer
* Оформлення документації у форматі Skelar (`docs/features/`).
* Підготовка презентації (до 10 слайдів, акцент на VAMP та фінансовий ROI).
* Підготовка демо-скрипта на 3 хвилини.

---

## ⏰ Таймлайн до здачі (17:00):
* **13:45** — Працюючий Spring Boot бекенд з усіма REST ендпоінтами.
* **15:00** — Повна інтеграція з фронтендом React.
* **16:00** — JUnit тести, проходження валідації та оформлені `docs/features/`.
* **16:30** — Презентація та готове відеодемо.
* **17:00** — Фінальна здача ZIP-архіву та посилань.
