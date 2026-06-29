package bigbang.butilkka_be;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class ButilkkaBeApplication {

	public static void main(String[] args) {
		SpringApplication.run(ButilkkaBeApplication.class, args);
	}

}
