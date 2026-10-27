package kr.co.workhub;

import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.jdbc.datasource.lookup.JndiDataSourceLookup;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
public class AppConfig {
  @Bean
  @Profile("dev")
  public DataSource devDataSource() {
    DriverManagerDataSource ds = new DriverManagerDataSource();
    ds.setDriverClassName("org.h2.Driver");
    ds.setUrl(
        System.getProperty(
            "workhub.db.url", "jdbc:h2:file:./.data/workhub;DB_CLOSE_ON_EXIT=FALSE"));
    ds.setUsername("sa");
    ds.setPassword("");
    new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(ds);
    return ds;
  }

  @Bean
  @Profile("container-demo")
  public String containerDemoSecret(Environment env) {
    if (env.acceptsProfiles(org.springframework.core.env.Profiles.of("dev")))
      throw new IllegalStateException("container-demo cannot be combined with dev");
    return ContainerDemoPolicy.requireSecret(env.getProperty("WORKHUB_DEMO_PASSWORD"));
  }

  @Bean
  @Profile("container-demo")
  public DataSource containerDataSource(
      @Qualifier("containerDemoSecret") String secret, Environment env) {
    String directory = env.getProperty("WORKHUB_DATA_DIR", "/var/lib/workhub");
    if (!directory.matches("/[A-Za-z0-9_./-]+") || directory.contains(".."))
      throw new IllegalStateException("WORKHUB_DATA_DIR must be a simple absolute directory");
    DriverManagerDataSource ds = new DriverManagerDataSource();
    ds.setDriverClassName("org.h2.Driver");
    ds.setUrl("jdbc:h2:file:" + directory + "/workhub;DB_CLOSE_ON_EXIT=FALSE");
    ds.setUsername("sa");
    ds.setPassword("");
    new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(ds);
    return ds;
  }

  @Bean
  @Profile("container-demo")
  public Object containerDemoData(Store store, @Qualifier("containerDemoSecret") String secret) {
    store.seedContainerDemo(secret);
    return new Object();
  }

  @Bean
  @Profile("!dev & !container-demo")
  public DataSource productionDataSource() {
    return new JndiDataSourceLookup()
        .getDataSource(System.getProperty("workhub.jndi", "java:comp/env/jdbc/WorkhubDS"));
  }

  @Bean
  public JdbcTemplate jdbc(DataSource ds) {
    return new JdbcTemplate(ds);
  }

  @Bean
  public TransactionTemplate transactions(DataSource ds) {
    return new TransactionTemplate(new DataSourceTransactionManager(ds));
  }

  @Bean
  public BCryptPasswordEncoder passwords() {
    return new BoundedBCryptPasswordEncoder();
  }

  @Bean
  public Store store(JdbcTemplate jdbc, TransactionTemplate tx, BCryptPasswordEncoder encoder) {
    return new Store(jdbc, tx, encoder);
  }

  @Bean
  @Profile("dev")
  public Object demoData(Store s) {
    s.seed();
    return new Object();
  }
}
