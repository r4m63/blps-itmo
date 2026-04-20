package blps.itmo.config;

import java.util.Properties;

import javax.sql.DataSource;
import javax.sql.XADataSource;

import org.postgresql.xa.PGXADataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.jdbc.XADataSourceWrapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;

@Configuration
@EnableJpaRepositories(
        basePackages = "blps.itmo.repository.auth",
        entityManagerFactoryRef = "authEntityManagerFactory",
        transactionManagerRef = "transactionManager"
)
public class AuthPersistenceConfig {

    private static final Logger log = LoggerFactory.getLogger(AuthPersistenceConfig.class);

    @Value("${AUTH_DB_HOST}")
    private String host;

    @Value("${AUTH_DB_PORT}")
    private int port;

    @Value("${AUTH_DB_NAME}")
    private String databaseName;

    @Value("${AUTH_DB_USERNAME}")
    private String username;

    @Value("${AUTH_DB_PASSWORD}")
    private String password;

    @Bean("authXaDataSource")
    public XADataSource authXaDataSource() {
        PGXADataSource dataSource = new PGXADataSource();
        dataSource.setServerName(host);
        dataSource.setPortNumber(port);
        dataSource.setDatabaseName(databaseName);
        dataSource.setUrl("jdbc:postgresql://" + host + ":" + port + "/" + databaseName);
        dataSource.setUser(username);
        dataSource.setPassword(password);
        dataSource.setCurrentSchema("public");
        dataSource.setApplicationName("blps-auth-xa");
        log.info("Configured auth XA datasource for jdbc:postgresql://{}:{}/{}", host, port, databaseName);
        return dataSource;
    }

    @Bean("authDataSource")
    public DataSource authDataSource(
            XADataSourceWrapper wrapper,
            @Qualifier("authXaDataSource") XADataSource xaDataSource) throws Exception {
        return wrapper.wrapDataSource(xaDataSource);
    }

    @Bean("authEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean authEntityManagerFactory(
            @Qualifier("authDataSource") DataSource dataSource) {
        LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
        factory.setJtaDataSource(dataSource);
        factory.setPackagesToScan("blps.itmo.entity.auth");
        factory.setPersistenceUnitName("auth");

        HibernateJpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
        vendorAdapter.setDatabase(org.springframework.orm.jpa.vendor.Database.POSTGRESQL);
        factory.setJpaVendorAdapter(vendorAdapter);

        Properties props = new Properties();
        props.setProperty("hibernate.transaction.jta.platform", NarayanaJtaPlatform.class.getName());
        props.setProperty("hibernate.transaction.coordinator_class", "jta");
        props.setProperty("jakarta.persistence.transactionType", "JTA");
        props.setProperty("hibernate.hbm2ddl.auto", "none");
        props.setProperty("hibernate.show_sql", "true");
        props.setProperty("hibernate.format_sql", "true");
        props.setProperty("hibernate.default_schema", "public");
        factory.setJpaProperties(props);

        return factory;
    }
}
