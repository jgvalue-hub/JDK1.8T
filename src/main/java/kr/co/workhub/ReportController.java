package kr.co.workhub;

import java.security.Principal;
import java.time.LocalDate;
import java.util.*;
import javax.servlet.http.*;
import org.springframework.core.io.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class ReportController {
  final ReportStore reports;
  final Store store;

  public ReportController(ReportStore reports, Store store) {
    this.reports = reports;
    this.store = store;
  }

  @ModelAttribute
  public void common(Principal p, HttpServletRequest request, Model m) {
    m.addAttribute("ctx", request.getContextPath());
    m.addAttribute("labels", WebController.labels());
    Map<String, String> eventLabels = new LinkedHashMap<>();
    String[][] names = {
      {"CREATE", "작성"},
      {"UPDATE", "명세 수정"},
      {"SUBMIT", "검토 상신"},
      {"WITHDRAW", "상신 회수"},
      {"APPROVE", "승인 확정"},
      {"REJECT", "반려"},
      {"DELETE", "휴지통 이동"},
      {"RESTORE", "복구"},
      {"COPY", "사본 생성"}
    };
    for (String[] name : names) eventLabels.put(name[0], name[1]);
    m.addAttribute("eventLabels", eventLabels);
    if (p != null) {
      Store.Actor a = who(p);
      m.addAttribute("actorId", a.id);
      m.addAttribute("username", a.username);
      m.addAttribute("admin", a.admin());
      m.addAttribute("manager", a.manager());
    }
  }

  private Store.Actor who(Principal p) {
    return store.actor(p.getName());
  }

  private ReportCatalog.Type type(String code, Model m) {
    ReportCatalog.Type t = ReportCatalog.get(code);
    m.addAttribute("reportType", t);
    m.addAttribute("title", t.title);
    return t;
  }

  @GetMapping(value = "/assets/reports.js", produces = "application/javascript")
  @ResponseBody
  public Resource script() {
    return new ClassPathResource("reports.js");
  }

  @GetMapping("/reports")
  public String index(Principal p, Model m) {
    m.addAllAttributes(reports.overview(who(p)));
    m.addAttribute("title", "회계·리스크 보고센터");
    return "reports-index";
  }

  @GetMapping("/reports/{code}")
  public String list(
      @PathVariable String code, @RequestParam Map<String, String> f, Principal p, Model m) {
    type(code, m);
    m.addAllAttributes(reports.list(code, f, who(p)));
    return "reports-list";
  }

  @GetMapping("/reports/{code}/edit")
  public String edit(
      @PathVariable String code, @RequestParam(defaultValue = "0") long id, Principal p, Model m) {
    ReportCatalog.Type t = type(code, m);
    Store.Actor a = who(p);
    Map<String, Object> r;
    List<Map<String, Object>> lines;
    if (id > 0) {
      r = reports.get(code, id, a);
      reports.editable(r, a);
      lines = reports.lines(id);
    } else {
      r = new LinkedHashMap<>();
      r.put("id", 0);
      r.put("version", 0);
      r.put("title", t.title + " · " + LocalDate.now());
      r.put("period_start", LocalDate.now().withDayOfMonth(1));
      r.put("period_end", LocalDate.now());
      r.put("currency", "KRW");
      lines = new ArrayList<>();
      Map<String, Object> row = new HashMap<>();
      row.put("category", t.categories.get(0));
      row.put("amount_a", 0);
      row.put("amount_b", 0);
      row.put("amount_c", 0);
      lines.add(row);
    }
    m.addAttribute("record", r);
    m.addAttribute("lines", lines);
    m.addAttribute("reviewers", reports.reviewers(a));
    return "reports-edit";
  }

  @PostMapping("/reports/{code}/save")
  public String save(
      @PathVariable String code,
      @RequestParam MultiValueMap<String, String> fields,
      Principal p,
      Model m,
      HttpServletResponse response,
      RedirectAttributes redirect) {
    type(code, m);
    ReportInput input = null;
    try {
      input = ReportInput.from(fields);
      long id = reports.save(code, input, who(p));
      return "redirect:/reports/" + code + "/" + id + "?saved";
    } catch (Problem ex) {
      if (ex.status != 400 || input == null) throw ex;
      response.setStatus(400);
      m.addAttribute("validationError", ex.getMessage());
      m.addAttribute("record", input.header);
      m.addAttribute("lines", input.lines);
      m.addAttribute("reviewers", reports.reviewers(who(p)));
      return "reports-edit";
    }
  }

  @GetMapping("/reports/{code}/{id:[0-9]+}")
  public String detail(@PathVariable String code, @PathVariable long id, Principal p, Model m) {
    ReportCatalog.Type t = type(code, m);
    Store.Actor a = who(p);
    Map<String, Object> r = reports.get(code, id, a);
    List<Map<String, Object>> lines = reports.lines(id);
    m.addAttribute("record", r);
    m.addAttribute("totals", ReportStore.summary(t, lines));
    m.addAttribute("lines", lines);
    m.addAttribute("events", reports.events(id));
    m.addAttribute("reviewers", reports.reviewers(a));
    return "reports-detail";
  }

  @PostMapping("/reports/{code}/{id:[0-9]+}/transition")
  public String transition(
      @PathVariable String code,
      @PathVariable long id,
      @RequestParam long version,
      @RequestParam String action,
      @RequestParam(defaultValue = "") String note,
      Principal p,
      RedirectAttributes redirect) {
    reports.transition(code, id, version, action, note, who(p));
    return "redirect:/reports/" + code + "/" + id + "?saved";
  }

  @PostMapping("/reports/{code}/{id:[0-9]+}/copy")
  public String copy(
      @PathVariable String code,
      @PathVariable long id,
      @RequestParam long reviewer_id,
      Principal p,
      RedirectAttributes redirect) {
    return "redirect:/reports/"
        + code
        + "/"
        + reports.copy(code, id, reviewer_id, who(p))
        + "?saved";
  }

  @ExceptionHandler(Problem.class)
  public String problem(Problem e, HttpServletResponse response, Model m) {
    response.setStatus(e.status);
    m.addAttribute("title", "보고서 요청 확인");
    m.addAttribute("message", e.getMessage());
    return "error";
  }
}
