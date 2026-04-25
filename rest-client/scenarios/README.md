# Full Business Scenarios

Здесь каждый `.http` файл самодостаточен и покрывает полный цикл бизнес-логики от первой загрузки вложения до финального состояния заявки.

- [00-rbac-smoke.http](/Users/ramil/Projects/blps-itmo/rest-client/scenarios/00-rbac-smoke.http)
  Базовые проверки безопасности, не полный business flow.

- [10-intake-additional-assessment-no.http](/Users/ramil/Projects/blps-itmo/rest-client/scenarios/10-intake-additional-assessment-no.http)
  Полный цикл:
  - загрузка вложения
  - создание заявки
  - запрос доп. материалов
  - ответ арендодателя
  - оценка без оснований для штрафа

- [11-intake-additional-final-penalty.http](/Users/ramil/Projects/blps-itmo/rest-client/scenarios/11-intake-additional-final-penalty.http)
  Полный цикл:
  - загрузка вложения
  - создание заявки
  - запрос доп. материалов
  - ответ арендодателя
  - есть основания для штрафа
  - ответ арендатора
  - финальное решение: штраф применён

- [12-intake-additional-final-no-penalty.http](/Users/ramil/Projects/blps-itmo/rest-client/scenarios/12-intake-additional-final-no-penalty.http)
  Полный цикл:
  - загрузка вложения
  - создание заявки
  - запрос доп. материалов
  - ответ арендодателя
  - есть основания для штрафа
  - ответ арендатора
  - финальное решение: штраф не применён

- [20-intake-ok-assessment-no.http](/Users/ramil/Projects/blps-itmo/rest-client/scenarios/20-intake-ok-assessment-no.http)
  Полный цикл:
  - загрузка вложения
  - создание заявки
  - intake сразу ок
  - оценка без оснований для штрафа

- [21-intake-ok-final-penalty.http](/Users/ramil/Projects/blps-itmo/rest-client/scenarios/21-intake-ok-final-penalty.http)
  Полный цикл:
  - загрузка вложения
  - создание заявки
  - intake сразу ок
  - есть основания для штрафа
  - ответ арендатора
  - финальное решение: штраф применён

- [22-intake-ok-final-no-penalty.http](/Users/ramil/Projects/blps-itmo/rest-client/scenarios/22-intake-ok-final-no-penalty.http)
  Полный цикл:
  - загрузка вложения
  - создание заявки
  - intake сразу ок
  - есть основания для штрафа
  - ответ арендатора
  - финальное решение: штраф не применён
