package kr.co.workhub;

import static org.junit.Assert.*;

import javax.sql.DataSource;
import org.junit.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.mock.jndi.SimpleNamingContextBuilder;

public class ProductionConfigTest {
  @Test
  public void productionUsesJndiWithoutSeeding() throws Exception {
    DriverManagerDataSource ds =
        new DriverManagerDataSource("jdbc:h2:mem:productionconfig;DB_CLOSE_DELAY=-1", "sa", "");
    new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(ds);
    SimpleNamingContextBuilder naming = new SimpleNamingContextBuilder();
    naming.bind("java:comp/env/jdbc/WorkhubDS", ds);
    naming.activate();
    try (AnnotationConfigApplicationContext context =
        new AnnotationConfigApplicationContext(AppConfig.class)) {
      assertSame(ds, context.getBean(DataSource.class));
      assertFalse(context.containsBean("demoData"));
      assertEquals(
          Integer.valueOf(0),
          context
              .getBean(Store.class)
              .db
              .queryForObject("SELECT COUNT(*) FROM users", Integer.class));
    } finally {
      naming.deactivate();
    }
  }
}
