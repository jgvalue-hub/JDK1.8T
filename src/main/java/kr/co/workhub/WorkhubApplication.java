package kr.co.workhub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.autoconfigure.sql.init.SqlInitializationAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.ServletContextInitializer;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

@SpringBootApplication(
    exclude = {
      DataSourceAutoConfiguration.class, DataSourceTransactionManagerAutoConfiguration.class,
      JdbcTemplateAutoConfiguration.class, SqlInitializationAutoConfiguration.class,
      UserDetailsServiceAutoConfiguration.class
    })
public class WorkhubApplication extends SpringBootServletInitializer {
  public static void main(String[] args) {
    SpringApplication.run(WorkhubApplication.class, args);
  }

  @Override
  protected SpringApplicationBuilder configure(SpringApplicationBuilder app) {
    return app.sources(WorkhubApplication.class);
  }

  @Bean
  public ServletContextInitializer sessionPolicy(Environment env) {
    return context -> {
      context.getSessionCookieConfig().setHttpOnly(true);
      context.getSessionCookieConfig().setSecure(!env.acceptsProfiles(Profiles.of("dev")));
    };
  }
}
