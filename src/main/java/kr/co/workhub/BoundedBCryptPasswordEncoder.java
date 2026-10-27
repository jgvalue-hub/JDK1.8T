package kr.co.workhub;

import java.nio.charset.StandardCharsets;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/** Rejects overlong UTF-8 passwords before legacy BCrypt can truncate them. */
public final class BoundedBCryptPasswordEncoder extends BCryptPasswordEncoder {
  public BoundedBCryptPasswordEncoder() {
    super(12);
  }

  @Override
  public boolean matches(CharSequence raw, String encoded) {
    return raw != null
        && raw.toString().getBytes(StandardCharsets.UTF_8).length <= 72
        && super.matches(raw, encoded);
  }
}
