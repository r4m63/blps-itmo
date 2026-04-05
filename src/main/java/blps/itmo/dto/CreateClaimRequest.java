package blps.itmo.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;
import lombok.Data;

/**
 * Запрос на создание заявки о штрафных санкциях.
 * <p>
 * Идентификатор арендодателя (автор заявки) НЕ принимается от клиента —
 * он извлекается из контекста аутентификации Spring Security.
 * Это защищает от спуфинга актора.
 */
@Data
public class CreateClaimRequest {

    /** Идентификатор арендатора, против которого подаётся заявка. */
    @NotNull
    private Long tenantId;

    @NotBlank
    private String title;

    @NotBlank
    private String description;

    @NotNull
    @DecimalMin(value = "0.0", inclusive = true)
    private BigDecimal claimedAmount;

    @NotBlank
    private String currency = "USD";

    /**
     * Список object_key уже загруженных в MinIO файлов-доказательств.
     */
    @JsonAlias({"attachments", "attachmentUrls"})
    private List<String> attachmentKeys;
}
