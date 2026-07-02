package bigbang.butilkka_be;

import bigbang.butilkka_be.common.config.FlywayMigrationListener;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class ButilkkaBeApplication {

	public static void main(String[] args) {
		SpringApplication app = new SpringApplication(ButilkkaBeApplication.class);
		app.addInitializers(context -> context.addBeanFactoryPostProcessor(new FlywayMigrationListener()));
		app.run(args);
	}

}
