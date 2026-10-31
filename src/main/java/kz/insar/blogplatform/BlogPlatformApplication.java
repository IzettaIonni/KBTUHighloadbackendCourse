package kz.insar.blogplatform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class BlogPlatformApplication {

	public static void main(String[] args) {
		SpringApplication.run(BlogPlatformApplication.class, args);
	}

}
