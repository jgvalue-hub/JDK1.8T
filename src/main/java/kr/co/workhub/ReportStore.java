package kr.co.workhub;

import java.math.*;
import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;

@Service
public class ReportStore {
  private final Store store;
  private static final BigDecimal HUNDRED = new BigDecimal("100");

  public ReportStore(Store store) {
    this.store = store;
  }

  public Map<String, Object> overview(Store.Actor actor) {
    String access = actor.admin() ? "" : " AND (owner_id=? OR reviewer_id=?)";
    Object[] args = actor.admin() ? new Object[] {} : new Object[] {actor.id, actor.id};
    Map<String, Object> model = new HashMap<>();
    Map<String, Object> counts = new LinkedHashMap<>();
    for (String state : Arrays.asList("DRAFT", "PENDING", "APPROVED", "REJECTED"))
      counts.put(state, 0);
    for (Map<String, Object> row :
        store.db.queryForList(
            "SELECT status,COUNT(*) AS total FROM financial_reports WHERE deleted=FALSE"
                + access
                + " GROUP BY status",
            args)) counts.put(row.get("status").toString(), row.get("total"));
    model.put("reportCounts", counts);
    model.put(
        "reviewQueue",
        store.db.queryForList(
            "SELECT id,report_code,title,period_end FROM financial_reports WHERE reviewer_id=? AND"
                + " status='PENDING' AND deleted=FALSE ORDER BY updated_at,id LIMIT 10",
            actor.id));
    return model;
  }

  public List<Map<String, Object>> reviewers(Store.Actor actor) {
    List<Map<String, Object>> rows =
        store.db.queryForList(
            "SELECT id,name,username FROM users WHERE enabled=TRUE AND role IN ('ADMIN','MANAGER')"
                + " AND id<>? ORDER BY name,id",
            actor.id);
    for (Map<String, Object> row : rows) row.put("selectionKey", row.get("id").toString());
    return rows;
  }

  private void checkReviewer(long reviewer, Store.Actor owner) {
    Integer n =
        store.db.queryForObject(
            "SELECT COUNT(*) FROM users WHERE id=? AND id<>? AND enabled=TRUE AND role IN"
                + " ('ADMIN','MANAGER')",
            Integer.class,
            reviewer,
            owner.id);
    if (n == null || n != 1) throw new Problem(400, "작성자와 다른 활성 매니저/관리자를 검토자로 지정하세요.");
  }

  Map<String, Object> raw(String code, long id, boolean lock) {
    ReportCatalog.get(code);
    List<Map<String, Object>> rows =
        store.db.queryForList(
            "SELECT r.*,o.name AS owner_name,v.name AS reviewer_name FROM financial_reports r JOIN"
                + " users o ON o.id=r.owner_id JOIN users v ON v.id=r.reviewer_id WHERE r.id=? AND"
                + " r.report_code=?"
                + (lock ? " FOR UPDATE" : ""),
            id,
            code);
    if (rows.isEmpty()) throw new Problem(404, "보고서를 찾을 수 없습니다.");
    return rows.get(0);
  }

  void readable(Map<String, Object> r, Store.Actor a) {
    if (!a.admin() && Store.num(r, "owner_id") != a.id && Store.num(r, "reviewer_id") != a.id)
      throw new Problem(403, "작성자·지정 검토자·관리자만 열람할 수 있습니다.");
  }

  void editable(Map<String, Object> r, Store.Actor a) {
    readable(r, a);
    if (Store.num(r, "owner_id") != a.id) throw new Problem(403, "작성자만 보고서와 명세를 수정할 수 있습니다.");
    if (Boolean.TRUE.equals(r.get("deleted"))
        || !(r.get("status").equals("DRAFT") || r.get("status").equals("REJECTED")))
      throw new Problem(409, "임시저장·반려 상태의 활성 보고서만 수정할 수 있습니다.");
  }

  public Map<String, Object> get(String code, long id, Store.Actor a) {
    Map<String, Object> r = raw(code, id, false);
    readable(r, a);
    return r;
  }

  public List<Map<String, Object>> lines(long id) {
    return store.db.queryForList(
        "SELECT * FROM report_lines WHERE report_id=? ORDER BY line_order", id);
  }

