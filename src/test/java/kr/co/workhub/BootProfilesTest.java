package kr.co.workhub;

import static org.junit.Assert.*;

import javax.sql.DataSource;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

public class BootProfilesTest {
  @Rule public TemporaryFolder temp = new TemporaryFolder();

  private SpringApplicationBuilder application(String... profiles) {
    return new SpringApplicationBuilder(WorkhubApplication.class)
        .profiles(profiles)
        .properties("server.port=0", "server.address=127.0.0.1", "spring.main.banner-mode=off");
  }

  @Test
  public void bootProductionUsesOnlyJndiAndNoSeed() throws Exception {
    DriverManagerDataSource ds =
        new DriverManagerDataSource("jdbc:h2:mem:bootproduction;DB_CLOSE_DELAY=-1", "sa", "");
    new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(ds);
    JndiFixture.bind(ds);
    try (ConfigurableApplicationContext c = application().run()) {
      assertSame(ds, c.getBean(DataSource.class));
      assertEquals(1, c.getBeansOfType(DataSource.class).size());
      assertFalse(c.containsBean("demoData"));
      assertFalse(c.containsBean("containerDemoData"));
      assertEquals(
          Integer.valueOf(0),
          c.getBean(Store.class).db.queryForObject("SELECT COUNT(*) FROM users", Integer.class));
    } finally {
      JndiFixture.deactivate();
    }
  }

  @Test
  public void bootContainerDemoInitializesOnceWithoutLocalAccounts() throws Exception {
    // Non-deployment test fixture; no deployed credential is read or generated.
    String fixture = "UnitTestOnly-NotADeploymentSecret-12345";
    try (ConfigurableApplicationContext c =
        application("container-demo")
            .properties(
                "WORKHUB_DEMO_PASSWORD=" + fixture,
                "WORKHUB_DATA_DIR=" + temp.newFolder().getAbsolutePath())
            .run()) {
      assertEquals(1, c.getBeansOfType(DataSource.class).size());
      assertFalse(c.containsBean("demoData"));
      assertFalse(c.containsBean("productionDataSource"));
      Store store = c.getBean(Store.class);
      assertEquals(
          Integer.valueOf(1),
          store.db.queryForObject("SELECT COUNT(*) FROM users WHERE enabled=TRUE", Integer.class));
      assertTrue(store.encoder.matches(fixture, (String) store.raw("users", 1).get("password")));
    }
  }

  @Test
  public void bootContainerDemoWithoutSecretFailsClosed() {
    try (ConfigurableApplicationContext ignored =
        application("container-demo").run("--WORKHUB_DEMO_PASSWORD=")) {
      fail("Missing secret permitted startup");
    } catch (RuntimeException expected) {
      assertTrue(rootMessage(expected).contains("WORKHUB_DEMO_PASSWORD"));
    }
  }

  @Test
  public void bootMixedDemoProfilesFailClosed() {
    try (ConfigurableApplicationContext ignored = application("dev", "container-demo").run()) {
      fail("Mixed demo profiles permitted startup");
    } catch (RuntimeException expected) {
      // Either explicit profile guard or ambiguous DataSource must prevent startup.
      assertTrue(
          rootMessage(expected).contains("cannot be combined")
              || rootMessage(expected).contains("expected single matching bean"));
    }
  }

  private String rootMessage(Throwable t) {
    while (t.getCause() != null) t = t.getCause();
    return String.valueOf(t.getMessage());
  }
}
