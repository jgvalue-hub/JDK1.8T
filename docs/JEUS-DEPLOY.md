# JEUS 8.5 배포 안내

실제 JEUS 런타임과 라이선스는 이 환경에 없었습니다. 다음은 Java 8 / Java EE 8 WAR의 배포 절차이며 **JEUS 8.5 실배포 통과 보고가 아닙니다**. 서버 fix 수준에 맞는 TmaxSoft 매뉴얼을 우선 적용하세요. 관리 CLI의 버전별 차이를 피하려고 검증되지 않은 jeusadmin 명령이나 XML descriptor를 제공하지 않습니다.

1. 승인된 JEUS 8.5 fix와 지원 JDK 8 패치 수준을 준비합니다. `java -version`과 실제 Managed Server의 Java home을 확인합니다. Servlet 4.0/JSP/JSTL 지원과 UTF-8 인코딩을 점검합니다.
2. `mvn clean verify`로 운영 WAR를 만듭니다. `jar tf target/workhub.war`에서 H2, servlet-api, Tomcat 라이브러리가 없는지 확인합니다. dev WAR를 운영에 올리지 않습니다.
3. 운영 DB 종류를 확정합니다. `src/main/resources/schema.sql`은 H2 검증용 기준 DDL입니다. DBA가 대상 DB에 맞게 검토·변환하고 변경 승인 후 수동 적용합니다. 앱은 운영 스키마를 자동 생성하지 않습니다. `LIMIT/OFFSET`, identity, boolean, timestamp, generated keys 반환이 주요 이식 지점입니다. 이 작업에서는 운영 DB 변경을 하지 않았습니다.
4. JEUS 관리 콘솔에서 JDBC 드라이버, 커넥션 풀, 최소 DB 권한을 가진 계정과 DataSource를 등록합니다. 비밀번호는 서버 보안 설정에 보관합니다. 서버 JNDI를 애플리케이션 `java:comp/env/jdbc/WorkhubDS`에 매핑하거나 JVM 옵션 `-Dworkhub.jndi=<실제 JNDI 이름>`으로 연결합니다. 매핑 방법·descriptor 스키마는 설치된 JEUS 매뉴얼을 따릅니다. DB 종류에 맞는 JDBC 드라이버도 서버에 따로 배치합니다.
5. 운영에는 `spring.profiles.active=dev`를 **설정하지 않습니다**. 기본 `!dev` 설정은 JNDI 연결만 사용합니다. 운영 DataSource 등록 누락은 시작 실패로 드러납니다. H2 JNDI를 사용할 경우 H2 JDBC 드라이버는 JEUS 서버 라이브러리에 별도로 필요합니다.
6. 최초 관리자 계정은 오프라인으로 생성합니다. `PasswordHash`가 비밀번호를 터미널에서 비표시 입력받아 BCrypt cost 12 해시를 출력합니다.
   ```bash
   java -cp 'target/workhub/WEB-INF/classes:target/workhub/WEB-INF/lib/*' kr.co.workhub.PasswordHash
   ```
   DBA가 `users` 테이블에 실제 아이디, 이름, 이메일, 출력된 해시, `role='ADMIN'`, `enabled=TRUE`를 바인딩하여 삽입합니다. 개발 계정의 비밀번호를 재사용하지 않습니다. SQL 입력은 대상 DB 툴의 바인딩 기능을 사용하고 작업 기록에 평문 비밀번호를 남기지 않습니다.
7. WebAdmin에서 새 WAR 애플리케이션으로 `target/workhub.war`를 등록하고 컨텍스트 `/workhub` 및 대상 Managed Server를 선택합니다. Servlet API는 서버가 제공하고 Spring은 WAR에서 로딩하도록 라이브러리 충돌을 확인합니다. 서버 공용 Spring 버전과 충돌하면 JEUS의 애플리케이션 클래스 로딩 정책을 검토합니다.
8. 운영 HTTPS를 구성합니다. 앱 운영 모드의 세션 쿠키는 Secure/HttpOnly이며 HTTP만으로는 로그인 세션이 유지되지 않습니다. WebtoB/프록시 TLS 종료를 쓰면 JEUS의 신뢰 프록시·스킴·secure 포트 전달을 설정합니다. SameSite=Lax 쿠키 정책, TLS/HSTS, 로그인 속도 제한은 서버·프록시에서 적용하고 확인하세요. 앱의 CSP/CSRF를 해제하지 않습니다.
9. 비운영 JEUS에서 로그인, 20개 화면, 한글 입력·검색·출력, 생성 키, DB 재접속, 사용자별 권한, CSRF, 동시 승인, Secure 쿠키, 재기동 후 영속성을 검증합니다. 클러스터 환경의 세션 복제/고정 세션 및 트랜잭션 특성은 별도 검증 대상입니다.
10. DB 백업·복구, 로그 보존, 세션 정책, 모니터링을 마련하고 상용 Spring 유지보수 버전의 회귀 테스트를 통과한 후 운영 반영합니다. WAR 롤백만으로 DB 변경은 롤백되지 않습니다. 이 버전의 앱은 자동 마이그레이션을 수행하지 않습니다.

현재 구조는 단일 DB의 로컬 트랜잭션을 사용합니다. 분산 XA/JTA, 첨부파일, 이메일 발송, SSO, 복잡한 다단계 결재선은 이번 범위에 포함되지 않습니다.
