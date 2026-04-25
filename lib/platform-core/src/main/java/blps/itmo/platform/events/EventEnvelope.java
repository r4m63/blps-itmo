package blps.itmo.platform.events;

import java.time.Instant;

import com.fasterxml.jackson.databind.JsonNode;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventEnvelope {
    private String eventId;
    private EventType eventType;
    @Builder.Default
    private int eventVersion = 1;
    private Instant occurredAt;
    private String producerService;
    private String correlationId;
    private String sagaId;
    private String aggregateType;
    private String aggregateId;
    private Long actorId;
    private JsonNode payload;
}
