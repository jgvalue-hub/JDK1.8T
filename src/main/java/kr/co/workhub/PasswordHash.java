package kr.co.workhub;

import java.io.Console;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/** Offline bootstrap helper. Never accepts a password in command-line arguments. */
public final class PasswordHash {
  public static void main(String[] args) {
    Console c = System.console();
    if (c == null) throw new IllegalStateException("대화형 터미널에서 실행하세요.");
    char[] input = c.readPassword("관리자 비밀번호 (12자 이상, UTF-8 72바이트 이하): ");
    if (input == null) return;
    try {
      String p = new String(input);
      if (p.length() < 12 || p.getBytes(StandardCharsets.UTF_8).length > 72)
        throw new IllegalArgumentException("비밀번호 길이를 확인하세요.");
      c.printf("%s%n", new BCryptPasswordEncoder(12).encode(p));
    } finally {
      Arrays.fill(input, '\0');
    }
  }
}
