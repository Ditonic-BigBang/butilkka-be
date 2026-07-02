package bigbang.butilkka_be.common.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails;
import org.springframework.core.env.ConfigurableEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FlywayMigrationListenerTest {

    private final FlywayMigrationListener listener = new FlywayMigrationListener();

    @Mock
    private ConfigurableListableBeanFactory beanFactory;
    @Mock
    private ConfigurableEnvironment environment;
    @Mock
    private ObjectProvider<JdbcConnectionDetails> connectionDetailsProvider;
    @Mock
    private JdbcConnectionDetails connectionDetails;

    @Test
    void resolveConnection_prefersDockerComposeConnectionDetailsWhenAvailable() {
        when(beanFactory.getBeanProvider(JdbcConnectionDetails.class)).thenReturn(connectionDetailsProvider);
        when(connectionDetailsProvider.getIfAvailable()).thenReturn(connectionDetails);
        when(connectionDetails.getJdbcUrl()).thenReturn("jdbc:mysql://localhost:3307/butilkka");
        when(connectionDetails.getUsername()).thenReturn("root");
        when(connectionDetails.getPassword()).thenReturn("1234");

        FlywayMigrationListener.JdbcConnection connection = listener.resolveConnection(beanFactory, environment);

        assertThat(connection.url()).isEqualTo("jdbc:mysql://localhost:3307/butilkka");
        assertThat(connection.username()).isEqualTo("root");
        assertThat(connection.password()).isEqualTo("1234");
    }

    @Test
    void resolveConnection_fallsBackToEnvironmentPropertiesWhenNoConnectionDetailsBean() {
        when(beanFactory.getBeanProvider(JdbcConnectionDetails.class)).thenReturn(connectionDetailsProvider);
        when(connectionDetailsProvider.getIfAvailable()).thenReturn(null);
        when(environment.getRequiredProperty("spring.datasource.url")).thenReturn("jdbc:mysql://prod-host:3306/butilkka");
        when(environment.getProperty("spring.datasource.username", "")).thenReturn("prod_user");
        when(environment.getProperty("spring.datasource.password", "")).thenReturn("prod_pass");

        FlywayMigrationListener.JdbcConnection connection = listener.resolveConnection(beanFactory, environment);

        assertThat(connection.url()).isEqualTo("jdbc:mysql://prod-host:3306/butilkka");
        assertThat(connection.username()).isEqualTo("prod_user");
        assertThat(connection.password()).isEqualTo("prod_pass");
    }
}
