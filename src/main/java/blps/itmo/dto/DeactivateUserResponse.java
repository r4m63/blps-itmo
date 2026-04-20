package blps.itmo.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DeactivateUserResponse {
    private Long userId;
    private int closedClaimsCount;
    private String message;
}