  public List<Map<String, Object>> events(long id) {
    return store.db.queryForList(
        "SELECT * FROM report_events WHERE report_id=? ORDER BY id DESC LIMIT 100", id);
  }

  private void event(long id, Store.Actor a, String action, String note, int version) {
    store.db.update(
        "INSERT INTO report_events(report_id,actor,action,note,report_version) VALUES(?,?,?,?,?)",
        id,
        a.username,
        action,
        note,
        version);
    store.audit(a.username, "REPORT_" + action, "report:" + id + ":v" + version);
  }

  static String text(Object value, int max, String name, boolean required) {
    String s = value == null ? "" : value.toString().trim();
    if ((required && s.isEmpty()) || s.length() > max)
      throw new Problem(400, name + " 입력을 확인하세요 (최대 " + max + "자).");
    return s;
  }

  static long integer(Object value, String field) {
    try {
      long n = Long.parseLong(String.valueOf(value));
      if (n < 0) throw new NumberFormatException();
      return n;
    } catch (Exception ex) {
      throw new Problem(400, field + " 값이 올바르지 않습니다.");
    }
  }

  static LocalDate date(Object value) {
    try {
      return LocalDate.parse(String.valueOf(value));
    } catch (Exception ex) {
      throw new Problem(400, "보고 기간을 올바른 날짜로 입력하세요.");
    }
  }

  static BigDecimal decimal(Object value) {
    try {
      String s = String.valueOf(value);
      if (!s.matches("[0-9]{1,12}(\\.[0-9]{1,4})?")) throw new NumberFormatException();
      return new BigDecimal(s);
    } catch (Exception ex) {
      throw new Problem(400, "금액/점수는 0 이상의 숫자(정수 12자리, 소수 4자리 이내)로 입력하세요.");
    }
  }

  private List<Map<String, Object>> validate(
      ReportCatalog.Type type, ReportInput input, Store.Actor a) {
    Map<String, Object> h = input.header;
    text(h.get("title"), 120, "제목", true);
    text(h.get("business_unit"), 100, "관리 단위", true);
    text(h.get("summary_note"), 2000, "검토 메모", false);
    LocalDate start = date(h.get("period_start")), end = date(h.get("period_end"));
    if (end.isBefore(start)
        || ChronoUnit.DAYS.between(start, end) > 366
        || start.getYear() < 2000
        || end.getYear() > 2100) throw new Problem(400, "보고 기간은 2000~2100년, 시작일 이후 최대 366일 이내입니다.");
    if (!Arrays.asList("KRW", "USD", "EUR").contains(h.get("currency")))
      throw new Problem(400, "지원하지 않는 표시 통화입니다.");
    checkReviewer(integer(h.get("reviewer_id"), "검토자"), a);
    if (input.lines.isEmpty() || input.lines.size() > 12)
      throw new Problem(400, "명세는 1~12행이어야 합니다.");
    List<Map<String, Object>> result = new ArrayList<>();
    for (Map<String, Object> source : input.lines) {
      Map<String, Object> r = new LinkedHashMap<>();
      for (Map.Entry<String, Object> field : source.entrySet())
        r.put(field.getKey().toLowerCase(Locale.ROOT), field.getValue());
      r.put("label", text(r.get("label"), 120, type.dimension, true));
      r.put("memo", text(r.get("memo"), 300, "명세 메모", false));
      if (!type.categories.contains(r.get("category")))
        throw new Problem(400, "명세 분류를 목록에서 선택하세요.");
      BigDecimal x = decimal(r.get("amount_a")),
          y = decimal(r.get("amount_b")),
          z = decimal(r.get("amount_c"));
      if (type.family.equals("RECEIVABLE")
          && (y.compareTo(x) > 0
              || z.compareTo(new BigDecimal("3650")) > 0
              || z.stripTrailingZeros().scale() > 0))
        throw new Problem(400, "회수액은 청구액 이내, 연체일은 0~3650 정수여야 합니다.");
      if (type.family.equals("PAYABLE") && y.add(z).compareTo(x) > 0)
        throw new Problem(400, "지급과 공제 합계가 매입금액을 초과합니다.");
      if (type.family.equals("ASSET")
          && (y.compareTo(x) > 0
              || z.compareTo(BigDecimal.ONE) < 0
              || z.compareTo(new BigDecimal("600")) > 0
              || z.stripTrailingZeros().scale() > 0))
        throw new Problem(400, "잔존가치는 취득원가 이하, 내용연수는 1~600개월 정수입니다.");
      if (Arrays.asList("MARKET", "CREDIT").contains(type.family)
          && (y.compareTo(HUNDRED) > 0 || z.compareTo(HUNDRED) > 0))
        throw new Problem(400, "확률·충격률·헤지율·손실률은 0~100%입니다.");
      if (type.family.equals("OPERATION")
          && (x.compareTo(BigDecimal.ONE) < 0
              || x.compareTo(new BigDecimal("5")) > 0
              || y.compareTo(BigDecimal.ONE) < 0
              || y.compareTo(new BigDecimal("5")) > 0
              || x.stripTrailingZeros().scale() > 0
              || y.stripTrailingZeros().scale() > 0
              || z.compareTo(HUNDRED) > 0))
        throw new Problem(400, "가능성과 영향도는 1~5 정수, 통제효과는 0~100%입니다.");
      if (type.family.equals("CONTROL")
          && (y.compareTo(x) > 0
              || z.compareTo(y) > 0
              || x.stripTrailingZeros().scale() > 0
              || y.stripTrailingZeros().scale() > 0
              || z.stripTrailingZeros().scale() > 0))
        throw new Problem(400, "점검 건수는 정수이며 예외 ≤ 완료 ≤ 대상이어야 합니다.");
      r.put("amount_a", x);
      r.put("amount_b", y);
      r.put("amount_c", z);
      result.add(r);
    }
    return result;
  }

