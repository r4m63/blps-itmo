package blps.itmo.platform.grpc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import com.google.protobuf.Timestamp;

/**
 * МАППИНГ ТИПОВ ДАННЫХ МЕЖДУ Java И Protobuf.
 *
 * Проблема: Protobuf (язык описания gRPC) имеет свои типы данных,
 * которые не всегда совпадают с Java типами.
 *
 * Protobuf тип → Java тип:
 * - string → String
 * - int64 → Long
 * - Timestamp → com.google.protobuf.Timestamp (не java.time.Instant!)
 *
 * Эти методы помогают конвертировать:
 * - Java BigDecimal → string (proto не умеет в decimal)
 * - Java OffsetDateTime → proto Timestamp
 * - Обработка null (прото-типы не могут быть null)
 */
public final class GrpcMapping {

    private GrpcMapping() {
        // Утилитный класс
    }

    // ========== Money (BigDecimal) ==========

    /**
     * Конвертирует BigDecimal в строку для передачи по gRPC.
     *
     * Почему не использовать double/float?
     * - Они неточные для финансов (0.1 + 0.2 может быть 0.30000000000000004)
     * - BigDecimal дает точное представление
     *
     * В proto-файле это поле выглядит так:
     *   string amount = 1;  // денежная сумма в формате "123.45"
     */
    public static String money(BigDecimal value) {
        return value == null ? "" : value.toPlainString();  // "123.45" без экспоненты
    }

    /**
     * Конвертирует строку из gRPC обратно в BigDecimal.
     * Пустая строка → null (не было значения)
     */
    public static BigDecimal money(String value) {
        return value == null || value.isBlank() ? null : new BigDecimal(value);
    }

    // ========== String ==========

    /**
     * Защита от null для строк.
     * Protobuf строки не могут быть null (только пустые), поэтому
     * null в Java превращаем в пустую строку для gRPC
     */
    public static String text(String value) {
        return value == null ? "" : value;
    }

    // ========== DateTime ==========

    /**
     * Конвертирует Java OffsetDateTime в Protobuf Timestamp.
     *
     * Protobuf Timestamp хранит:
     * - seconds (с 1970-01-01)
     * - nanos (доли секунды)
     *
     * Все даты передаются в UTC (ZoneOffset.UTC) для единообразия
     */
    public static Timestamp timestamp(OffsetDateTime value) {
        if (value == null) {
            return Timestamp.getDefaultInstance();  // пустой timestamp
        }
        Instant instant = value.toInstant();
        return Timestamp.newBuilder()
                .setSeconds(instant.getEpochSecond())
                .setNanos(instant.getNano())
                .build();
    }

    /**
     * Конвертирует Protobuf Timestamp обратно в OffsetDateTime.
     *
     * Если timestamp пустой (0 секунд, 0 наносекунд) → null
     * Всегда возвращает время в UTC
     */
    public static OffsetDateTime offsetDateTime(Timestamp value) {
        if (value == null || (value.getSeconds() == 0 && value.getNanos() == 0)) {
            return null;
        }
        return OffsetDateTime.ofInstant(
                Instant.ofEpochSecond(value.getSeconds(), value.getNanos()),
                ZoneOffset.UTC  // всегда UTC
        );
    }
}