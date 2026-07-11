package bigbang.butilkka_be.common.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.ConfigurableEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FlywayMigrationListenerTest {

    private final FlywayMigrationListener listener = new FlywayMigrationListener();

    @Mock
    private ConfigurableEnvironment environment;

    @Test
    void resolveConnection_readsConnectionDetailsFromEnvironmentProperties() {
        when(environment.getRequiredProperty("spring.datasource.url")).thenReturn("jdbc:mysql://prod-host:3306/butilkka");
        when(environment.getProperty("spring.datasource.username", "")).thenReturn("prod_user");
        when(environment.getProperty("spring.datasource.password", "")).thenReturn("prod_pass");

        FlywayMigrationListener.JdbcConnection connection = listener.resolveConnection(environment);

        assertThat(connection.url()).isEqualTo("jdbc:mysql://prod-host:3306/butilkka");
        assertThat(connection.username()).isEqualTo("prod_user");
        assertThat(connection.password()).isEqualTo("prod_pass");
    }
}
