package kr.co.workhub;

import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@ControllerAdvice
public class ReportNavigation {
  @ModelAttribute
  public void menus(Model model) {
    model.addAttribute("reportGroups", ReportCatalog.groups());
    model.addAttribute("businessMenuCount", ReportCatalog.all().size());
  }
}
