package blps.itmo.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
public class TransactionTemplateConfig {

    @Bean("jtaTransactionTemplate")
    public TransactionTemplate jtaTransactionTemplate(
            @Qualifier("transactionManager") PlatformTransactionManager transactionManager,
            @Value("${narayana.default-timeout:60}") int timeoutSeconds) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        template.setTimeout(timeoutSeconds);
        return template;
    }

    @Bean("jtaReadOnlyTransactionTemplate")
    public TransactionTemplate jtaReadOnlyTransactionTemplate(
            @Qualifier("transactionManager") PlatformTransactionManager transactionManager,
            @Value("${narayana.default-timeout:60}") int timeoutSeconds) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        template.setReadOnly(true);
        template.setTimeout(timeoutSeconds);
        return template;
    }
}