  public long save(String code, ReportInput input, Store.Actor a) {
    ReportCatalog.Type type = ReportCatalog.get(code);
    return store.tx.execute(
        tx -> {
          long id = integer(input.header.get("id"), "보고서"),
              v = integer(input.header.get("version"), "버전");
          if (id > 0) {
            Map<String, Object> old = raw(code, id, true);
            editable(old, a);
            if (Store.num(old, "version") != v)
              throw new Problem(409, "다른 변경이 있습니다. 최신 보고서를 다시 여세요.");
          }
          List<Map<String, Object>> rows = validate(type, input, a);
          Map<String, Object> h = input.header;
          Object[] params = {
            text(h.get("title"), 120, "제목", true),
            date(h.get("period_start")).toString(),
            date(h.get("period_end")).toString(),
            text(h.get("business_unit"), 100, "관리 단위", true),
            h.get("currency"),
            integer(h.get("reviewer_id"), "검토자"),
            text(h.get("summary_note"), 2000, "메모", false)
          };
          if (id == 0) {
            GeneratedKeyHolder key = new GeneratedKeyHolder();
            store.db.update(
                connection -> {
                  PreparedStatement ps =
                      connection.prepareStatement(
                          "INSERT INTO"
                              + " financial_reports(title,period_start,period_end,business_unit,currency,reviewer_id,summary_note,report_code,owner_id)"
                              + " VALUES(?,?,?,?,?,?,?,?,?)",
                          new String[] {"id"});
                  for (int i = 0; i < params.length; i++) ps.setObject(i + 1, params[i]);
                  ps.setString(8, code);
                  ps.setLong(9, a.id);
                  return ps;
                },
                key);
            id = key.getKey().longValue();
            v = 0;
          } else {
            List<Object> p = new ArrayList<>(Arrays.asList(params));
            p.add(id);
            p.add(v);
            int changed =
                store.db.update(
                    "UPDATE financial_reports SET"
                        + " title=?,period_start=?,period_end=?,business_unit=?,currency=?,reviewer_id=?,summary_note=?,status='DRAFT',version=version+1,updated_at=CURRENT_TIMESTAMP"
                        + " WHERE id=? AND version=?",
                    p.toArray());
            if (changed != 1) throw new Problem(409, "보고서 버전이 변경되었습니다.");
            v++;
            store.db.update("DELETE FROM report_lines WHERE report_id=?", id);
          }
          int order = 0;
          for (Map<String, Object> r : rows)
            store.db.update(
                "INSERT INTO"
                    + " report_lines(report_id,line_order,label,category,amount_a,amount_b,amount_c,memo)"
                    + " VALUES(?,?,?,?,?,?,?,?)",
                id,
                ++order,
                r.get("label"),
                r.get("category"),
                r.get("amount_a"),
                r.get("amount_b"),
                r.get("amount_c"),
                r.get("memo"));
          event(id, a, v == 0 ? "CREATE" : "UPDATE", "명세 " + rows.size() + "행 저장", (int) v);
          return id;
        });
  }

