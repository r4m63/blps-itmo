package blps.itmo.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class IntakeDecisionRequest {
    @NotNull
    private Long adminId;

    private boolean needMoreInfo;
    private String comment;
}
