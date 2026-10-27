# Workhub — Java 8 / JEUS 8.5 업무관리

한국어 사내 업무관리 웹시스템. **Java 1.8, javax Servlet 4.0, Spring MVC 5.3.39, Spring Security 5.8.16, JSP/JSTL, Maven WAR**로 작성했습니다. 등록/수정을 하나의 화면 모드로 계산한 20개 화면을 제공합니다. 원래 저장소는 커밋·소스가 없는 상태였으며 기존 파일을 덮어쓰지 않았습니다.

## 빠른 실행

JDK 8, Maven 3.9.x, Tomcat 9가 필요합니다. Tomcat은 JEUS가 없는 개발 환경의 대체 서버입니다.

```bash
export JAVA_HOME=/path/to/jdk8
export PATH="$JAVA_HOME/bin:/path/to/apache-maven/bin:$PATH"
mvn -Pdev clean verify
cp target/workhub.war target/workhub-dev.war
export CATALINA_HOME=/path/to/apache-tomcat-9
./scripts/dev-server.sh run
```

Tomcat 커넥터를 localhost:18080으로 설정하면 `http://127.0.0.1:18080/workhub/login`에서 접속합니다. 일반 Tomcat 기본 포트는 8080입니다. 개발 서버 스크립트는 포트 설정을 바꾸지 않습니다. 데이터는 저장소의 `.data/workhub.mv.db`에 남습니다. 중지는 Ctrl-C 또는 `./scripts/dev-server.sh stop`입니다.

검증 환경은 Temurin JDK 8u472-b08, Maven 3.9.9, Tomcat 9.0.113입니다. 로컬 경로와 프록시 설정은 각 개발 환경에서 지정하며 저장소에 포함하지 않습니다. 이 버전들이 최신 보안 패치라는 의미는 아닙니다.

**demo-only 개발 전용 계정**: `admin`(관리자), `manager`(매니저), `user` 및 `other`(일반 사용자). 공통 비밀번호는 `DevOnly!2026pass`. `spring.profiles.active=dev`에서 빈 개발 DB에만 생성됩니다. 운영 프로필에는 계정·샘플 데이터 자동 생성이 없습니다. 가상 이메일은 `example.test`만 사용합니다.

## 기능과 권한

- 사용자 등록/수정/비활성화, 부서·거래처·프로젝트·업무·공지 CRUD. 사용자 삭제 대신 비활성화하여 연결 자료를 유지합니다.
- 목록별 이름/제목 검색, SQL 바인딩, 10건 단위 서버 페이징. `%`/`_`는 일반 검색 문자로 처리합니다.
- 전 직원이 공용 프로젝트·업무·거래처·공지를 조회합니다. 프로젝트/업무 수정은 담당자 또는 매니저·관리자에게 허용합니다. 일반 사용자는 자신에게만 배정합니다.
- 거래처·공지 쓰기는 매니저/관리자, 사용자·부서·감사로그는 관리자 권한이 필요합니다. DB에서 매 요청의 활성 여부와 현재 역할을 재확인합니다. 상위 URL 권한은 로그인 당시 역할도 요구하므로 역할 승격 후 다시 로그인해야 합니다.
- 승인요청은 작성자·지정 승인자·관리자만 조회합니다. 관리자는 열람할 수 있지만 지정 승인자가 아니면 처리할 수 없습니다. 자기 승인은 금지합니다.
- 승인 흐름: `DRAFT → PENDING → APPROVED/REJECTED`. 반려 후 작성자가 수정하면 `DRAFT`로 돌아가 다시 상신합니다. 승인/반려 의견은 필수입니다. 대기·완료 요청은 수정/삭제할 수 없습니다.
- 프로젝트 `PLANNED → ACTIVE → DONE`, 업무 `TODO → DOING → DONE`. 역행·단계 건너뛰기는 거절합니다. 버전 검사로 이중 승인과 동시 수정을 차단합니다. 사용자·부서는 버전 잠금 대상이 아니며 마지막 저장이 적용됩니다.
- 트랜잭션 안에서 변경과 감사로그를 함께 저장합니다. 로그는 앱에서 수정/삭제할 수 없지만 DBA 수준의 변조 방지 저장소는 아닙니다.
- BCrypt cost 12, 로그인 시 세션 ID 변경, 30분 세션 만료, HttpOnly, POST 로그아웃, CSRF, CSP/보안 응답 헤더, 서버 입력검증, JSP 출력 이스케이프, 외래키 검사를 적용합니다. 운영 쿠키는 Secure입니다.