  public void transition(
      String code, long id, long version, String action, String note, Store.Actor actor) {
    ReportCatalog.Type type = ReportCatalog.get(code);
    store.tx.execute(
        tx -> {
          Map<String, Object> r = raw(code, id, true);
          readable(r, actor);
          if (Store.num(r, "version") != version) throw new Problem(409, "최신 버전을 확인하세요.");
          String state = (String) r.get("status"), next = state;
          boolean deleted = Boolean.TRUE.equals(r.get("deleted"));
          boolean owner = Store.num(r, "owner_id") == actor.id;
          noteCheck(note);
          switch (action) {
            case "submit":
              editable(r, actor);
              checkReviewer(Store.num(r, "reviewer_id"), actor);
              if (!state.equals("DRAFT")) throw new Problem(409, "반려 보고서는 수정 저장 후 재상신하세요.");
              List<Map<String, Object>> rows = lines(id);
              if (rows.isEmpty()) throw new Problem(400, "명세가 필요합니다.");
              if (Arrays.asList("journal-vouchers", "trial-balance").contains(code)
                  && summary(type, rows).get("result").compareTo(BigDecimal.ZERO) != 0)
                throw new Problem(400, "차대변과 조정의 합계가 0이어야 상신할 수 있습니다.");
              next = "PENDING";
              break;
            case "approve":
            case "reject":
              if (owner || Store.num(r, "reviewer_id") != actor.id || !actor.manager())
                throw new Problem(403, "지정된 활성 매니저/관리자만 승인·반려할 수 있습니다.");
              if (deleted || !state.equals("PENDING"))
                throw new Problem(409, "승인대기 보고서만 처리할 수 있습니다.");
              text(note, 1000, "처리 의견", true);
              next = action.equals("approve") ? "APPROVED" : "REJECTED";
              break;
            case "withdraw":
              if (!owner) throw new Problem(403, "작성자만 회수할 수 있습니다.");
              if (deleted || !state.equals("PENDING"))
                throw new Problem(409, "승인대기 보고서만 회수할 수 있습니다.");
              text(note, 1000, "회수 사유", true);
              next = "DRAFT";
              break;
            case "delete":
              editable(r, actor);
              text(note, 1000, "삭제 사유", true);
              deleted = true;
              break;
            case "restore":
              if (!owner && !actor.admin()) throw new Problem(403, "작성자 또는 관리자만 복구할 수 있습니다.");
              if (!deleted || state.equals("APPROVED") || state.equals("PENDING"))
                throw new Problem(409, "휴지통의 초안만 복구할 수 있습니다.");
              deleted = false;
              break;
            default:
              throw new Problem(400, "지원하지 않는 보고서 처리입니다.");
          }
          int count =
              store.db.update(
                  "UPDATE financial_reports SET"
                      + " status=?,deleted=?,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE"
                      + " id=? AND version=?",
                  next,
                  deleted,
                  id,
                  version);
          if (count != 1) throw new Problem(409, "동시에 변경된 보고서입니다.");
          event(
              id,
              actor,
              action.toUpperCase(Locale.ROOT),
              text(note, 1000, "의견", false),
              (int) version + 1);
          return null;
        });
  }

  private void noteCheck(String note) {
    text(note, 1000, "의견", false);
  }

