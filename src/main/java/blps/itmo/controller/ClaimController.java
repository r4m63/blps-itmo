package blps.itmo.controller;

import blps.itmo.dto.ClaimDto;
import blps.itmo.dto.CreateClaimRequest;
import blps.itmo.dto.IntakeCheckRequest;
import blps.itmo.dto.RespondentResponseRequest;
import blps.itmo.dto.RulesCheckRequest;
import blps.itmo.dto.SupportDecisionRequest;
import blps.itmo.entity.AttachmentType;
import blps.itmo.entity.Claim;
import blps.itmo.service.ClaimService;
import blps.itmo.service.MinioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/claims")
@Tag(name = "Penalty Claims", description = "Управление заявками на штрафные санкции (BPMN: PenaltyClaimProcess)")
public class ClaimController {

    private final ClaimService claimService;
    private final MinioService minioService;

    public ClaimController(ClaimService claimService, MinioService minioService) {
        this.claimService = claimService;
        this.minioService = minioService;
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

    @PostMapping(value = "/{id}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Загрузить и прикрепить файл к заявке",
            description = """
                    Кто вызывает: инициатор/ответчик/поддержка.
                    Что делает: загружает файл во внутреннее хранилище и прикрепляет к заявке.
                    URL хранения скрыт от пользователя.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Файл прикреплён", content = @Content(schema = @Schema(implementation = ClaimDto.class))),
            @ApiResponse(responseCode = "400", description = "Файл не передан или ошибка валидации"),
            @ApiResponse(responseCode = "404", description = "Заявка не найдена")
    })
    public ClaimDto addAttachment(
            @PathVariable Long id,
            @Parameter(description = "Файл (фото, PDF и т.п.)", required = true)
            @RequestParam("file") MultipartFile file,
            @Parameter(description = "ID загружающего пользователя", required = true)
            @RequestParam("uploaderId") String uploaderId,
            @Parameter(description = "Тип вложения: EVIDENCE, CLARIFICATION, RESPONDENT_COMMENT, PHOTO")
            @RequestParam(value = "type", defaultValue = "PHOTO") AttachmentType type) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File must not be empty");
        }
        String url = minioService.upload(file);
        claimService.addAttachment(id, uploaderId, type, url);
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

    @PostMapping(value = "/{id}/provide-docs", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Предоставить доп. материалы (заявитель)",
            description = "Отвечает на запрос доп. данных; загружает один или несколько файлов и возвращает в повторную проверку полноты."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Доп материалы приняты", content = @Content(schema = @Schema(implementation = ClaimDto.class))),
            @ApiResponse(responseCode = "400", description = "Неверный статус (ожидался NEED_ADDITIONAL_DATA) или валидация"),
            @ApiResponse(responseCode = "404", description = "Заявка не найдена")
    })
    public ClaimDto provideDocs(
            @PathVariable Long id,
            @Parameter(description = "Файлы (один или несколько)", required = true)
            @RequestParam("files") List<MultipartFile> files,
            @Parameter(description = "ID загружающего пользователя", required = true)
            @RequestParam("uploaderId") String uploaderId,
            @Parameter(description = "Тип вложения: EVIDENCE, CLARIFICATION, PHOTO")
            @RequestParam(value = "type", defaultValue = "CLARIFICATION") AttachmentType type) {
        for (MultipartFile file : files) {
            if (!file.isEmpty()) {
                String url = minioService.upload(file);
                claimService.addAttachment(id, uploaderId, type, url);
            }
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
