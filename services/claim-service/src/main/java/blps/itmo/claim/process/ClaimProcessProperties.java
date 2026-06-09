package blps.itmo.claim.process;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "claim-process")
public class ClaimProcessProperties {

    private Timers timers = new Timers();

    public Timers getTimers() {
        return timers;
    }

    public void setTimers(Timers timers) {
        this.timers = timers;
    }

    public static class Timers {
        private String startedHold = "PT0S";
        private String tenantResponseTimeout = "PT72H";
        private String awaitingPenaltyApplied = "PT60S";
        private String awaitingPenaltyCounted = "PT60S";
        private String compensatingRetry = "PT30S";

        public String getStartedHold() {
            return startedHold;
        }

        public void setStartedHold(String startedHold) {
            this.startedHold = startedHold;
        }

        public String getTenantResponseTimeout() {
            return tenantResponseTimeout;
        }

        public void setTenantResponseTimeout(String tenantResponseTimeout) {
            this.tenantResponseTimeout = tenantResponseTimeout;
        }

        public String getAwaitingPenaltyApplied() {
            return awaitingPenaltyApplied;
        }

        public void setAwaitingPenaltyApplied(String awaitingPenaltyApplied) {
            this.awaitingPenaltyApplied = awaitingPenaltyApplied;
        }

        public String getAwaitingPenaltyCounted() {
            return awaitingPenaltyCounted;
        }

        public void setAwaitingPenaltyCounted(String awaitingPenaltyCounted) {
            this.awaitingPenaltyCounted = awaitingPenaltyCounted;
        }

        public String getCompensatingRetry() {
            return compensatingRetry;
        }

        public void setCompensatingRetry(String compensatingRetry) {
            this.compensatingRetry = compensatingRetry;
        }
    }
}
