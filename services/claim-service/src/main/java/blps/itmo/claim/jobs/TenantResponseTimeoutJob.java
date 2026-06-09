package blps.itmo.claim.jobs;

import blps.itmo.claim.service.ClaimService;
import lombok.RequiredArgsConstructor;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Component
@RequiredArgsConstructor
public class TenantResponseTimeoutJob implements Job {

    private final ClaimService claimService;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        Instant threshold = Instant.now().minus(Duration.ofDays(3));
        claimService.expireTenantResponses(threshold);
    }
}
