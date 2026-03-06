package blps.itmo.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record ProvideDocsRequest(@NotEmpty List<AttachmentRequest> attachments) {
}
