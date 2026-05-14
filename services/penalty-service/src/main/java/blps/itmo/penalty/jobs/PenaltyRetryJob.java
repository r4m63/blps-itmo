package blps.itmo.penalty.jobs;

import blps.itmo.penalty.service.PenaltyService;
import lombok.RequiredArgsConstructor;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PenaltyRetryJob implements Job {

    private final PenaltyService penaltyService;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        penaltyService.retryFailedBatch(20);
    }
}
