package bigbang.butilkka_be.common.config;

import org.flywaydb.core.Flyway;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * Spring Boot 4.1 does not ship a Flyway auto-configuration module, so migrations
 * are triggered manually here, before any DataSource-consuming bean (e.g. the JPA
 * EntityManagerFactory) is created.
 */
public class FlywayMigrationListener implements ApplicationListener<ApplicationEnvironmentPreparedEvent>, Ordered {

    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
        ConfigurableEnvironment env = event.getEnvironment();
        if (!env.getProperty("spring.flyway.enabled", Boolean.class, false)) {
            return;
        }

        String url = env.getRequiredProperty("spring.datasource.url");
        String user = env.getProperty("spring.datasource.username", "");
        String password = env.getProperty("spring.datasource.password", "");

        Flyway.configure()
                .dataSource(url, user, password)
                .load()
                .migrate();
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 20;
    }
}
