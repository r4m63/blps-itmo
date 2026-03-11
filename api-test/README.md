# API test scripts for Penalty Claims

Scripts cover main процесс ветки:
- `scenario_happy_respondent_approves.sh` — достаточно данных, основания есть, арендатор отвечает, поддержка одобряет штраф.
- `scenario_need_more_docs_reject.sh` — данных не хватает → доп. материалы → оснований нет → отказ.
- `scenario_timeout_support_approves.sh` — основания есть, арендатор не ответил (таймаут) → поддержка применяет штраф.
- `scenario_respondent_disputes_support_rejects.sh` — основания есть, арендатор возражает → поддержка отказывает в штрафе.
- `run-all.sh` — запускает все сценарии подряд.

Запуск (требуются `curl` и `jq`):
```bash
cd api-test
BASE_URL=http://localhost:8080/api ./run-all.sh
```

Внутри используется `BASE_URL` (по умолчанию `http://localhost:8080/api`).
