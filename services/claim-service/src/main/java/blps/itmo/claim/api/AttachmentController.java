package blps.itmo.claim.api;

import blps.itmo.claim.api.dto.AttachmentConfirmResponse;
import blps.itmo.claim.api.dto.AttachmentInitRequest;
import blps.itmo.claim.api.dto.AttachmentInitResponse;
import blps.itmo.claim.service.AttachmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/attachments")
@RequiredArgsConstructor
public class AttachmentController {

    private static final String USER_ID_HEADER = "X-User-Id";

    private final AttachmentService attachmentService;

    @PostMapping("/init")
    @ResponseStatus(HttpStatus.CREATED)
    public AttachmentInitResponse init(@RequestHeader(USER_ID_HEADER) Integer userId,
                                       @Valid @RequestBody AttachmentInitRequest req) {
        return attachmentService.initAttachment(userId, req);
    }

    @PostMapping("/{id}/confirm")
    public AttachmentConfirmResponse confirm(@PathVariable Integer id) {
        return attachmentService.confirmAttachment(id);
    }
}
