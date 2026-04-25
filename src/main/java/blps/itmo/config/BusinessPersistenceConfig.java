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
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration
@EnableTransactionManagement
@EnableJpaRepositories(
        basePackages = "blps.itmo.repository.business",
        entityManagerFactoryRef = "businessEntityManagerFactory",
        transactionManagerRef = "transactionManager"
)
public class BusinessPersistenceConfig {

    private static final Logger log = LoggerFactory.getLogger(BusinessPersistenceConfig.class);

    @Value("${BUSINESS_DB_HOST}")
    private String host;

    @Value("${BUSINESS_DB_PORT}")
    private int port;

    @Value("${BUSINESS_DB_NAME}")
    private String databaseName;

    @Value("${BUSINESS_DB_USERNAME}")
    private String username;

    @Value("${BUSINESS_DB_PASSWORD}")
    private String password;

    @Primary
    @Bean("businessXaDataSource")
    public XADataSource businessXaDataSource() {
        PGXADataSource dataSource = new PGXADataSource();
        dataSource.setServerName(host);
        dataSource.setPortNumber(port);
        dataSource.setDatabaseName(databaseName);
        dataSource.setUrl("jdbc:postgresql://" + host + ":" + port + "/" + databaseName);
        dataSource.setUser(username);
        dataSource.setPassword(password);
        dataSource.setCurrentSchema("public");
        dataSource.setApplicationName("blps-business-xa");
        log.info("Configured business XA datasource for jdbc:postgresql://{}:{}/{}", host, port, databaseName);
        return dataSource;
    }

    @Primary
    @Bean("businessDataSource")
    public DataSource businessDataSource(
            XADataSourceWrapper wrapper,
            @Qualifier("businessXaDataSource") XADataSource xaDataSource) throws Exception {
        return wrapper.wrapDataSource(xaDataSource);
    }

    @Primary
    @Bean("businessEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean businessEntityManagerFactory(
            @Qualifier("businessDataSource") DataSource dataSource) {
        LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
        factory.setJtaDataSource(dataSource);
        factory.setPackagesToScan("blps.itmo.entity.business");
        factory.setPersistenceUnitName("business");

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
