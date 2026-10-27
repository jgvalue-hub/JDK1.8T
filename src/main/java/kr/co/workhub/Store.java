package kr.co.workhub;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;

public class Store {
  final JdbcTemplate db;
  final TransactionTemplate tx;
  final BCryptPasswordEncoder encoder;
  static final Set<String> ENTITIES =
      new HashSet<>(
          Arrays.asList(
              "users",
              "departments",
              "clients",
              "projects",
              "tasks",
              "approvals",
              "notices",
              "audit_log"));

  public Store(JdbcTemplate db, TransactionTemplate tx, BCryptPasswordEncoder encoder) {
    this.db = db;
    this.tx = tx;
    this.encoder = encoder;
  }

  public static class Actor {
    public final long id;
    public final String username, role;

    Actor(long id, String username, String role) {
      this.id = id;
      this.username = username;
      this.role = role;
    }

    public boolean admin() {
      return "ADMIN".equals(role);
    }

    public boolean manager() {
      return admin() || "MANAGER".equals(role);
    }
  }

  public Actor actor(String name) {
    Map<String, Object> r =
        db.queryForMap("SELECT id,username,role,enabled FROM users WHERE username=?", name);
    if (!Boolean.TRUE.equals(r.get("enabled"))) throw new Problem(403, "비활성 계정입니다. 다시 로그인하세요.");
    return new Actor(num(r, "id"), name, (String) r.get("role"));
  }

  static long num(Map<String, Object> r, String key) {
    return ((Number) r.get(key)).longValue();
  }

  void entity(String e) {
    if (!ENTITIES.contains(e)) throw new Problem(404, "화면을 찾을 수 없습니다.");
  }

  public void audit(String actor, String action, String target) {
    db.update("INSERT INTO audit_log(actor,action,target) VALUES(?,?,?)", actor, action, target);
  }

  public Map<String, Object> raw(String e, long id) {
    entity(e);
    List<Map<String, Object>> rows = db.queryForList("SELECT * FROM " + e + " WHERE id=?", id);
    if (rows.isEmpty()) throw new Problem(404, "자료를 찾을 수 없습니다.");
    return rows.get(0);
  }

  public Map<String, Object> get(String e, long id, Actor a) {
    Map<String, Object> r = raw(e, id);
    access(e, r, a, false);
    r.remove("password");
    return r;
  }

  void access(String e, Map<String, Object> r, Actor a, boolean write) {
    if ((e.equals("users") || e.equals("departments") || e.equals("audit_log")) && !a.admin())
      throw new Problem(403, "관리자 권한이 필요합니다.");
    if (e.equals("approvals")) {
      if (num(r, "owner_id") != a.id && num(r, "approver_id") != a.id && !a.admin())
        throw new Problem(403, "이 승인요청에 접근할 수 없습니다.");
      if (write
          && (num(r, "owner_id") != a.id
              || !(r.get("status").equals("DRAFT") || r.get("status").equals("REJECTED"))))
        throw new Problem(409, "작성자만 임시저장·반려 요청을 수정할 수 있습니다.");
    } else if (write && (e.equals("clients") || e.equals("notices")) && !a.manager())
      throw new Problem(403, "관리자 또는 매니저 권한이 필요합니다.");
    else if (write
        && (e.equals("projects") || e.equals("tasks"))
        && !a.manager()
        && num(r, "owner_id") != a.id) throw new Problem(403, "담당자만 수정할 수 있습니다.");
  }