## 운영 빌드와 배포

```bash
mvn clean verify                  # 기본 프로필: H2는 테스트 의존성만
# target/workhub.war → JEUS에 배포할 운영 WAR
```

**기본 실행은 운영 모드**입니다. JNDI `java:comp/env/jdbc/WorkhubDS`가 반드시 필요하며 DB 생성/수정/샘플 삽입을 자동 수행하지 않습니다. `-Dworkhub.jndi=...`로 정확한 서버 JNDI 이름을 지정할 수 있습니다. 운영 WAR에는 H2·Servlet API·Tomcat이 포함되지 않습니다.

현재 스키마와 SQL은 H2 2.2.224에서 검증했습니다. 운영 DB가 지정되지 않았으므로 Oracle/Tibero/MySQL 등 특정 DB 지원을 주장하지 않습니다. JNDI 연결 분리는 구현됐지만 다른 DB는 identity/boolean/check/페이징 문법과 생성 키 반환을 조정하고 회귀 테스트해야 합니다. 상세 절차는 [JEUS 배포 안내](docs/JEUS-DEPLOY.md)를 보세요.

## 보안 지원 한계

**Spring Framework 5.3 OSS 지원은 2024-08-31 종료되었습니다.** Spring Security 5.8도 해당 종료 일정에 포함됩니다. Java 8 요구사항을 유지하기 위해 이 계열을 사용했으며 최신 지원 계열로 임의 전환하지 않았습니다. 이 WAR를 보안 검토 없이 운영에 공개하면 안 됩니다. 상용 유지보수 버전 적용 또는 플랫폼 업그레이드 계획이 필요합니다.

[Spring 공식 지원 종료 안내](https://spring.io/blog/2024/03/01/support-timeline-announcement-for-spring-framework-6-0-x-and-5-3-x/)와 [의존성·보안 검토](docs/SECURITY.md)에 알려진 한계와 버전 확인 근거를 기록했습니다. 몇 가지 공격 경로를 피했다고 모든 알려진 취약점이 해결된 것은 아닙니다. 전체 SCA 무결점·침투테스트·JEUS 실배포는 확인되지 않았습니다.

## 검증과 산출물

- [20개 화면 목록](docs/SCREENS.md)
- [실제 검증 결과](docs/VERIFICATION.md)
- `target/workhub.war`: 운영 WAR, `target/workhub-dev.war`: 개발 WAR
- `scripts/http-smoke.py`: 실제 HTTP 흐름, `scripts/browser-check.py`: Chromium 화면 검증
- `scripts/check-bytecode.py`: WAR 및 의존성의 Java 8 바이트코드 검사
- `src/test/java/kr/co/workhub/WorkflowTest.java`: Spring MVC/보안/DB 통합 테스트
- WAR/ZIP, 실행 DB, 캐시, 로그, 브라우저 캡처는 `.gitignore`로 소스 저장에서 제외합니다.

외부 발송, 공개 웹 배포, 운영 DB 변경, PR/merge는 구현 및 검증 범위에 포함되지 않습니다. GitHub에는 전용 작업 브랜치의 소스·문서·테스트만 저장합니다.

## 모바일 테스트용 별도 배포 준비

[컨테이너 데모 배포 안내](docs/CONTAINER-DEMO.md)에 Dockerfile, Java 8/Tomcat 9, HTTPS 프록시, PORT 및 별도 비밀 설정을 설명했습니다. `container-demo`는 고정 로컬 비밀번호를 사용하지 않고 필수 설정 없이는 기동을 거절합니다. Codex 자체 모바일 접속 URL은 제공되지 않습니다. 외부 배포·비밀 설정·공개 범위는 별도 승인이 필요합니다.
