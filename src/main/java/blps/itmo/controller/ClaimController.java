package blps.itmo.controller;

import blps.itmo.dto.AttachmentRequest;
import blps.itmo.dto.ClaimDto;
import blps.itmo.dto.CreateClaimRequest;
import blps.itmo.dto.IntakeCheckRequest;
import blps.itmo.dto.ProvideDocsRequest;
import blps.itmo.dto.RespondentResponseRequest;
import blps.itmo.dto.RulesCheckRequest;
import blps.itmo.dto.SupportDecisionRequest;
import blps.itmo.entity.Claim;
import blps.itmo.service.ClaimService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/claims")
@Tag(name = "Penalty Claims", description = "Управление заявками на штрафные санкции (BPMN: PenaltyClaimProcess)")
public class ClaimController {

    private final ClaimService claimService;

    public ClaimController(ClaimService claimService) {
        this.claimService = claimService;
    }

    @PostMapping
    @Operation(
            summary = "Создать заявку (старт процесса)",
            description = """
                    Кто вызывает: арендодатель (initiator).
                    Что делает: создаёт заявку, прикладывает первичные доказательства.
                    Результат: новая заявка со статусом DATA_REVIEW.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Заявка создана", content = @Content(schema = @Schema(implementation = ClaimDto.class))),
            @ApiResponse(responseCode = "400", description = "Ошибка валидации запроса")
    })
    @ResponseStatus(HttpStatus.CREATED)
    public ClaimDto create(@Valid @RequestBody CreateClaimRequest request) {
        Claim claim = claimService.createClaim(
                request.initiatorId(),
                request.respondentId(),
                request.amount(),
                request.currency(),
                request.reason());

        if (request.attachments() != null) {
            for (AttachmentRequest a : request.attachments()) {
                claimService.addAttachment(claim.getId(), a.uploaderId(), a.type(), a.url());
            }
        }
        return ClaimDto.from(claimService.get(claim.getId()));
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Получить заявку",
            description = """
                    Кто вызывает: любая роль (для проверки состояния).
                    Что делает: возвращает актуальное состояние заявки.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Успешно", content = @Content(schema = @Schema(implementation = ClaimDto.class))),
            @ApiResponse(responseCode = "404", description = "Заявка не найдена")
    })
    public ClaimDto get(@PathVariable Long id) {
        return ClaimDto.from(claimService.get(id));
    }

    @PostMapping("/{id}/attachments")
    @Operation(
            summary = "Добавить вложение",
            description = """
                    Кто вызывает: инициатор/ответчик/поддержка.
                    Что делает: добавляет материалы к заявке (доказательства, уточнения и т.п.).
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Вложение добавлено", content = @Content(schema = @Schema(implementation = ClaimDto.class))),
            @ApiResponse(responseCode = "400", description = "Ошибка валидации запроса"),
            @ApiResponse(responseCode = "404", description = "Заявка не найдена")
    })
    public ClaimDto addAttachment(@PathVariable Long id, @Valid @RequestBody AttachmentRequest request) {
        claimService.addAttachment(id, request.uploaderId(), request.type(), request.url());
        return ClaimDto.from(claimService.get(id));
    }

    @PostMapping("/{id}/intake-check")
    @Operation(
            summary = "Проверка полноты/формата (администратор)",
            description = "Шаг intake: enoughData=true → RISK_REVIEW; false → NEED_ADDITIONAL_DATA."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Решение intake сохранено", content = @Content(schema = @Schema(implementation = ClaimDto.class))),
            @ApiResponse(responseCode = "400", description = "Неверный статус или валидация (DomainException/validation)"),
            @ApiResponse(responseCode = "404", description = "Заявка не найдена")
    })
    public ClaimDto intakeCheck(@PathVariable Long id, @Valid @RequestBody IntakeCheckRequest request) {
        claimService.intakeCheck(id, request.enoughData());
        return ClaimDto.from(claimService.get(id));
    }

    @PostMapping("/{id}/provide-docs")
    @Operation(
            summary = "Предоставить доп. материалы (заявитель)",
            description = "Отвечает на запрос доп. данных; прикладывает файлы и возвращает в повторную проверку полноты."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Доп материалы приняты", content = @Content(schema = @Schema(implementation = ClaimDto.class))),
            @ApiResponse(responseCode = "400", description = "Неверный статус (ожидался NEED_ADDITIONAL_DATA) или валидация"),
            @ApiResponse(responseCode = "404", description = "Заявка не найдена")
    })
    public ClaimDto provideDocs(@PathVariable Long id, @Valid @RequestBody ProvideDocsRequest request) {
        for (AttachmentRequest a : request.attachments()) {
            claimService.addAttachment(id, a.uploaderId(), a.type(), a.url());
        }
        claimService.provideAdditionalData(id);
        return ClaimDto.from(claimService.get(id));
    }

    @PostMapping("/{id}/rules-check")
    @Operation(
            summary = "Проверка правил/рисков (администратор)",
            description = "groundsForPenalty=true → запрос комментария ответчика (WAITING_RESPONDENT, дедлайн +3 дня); false → закрытие без штрафа."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Решение по правилам сохранено", content = @Content(schema = @Schema(implementation = ClaimDto.class))),
            @ApiResponse(responseCode = "400", description = "Неверный статус (ожидался RISK_REVIEW) или валидация"),
            @ApiResponse(responseCode = "404", description = "Заявка не найдена")
    })
    public ClaimDto rulesCheck(@PathVariable Long id, @Valid @RequestBody RulesCheckRequest request) {
        claimService.rulesCheck(id, request.groundsForPenalty());
        return ClaimDto.from(claimService.get(id));
    }

    @PostMapping("/{id}/respondent/response")
    @Operation(
            summary = "Комментарий ответчика",
            description = "Ответчик (арендатор) добавляет возражение; переводит на ручную проверку поддержки."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Комментарий принят", content = @Content(schema = @Schema(implementation = ClaimDto.class))),
            @ApiResponse(responseCode = "400", description = "Неверный статус (ожидался WAITING_RESPONDENT) или валидация"),
            @ApiResponse(responseCode = "404", description = "Заявка не найдена")
    })
    public ClaimDto respondentResponse(@PathVariable Long id, @Valid @RequestBody RespondentResponseRequest request) {
        claimService.respondentResponse(id, request.comment());
        return ClaimDto.from(claimService.get(id));
    }

    @PostMapping("/{id}/respondent/timeout")
    @Operation(
            summary = "Отметить таймаут ответчика",
            description = "Технический шаг при отсутствии ответа в срок (P3D); переводит на ручную проверку."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Таймаут зафиксирован", content = @Content(schema = @Schema(implementation = ClaimDto.class))),
            @ApiResponse(responseCode = "400", description = "Неверный статус (ожидался WAITING_RESPONDENT)"),
            @ApiResponse(responseCode = "404", description = "Заявка не найдена")
    })
    public ClaimDto timeout(@PathVariable Long id) {
        claimService.markTimeout(id);
        return ClaimDto.from(claimService.get(id));
    }

    @PostMapping("/{id}/support-decision")
    @Operation(
            summary = "Решение поддержки / risk",
            description = "approve=true → штраф применён; approve=false → отказ без штрафа."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Решение применено", content = @Content(schema = @Schema(implementation = ClaimDto.class))),
            @ApiResponse(responseCode = "400", description = "Неверный статус (ожидался SUPPORT_REVIEW) или валидация"),
            @ApiResponse(responseCode = "404", description = "Заявка не найдена")
    })
    public ClaimDto supportDecision(@PathVariable Long id, @Valid @RequestBody SupportDecisionRequest request) {
        claimService.supportDecision(id, request.approvePenalty(), request.comment());
        return ClaimDto.from(claimService.get(id));
    }
}
