package blps.itmo.penalty.jobs;

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
    public JobDetail penaltyRetryJobDetail() {
        return newJob(PenaltyRetryJob.class)
                .withIdentity("penaltyRetryJob")
                .storeDurably()
                .build();
    }

    @Bean
    public Trigger penaltyRetryTrigger(JobDetail penaltyRetryJobDetail) {
        return newTrigger()
                .forJob(penaltyRetryJobDetail)
                .withIdentity("penaltyRetryTrigger")
                .withSchedule(cronSchedule("0 */5 * * * ?"))
                .build();
    }
}