  public long copy(String code, long id, long reviewer, Store.Actor actor) {
    return store.tx.execute(
        tx -> {
          Map<String, Object> r = raw(code, id, true);
          readable(r, actor);
          if (Boolean.TRUE.equals(r.get("deleted"))) throw new Problem(409, "휴지통 보고서는 먼저 복구하세요.");
          ReportInput in = new ReportInput();
          for (Map.Entry<String, Object> field : r.entrySet())
            in.header.put(field.getKey().toLowerCase(Locale.ROOT), field.getValue());
          in.header.put("id", "0");
          in.header.put("version", "0");
          in.header.put("reviewer_id", String.valueOf(reviewer));
          in.header.put(
              "title",
              text(r.get("title"), 120, "제목", true)
                      .substring(0, Math.min(110, r.get("title").toString().length()))
                  + " (사본)");
          in.lines.addAll(lines(id));
          long fresh = save(code, in, actor);
          store.db.update("UPDATE financial_reports SET source_id=? WHERE id=?", id, fresh);
          event(fresh, actor, "COPY", "원본 보고서 #" + id, 0);
          return fresh;
        });
  }

  public static BigDecimal metric(ReportCatalog.Type t, Map<String, Object> r) {
    BigDecimal a = new BigDecimal(r.get("amount_a").toString()),
        b = new BigDecimal(r.get("amount_b").toString()),
        c = new BigDecimal(r.get("amount_c").toString());
    switch (t.family) {
      case "LEDGER":
        return a.subtract(b).add(c);
      case "RECEIVABLE":
      case "BUDGET":
        return a.subtract(b);
      case "PAYABLE":
        return a.subtract(b).subtract(c);
      case "ASSET":
        return a.subtract(b).divide(c, 4, RoundingMode.HALF_UP);
      case "CASH":
        return a.add(b).subtract(c);
      case "MARKET":
        return a.multiply(b)
            .multiply(HUNDRED.subtract(c))
            .divide(new BigDecimal("10000"), 4, RoundingMode.HALF_UP);
      case "CREDIT":
        return a.multiply(b).multiply(c).divide(new BigDecimal("10000"), 4, RoundingMode.HALF_UP);
      case "OPERATION":
        return a.multiply(b).multiply(HUNDRED.subtract(c)).divide(HUNDRED, 4, RoundingMode.HALF_UP);
      case "CONTROL":
        return a.signum() == 0
            ? BigDecimal.ZERO
            : b.multiply(HUNDRED).divide(a, 4, RoundingMode.HALF_UP);
      default:
        throw new IllegalStateException();
    }
  }

  public static Map<String, BigDecimal> summary(
      ReportCatalog.Type t, List<Map<String, Object>> rows) {
    BigDecimal a = BigDecimal.ZERO,
        b = BigDecimal.ZERO,
        c = BigDecimal.ZERO,
        result = BigDecimal.ZERO;
    int alerts = 0;
    for (Map<String, Object> r : rows) {
      a = a.add(new BigDecimal(r.get("amount_a").toString()));
      b = b.add(new BigDecimal(r.get("amount_b").toString()));
      c = c.add(new BigDecimal(r.get("amount_c").toString()));
      BigDecimal m = metric(t, r);
      r.put("metric", m.setScale(2, RoundingMode.HALF_UP));
      result = t.family.equals("OPERATION") ? result.max(m) : result.add(m);
      boolean alert =
          t.family.equals("OPERATION")
              ? m.compareTo(new BigDecimal("12")) >= 0
              : t.family.equals("RECEIVABLE")
                  ? new BigDecimal(r.get("amount_c").toString()).compareTo(new BigDecimal("90"))
                          >= 0
                      && m.signum() > 0
                  : t.family.equals("CONTROL")
                      ? new BigDecimal(r.get("amount_c").toString()).signum() > 0
                      : m.signum() < 0;
      r.put("alert", alert);
      if (alert) alerts++;
    }
    if (t.family.equals("CONTROL"))
      result =
          a.signum() == 0
              ? BigDecimal.ZERO
              : b.multiply(HUNDRED).divide(a, 4, RoundingMode.HALF_UP);
    Map<String, BigDecimal> out = new LinkedHashMap<>();
    out.put("a", a);
    out.put("b", b);
    out.put("c", c);
    out.put("result", result);
    out.put("display", result.setScale(2, RoundingMode.HALF_UP));
    out.put("alerts", new BigDecimal(alerts));
    return out;
  }

