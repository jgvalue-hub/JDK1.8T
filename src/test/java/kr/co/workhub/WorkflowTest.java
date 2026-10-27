package kr.co.workhub;

import static org.junit.Assert.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.*;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.*;
import org.springframework.web.context.WebApplicationContext;

@RunWith(SpringJUnit4ClassRunner.class)
@WebAppConfiguration
@ContextConfiguration(classes = {AppConfig.class, SecurityConfig.class, MvcConfig.class})
@ActiveProfiles("dev")
public class WorkflowTest {
  static {
    System.setProperty("workhub.db.url", "jdbc:h2:mem:tests;DB_CLOSE_DELAY=-1");
  }

  @Autowired WebApplicationContext context;
  @Autowired Store s;
  MockMvc mvc;

  @Before
  public void setup() {
    mvc =
        org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(context)
            .apply(springSecurity())
            .build();
  }

  Map<String, String> fields(String... pairs) {
    Map<String, String> m = new HashMap<>();
    for (int i = 0; i < pairs.length; i += 2) m.put(pairs[i], pairs[i + 1]);
    return m;
  }

  long approval() {
    return s.save(
        "approvals",
        fields("name", "휴가 요청", "description", "일정 확인", "approver_id", "2"),
        s.actor("user"));
  }

  void problem(int code, Runnable fn) {
    try {
      fn.run();
      fail("must reject");
    } catch (Problem p) {
      assertEquals(code, p.status);
    }
  }

  @Test
  public void bcryptRejectsOverlongPasswordSuffix() {
    String boundary = String.join("", Collections.nCopies(72, "a"));
    String hash = s.encoder.encode(boundary);
    assertTrue(s.encoder.matches(boundary, hash));
    assertFalse(s.encoder.matches(boundary + "extra", hash));
  }

  @Test
  public void allTwentyScreensResolve() throws Exception {
    long id = approval();
    String[] paths = {
      "/login",
      "/dashboard",
      "/profile",
      "/users",
      "/users/edit",
      "/departments",
      "/clients",
      "/clients/edit",
      "/projects",
      "/projects/edit",
      "/projects/1",
      "/tasks",
      "/tasks/edit",
      "/tasks/1",
      "/approvals",
      "/approvals/edit",
      "/inbox",
      "/approvals/" + id,
      "/notices",
      "/audit"
    };
    for (String path : paths)
      mvc.perform(get(path).with(user("admin").roles("ADMIN"))).andExpect(status().isOk());
  }

  @Test
  public void authenticationCsrfAndRoleGates() throws Exception {
    mvc.perform(get("/tasks")).andExpect(status().is3xxRedirection());
    mvc.perform(get("/users").with(user("user").roles("USER"))).andExpect(status().isForbidden());
    mvc.perform(post("/tasks/save").with(user("user").roles("USER")))
        .andExpect(status().isForbidden());
    mvc.perform(get("/logout").with(user("user").roles("USER")))
        .andExpect(status().is4xxClientError());
  }

  @Test
  public void approvalLifecycleRejectsUnauthorizedAndDuplicate() {
    long id = approval();
    Store.Actor u = s.actor("user"), manager = s.actor("manager"), other = s.actor("other");
    problem(403, () -> s.get("approvals", id, other));
    problem(403, () -> s.transition(id, "approve", "ok", 0, other));
    s.transition(id, "submit", "", 0, u);
    problem(
        409,
        () ->
            s.save(
                "approvals",
                fields(
                    "id",
                    "" + id,
                    "name",
                    "changed",
                    "description",
                    "x",
                    "approver_id",
                    "2",
                    "version",
                    "1"),
                u));
    s.transition(id, "reject", "일정 재검토", 1, manager);
    assertEquals("REJECTED", s.raw("approvals", id).get("status"));
    s.save(
        "approvals",
        fields(
            "id",
            "" + id,
            "name",
            "수정 요청",
            "description",
            "수정",
            "approver_id",
            "2",
            "version",
            "2"),
        u);
    s.transition(id, "submit", "", 3, u);
    s.transition(id, "approve", "승인합니다", 4, manager);
    assertEquals("APPROVED", s.raw("approvals", id).get("status"));
    problem(409, () -> s.transition(id, "reject", "중복", 4, manager));
  }

