package kr.co.workhub;

import java.security.Principal;
import java.util.*;
import javax.servlet.http.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class WebController {
  @Autowired Store s;
  static final Map<String, String> TITLES = new LinkedHashMap<>();

  static {
    TITLES.put("users", "사용자");
    TITLES.put("departments", "부서");
    TITLES.put("clients", "거래처");
    TITLES.put("projects", "프로젝트");
    TITLES.put("tasks", "업무");
    TITLES.put("approvals", "내 승인요청");
    TITLES.put("notices", "공지사항");
    TITLES.put("audit_log", "감사로그");
  }

  @ModelAttribute
  public void common(Principal principal, Model m, HttpServletRequest req) {
    m.addAttribute("ctx", req.getContextPath());
    m.addAttribute("titles", TITLES);
    if (principal != null) {
      Store.Actor a = s.actor(principal.getName());
      m.addAttribute("actor", a);
      m.addAttribute("actorId", a.id);
      m.addAttribute("admin", a.admin());
      m.addAttribute("manager", a.manager());
      m.addAttribute("username", a.username);
    }
    m.addAttribute("labels", labels());
  }

  static Map<String, String> labels() {
    Map<String, String> m = new HashMap<>();
    String[][] entries = {
      {"PLANNED", "준비"},
      {"ACTIVE", "진행"},
      {"DONE", "완료"},
      {"TODO", "할 일"},
      {"DOING", "진행"},
      {"DRAFT", "임시저장"},
      {"PENDING", "승인대기"},
      {"APPROVED", "승인"},
      {"REJECTED", "반려"},
      {"ADMIN", "관리자"},
      {"MANAGER", "매니저"},
      {"USER", "일반 사용자"}
    };
    for (String[] e : entries) m.put(e[0], e[1]);
    return m;
  }

  Store.Actor who(Principal p) {
    return s.actor(p.getName());
  }

  @GetMapping("/")
  public String home(RedirectAttributes redirect) {
    return "redirect:/dashboard";
  }

  @GetMapping("/login")
  public String login(Model m) {
    m.addAttribute("title", "로그인");
    return "login";
  }

  @GetMapping(value = "/assets/app.css", produces = "text/css")
  @ResponseBody
  public Resource css() {
    return new ClassPathResource("app.css");
  }

  @GetMapping("/dashboard")
  public String dashboard(Principal p, Model m) {
    m.addAttribute("title", "대시보드");
    m.addAttribute("counts", s.counts(who(p)));
    m.addAttribute("tasks", s.list("tasks", "", 1, who(p), false).get("rows"));
    m.addAttribute("notices", s.list("notices", "", 1, who(p), false).get("rows"));
    return "dashboard";
  }

  @GetMapping("/profile")
  public String profile(Principal p, Model m) {
    Map<String, Object> u = s.raw("users", who(p).id);
    u.remove("password");
    m.addAttribute("record", u);
    m.addAttribute("title", "내 정보");
    return "profile";
  }

  @PostMapping("/profile")
  public String profileSave(
      @RequestParam Map<String, String> f, Principal p, RedirectAttributes redirect) {
    s.profile(f, who(p));
    return "redirect:/profile?saved";
  }

  @GetMapping({
    "/users",
    "/departments",
    "/clients",
    "/projects",
    "/tasks",
    "/approvals",
    "/notices",
    "/audit",
    "/inbox"
  })
  public String list(
      HttpServletRequest req,
      @RequestParam(defaultValue = "") String q,
      @RequestParam(defaultValue = "1") int page,
      Principal p,
      Model m) {
    String path = req.getRequestURI().substring(req.getContextPath().length() + 1);
    String e = path.equals("audit") ? "audit_log" : path.equals("inbox") ? "approvals" : path;
    setup(e, m);
    m.addAllAttributes(s.list(e, q, page, who(p), path.equals("inbox")));
    m.addAttribute("path", path);
    m.addAttribute("inbox", path.equals("inbox"));
    if (path.equals("inbox")) m.addAttribute("title", "승인함");
    if (e.equals("departments") || e.equals("notices")) m.addAttribute("record", new HashMap<>());
    return "list";
  }

  void setup(String e, Model m) {
    s.entity(e);
    m.addAttribute("entity", e);
    m.addAttribute("title", TITLES.get(e));
  }

  @GetMapping("/{entity:users|clients|projects|tasks|approvals}/edit")
  public String edit(
      @PathVariable String entity,
      @RequestParam(defaultValue = "0") long id,
      Principal p,
      Model m) {
    setup(entity, m);
    Store.Actor a = who(p);
    Map<String, Object> r = id > 0 ? s.get(entity, id, a) : new HashMap<>();
    if (id > 0) s.access(entity, r, a, true);
    else {
      Map<String, Object> probe = new HashMap<>();
      probe.put("owner_id", a.id);
      probe.put("approver_id", a.id);
      probe.put("status", "DRAFT");
      s.access(entity, probe, a, true);
    }
    m.addAttribute("record", r);
    m.addAttribute("title", TITLES.get(entity) + (id > 0 ? " 수정" : " 등록"));
    choices(m);
    return "edit";
  }

  void choices(Model m) {
    for (String e : Arrays.asList("users", "departments", "projects", "clients"))
      m.addAttribute(e + "Choices", s.choices(e));
  }

  @GetMapping("/{entity:projects|tasks|approvals}/{id:[0-9]+}")
  public String detail(@PathVariable String entity, @PathVariable long id, Principal p, Model m) {
    setup(entity, m);
    m.addAttribute("record", s.get(entity, id, who(p)));
    m.addAttribute("title", TITLES.get(entity) + " 상세");
    choices(m);
    return "detail";
  }

  @PostMapping("/{entity:users|departments|clients|projects|tasks|approvals|notices}/save")
  public String save(
      @PathVariable String entity,
      @RequestParam Map<String, String> f,
      Principal p,
      RedirectAttributes redirect) {
    long id = s.save(entity, f, who(p));
    return "redirect:/"
        + entity
        + (Arrays.asList("projects", "tasks", "approvals").contains(entity) ? "/" + id : "")
        + "?saved";
  }

  @PostMapping("/{entity:departments|clients|projects|tasks|approvals|notices}/{id:[0-9]+}/delete")
  public String delete(
      @PathVariable String entity,
      @PathVariable long id,
      @RequestParam(defaultValue = "0") long version,
      Principal p,
      RedirectAttributes redirect) {
    s.delete(entity, id, version, who(p));
    return "redirect:/" + entity + "?deleted";
  }

  @PostMapping("/approvals/{id:[0-9]+}/transition")
  public String transition(
      @PathVariable long id,
      @RequestParam String action,
      @RequestParam(defaultValue = "") String note,
      @RequestParam long version,
      Principal p,
      RedirectAttributes redirect) {
    s.transition(id, action, note, version, who(p));
    return "redirect:/approvals/" + id + "?saved";
  }

  @ExceptionHandler(Problem.class)
  public String problem(Problem ex, HttpServletResponse response, Model m) {
    response.setStatus(ex.status);
    m.addAttribute("title", "요청 확인");
    m.addAttribute("message", ex.getMessage());
    return "error";
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  public String integrity(HttpServletResponse response, Model m) {
    response.setStatus(409);
    m.addAttribute("title", "저장할 수 없습니다");
    m.addAttribute("message", "중복된 아이디·부서명이 있거나 다른 자료에서 사용 중입니다. 입력과 연결 자료를 확인하세요.");
    return "error";
  }
}