  public Map<String, Object> list(String code, Map<String, String> filters, Store.Actor actor) {
    ReportCatalog.Type t = ReportCatalog.get(code);
    String q = text(filters.get("q"), 100, "검색어", false),
        currency = filters.getOrDefault("currency", "KRW"),
        state = filters.getOrDefault("status", ""),
        scope = filters.getOrDefault("scope", "all"),
        category = filters.getOrDefault("category", "");
    boolean trash = "true".equals(filters.get("trash"));
    if (!Arrays.asList("KRW", "USD", "EUR").contains(currency)
        || !Arrays.asList("", "DRAFT", "PENDING", "APPROVED", "REJECTED").contains(state)
        || !Arrays.asList("all", "mine", "review").contains(scope)
        || (!category.isEmpty() && !t.categories.contains(category)))
      throw new Problem(400, "조회 조건을 확인하세요.");
    StringBuilder where =
        new StringBuilder(" WHERE r.report_code=? AND r.deleted=? AND r.currency=?");
    List<Object> args = new ArrayList<>(Arrays.asList(code, trash, currency));
    if (!actor.admin()) {
      where.append(" AND (r.owner_id=? OR r.reviewer_id=?)");
      args.add(actor.id);
      args.add(actor.id);
    }
    if (scope.equals("mine")) {
      where.append(" AND r.owner_id=?");
      args.add(actor.id);
    }
    if (scope.equals("review")) {
      where.append(" AND r.reviewer_id=?");
      args.add(actor.id);
    }
    if (!state.isEmpty()) {
      where.append(" AND r.status=?");
      args.add(state);
    }
    if (!q.isEmpty()) {
      where.append(
          " AND (LOWER(r.title) LIKE ? ESCAPE '!' OR LOWER(r.business_unit) LIKE ? ESCAPE '!')");
      String like =
          "%"
              + q.toLowerCase(Locale.ROOT).replace("!", "!!").replace("%", "!%").replace("_", "!_")
              + "%";
      args.add(like);
      args.add(like);
    }
    if (!category.isEmpty()) {
      where.append(
          " AND EXISTS(SELECT 1 FROM report_lines x WHERE x.report_id=r.id AND x.category=?)");
      args.add(category);
    }
    for (String key : Arrays.asList("from", "to")) {
      String value = filters.getOrDefault(key, "");
      if (!value.isEmpty()) {
        where.append(key.equals("from") ? " AND r.period_end>=?" : " AND r.period_start<=?");
        args.add(date(value).toString());
      }
    }
    int count =
        store.db.queryForObject(
            "SELECT COUNT(*) FROM financial_reports r" + where, Integer.class, args.toArray());
    int page = (int) Math.min(100000, integer(filters.getOrDefault("page", "1"), "페이지"));
    int pages = Math.max(1, (count + 9) / 10);
    page = Math.max(1, Math.min(page, pages));
    List<Object> paged = new ArrayList<>(args);
    paged.add(10);
    paged.add((page - 1) * 10);
    List<Map<String, Object>> reports =
        store.db.queryForList(
            "SELECT r.*,o.name AS owner_name,v.name AS reviewer_name FROM financial_reports r JOIN"
                + " users o ON o.id=r.owner_id JOIN users v ON v.id=r.reviewer_id"
                + where
                + " ORDER BY r.updated_at DESC,r.id DESC LIMIT ? OFFSET ?",
            paged.toArray());
    // Paginated summary bounds memory independently of the amount of historical data.
    List<Map<String, Object>> pageLines = new ArrayList<>();
    for (Map<String, Object> r : reports) pageLines.addAll(lines(Store.num(r, "id")));
    Map<String, Object> model = new HashMap<>();
    model.put("rows", reports);
    model.put("count", count);
    model.put("page", page);
    model.put("pages", pages);
    model.put("totals", summary(t, pageLines));
    model.put("trash", trash);
    model.put("currency", currency);
    model.put("filters", filters);
    return model;
  }
}
