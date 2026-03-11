package blps.itmo.controller;

import blps.itmo.dto.UploadResponse;
import blps.itmo.service.MinioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
@Tag(name = "Storage", description = "Загрузка файлов в MinIO")
public class StorageController {

    private final MinioService minioService;

    public StorageController(MinioService minioService) {
        this.minioService = minioService;
    }

    @PostMapping(value = "/storage/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Загрузить файл в MinIO (внутренний)",
            description = "Загружает произвольный файл и возвращает URL. Используйте /api/claims/{id}/attachments для прикрепления файла к заявке.",
            responses = {
                    @ApiResponse(responseCode = "201", description = "Файл загружен, URL возвращён",
                            content = @Content(schema = @Schema(implementation = UploadResponse.class))),
                    @ApiResponse(responseCode = "400", description = "Файл не передан"),
                    @ApiResponse(responseCode = "500", description = "Ошибка загрузки в MinIO")
            }
    )
    public UploadResponse upload(
            @Parameter(description = "Файл для загрузки (фото, PDF, и т.п.)", required = true)
            @RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File must not be empty");
        }
        String url = minioService.upload(file);
        return new UploadResponse(url, file.getOriginalFilename(), file.getSize());
    }
}
