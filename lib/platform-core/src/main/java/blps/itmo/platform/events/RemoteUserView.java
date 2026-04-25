package blps.itmo.platform.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RemoteUserView {
    private Long id;
    private String email;
    private String role;
    private boolean enabled;
    private int penaltyCount;
}
