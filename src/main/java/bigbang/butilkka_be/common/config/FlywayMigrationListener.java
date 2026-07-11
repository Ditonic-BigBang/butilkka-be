package bigbang.butilkka_be.common.config;

import org.flywaydb.core.Flyway;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * Spring Boot 4.1 does not ship a Flyway auto-configuration module, so migrations
 * are triggered manually here, before any DataSource-consuming bean (e.g. the JPA
 * EntityManagerFactory) is instantiated. Registered as a manually-added
 * BeanFactoryPostProcessor (see ButilkkaBeApplication.main) so it runs once the
 * ApplicationContext's bean factory is active during refresh() - by then
 * spring-boot-docker-compose has already registered a JdbcConnectionDetails bean
 * reflecting the container's actual mapped port/credentials, which raw
 * spring.datasource.* properties never reflect - but before singleton beans like
 * EntityManagerFactory are created.
 */
public class FlywayMigrationListener implements BeanFactoryPostProcessor {

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        ConfigurableEnvironment env = beanFactory.getBean(ConfigurableEnvironment.class);
        if (!env.getProperty("spring.flyway.enabled", Boolean.class, false)) {
            return;
        }

        JdbcConnection connection = resolveConnection(beanFactory, env);

        Flyway.configure()
                .dataSource(connection.url(), connection.username(), connection.password())
                .load()
                .migrate();
    }

    JdbcConnection resolveConnection(ConfigurableListableBeanFactory beanFactory, ConfigurableEnvironment env) {
        try {
            JdbcConnectionDetails connectionDetails =
                    beanFactory.getBeanProvider(JdbcConnectionDetails.class).getIfAvailable();
            if (connectionDetails != null) {
                return new JdbcConnection(
                        connectionDetails.getJdbcUrl(), connectionDetails.getUsername(), connectionDetails.getPassword());
            }
        } catch (BeansException | IllegalStateException ex) {
            // This runs before @ConfigurationProperties binding has populated DataSourceProperties.
            // When no docker-compose-provided JdbcConnectionDetails bean exists, the only candidate
            // is Spring Boot's own PropertiesJdbcConnectionDetails, which just wraps DataSourceProperties -
            // calling getJdbcUrl() on it triggers DataSourceProperties.determineUrl(), which fails
            // here because the properties haven't been bound yet. Fall back to reading
            // spring.datasource.* straight from the Environment, which is already fully populated.
        }

        String url = env.getRequiredProperty("spring.datasource.url");
        String user = env.getProperty("spring.datasource.username", "");
        String password = env.getProperty("spring.datasource.password", "");
        return new JdbcConnection(url, user, password);
    }

    record JdbcConnection(String url, String username, String password) {
    }
}
