package blps.itmo.platform.grpc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import com.google.protobuf.Timestamp;

public final class GrpcMapping {

    private GrpcMapping() {
    }

    public static String money(BigDecimal value) {
        return value == null ? "" : value.toPlainString();
    }

    public static BigDecimal money(String value) {
        return value == null || value.isBlank() ? null : new BigDecimal(value);
    }

    public static String text(String value) {
        return value == null ? "" : value;
    }

    public static Timestamp timestamp(OffsetDateTime value) {
        if (value == null) {
            return Timestamp.getDefaultInstance();
        }
        Instant instant = value.toInstant();
        return Timestamp.newBuilder()
                .setSeconds(instant.getEpochSecond())
                .setNanos(instant.getNano())
                .build();
    }

    public static OffsetDateTime offsetDateTime(Timestamp value) {
        if (value == null || (value.getSeconds() == 0 && value.getNanos() == 0)) {
            return null;
        }
        return OffsetDateTime.ofInstant(Instant.ofEpochSecond(value.getSeconds(), value.getNanos()), ZoneOffset.UTC);
    }
}
