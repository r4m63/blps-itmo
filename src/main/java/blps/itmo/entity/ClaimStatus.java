package blps.itmo.entity;

public enum ClaimStatus {
    CREATED,                // заявка создана
    DATA_REVIEW,            // проверка полноты/формата
    NEED_ADDITIONAL_DATA,   // запрошены доп материалы
    RISK_REVIEW,            // проверка правил/рисков
    WAITING_RESPONDENT,     // запрос комментария ответчика
    SUPPORT_REVIEW,         // ручная проверка поддержки
    CLOSED_PENALTY,         // штраф применен
    CLOSED_REJECT           // отказ без штрафа
}
