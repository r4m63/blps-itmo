package blps.itmo.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import java.util.List;
import lombok.Data;

/**
 * Ответ арендодателя на запрос дополнительных материалов.
 * Идентификатор арендодателя берётся из контекста аутентификации,
 * принадлежность заявки проверяется на сервисном слое.
 */
@Data
public class AdditionalInfoReplyRequest {
    private String comment;

    @JsonAlias({"attachments", "attachmentUrls"})
    private List<String> attachmentKeys;

    @JsonAlias({"attachmentIds"})
    private List<Long> attachmentIds;
}
