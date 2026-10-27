package kr.co.workhub;

import static org.junit.Assert.*;

import java.util.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.support.TransactionTemplate;

public class ContainerDemoTest {
  // Ephemeral test fixture only; never supplied to a deployed service.
  private static final String FIXTURE = "UnitTestOnly-NotADeploymentSecret-12345";
  @Rule public TemporaryFolder temp = new TemporaryFolder();

  Store store(String name) {
    DriverManagerDataSource ds =
        new DriverManagerDataSource("jdbc:h2:mem:" + name + ";DB_CLOSE_DELAY=-1", "sa", "");
    new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(ds);
    return new Store(
        new JdbcTemplate(ds),
        new TransactionTemplate(new DataSourceTransactionManager(ds)),
        new BoundedBCryptPasswordEncoder());
  }

  @Test
  public void secretsFailClosed() {
    for (String p :
        new String[] {
          null,
          "",
          "DevOnly!2026pass",
          "short",
          String.join("", Collections.nCopies(73, "a")),
          "bad secret with spaces 123456789"
        }) {
      try {
        ContainerDemoPolicy.requireSecret(p);
        fail("invalid secret accepted");
      } catch (IllegalStateException expected) {
        assertFalse(expected.getMessage().contains("DevOnly!2026pass"));
      }
    }
  }

  @Test
  public void onlyAdminEnabledWithSuppliedSecret() {
    Store s = store("containerfresh");
    s.seedContainerDemo(FIXTURE);
    assertEquals(
        Integer.valueOf(1),
        s.db.queryForObject("SELECT COUNT(*) FROM users WHERE enabled=TRUE", Integer.class));
    String hash = (String) s.raw("users", 1).get("password");
    assertTrue(s.encoder.matches(FIXTURE, hash));
    assertFalse(s.encoder.matches("DevOnly!2026pass", hash));
  }

  @Test
  public void refusesLegacyDevVolume() {
    Store s = store("legacyvolume");
    s.seed();
    try {
      s.seedContainerDemo(FIXTURE);
      fail("legacy dev database accepted");
    } catch (IllegalStateException expected) {
      assertTrue(expected.getMessage().contains("local dev database"));
    }
  }

  @Test
  public void restartDoesNotResetPasswords() {
    Store s = store("containerrestart");
    s.seedContainerDemo(FIXTURE);
    String original = (String) s.raw("users", 1).get("password");
    s.seedContainerDemo("SecondUnitTestFixture-123456789");
    assertEquals(original, s.raw("users", 1).get("password"));
  }

  @Test
  public void profileUsesIsolatedFileDatabase() throws Exception {
    try (AnnotationConfigApplicationContext c = new AnnotationConfigApplicationContext()) {
      c.getEnvironment().setActiveProfiles("container-demo");
      Map<String, Object> props = new HashMap<>();
      props.put("WORKHUB_DEMO_PASSWORD", FIXTURE);
      props.put("WORKHUB_DATA_DIR", temp.newFolder().getAbsolutePath());
      c.getEnvironment()
          .getPropertySources()
          .addFirst(new MapPropertySource("test-fixtures", props));
      c.register(AppConfig.class);
      c.refresh();
      assertFalse(c.containsBean("demoData"));
      assertFalse(c.containsBean("productionDataSource"));
      assertTrue(c.containsBean("containerDemoData"));
      assertEquals("ADMIN", c.getBean(Store.class).actor("admin").role);
    }
  }

  @Test
  public void profileWithoutSecretCannotStart() {
    try (AnnotationConfigApplicationContext c = new AnnotationConfigApplicationContext()) {
      c.getEnvironment().setActiveProfiles("container-demo");
      c.register(AppConfig.class);
      try {
        c.refresh();
        fail("missing secret permitted startup");
      } catch (org.springframework.beans.BeansException expected) {
        assertTrue(expected.getMessage().contains("WORKHUB_DEMO_PASSWORD"));
      }
    }
  }
}
