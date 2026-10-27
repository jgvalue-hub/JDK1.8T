# 검증 결과

검증일: 2026-10-27. 빈 `JDK1.8T` 저장소에 신규 구현했습니다. 기존 AGENTS.md 또는 로컬 .agents/skills는 없었습니다.

## 통과 항목

- **실제 JDK 8 실행**: Eclipse Temurin `1.8.0_472-b08`. 시스템 기본 JDK 21 대신 별도 JDK 8을 설치해 Maven과 Tomcat을 실행했습니다.
- **개발/운영 WAR 빌드**: Maven 3.9.9의 dev `verify` 및 기본 프로필 `clean verify` 성공. 운영 빌드는 clean으로 개발 H2가 staging 디렉터리에 남지 않도록 재구성했습니다.
- **자동 통합 테스트 11개, 실패 0, 오류 0**: `WorkflowTest` 10개 + `ProductionConfigTest` 1개. Maven 실행 후 `target/surefire-reports/`에서 재생성할 수 있습니다.
- **20개 화면 경로**: MockMvc 전체 경로 200. 별도로 Tomcat 실제 JSP 렌더링을 HTTP로 검사했습니다.
- **승인 흐름**: 작성→상신→반려→수정→재상신→승인, 중복 처리 409, 지정 승인자 외 처리/타인 조회 403.
- **CRUD 및 입력**: 거래처 생성/조회/수정/삭제와 삭제 후 404, 업무 담당자 검사, 잘못된 상태 전이 거절, FK 참조 삭제 거절, 낙관적 잠금, 검색/10건 페이징/SQL 주입 문자열 및 와일드카드 리터럴 처리.
- **보안**: 익명 접근 로그인 이동, ADMIN URL 접근 차단, CSRF 누락 403, 실제 로그인 세션 ID 변경, POST 로그아웃 후 보호 화면 접근 불가, 저장형 XSS HTML 이스케이프, 비밀번호 해시 및 변경 실패 트랜잭션 롤백, 72바이트 초과 BCrypt 접미사 거절.
- **운영 설정 분리**: mock JNDI DataSource로 기본 운영 프로필 기동, 개발 seed bean 부재와 사용자 0건 확인. 실제 JEUS JNDI 검증은 아닙니다.
- **바이트코드**: 운영 WAR 6,611개 클래스와 개발 WAR 7,660개 기본 클래스가 모두 major ≤52(Java 8). 개발 H2의 multi-release/module-info 3개는 Java 8에서 선택되지 않는 항목으로 제외했습니다. 운영 WAR에서 H2·Servlet API·Tomcat JAR 미포함을 확인했습니다.

## 실제 웹 실행 증거

- 대체 서버: Tomcat 9.0.113 / JDK 8 / `127.0.0.1:18080` / 파일 H2 2.2.224.
- [HTTP 결과 JSON](http-smoke-results.json): 실제 JSP 20개 화면, 한글 저장, 승인·반려, 세션, XSS, CRUD.
- [Chromium 결과 JSON](browser-results.json): 실제 로그인, CSS 로딩, 한글 업무 입력 및 폼 저장, 모바일 viewport 가로 넘침 없음, JS 오류 없음.
- [재기동 영속성 결과](persistence-results.json): Tomcat 프로세스 종료/재시작 뒤 승인 완료 요청의 한글 제목과 처리 의견 유지.
- [개발 바이트코드](bytecode-dev.json), [운영 바이트코드](bytecode-production.json), [의존성 트리](dependencies.txt).

실제 브라우저 검증으로 발견한 공통 JSP fragment의 한글 디코딩 오류와 요청 UTF-8 필터 순서 문제를 수정했습니다. 필터 중복 등록에 따른 재배포 실패도 수정한 뒤 최종 검증을 수행했습니다. 최초 실패한 인코딩 검사가 만든 가상 업무 1건은 삭제했습니다.

## 재실행

```bash
mvn -Pdev clean verify
cp target/workhub.war target/workhub-dev.war
# JAVA_HOME=JDK8, CATALINA_HOME=Tomcat9 지정 후
./scripts/dev-server.sh run
# 다른 터미널에서
python3 scripts/http-smoke.py > docs/http-smoke-results.json
python3 scripts/browser-check.py
# 서버를 완전히 stop/start 한 다음
python3 scripts/persistence-check.py
python3 scripts/check-bytecode.py target/workhub-dev.war
```

브라우저 스크립트는 Python Playwright와 `/usr/bin/chromium`을 사용합니다. HTTP 스크립트는 Python 표준 라이브러리만 사용합니다. 테스트는 개발 DB에 가상 승인요청·업무·감사 기록을 남깁니다. 운영 DB에 실행하지 마세요. HTTP/브라우저 검사는 지정된 개발 계정이 있는 dev 프로필을 전제로 합니다.

## 남은 제약

JEUS 8.5 서버/라이선스가 없어 실제 배포, fix별 클래스 로딩, WebtoB 연계, 실 JNDI, TLS/SameSite, 클러스터링은 검증하지 못했습니다. 운영 DB가 지정되지 않아 H2 외 SQL 호환성은 검증하지 않았습니다. Spring 5.3 및 Security 5.8은 OSS 지원 종료 상태이며, 최신 전체 CVE SCA·상용 유지보수 패치 적용·침투테스트는 완료하지 않았습니다. 테스트 JDK와 Tomcat 버전도 운영용 최신 패치라고 보증하지 않습니다.

소스 브랜치 저장과 웹 애플리케이션 공개 배포는 별개입니다. 외부 메일/메시지, 공개 웹 배포, 운영 DB 변경, PR/merge는 수행하지 않았습니다. WAR/ZIP/DB/캐시/로그/캡처는 커밋에서 제외합니다.

## GitHub 소스 저장 전 최종 확인

최종 변경은 README와 `.gitignore`, 검증 문서 정리이며 애플리케이션 동작 코드는 변경하지 않았습니다. Java 8 환경에서 두 프로필을 다시 검증했고, 2026-10-27 21:21 UTC 기준 각각 빌드 성공 및 테스트 11개(실패·오류 0)가 확인됐습니다. 두 WAR의 바이트코드 검사도 PASS입니다.

```bash
export JAVA_HOME=/path/to/jdk8
export PATH="$JAVA_HOME/bin:/path/to/apache-maven/bin:$PATH"
mvn -B -Pdev clean verify
python3 scripts/check-bytecode.py target/workhub.war
mvn -B clean verify
python3 scripts/check-bytecode.py target/workhub.war
```

개발 WAR 보관이 필요하면 두 번째 clean 실행 전에 저장소 밖에 복사하세요. 환경별 Maven 프록시 설정은 저장소에 포함하지 않습니다. 이전 HTTP/Chromium/재기동 검증 결과는 위 JSON에 보존합니다.
