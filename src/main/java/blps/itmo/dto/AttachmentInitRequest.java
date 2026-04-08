package blps.itmo.dto;

import blps.itmo.entity.business.AttachmentPurpose;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Запрос на инициализацию загрузки файла в объектное хранилище.
 * Идентификатор загружающего пользователя берётся из контекста аутентификации.
 */
@Data
public class AttachmentInitRequest {
    @NotBlank
    private String fileName;

    @NotBlank
    private String contentType;

    private AttachmentPurpose purpose = AttachmentPurpose.DAMAGE_EVIDENCE;
}
