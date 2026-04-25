package blps.itmo.dto;

import lombok.Data;

/**
 * Решение администратора на этапе первичной проверки заявки.
 * Идентификатор администратора берётся из контекста аутентификации.
 */
@Data
public class IntakeDecisionRequest {
    private boolean needMoreInfo;
    private String comment;
}
