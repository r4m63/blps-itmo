package blps.itmo.claim.process;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ClaimProcessProperties.class)
public class ClaimProcessConfig {
}
