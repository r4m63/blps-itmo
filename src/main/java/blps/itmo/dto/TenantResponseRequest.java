package blps.itmo.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Data;

@Data
public class TenantResponseRequest {
    @NotNull
    private Long tenantId;
    private boolean agree;
    private String comment;
    @JsonAlias({"attachments", "attachmentUrls"})
    private List<String> attachmentKeys;

    @JsonAlias({"attachmentIds"})
    private List<Long> attachmentIds;
}
