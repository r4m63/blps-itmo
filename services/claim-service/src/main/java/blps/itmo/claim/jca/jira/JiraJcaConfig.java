package blps.itmo.claim.jca.jira;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jca.support.LocalConnectionFactoryBean;

@Configuration
@ConditionalOnProperty(prefix = "jira", name = "enabled", havingValue = "true")
public class JiraJcaConfig {

    @Bean
    public LocalConnectionFactoryBean jiraConnectionFactory(
            @Value("${jira.url}") String url,
            @Value("${jira.user}") String user,
            @Value("${jira.token}") String token) {
        JiraManagedConnectionFactory mcf = new JiraManagedConnectionFactory();
        mcf.setBaseUrl(url);
        mcf.setUser(user);
        mcf.setToken(token);
        LocalConnectionFactoryBean factoryBean = new LocalConnectionFactoryBean();
        factoryBean.setManagedConnectionFactory(mcf);
        return factoryBean;
    }
}
