package blps.itmo.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import java.util.List;
import lombok.Data;

/**
 * Ответ арендатора на претензию.
 * Идентификатор арендатора берётся из контекста аутентификации, а
 * принадлежность заявки проверяется на сервисном слое.
 */
@Data
public class TenantResponseRequest {
    private boolean agree;
    private String comment;

    @JsonAlias({"attachments", "attachmentUrls"})
    private List<String> attachmentKeys;

    @JsonAlias({"attachmentIds"})
    private List<Long> attachmentIds;
}
