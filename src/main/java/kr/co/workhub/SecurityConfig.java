package kr.co.workhub;

import java.util.*;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
  @Bean
  public UserDetailsService userDetails(JdbcTemplate db) {
    return username -> {
      List<Map<String, Object>> rows =
          db.queryForList(
              "SELECT username,password,role,enabled FROM users WHERE username=?", username);
      if (rows.isEmpty()) throw new UsernameNotFoundException("계정을 확인하세요");
      Map<String, Object> u = rows.get(0);
      return User.withUsername((String) u.get("username"))
          .password((String) u.get("password"))
          .roles((String) u.get("role"))
          .disabled(!Boolean.TRUE.equals(u.get("enabled")))
          .build();
    };
  }

  @Bean
  public DaoAuthenticationProvider auth(UserDetailsService service, BCryptPasswordEncoder encoder) {
    DaoAuthenticationProvider p = new DaoAuthenticationProvider();
    p.setUserDetailsService(service);
    p.setPasswordEncoder(encoder);
    return p;
  }

  @Bean
  public SecurityFilterChain security(
      HttpSecurity http, DaoAuthenticationProvider provider, Store store) throws Exception {
    http.authenticationProvider(provider)
        .authorizeRequests()
        .antMatchers("/login", "/assets/app.css")
        .permitAll()
        .antMatchers("/users/**", "/departments/**", "/audit")
        .hasRole("ADMIN")
        .antMatchers("/inbox")
        .hasAnyRole("MANAGER", "ADMIN")
        .anyRequest()
        .authenticated()
        .and()
        .formLogin()
        .loginPage("/login")
        .successHandler(
            (req, res, auth) -> {
              store.audit(auth.getName(), "LOGIN", "session");
              res.sendRedirect(req.getContextPath() + "/reports");
            })
        .failureUrl("/login?error")
        .permitAll()
        .and()
        .logout()
        .logoutUrl("/logout")
        .logoutSuccessUrl("/login?logout")
        .invalidateHttpSession(true)
        .deleteCookies("JSESSIONID")
        .and()
        .sessionManagement()
        .sessionFixation()
        .changeSessionId()
        .and()
        .headers()
        .contentSecurityPolicy(
            "default-src 'self'; style-src 'self'; img-src 'self'; frame-ancestors 'none';"
                + " form-action 'self'; base-uri 'none'");
    return http.build();
  }
}
