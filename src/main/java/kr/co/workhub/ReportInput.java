package kr.co.workhub;

import java.util.*;
import org.springframework.util.MultiValueMap;

/** Bound strings are retained for validation redisplay; numeric conversion is server-side. */
public class ReportInput {
  final Map<String, Object> header = new LinkedHashMap<>();
  final List<Map<String, Object>> lines = new ArrayList<>();

  public static ReportInput from(MultiValueMap<String, String> values) {
    ReportInput input = new ReportInput();
    for (String key :
        Arrays.asList(
            "id",
            "version",
            "title",
            "period_start",
            "period_end",
            "business_unit",
            "currency",
            "reviewer_id",
            "summary_note"))
      input.header.put(key, values.getFirst(key) == null ? "" : values.getFirst(key));
    String[] names = {"lineLabel", "lineCategory", "lineA", "lineB", "lineC", "lineMemo"};
    String[] keys = {"label", "category", "amount_a", "amount_b", "amount_c", "memo"};
    int size = values.getOrDefault("lineLabel", Collections.emptyList()).size();
    if (size < 1 || size > 12) throw new Problem(400, "명세는 1~12행이어야 합니다.");
    for (String n : names)
      if (values.getOrDefault(n, Collections.emptyList()).size() != size)
        throw new Problem(400, "명세 열의 개수가 일치하지 않습니다.");
    for (int i = 0; i < size; i++) {
      Map<String, Object> row = new LinkedHashMap<>();
      for (int j = 0; j < names.length; j++) row.put(keys[j], values.get(names[j]).get(i));
      input.lines.add(row);
    }
    return input;
  }
}
