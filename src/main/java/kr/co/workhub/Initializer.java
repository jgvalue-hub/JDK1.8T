package kr.co.workhub;

import javax.servlet.*;
import org.springframework.web.filter.CharacterEncodingFilter;
import org.springframework.web.servlet.support.AbstractAnnotationConfigDispatcherServletInitializer;

public class Initializer extends AbstractAnnotationConfigDispatcherServletInitializer {
  protected Class<?>[] getRootConfigClasses() {
    return new Class<?>[] {AppConfig.class, SecurityConfig.class};
  }

  protected Class<?>[] getServletConfigClasses() {
    return new Class<?>[] {MvcConfig.class};
  }

  protected String[] getServletMappings() {
    return new String[] {"/"};
  }


  public void onStartup(ServletContext context) throws ServletException {
    super.onStartup(context);
    if (!"dev".equals(System.getProperty("spring.profiles.active")))
      context.getSessionCookieConfig().setSecure(true);
  }
}
