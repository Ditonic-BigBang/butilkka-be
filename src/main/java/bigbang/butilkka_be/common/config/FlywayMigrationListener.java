package bigbang.butilkka_be.common.config;

import org.flywaydb.core.Flyway;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * Spring Boot 4.1 does not ship a Flyway auto-configuration module, so migrations
 * are triggered manually here, before any DataSource-consuming bean (e.g. the JPA
 * EntityManagerFactory) is instantiated. Registered as a manually-added
 * BeanFactoryPostProcessor (see ButilkkaBeApplication.main) so it runs once the
 * ApplicationContext's bean factory is active during refresh(), but before
 * singleton beans like EntityManagerFactory are created.
 *
 * Connection details are read directly from the Environment rather than through a
 * JdbcConnectionDetails bean. BeanFactoryPostProcessors run before Spring registers
 * ConfigurationPropertiesBindingPostProcessor, so eagerly resolving Spring Boot's
 * auto-configured JdbcConnectionDetails bean here would build - and permanently
 * cache as a singleton - an unbound, empty DataSourceProperties instance, silently
 * corrupting the real DataSource bean created later in the context lifecycle.
 */
public class FlywayMigrationListener implements BeanFactoryPostProcessor {

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        ConfigurableEnvironment env = beanFactory.getBean(ConfigurableEnvironment.class);
        if (!env.getProperty("spring.flyway.enabled", Boolean.class, false)) {
            return;
        }

        JdbcConnection connection = resolveConnection(env);

        Flyway.configure()
                .dataSource(connection.url(), connection.username(), connection.password())
                .load()
                .migrate();
    }

    JdbcConnection resolveConnection(ConfigurableEnvironment env) {
        String url = env.getRequiredProperty("spring.datasource.url");
        String user = env.getProperty("spring.datasource.username", "");
        String password = env.getProperty("spring.datasource.password", "");
        return new JdbcConnection(url, user, password);
    }

    record JdbcConnection(String url, String username, String password) {
    }
}
