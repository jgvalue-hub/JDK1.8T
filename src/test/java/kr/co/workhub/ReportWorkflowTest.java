package kr.co.workhub;

import static org.junit.Assert.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.context.WebApplicationContext;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest(
    classes = WorkhubApplication.class,
    properties = "workhub.db.url=jdbc:h2:mem:reporttests;DB_CLOSE_DELAY=-1",
    webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("dev")
public class ReportWorkflowTest {
  @Autowired ReportStore r;
  @Autowired Store s;
  @Autowired WebApplicationContext context;
  MockMvc mvc;
  Store.Actor user, manager, other, admin;

  @Before
  public void setup() {
    s.db.update("UPDATE financial_reports SET source_id=NULL");
    s.db.update("DELETE FROM report_events");
    s.db.update("DELETE FROM report_lines");
    s.db.update("DELETE FROM financial_reports");
    user = s.actor("user");
    manager = s.actor("manager");
    other = s.actor("other");
    admin = s.actor("admin");
    mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  static String[] amounts(String family) {
    switch (family) {
      case "LEDGER":
        return new String[] {"1000", "1000", "0"};
      case "RECEIVABLE":
        return new String[] {"1000", "300", "95"};
      case "PAYABLE":
        return new String[] {"1000", "300", "50"};
      case "ASSET":
        return new String[] {"1200", "0", "12"};
      case "BUDGET":
        return new String[] {"1000", "800", "1100"};
      case "CASH":
        return new String[] {"500", "300", "200"};
      case "MARKET":
        return new String[] {"10000", "10", "20"};
      case "CREDIT":
        return new String[] {"10000", "5", "40"};
      case "OPERATION":
        return new String[] {"4", "4", "25"};
      default:
        return new String[] {"20", "15", "2"};
    }
  }

  MultiValueMap<String, String> form(String code) {
    ReportCatalog.Type t = ReportCatalog.get(code);
    String[] x = amounts(t.family);
    MultiValueMap<String, String> f = new LinkedMultiValueMap<>();
    String[][] fields = {
      {"id", "0"},
      {"version", "0"},
      {"title", "가상 보고 " + code},
      {"period_start", "2026-10-01"},
      {"period_end", "2026-10-28"},
      {"business_unit", "데모 재무팀"},
      {"currency", "KRW"},
      {"reviewer_id", String.valueOf(manager.id)},
      {"summary_note", "교육용 예시"},
      {"lineLabel", "가상 항목"},
      {"lineCategory", t.categories.get(0)},
      {"lineA", x[0]},
      {"lineB", x[1]},
      {"lineC", x[2]},
      {"lineMemo", "증빙 예시"}
    };
    for (String[] field : fields) f.add(field[0], field[1]);
    return f;
  }

  long create(String code) {
    return r.save(code, ReportInput.from(form(code)), user);
  }

  void problem(int status, Runnable action) {
    try {
      action.run();
      fail("Expected " + status);
    } catch (Problem p) {
      assertEquals(p.getMessage(), status, p.status);
    }
  }

  @Test
  public void exactlyFiftyMenusAndAllRoutesResolve() throws Exception {
    assertEquals(50, ReportCatalog.all().size());
    assertEquals(10, ReportCatalog.groups().size());
    Set<String> paths = new HashSet<>();
    for (ReportCatalog.Type t : ReportCatalog.all()) {
      assertTrue(paths.add(t.code));
      mvc.perform(get("/reports/" + t.code).with(user("user")))
          .andExpect(status().isOk())
          .andExpect(model().attribute("businessMenuCount", 50));
      mvc.perform(get("/reports/" + t.code + "/edit").with(user("user")))
          .andExpect(status().isOk());
    }
    mvc.perform(get("/reports/not-a-menu").with(user("user"))).andExpect(status().isNotFound());
  }

  @Test
  public void fiftyTypesPersistUpdateDeleteRestoreAndCalculate() {
    Map<String, String> expected = new HashMap<>();
    String[][] pairs = {
      {"LEDGER", "0"},
      {"RECEIVABLE", "700"},
      {"PAYABLE", "650"},
      {"ASSET", "100"},
      {"BUDGET", "200"},
      {"CASH", "600"},
      {"MARKET", "800"},
      {"CREDIT", "200"},
      {"OPERATION", "12"},
      {"CONTROL", "75"}
    };
    for (String[] p : pairs) expected.put(p[0], p[1]);
    for (ReportCatalog.Type t : ReportCatalog.all()) {
      long id = create(t.code);
      assertEquals(
          0,
          r.summary(t, r.lines(id))
              .get("result")
              .compareTo(new BigDecimal(expected.get(t.family))));
      MultiValueMap<String, String> f = form(t.code);
      f.set("id", String.valueOf(id));
      f.set("title", "수정된 보고서");
      r.save(t.code, ReportInput.from(f), user);
      assertEquals("수정된 보고서", r.get(t.code, id, user).get("title"));
      r.transition(t.code, id, 1, "delete", "초안 정리", user);
      assertEquals(true, r.get(t.code, id, user).get("deleted"));
      r.transition(t.code, id, 2, "restore", "", user);
      assertEquals(false, r.get(t.code, id, user).get("deleted"));
      assertEquals(1, r.lines(id).size());
      assertEquals(4, r.events(id).size());
    }
  }

  @Test
  public void lifecycleProtectsApprovedAndCopyPreservesOriginal() {
    String code = "budget-performance";
    long id = create(code);
    r.transition(code, id, 0, "submit", "", user);
    problem(403, () -> r.transition(code, id, 1, "approve", "self", user));
    problem(403, () -> r.transition(code, id, 1, "approve", "admin override", admin));
    r.transition(code, id, 1, "reject", "전망 수정", manager);
    problem(409, () -> r.transition(code, id, 2, "submit", "", user));
    MultiValueMap<String, String> f = form(code);
    f.set("id", String.valueOf(id));
    f.set("version", "2");
    r.save(code, ReportInput.from(f), user);
    r.transition(code, id, 3, "submit", "", user);
    r.transition(code, id, 4, "withdraw", "명세 보완", user);
    r.transition(code, id, 5, "submit", "", user);
    r.transition(code, id, 6, "approve", "검토 완료", manager);
    problem(409, () -> r.transition(code, id, 7, "delete", "delete approved", user));
    f.set("version", "7");
    problem(409, () -> r.save(code, ReportInput.from(f), user));
    long copy = r.copy(code, id, manager.id, user);
    assertEquals("DRAFT", r.get(code, copy, user).get("status"));
    assertEquals(id, Store.num(r.get(code, copy, user), "source_id"));
    assertEquals("APPROVED", r.get(code, id, user).get("status"));
  }

  @Test
  public void validationIsDomainSpecificAndAtomic() {
    String[] codes = {
      "receivable-aging",
      "payable-aging",
      "fixed-assets",
      "fx-risk",
      "expected-loss",
      "risk-register",
      "control-testing"
    };
    for (String code : codes) {
      MultiValueMap<String, String> f = form(code);
      f.set("lineB", "999999");
      problem(400, () -> r.save(code, ReportInput.from(f), user));
    }
    MultiValueMap<String, String> f = form("budget-performance");
    f.set("period_end", "2026-09-30");
    problem(400, () -> r.save("budget-performance", ReportInput.from(f), user));
    f.set("period_end", "2026-10-28");
    f.set("reviewer_id", String.valueOf(user.id));
    problem(400, () -> r.save("budget-performance", ReportInput.from(f), user));
    assertEquals(
        Integer.valueOf(0),
        s.db.queryForObject("SELECT COUNT(*) FROM financial_reports", Integer.class));
    long id = create("budget-performance");
    final MultiValueMap<String, String> bad = form("budget-performance");
    bad.set("id", String.valueOf(id));
    bad.set("title", "must rollback");
    bad.set("lineA", "NaN");
    problem(400, () -> r.save("budget-performance", ReportInput.from(bad), user));
    assertEquals("가상 보고 budget-performance", r.get("budget-performance", id, user).get("title"));
    assertEquals(0, Store.num(r.get("budget-performance", id, user), "version"));
    assertEquals(1, r.events(id).size());
  }

  @Test
  public void balancedLedgerAndPercentageBoundaries() {
    MultiValueMap<String, String> f = form("journal-vouchers");
    f.set("lineB", "900");
    long id = r.save("journal-vouchers", ReportInput.from(f), user);
    problem(400, () -> r.transition("journal-vouchers", id, 0, "submit", "", user));
    f = form("fx-risk");
    f.set("lineC", "100");
    long hedge = r.save("fx-risk", ReportInput.from(f), user);
    assertEquals(
        0, ReportStore.metric(ReportCatalog.get("fx-risk"), r.lines(hedge).get(0)).signum());
    f = form("control-testing");
    f.set("lineA", "0");
    f.set("lineB", "0");
    f.set("lineC", "0");
    long zero = r.save("control-testing", ReportInput.from(f), user);
    assertEquals(
        0, r.summary(ReportCatalog.get("control-testing"), r.lines(zero)).get("result").signum());
  }

  @Test
  public void lineCrudPersistsAndStaleSavesDoNotReplaceLines() {
    String code = "cash-position";
    long id = create(code);
    MultiValueMap<String, String> f = form(code);
    f.set("id", String.valueOf(id));
    for (String key :
        Arrays.asList("lineLabel", "lineCategory", "lineA", "lineB", "lineC", "lineMemo"))
      f.add(key, f.getFirst(key));
    f.get("lineLabel").set(1, "두 번째 계좌");
    r.save(code, ReportInput.from(f), user);
    assertEquals(2, r.lines(id).size());
    problem(409, () -> r.save(code, ReportInput.from(f), user));
    assertEquals(2, r.lines(id).size());
    f.set("version", "1");
    for (String key :
        Arrays.asList("lineLabel", "lineCategory", "lineA", "lineB", "lineC", "lineMemo"))
      f.get(key).remove(0);
    r.save(code, ReportInput.from(f), user);
    assertEquals(1, r.lines(id).size());
    assertEquals("두 번째 계좌", r.lines(id).get(0).get("label"));
  }

  @Test
  public void privacyFilteringPagingAndCurrencyIsolation() {
    String code = "expected-loss";
    for (int i = 0; i < 12; i++) create(code);
    MultiValueMap<String, String> usd = form(code);
    usd.set("currency", "USD");
    r.save(code, ReportInput.from(usd), user);
    Map<String, String> filters = new HashMap<>();
    assertEquals(12, r.list(code, filters, user).get("count"));
    assertEquals(10, ((List<?>) r.list(code, filters, user).get("rows")).size());
    filters.put("page", "2");
    assertEquals(2, ((List<?>) r.list(code, filters, user).get("rows")).size());
    assertEquals(0, r.list(code, filters, other).get("count"));
    filters.put("currency", "USD");
    assertEquals(1, r.list(code, filters, user).get("count"));
    filters.put("q", "%' OR 1=1 --");
    assertEquals(0, r.list(code, filters, user).get("count"));
    filters.clear();
    filters.put("category", "없는 분류");
    problem(400, () -> r.list(code, filters, user));
  }

  @Test
  public void unauthorizedAndCrossMenuAccessRejected() throws Exception {
    long id = create("fx-risk");
    problem(403, () -> r.get("fx-risk", id, other));
    problem(404, () -> r.get("expected-loss", id, user));
    MultiValueMap<String, String> f = form("fx-risk");
    f.set("id", String.valueOf(id));
    problem(403, () -> r.save("fx-risk", ReportInput.from(f), manager));
    mvc.perform(post("/reports/fx-risk/save").params(form("fx-risk")).with(user("user")))
        .andExpect(status().isForbidden());
    mvc.perform(get("/reports/fx-risk/" + id).with(user("other")))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/reports/fx-risk/" + id + "/transition")
                .param("version", "0")
                .param("action", "delete")
                .param("note", "forged")
                .with(csrf())
                .with(user("other")))
        .andExpect(status().isForbidden());
  }

  @Test
  public void invalidFormReturnsInputWithoutSaving() throws Exception {
    MultiValueMap<String, String> f = form("fixed-assets");
    f.set("title", "오류 입력 유지");
    f.set("lineC", "0");
    mvc.perform(post("/reports/fixed-assets/save").params(f).with(csrf()).with(user("user")))
        .andExpect(status().isBadRequest())
        .andExpect(view().name("reports-edit"))
        .andExpect(model().attributeExists("validationError", "record", "lines"));
    assertEquals(
        Integer.valueOf(0),
        s.db.queryForObject("SELECT COUNT(*) FROM financial_reports", Integer.class));
  }

  @Test
  public void disabledReviewerCannotReceiveNewSubmission() {
    long id = create("fx-risk");
    s.db.update("UPDATE users SET enabled=FALSE WHERE id=?", manager.id);
    try {
      problem(400, () -> r.transition("fx-risk", id, 0, "submit", "", user));
    } finally {
      s.db.update("UPDATE users SET enabled=TRUE WHERE id=?", manager.id);
    }
  }

  @Test
  public void concurrentDecisionsHaveExactlyOneWinner() throws Exception {
    String code = "expected-loss";
    long id = create(code);
    r.transition(code, id, 0, "submit", "", user);
    ExecutorService pool = Executors.newFixedThreadPool(2);
    CountDownLatch start = new CountDownLatch(1);
    Callable<Integer> decision =
        () -> {
          start.await();
          try {
            r.transition(code, id, 1, "approve", "동시 승인 검사", manager);
            return 200;
          } catch (Problem ex) {
            return ex.status;
          }
        };
    try {
      Future<Integer> a = pool.submit(decision), b = pool.submit(decision);
      start.countDown();
      List<Integer> results =
          Arrays.asList(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS));
      assertTrue(results.contains(200));
      assertTrue(results.contains(409));
      assertEquals(3, r.events(id).size());
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  public void malformedLineArraysAndPrecisionRejected() {
    MultiValueMap<String, String> f = form("cash-position");
    f.add("lineA", "99");
    problem(400, () -> ReportInput.from(f));
    final MultiValueMap<String, String> bad = form("cash-position");
    bad.set("lineA", "1.00001");
    problem(400, () -> r.save("cash-position", ReportInput.from(bad), user));
  }

  @Test
  public void schemaUpgradeIsAdditiveAndIdempotent() {
    org.springframework.jdbc.datasource.DriverManagerDataSource ds =
        new org.springframework.jdbc.datasource.DriverManagerDataSource(
            "jdbc:h2:mem:upgradeproof;DB_CLOSE_DELAY=-1", "sa", "");
    org.springframework.jdbc.datasource.init.ResourceDatabasePopulator oldSchema =
        new org.springframework.jdbc.datasource.init.ResourceDatabasePopulator(
            new org.springframework.core.io.ClassPathResource("schema.sql"));
    oldSchema.execute(ds);
    org.springframework.jdbc.core.JdbcTemplate db =
        new org.springframework.jdbc.core.JdbcTemplate(ds);
    db.update(
        "INSERT INTO clients(name,contact,description,version) VALUES(?,?,?,?)",
        "기존 거래처",
        "demo@example.test",
        "기존 데이터 보존",
        7);
    org.springframework.jdbc.datasource.init.ResourceDatabasePopulator migration =
        new org.springframework.jdbc.datasource.init.ResourceDatabasePopulator(
            new org.springframework.core.io.ClassPathResource("report-schema.sql"));
    migration.execute(ds);
    migration.execute(ds);
    assertEquals(
        "기존 데이터 보존",
        db.queryForObject("SELECT description FROM clients WHERE name=?", String.class, "기존 거래처"));
    assertEquals(
        Integer.valueOf(7),
        db.queryForObject("SELECT version FROM clients WHERE name=?", Integer.class, "기존 거래처"));
    assertEquals(
        Integer.valueOf(0),
        db.queryForObject("SELECT COUNT(*) FROM financial_reports", Integer.class));
  }

  @Test
  public void redirectsDoNotLeakModelAndReviewQueueIsScoped() throws Exception {
    String location =
        mvc.perform(
                post("/reports/fx-risk/save")
                    .params(form("fx-risk"))
                    .with(csrf())
                    .with(user("user")))
            .andExpect(status().is3xxRedirection())
            .andReturn()
            .getResponse()
            .getHeader("Location");
    assertTrue(location.matches("/reports/fx-risk/[0-9]+\\?saved"));
    assertFalse(location.contains("username"));
    long id = create("expected-loss");
    r.transition("expected-loss", id, 0, "submit", "", user);
    assertEquals(1, ((List<?>) r.overview(manager).get("reviewQueue")).size());
    assertEquals(0, ((List<?>) r.overview(other).get("reviewQueue")).size());
  }
}
