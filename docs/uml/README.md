# UML Диаграммы

В каталоге `docs/uml/` лежат исходники диаграмм в формате `PlantUML`.

Почему так:

- текстовые диаграммы удобно ревьюить в git
- они не создают binary drift
- их можно рендерить локально в PNG/SVG при необходимости

## Список диаграмм

- [system-context.puml](system-context.puml) — внешний контекст системы и акторы
- [container-view.puml](container-view.puml) — контейнерная архитектура и связи сервисов
- [deployment-view.puml](deployment-view.puml) — локальная deployment-топология
- [claim-service-components.puml](claim-service-components.puml) — внутренние компоненты `claim-service`
- [claim-lifecycle-state.puml](claim-lifecycle-state.puml) — state machine заявки
- [create-claim-assessment-sequence.puml](create-claim-assessment-sequence.puml) — создание claim и async assessment
- [penalty-success-sequence.puml](penalty-success-sequence.puml) — успешный penalty flow
- [penalty-failure-retry-sequence.puml](penalty-failure-retry-sequence.puml) — failure + retry flow
- [user-deactivation-sequence.puml](user-deactivation-sequence.puml) — реакция на deactivation event
- [data-ownership-class-diagram.puml](data-ownership-class-diagram.puml) — модель данных и bounded contexts

## Как рендерить

Если у тебя установлен `plantuml`, можно рендерить так:

```bash
plantuml docs/uml/*.puml
```

Или по одному файлу:

```bash
plantuml docs/uml/container-view.puml
```

## Рекомендуемый порядок чтения

1. `system-context.puml`
2. `container-view.puml`
3. `deployment-view.puml`
4. `claim-lifecycle-state.puml`
5. sequence diagrams
6. `claim-service-components.puml`
7. `data-ownership-class-diagram.puml`
