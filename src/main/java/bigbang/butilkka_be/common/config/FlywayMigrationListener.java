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
        JdbcConnectionDetails connectionDetails = beanFactory.getBeanProvider(JdbcConnectionDetails.class).getIfAvailable();
        if (connectionDetails != null) {
            return new JdbcConnection(
                    connectionDetails.getJdbcUrl(), connectionDetails.getUsername(), connectionDetails.getPassword());
        }

        String url = env.getRequiredProperty("spring.datasource.url");
        String user = env.getProperty("spring.datasource.username", "");
        String password = env.getProperty("spring.datasource.password", "");
        return new JdbcConnection(url, user, password);
    }

    record JdbcConnection(String url, String username, String password) {
    }
}
