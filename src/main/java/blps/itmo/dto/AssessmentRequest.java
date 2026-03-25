package blps.itmo.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.Data;

@Data
public class AssessmentRequest {
    @NotNull
    private Long adminId;

    @NotNull
    private BigDecimal assessmentAmount;

    private String assessmentNotes;

    /**
     * true — есть основания для штрафа, двигаем к реакции арендатора;
     * false — оснований нет, закрываем без штрафа.
     */
    private boolean penaltyGrounds;
}
