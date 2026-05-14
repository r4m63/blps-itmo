package blps.itmo.claim.jobs;

import org.quartz.JobDetail;
import org.quartz.Trigger;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.quartz.JobBuilder.newJob;
import static org.quartz.TriggerBuilder.newTrigger;
import static org.quartz.CronScheduleBuilder.cronSchedule;

@Configuration
public class QuartzJobsConfig {

    @Bean
    public JobDetail tenantResponseTimeoutJobDetail() {
        return newJob(TenantResponseTimeoutJob.class)
                .withIdentity("tenantResponseTimeoutJob")
                .storeDurably()
                .build();
    }

    @Bean
    public Trigger tenantResponseTimeoutTrigger(JobDetail tenantResponseTimeoutJobDetail) {
        return newTrigger()
                .forJob(tenantResponseTimeoutJobDetail)
                .withIdentity("tenantResponseTimeoutTrigger")
                .withSchedule(cronSchedule("0 */1 * * * ?"))
                .build();
    }
}
