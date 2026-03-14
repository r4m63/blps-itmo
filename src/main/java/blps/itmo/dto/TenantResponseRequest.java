package blps.itmo.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Data;

@Data
public class TenantResponseRequest {
    @NotNull
    private Long tenantId;
    private boolean agree;
    private String comment;
    private List<String> attachmentKeys;
}
