> 아래는 Boot/Gradle 전환 당시 검증 기록입니다. 이후 50개 메뉴 확장의 현재 검증은 [REPORTS-VERIFICATION.md](REPORTS-VERIFICATION.md)를 참고하세요.

# Java 8 / Boot 2.7.18 / Gradle 7.6 재구성 검증

검증일: 2026-10-28. 기준 커밋 `c8adff987ea0c8313bf896d430d04f0867a9bb06`에서 로컬 브랜치 `codex/workhub-boot2718-gradle76`를 만들었습니다. 기존 `codex/workhub-java8-jeus85`와 main은 변경하지 않았고 원격 push/Render 재배포하지 않았습니다. 아래 localhost 주소는 검증 환경 내부 주소이며 사용자용 공개 링크가 아닙니다.

## 변경과 실제 해석 버전

| 항목 | 이전 | 이번 구성/확인값 |
|---|---|---|
| Java | Java 8 | source/target 1.8, 호스트 Temurin 1.8.0_472-b08 실제 실행 |
| Framework | 5.3.39 | 요청한 정확한 5.3.34, 모든 runtime/test Spring 모듈 검사 |
| Boot | 없음 | 정확한 2.7.18, starter web/security, SpringBootServletInitializer와 main |
| Security | 5.8.16 | 유지, Boot BOM의 5.7.11로 내리지 않음 |
| 빌드 | Maven 3.9.9 / pom.xml | Gradle 7.6 공식 Wrapper; dependency-management 플러그인 1.1.4 |
| 외부 서버 | Tomcat 9.0.113, JEUS 8.5 목표 | Tomcat 9.0.113 재검증, JEUS 실배포는 미검증 |
| 내장/컨테이너 Tomcat | 컨테이너 9.0.123 | 내장/컨테이너 9.0.123, 컨테이너 Java 1.8.0_504-b01 |
| 화면/DB | 20 JSP / H2 또는 JNDI | 20화면 유지, H2 2.2.224 개발/컨테이너 데모, 운영 JNDI |
| Servlet/JSP/JSTL | javax Servlet 4.0 / JSP / JSTL1.2 | 동일, Servlet API 선언 4.0.1; JSP 구현은 서버가 제공 |

Boot 2.7.18의 실제 BOM POM에서 Framework 기본 5.3.31/Security 기본 5.7.11을 확인했습니다. `build.gradle`의 `spring-framework.version=5.3.34`, `spring-security.version=5.8.16` override가 적용된 실제 그래프를 `verifyVersions`와 실행 테스트가 확인합니다. [전체 개발·운영 해석 결과](dependencies.txt)에 Jackson 2.13.5, Logback 1.2.12, SnakeYAML 1.30 등을 포함했습니다. 별도 JDBC 풀은 추가하지 않았으며 개발은 DriverManagerDataSource, 운영 풀은 JEUS JNDI 제공자 책임입니다.

기존 DispatcherServlet/Security initializer 두 개를 제거하고 Boot가 각각 한 번 등록하도록 했습니다. 기존 MVC/JSP resolver, SecurityFilterChain, Store/CRUD와 권한·CSRF·승인 로직은 유지했습니다. Boot의 DataSource/JdbcTemplate/트랜잭션/SQL 초기화/기본 사용자 자동 설정을 제외해 명시한 DataSource·seed·인증만 사용합니다. bean overriding과 circular references는 false입니다.

## 통과 결과

- **최종 개발/운영 clean 빌드 각각 성공, 테스트 각각 22개, 실패/오류/스킵 0.** WorkflowTest 11, ContainerDemoTest 6, ProductionConfigTest 1, BootProfilesTest 4. [요약](gradle-test-results.json), 재실행 후 상세 `build/reports/tests/test/index.html` / `build/test-results/test/`.
- **Boot 회귀**: 실제 Framework/Boot/Security 버전, 단일 DataSource/JdbcTemplate/security proxy/필터 등록/UTF-8 필터/UserDetailsService, SQL 자동 초기화 및 기본 사용자 생성 부재를 검사했습니다. Boot 웹 컨텍스트에서 운영 mock JNDI 연결·seed 미생성, container-demo 초기화와 관리자만 활성화, 비밀 누락 및 dev/container-demo 혼합 시작 실패를 검증했습니다. 실제 배포 비밀은 조회하지 않았습니다.
- **20개 실제 JSP 화면과 HTTP 흐름**: 외부 Tomcat9.0.113 `127.0.0.1:18081/workhub`와 실행형 Boot WAR/내장 Tomcat9.0.123 `127.0.0.1:18082/workhub`에서 각각 통과. 한글 입력·출력, 세션 ID 변경, CSRF 누락403, 역할403, 레코드권한403, 입력400, 승인→반려→수정→재상신→승인, 중복409, CRUD·삭제404, 저장형XSS 이스케이프, POST 로그아웃을 검사했습니다. [외부 서버](http-smoke-results.json), [Boot launcher](boot-http-smoke-results.json).
- **브라우저**: 실제 Chromium 로그인/CSS/한글 업무 저장, 1440×1000 및 390×844 모바일 viewport 가로 넘침 없음, JS 오류 없음. [결과](browser-results.json). 캡처 파일은 소스에 포함하지 않습니다.
- **영속성**: 외부 Tomcat 프로세스를 완전히 종료하고 재기동한 뒤 승인 완료 제목과 의견 유지. [결과](persistence-results.json).
- **패키징**: 일반 운영 WAR는 H2/Servlet API/Tomcat/Jasper/ECJ 없음. 개발 WAR만 H2 포함. 실행형 WAR는 내장 서버를 WEB-INF/lib-provided에 격리하고 WarLauncher/Main-Class를 확인했습니다. [네 가지 WAR 검사](packaging-results.json), [산출물 SHA-256](artifact-checksums.json).
- **Java8 바이트코드**: 일반 운영 10,713개/개발 11,762개, 실행형 운영 13,969개/개발 15,018개 기본 클래스 모두 major≤52. 의존성의 multi-release/module-info는 Java8에서 선택되지 않아 제외했습니다. `WEB-INF/lib`와 `WEB-INF/lib-provided`를 모두 검사합니다. [운영](bytecode-production.json), [개발](bytecode-dev.json), [Boot 운영](bytecode-production-boot.json), [Boot 개발](bytecode-boot.json).
- **컨테이너**: 공식 캐시를 이용한 네트워크 없는 동등 빌드와 이미지 내부 22개 테스트 통과. UID/GID10001, 실제 Java1.8.0_504, Tomcat configtest(실제 포트/호스트 JVM 속성 전달) 통과. 동의/비밀 누락 시 exit64, 셸 시작 차단8개/XML 검사 통과. 호스트 포트 publish와 외부 배포는 하지 않았습니다. [컨테이너 결과](container-verification.json).

