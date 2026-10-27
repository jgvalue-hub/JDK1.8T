package kr.co.workhub;

import org.springframework.context.annotation.*;
import org.springframework.web.servlet.config.annotation.*;
import org.springframework.web.servlet.view.InternalResourceViewResolver;

@Configuration
@EnableWebMvc
@ComponentScan(
    basePackageClasses = WebController.class,
    useDefaultFilters = false,
    includeFilters = @ComponentScan.Filter(org.springframework.stereotype.Controller.class))
public class MvcConfig implements WebMvcConfigurer {
  @Bean
  public InternalResourceViewResolver views() {
    return new InternalResourceViewResolver("/WEB-INF/views/", ".jsp");
  }
  // Exact controller mappings serve static assets; no filesystem resource handler.
}
