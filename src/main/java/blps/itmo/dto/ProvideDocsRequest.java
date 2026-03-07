package blps.itmo.dto;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;

public record ProvideDocsRequest(@NotEmpty List<AttachmentRequest> attachments) {
}