새 Boot 테스트가 추가되며 테스트용 JNDI factory의 JVM 전역 등록이 후속 테스트에 남는 문제가 드러났습니다. `JndiFixture`에서 한 builder를 재사용하고 매 테스트 deactivate하도록 수정한 후 전체 두 프로필의 clean 테스트를 통과했습니다. 애플리케이션 JNDI 로직을 우회하지 않았습니다.

## 재실행 명령

JDK8 JAVA_HOME/PATH를 지정한 저장소 루트에서 실행합니다. 환경 전용 프록시/truststore 설정은 저장소에 포함하지 않습니다.

```bash
./gradlew --version
./gradlew clean test verifyVersions war bootWar
python3 scripts/check-bytecode.py build/libs/workhub.war
# 개발 clean 전에 운영 WAR 보관이 필요하면 별도 폴더에 복사
./gradlew -Pdev clean test verifyVersions war bootWar
python3 scripts/check-bytecode.py build/libs/workhub-dev-boot.war
java -Dspring.profiles.active=dev -jar build/libs/workhub-dev-boot.war --server.port=18082
# 다른 터미널에서; 빈 개발 DB/가상 계정만 사용
python3 scripts/http-smoke.py http://127.0.0.1:18082/workhub
python3 scripts/browser-check.py http://127.0.0.1:18082/workhub
python3 scripts/check-container-config.py
```

외부 Tomcat은 개발 일반 WAR를 `webapps/workhub.war`로 배포하고 dev 런타임 프로필을 별도로 지정합니다. HTTP 결과를 `docs/http-smoke-results.json`에 저장한 후 서버 종료/재시작하고 `python3 scripts/persistence-check.py <같은 base URL>`로 영속성을 재검사합니다. `scripts/check-packaging.py`는 네 개 WAR를 build/libs에 보관한 상태에서 실행합니다. `-Pdev`는 H2 포함 여부만 바꾸며 실행 프로필을 켜지 않습니다. 브라우저 검사는 Playwright와 `/usr/bin/chromium`이 필요합니다. Windows PowerShell 실행 명령은 README에 있으며 Windows 실기동은 미검증입니다.

기존 CI workflow는 없었습니다. CI에서도 같은 Wrapper 명령을 사용하면 되며 별도 Maven 실행 단계가 없습니다. Gradle의 `check`는 버전 검사를 포함합니다. 저장소에는 환경별 캐시/프록시 설정이나 빌드 출력물을 넣지 않습니다.

## 공식 Wrapper 무결성

- 배포본: https://services.gradle.org/distributions/gradle-7.6-bin.zip
- 공식 SHA256: `7ba68c54029790ab444b39d7e293d3236b2632631fb5f2e012bb28b4ff669e4b` — 공식 `.sha256`와 다운로드 ZIP 비교, wrapper properties에 고정.
- Wrapper JAR: `c5a643cf80162e665cc228f7b16f343fef868e47d3a4836f62e18b7e17ac018a` — 공식 `https://services.gradle.org/distributions/gradle-7.6-wrapper.jar.sha256`와 비교. 검증된 배포본의 wrapper 작업으로 생성했습니다.

## 남은 제한 / 차단

일반 온라인 Docker 빌드는 `services.gradle.org`의 컨테이너 DNS 해석 실패(UnknownHostException)로 중단됐습니다. 해당 다운로드는 성공하지 않았고 접근제한은 변경하지 않았습니다. 독립 검증으로 호스트에서 이미 정상 다운로드한 공식 배포본/의존성 캐시를 임시 컨텍스트에 복사하고 `--offline`, `docker build --network none`으로 빌드했습니다. **기본 Dockerfile의 온라인 end-to-end 성공은 미검증**입니다.

JEUS8.5 런타임/라이선스가 없어 실제 배포, 클래스 로더, 실JNDI, WebtoB/TLS/클러스터는 미검증입니다. H2 외 운영DB 이식, 전체 최신 SCA/침투테스트/장시간 부하, Windows 실행도 미검증입니다. Framework5.3/Security5.8/Boot2.x는 OSS 지원 종료 상태이고 요청한 Framework5.3.34는 기존5.3.39보다 이전 버전입니다. [보안 한계](SECURITY.md)에 공식 근거와 알려진 미수정 범위를 기록했습니다. 이번 작업에서는 비밀번호 조회·외부 배포·원격 push·PR/merge·유료 변경을 수행하지 않았습니다.