  @Test
  public void taskOwnershipValidationAndOptimisticLock() {
    Store.Actor u = s.actor("user");
    Map<String, String> f =
        fields(
            "name",
            "테스트 업무",
            "description",
            "정상 내용",
            "status",
            "TODO",
            "owner_id",
            "3",
            "project_id",
            "1",
            "due_date",
            "2027-01-01");
    long id = s.save("tasks", f, u);
    f.put("id", "" + id);
    f.put("version", "0");
    problem(403, () -> s.save("tasks", f, s.actor("other")));
    f.put("status", "DONE");
    problem(409, () -> s.save("tasks", f, u));
    f.put("status", "DOING");
    s.save("tasks", f, u);
    problem(409, () -> s.save("tasks", f, u));
    Map<String, String> bad = new HashMap<>(f);
    bad.put("due_date", "not-a-date");
    problem(400, () -> s.save("tasks", bad, u));
    s.delete("tasks", id, 1, u);
    problem(404, () -> s.raw("tasks", id));
  }

  @Test
  public void searchesAreBoundAndPagedAndWildcardsLiteral() {
    Store.Actor admin = s.actor("admin");
    for (int i = 0; i < 12; i++)
      s.save(
          "clients",
          fields("name", "페이지검사" + i, "contact", "sample@example.test", "description", "샘플"),
          admin);
    Map<String, Object> page = s.list("clients", "페이지검사", 2, admin, false);
    assertEquals(12, page.get("total"));
    assertEquals(2, ((List<?>) page.get("rows")).size());
    assertEquals(0, s.list("clients", "' OR 1=1 --", 1, admin, false).get("total"));
    assertEquals(0, s.list("clients", "%", 1, admin, false).get("total"));
  }

  @Test
  public void passwordsAndProfileRollback() {
    Store.Actor u = s.actor("user");
    Map<String, Object> original = s.raw("users", u.id);
    assertTrue(s.encoder.matches("DevOnly!2026pass", (String) original.get("password")));
    assertFalse(original.get("password").equals("DevOnly!2026pass"));
    problem(
        400,
        () ->
            s.profile(
                fields(
                    "name",
                    "should rollback",
                    "email",
                    "user@example.test",
                    "password",
                    "NewPassword!123",
                    "current_password",
                    "wrong"),
                u));
    assertEquals(original.get("name"), s.raw("users", u.id).get("name"));
    assertFalse(s.get("users", 3, s.actor("admin")).containsKey("password"));
  }

  @Test
  public void disabledAccountsRejectedAndOwnAdminProtected() {
    Store.Actor a = s.actor("admin");
    problem(
        409,
        () ->
            s.save(
                "users",
                fields(
                    "id",
                    "1",
                    "name",
                    "admin",
                    "username",
                    "admin",
                    "email",
                    "admin@example.test",
                    "role",
                    "USER",
                    "enabled",
                    "true"),
                a));
    long id =
        s.save(
            "users",
            fields(
                "name",
                "비활성",
                "username",
                "disabled",
                "email",
                "disabled@example.test",
                "role",
                "USER",
                "enabled",
                "false",
                "password",
                "DevOnly!2026pass"),
            a);
    problem(403, () -> s.actor("disabled"));
    assertTrue(id > 0);
  }

  @Test
  public void databaseRejectsReferencedDeletionAndAuditIsImmutable() {
    try {
      s.delete("projects", 1, 0, s.actor("admin"));
      fail("FK must reject");
    } catch (org.springframework.dao.DataIntegrityViolationException expected) {
    }
    problem(403, () -> s.delete("audit_log", 1, 0, s.actor("admin")));
    assertTrue(s.db.queryForObject("SELECT COUNT(*) FROM audit_log", Integer.class) > 0);
  }

  @Test
  public void httpValidationAndRecordAuthorization() throws Exception {
    mvc.perform(
            post("/tasks/save")
                .with(user("other").roles("USER"))
                .with(csrf())
                .param("id", "1")
                .param("name", "forged"))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/clients/save")
                .with(user("admin").roles("ADMIN"))
                .with(csrf())
                .param("name", " "))
        .andExpect(status().isBadRequest());
    long id = approval();
    mvc.perform(get("/approvals/" + id).with(user("other").roles("USER")))
        .andExpect(status().isForbidden());
  }
}
