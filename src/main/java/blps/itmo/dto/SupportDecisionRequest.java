package blps.itmo.dto;

import java.math.BigDecimal;
import lombok.Data;

/**
 * Финальное решение поддержки по заявке.
 * Идентификатор администратора берётся из контекста аутентификации.
 */
@Data
public class SupportDecisionRequest {
    private boolean applyPenalty;
    private BigDecimal penaltyAmount;
    private String penaltyCurrency = "USD";
    private String note;
}