  public Map<String, Object> list(String e, String query, int page, Actor a, boolean inbox) {
    entity(e);
    if ((e.equals("users") || e.equals("departments") || e.equals("audit_log")) && !a.admin())
      throw new Problem(403, "관리자 권한이 필요합니다.");
    if (inbox && !a.manager()) throw new Problem(403, "승인자 권한이 필요합니다.");
    String col = e.equals("audit_log") ? "target" : "name";
    String where = " WHERE LOWER(" + col + ") LIKE ? ESCAPE '!'";
    List<Object> args = new ArrayList<>();
    String q = query == null ? "" : query.trim();
    if (q.length() > 100) throw new Problem(400, "검색어는 100자 이내로 입력하세요.");
    args.add(
        "%"
            + q.toLowerCase(Locale.ROOT).replace("!", "!!").replace("%", "!%").replace("_", "!_")
            + "%");
    if (e.equals("approvals")) {
      where += inbox ? " AND approver_id=? AND status='PENDING'" : " AND owner_id=?";
      args.add(a.id);
    }
    int total =
        db.queryForObject("SELECT COUNT(*) FROM " + e + where, args.toArray(), Integer.class);
    int pages = Math.max(1, (total + 9) / 10);
    page = Math.max(1, Math.min(page, pages));
    List<Object> paging = new ArrayList<>(args);
    paging.add(10);
    paging.add((page - 1) * 10);
    String columns = e.equals("users") ? "id,username,name,email,role,department_id,enabled" : "*";
    List<Map<String, Object>> rows =
        db.queryForList(
            "SELECT " + columns + " FROM " + e + where + " ORDER BY id DESC LIMIT ? OFFSET ?",
            paging.toArray());
    Map<String, Object> out = new HashMap<>();
    out.put("rows", rows);
    out.put("page", page);
    out.put("pages", pages);
    out.put("total", total);
    out.put("q", q);
    return out;
  }

  public List<Map<String, Object>> choices(String e) {
    entity(e);
    if (!Arrays.asList("users", "departments", "projects", "clients").contains(e))
      throw new Problem(400, "잘못된 선택 항목");
    return db.queryForList(
        "SELECT id,name"
            + (e.equals("users") ? ",role" : "")
            + " FROM "
            + e
            + (e.equals("users") ? " WHERE enabled=TRUE" : "")
            + " ORDER BY name");
  }

  static String value(Map<String, String> f, String key, int max, boolean required) {
    String v = f.getOrDefault(key, "").trim();
    if ((required && v.isEmpty()) || v.length() > max)
      throw new Problem(400, key + ": 필수 값과 최대 " + max + "자를 확인하세요.");
    return v;
  }

  static long number(Map<String, String> f, String key) {
    try {
      return Long.parseLong(
          f.getOrDefault(key, "0").trim().isEmpty() ? "0" : f.getOrDefault(key, "0"));
    } catch (Exception ex) {
      throw new Problem(400, key + ": 숫자 값이 필요합니다.");
    }
  }

  static String oneOf(Map<String, String> f, String key, String... allowed) {
    String v = f.getOrDefault(key, "");
    if (!Arrays.asList(allowed).contains(v)) throw new Problem(400, "허용되지 않은 " + key + " 값입니다.");
    return v;
  }

  static String password(Map<String, String> f) {
    String p = f.getOrDefault("password", "");
    if (p.length() < 12 || p.getBytes(StandardCharsets.UTF_8).length > 72)
      throw new Problem(400, "비밀번호는 12자 이상, UTF-8 72바이트 이하여야 합니다.");
    return p;
  }

  void ref(String e, long id) {
    raw(e, id);
  }

