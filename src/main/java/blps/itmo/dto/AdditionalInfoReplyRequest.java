package blps.itmo.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Data;

@Data
public class AdditionalInfoReplyRequest {
    @NotNull
    private Long landlordId;
    private String comment;
    private List<String> attachmentKeys;
}
