package kr.co.workhub;

/** Only a deployment-secret value is accepted; no credential is generated or persisted here. */
public final class ContainerDemoPolicy {
  private ContainerDemoPolicy() {}

  public static String requireSecret(String secret) {
    if (secret == null || !secret.matches("[A-Za-z0-9@%+=:,._-]{24,72}"))
      throw new IllegalStateException(
          "WORKHUB_DEMO_PASSWORD must be a separately configured 24-72 character ASCII secret");
    if (secret.contains("DevOnly!2026pass")
        || secret.toLowerCase(java.util.Locale.ROOT).contains("change-me"))
      throw new IllegalStateException("A placeholder password cannot be used for container-demo");
    return secret;
  }
}