  public long save(String e, Map<String, String> f, Actor a) {
    entity(e);
    if (e.equals("audit_log")) throw new Problem(403, "감사로그는 변경할 수 없습니다.");
    return tx.execute(
        status -> {
          long id = number(f, "id");
          Map<String, Object> old = id > 0 ? raw(e, id) : new HashMap<>();
          if (id == 0) {
            old.put("owner_id", a.id);
            old.put("approver_id", a.id);
            old.put("status", "DRAFT");
          }
          access(e, old, a, true);
          LinkedHashMap<String, Object> values = new LinkedHashMap<>();
          values.put("name", value(f, "name", 100, true));
          if (e.equals("users")) {
            String user = value(f, "username", 40, true);
            if (!user.matches("[a-zA-Z0-9._-]{3,40}"))
              throw new Problem(400, "아이디는 영문·숫자·._- 3~40자입니다.");
            values.put("username", user);
            String email = value(f, "email", 150, true);
            if (!email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))
              throw new Problem(400, "이메일 형식을 확인하세요.");
            values.put("email", email);
            values.put("role", oneOf(f, "role", "ADMIN", "MANAGER", "USER"));
            boolean enabled = "true".equals(f.get("enabled"));
            values.put("enabled", enabled);
            if (id == a.id && (!enabled || !"ADMIN".equals(values.get("role"))))
              throw new Problem(409, "현재 관리자 자신의 권한·활성 상태는 변경할 수 없습니다.");
            long dep = number(f, "department_id");
            if (dep > 0) ref("departments", dep);
            values.put("department_id", dep > 0 ? dep : null);
            if (id == 0 || !f.getOrDefault("password", "").isEmpty())
              values.put("password", encoder.encode(password(f)));
          } else if (!e.equals("departments")) {
            values.put("description", value(f, "description", 4000, true));
            if (e.equals("clients")) values.put("contact", value(f, "contact", 150, true));
            if (e.equals("projects") || e.equals("tasks")) {
              long owner = number(f, "owner_id");
              if (!a.manager() && owner != a.id)
                throw new Problem(403, "일반 사용자는 자신에게만 배정할 수 있습니다.");
              Map<String, Object> user = raw("users", owner);
              if (!Boolean.TRUE.equals(user.get("enabled")))
                throw new Problem(400, "활성 담당자를 선택하세요.");
              values.put("owner_id", owner);
              String state =
                  e.equals("projects")
                      ? oneOf(f, "status", "PLANNED", "ACTIVE", "DONE")
                      : oneOf(f, "status", "TODO", "DOING", "DONE");
              if (id > 0 && !state.equals(old.get("status"))) {
                String before = (String) old.get("status");
                String expected =
                    e.equals("projects")
                        ? (before.equals("PLANNED")
                            ? "ACTIVE"
                            : before.equals("ACTIVE") ? "DONE" : "")
                        : (before.equals("TODO") ? "DOING" : before.equals("DOING") ? "DONE" : "");
                if (!state.equals(expected))
                  throw new Problem(409, "상태는 준비 → 진행 → 완료 순서로만 변경할 수 있습니다.");
              }
              if (id == 0 && !state.equals(e.equals("projects") ? "PLANNED" : "TODO"))
                throw new Problem(400, "신규 항목은 준비 상태로 등록하세요.");
              values.put("status", state);
              String date = value(f, "due_date", 10, true);
              try {
                LocalDate.parse(date);
              } catch (Exception ex) {
                throw new Problem(400, "마감일을 YYYY-MM-DD 형식으로 입력하세요.");
              }
              values.put("due_date", java.sql.Date.valueOf(date));
              String refKey = e.equals("projects") ? "client_id" : "project_id";
              long refId = number(f, refKey);
              if (refId > 0) ref(e.equals("projects") ? "clients" : "projects", refId);
              else if (e.equals("tasks")) throw new Problem(400, "프로젝트를 선택하세요.");
              values.put(refKey, refId > 0 ? refId : null);
            }
            if (e.equals("approvals")) {
              long approver = number(f, "approver_id");
              Map<String, Object> u = raw("users", approver);
              if (approver == a.id
                  || !Arrays.asList("ADMIN", "MANAGER").contains(u.get("role"))
                  || !Boolean.TRUE.equals(u.get("enabled")))
                throw new Problem(400, "본인 외 활성 관리자·매니저를 승인자로 선택하세요.");
              values.put("owner_id", a.id);
              values.put("approver_id", approver);
              values.put("status", "DRAFT");
              values.put("decision_note", "");
            }
          }
          if (id == 0) {
            final String sql =
                "INSERT INTO "
                    + e
                    + " ("
                    + String.join(",", values.keySet())
                    + ") VALUES ("
                    + String.join(",", Collections.nCopies(values.size(), "?"))
                    + ")";
            org.springframework.jdbc.support.GeneratedKeyHolder keys =
                new org.springframework.jdbc.support.GeneratedKeyHolder();
            db.update(
                c -> {
                  java.sql.PreparedStatement ps = c.prepareStatement(sql, new String[] {"id"});
                  int n = 1;
                  for (Object v : values.values()) ps.setObject(n++, v);
                  return ps;
                },
                keys);
            id = keys.getKey().longValue();
          } else {
            List<String> sets = new ArrayList<>();
            for (String k : values.keySet()) sets.add(k + "=?");
            List<Object> args = new ArrayList<>(values.values());
            String where = " WHERE id=?";
            args.add(id);
            if (!e.equals("users") && !e.equals("departments")) {
              sets.add("version=version+1");
              where += " AND version=?";
              args.add(number(f, "version"));
            }
            if (db.update("UPDATE " + e + " SET " + String.join(",", sets) + where, args.toArray())
                != 1) throw new Problem(409, "다른 사용자가 수정했습니다. 새로고침 후 다시 시도하세요.");
          }
          audit(a.username, "SAVE", e + ":" + id);
          return id;
        });
  }

  public void delete(String e, long id, long version, Actor a) {
    entity(e);
    if (e.equals("users") || e.equals("audit_log"))
      throw new Problem(403, "사용자는 비활성화하세요. 감사로그는 삭제할 수 없습니다.");
    tx.execute(
        status -> {
          Map<String, Object> r = raw(e, id);
          access(e, r, a, true);
          String where = "id=?";
          List<Object> args = new ArrayList<>();
          args.add(id);
          if (!e.equals("departments")) {
            where += " AND version=?";
            args.add(version);
          }
          if (db.update("DELETE FROM " + e + " WHERE " + where, args.toArray()) != 1)
            throw new Problem(409, "이미 수정된 자료입니다.");
          audit(a.username, "DELETE", e + ":" + id);
          return null;
        });
  }

  public void transition(long id, String action, String note, long version, Actor a) {
    tx.execute(
        status -> {
          String decisionNote = note;
          Map<String, Object> r = raw("approvals", id);
          access("approvals", r, a, false);
          String target;
          if (action.equals("submit")) {
            if (num(r, "owner_id") != a.id || !"DRAFT".equals(r.get("status")))
              throw new Problem(409, "본인의 임시저장 요청만 상신할 수 있습니다.");
            Map<String, Object> approver = raw("users", num(r, "approver_id"));
            if (!Boolean.TRUE.equals(approver.get("enabled"))
                || !Arrays.asList("ADMIN", "MANAGER").contains(approver.get("role")))
              throw new Problem(409, "승인자를 다시 선택하세요.");
            target = "PENDING";
            decisionNote = "";
          } else {
            if (!a.manager() || num(r, "approver_id") != a.id || num(r, "owner_id") == a.id)
              throw new Problem(403, "지정된 승인자만 처리할 수 있습니다.");
            if (!"PENDING".equals(r.get("status")))
              throw new Problem(409, "이미 처리되었거나 상신되지 않은 요청입니다.");
            if (!Arrays.asList("approve", "reject").contains(action))
              throw new Problem(400, "잘못된 처리입니다.");
            target = action.equals("approve") ? "APPROVED" : "REJECTED";
            if (note == null || note.trim().isEmpty() || note.length() > 1000)
              throw new Problem(400, "처리 의견을 1~1000자로 입력하세요.");
          }
          if (db.update(
                  "UPDATE approvals SET status=?,decision_note=?,version=version+1 WHERE id=? AND"
                      + " version=? AND status=?",
                  target,
                  decisionNote,
                  id,
                  version,
                  r.get("status"))
              != 1) throw new Problem(409, "다른 요청에서 이미 변경했습니다.");
          audit(a.username, target, "approvals:" + id);
          return null;
        });
  }

  public void profile(Map<String, String> f, Actor a) {
    tx.execute(
        status -> {
          String name = value(f, "name", 100, true), email = value(f, "email", 150, true);
          if (!email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))
            throw new Problem(400, "이메일 형식을 확인하세요.");
          db.update("UPDATE users SET name=?,email=? WHERE id=?", name, email, a.id);
          if (!f.getOrDefault("password", "").isEmpty()) {
            if (!encoder.matches(
                f.getOrDefault("current_password", ""),
                (String) raw("users", a.id).get("password")))
              throw new Problem(400, "현재 비밀번호가 일치하지 않습니다.");
            db.update("UPDATE users SET password=? WHERE id=?", encoder.encode(password(f)), a.id);
          }
          audit(a.username, "PROFILE", "users:" + a.id);
          return null;
        });
  }

  public Map<String, Integer> counts(Actor a) {
    Map<String, Integer> m = new LinkedHashMap<>();
    for (String e : Arrays.asList("projects", "tasks", "clients"))
      m.put(e, db.queryForObject("SELECT COUNT(*) FROM " + e, Integer.class));
    m.put(
        "approvals",
        db.queryForObject(
            "SELECT COUNT(*) FROM approvals WHERE approver_id=? AND status='PENDING'",
            Integer.class,
            a.id));
    return m;
  }

  public void seed() {
    seed("DevOnly!2026pass", false);
  }

  public void seedContainerDemo(String secret) {
    seed(ContainerDemoPolicy.requireSecret(secret), true);
  }

  private void seed(String seedPassword, boolean containerDemo) {
    tx.execute(
        status -> {
          if (db.queryForObject("SELECT COUNT(*) FROM users", Integer.class) > 0) {
            if (containerDemo) {
              for (String hash : db.queryForList("SELECT password FROM users", String.class))
                if (encoder.matches("DevOnly!2026pass", hash))
                  throw new IllegalStateException(
                      "A local dev database cannot be used for container-demo");
            }
            return null;
          }
          db.update("INSERT INTO departments(name) VALUES(?)", "기획운영팀");
          String hash = encoder.encode(seedPassword);
          for (String[] u :
              new String[][] {
                {"admin", "시스템 관리자", "ADMIN"},
                {"manager", "프로젝트 매니저", "MANAGER"},
                {"user", "업무 담당자", "USER"},
                {"other", "다른 담당자", "USER"}
              })
            db.update(
                "INSERT INTO users(username,password,name,email,role,department_id)"
                    + " VALUES(?,?,?,?,?,1)",
                u[0],
                hash,
                u[1],
                u[0] + "@example.test",
                u[2]);
          if (containerDemo) db.update("UPDATE users SET enabled=FALSE WHERE username <> 'admin'");
          db.update(
              "INSERT INTO clients(name,contact,description) VALUES(?,?,?)",
              "샘플 파트너",
              "partner@example.test",
              "개발 환경에서만 제공되는 가상 거래처입니다.");
          db.update(
              "INSERT INTO projects(name,description,status,owner_id,client_id,due_date)"
                  + " VALUES(?,?,'PLANNED',2,1,?)",
              "사내 업무환경 개선",
              "팀의 업무와 승인 과정을 한 곳에서 관리합니다.",
              java.sql.Date.valueOf(LocalDate.now().plusDays(30)));
          db.update(
              "INSERT INTO tasks(name,description,status,owner_id,project_id,due_date)"
                  + " VALUES(?,?,'TODO',3,1,?)",
              "요구사항 정리",
              "첫 업무를 진행 상태로 변경해 보세요.",
              java.sql.Date.valueOf(LocalDate.now().plusDays(7)));
          db.update(
              "INSERT INTO notices(name,description) VALUES(?,?)",
              "업무 포털에 오신 것을 환영합니다",
              "왼쪽 메뉴에서 프로젝트, 업무, 승인요청을 관리하세요. 이 데이터는 개발용입니다.");
          return null;
        });
  }
}
