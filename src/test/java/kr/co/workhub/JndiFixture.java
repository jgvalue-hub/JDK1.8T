package kr.co.workhub;

import javax.naming.NamingException;
import javax.sql.DataSource;
import org.springframework.mock.jndi.SimpleNamingContextBuilder;

/** JNDI permits only one InitialContextFactoryBuilder per JVM; reuse it across tests. */
final class JndiFixture {
  private static final SimpleNamingContextBuilder NAMING = new SimpleNamingContextBuilder();

  static void bind(DataSource dataSource) throws NamingException {
    NAMING.bind("java:comp/env/jdbc/WorkhubDS", dataSource);
    NAMING.activate();
  }

  static void deactivate() {
    NAMING.deactivate();
  }

  private JndiFixture() {}
}
