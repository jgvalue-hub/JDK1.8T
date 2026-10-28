# Workhub — 회계·리스크 보고센터 / 50개 업무 메뉴

한국어 회계·리스크 보고 시스템을 **Java 8, Spring Framework 5.3.34, Spring Boot 2.7.18, Gradle 7.6 Wrapper**로 재구성했습니다. Spring Security는 기존 **5.8.16**을 유지합니다. JSP/JSTL과 javax Servlet 4.0 기반이며 외부 Tomcat/JEUS용 일반 WAR와 로컬 실행형 Boot WAR를 함께 제공합니다. Maven 설치나 pom.xml은 필요하지 않습니다.

## 50개 주요 업무 메뉴

로그인 후 `/workhub/reports` 보고센터로 이동합니다. 주요 메뉴는 **10개 영역 × 5개 = 정확히 50개**이며, 등록·상세·수정·휴지통은 메뉴 내부 기능입니다. 기존 20개 화면과 데이터는 보조 업무 도구에 보존했습니다. 모바일에서는 영역을 펼치거나 메뉴 검색으로 바로 이동합니다.

| 영역 | 5개 업무 메뉴 |
|---|---|
| 결산·원장 | 분개전표 보고서, 합계잔액시산표, 결산조정 보고서, 비용원장 분석, 관계사 대사 보고서 |
| 매출·채권 | 매출채권 연령분석, 채권회수 계획, 매출 정산대사, 거래처 연체현황, 매출 기간귀속 검토 |
| 매입·채무 | 매입채무 잔액보고, 자금 지급계획, 공급업체 정산대사, 미청구 매입검토, 경비 정산보고 |
| 자산·투자 | 고정자산 명세, 감가상각 시뮬레이션, 자산 처분검토, 설비투자 검토, 무형자산 검토 |
| 예산·손익 | 예산 대비 실적, 부서별 예산보고, 프로젝트 손익계획, 원가 차이분석, 롤링 전망보고 |
| 자금·유동성 | 일일 자금현황, 현금흐름 계획, 유동성 갭 분석, 차입·조달 계획, 유동성 스트레스 |
| 시장위험 | 환율 민감도 보고, 금리 민감도 보고, 가격변동 위험, 헤지 시나리오 비교, 시장 스트레스 보고 |
| 신용위험 | 거래처 신용노출, 기대손실 시뮬레이션, 신용 집중도 검토, 담보 회수위험, 거래상대방 관찰 |
| 운영위험 | 운영위험 등록부, 사고 영향평가, 업무연속성 위험, 외주·공급망 위험, 정보보호 위험평가 |
| 통제·보고품질 | 내부통제 점검, 감사 개선조치, 보고서 품질점검, 준수사항 자체점검, 위험 대응추적 |

[50개 경로와 입력 열 전체 목록](docs/REPORT-MENUS.md)을 제공합니다. 제목만 바꾼 화면이 아니라 **10종의 입력·계산·검증 규칙**, 메뉴별 기준 항목·분류·업무 설명을 사용합니다. 공통 화면 구성은 재사용합니다.

- 기간/관리단위/통화/검토자와 1~12개 명세를 함께 저장하는 실제 CRUD. 명세 행 추가·수정·제거, 오류 시 입력 유지, 제목·기간·상태·분류·담당범위·휴지통 검색과 10건 페이징을 제공합니다.
- `DRAFT → PENDING → APPROVED/REJECTED`. 작성자 회수 가능, 반려 후 수정 저장하면 DRAFT. 자기 승인 금지, 지정된 활성 매니저/관리자만 처리합니다. 승인본 수정·삭제는 금지하며 사본 생성으로 개정합니다.
- 초안 삭제는 **휴지통 이동**이며 명세·이력을 유지합니다. 작성자 또는 관리자가 복구할 수 있습니다. 작성자·지정 검토자·관리자만 열람하며, 관리자도 남의 보고서 명세를 직접 변경하거나 승인자를 대신할 수 없습니다.
- 보고서/명세 변경과 승인·감사 기록을 한 트랜잭션으로 처리합니다. 버전 검사와 행 잠금으로 오래된 수정·중복 승인을 거절합니다. 이력은 추가만 가능하며 최근 100건을 화면에 표시합니다. 필드별 이전 값 전체 스냅샷을 보관하는 회계 원장 시스템은 아닙니다.
- 금액은 BigDecimal, 입력 소수 4자리, 계산 HALF_UP 4자리/표시 2자리. 통화별로 조회하며 환율 자동 환산은 하지 않습니다. 상단 금액 요약은 현재 페이지 명세 기준, 운영위험은 최고 점수, 통제는 합산 대상 대비 완료율입니다.

**모든 금액·산식·위험점수는 데모 예시**입니다. 실제 고객 금융자료를 입력하지 마세요. 규제 ECL/VaR, 법규 준수 판정, 실제 결산·세무 처리나 투자 조언을 구현한 것으로 해석하면 안 됩니다. 보고서 간 원장 자동 집계·연결 결산은 제공하지 않으며 입력 명세를 개별 저장합니다. 예시 채우기는 빈 명세에만 적용하고 저장 전까지 DB를 변경하지 않습니다.

## 직접 테스트하는 순서

1. 로컬 dev의 `user`로 로그인 → **기대손실 시뮬레이션** → 보고서 작성. 관리단위를 입력하고 검토자 `manager`를 선택합니다. "빈 명세에 데모 예시"를 누르고 저장하면 노출 10,000 × 부도확률 5% × 손실률 40% = **200.00**을 확인할 수 있습니다.
2. 수정 화면에서 명세를 추가·변경·제거하고 저장하세요. 다른 탭에 열린 예전 버전의 저장은 409로 거절됩니다. 잘못된 확률(예: 101%)은 400으로 거절하며 입력은 유지합니다.
3. 검토 상신 → `manager`로 로그인 → 보고센터 **내 검토 대기** → 의견을 입력하고 반려. `user`가 수정·재상신하고 `manager`가 승인합니다. 승인본은 수정·삭제할 수 없고 사본을 생성할 수 있습니다.
4. 새 초안을 휴지통으로 이동 → 목록의 보관함을 휴지통으로 조회 → 상세에서 복구. 기존 명세와 이력이 유지됩니다. `other`는 해당 보고서 URL로 열람할 수 없습니다.
5. **분개전표**의 차변과 대변을 다르게 저장하면 초안은 유지되지만 상신은 차단됩니다. **고정자산**의 내용연수 0개월, **내부통제**의 완료보다 큰 예외 건수도 저장이 차단됩니다.
6. 390px 모바일 폭에서 메뉴 검색, 영역 펼치기, 명세 표의 내부 가로 스크롤, 행 추가·삭제와 저장을 확인하세요. 명세 표 외 문서 전체가 가로로 넘치지 않도록 구성했습니다.
7. 같은 파일 DB 경로로 서버를 종료·재시작하면 저장한 보고서와 승인 이력이 남습니다. Render 무료 서비스의 임시 디스크는 별개이므로 재배포 후 보존을 보장하지 않습니다.

container-demo에서 활성 계정이 관리자 하나뿐이면 먼저 관리자 화면에서 별도의 활성 매니저를 등록해야 작성/상신을 테스트할 수 있습니다. 기존 비밀 값을 조회하거나 고정 개발 비밀번호를 외부 배포에 사용하지 마세요.

## 기존 DB 업그레이드

기존 테이블·자료를 초기화하지 않습니다. `report-schema.sql`의 세 테이블(financial_reports/report_lines/report_events)과 인덱스만 추가합니다. dev/container-demo에서는 시작 시 `CREATE ... IF NOT EXISTS`로 적용하며 중복 실행과 기존 거래처 보존을 테스트합니다. **운영 JNDI는 자동 DDL을 실행하지 않습니다.** DBA가 백업 후 대상 DB에 맞게 [추가 DDL](src/main/resources/report-schema.sql)을 검토·승인·적용한 뒤 새 WAR를 배포해야 합니다. 실제 운영 DB 변경은 수행하지 않았습니다.

현재 확장 검증은 [REPORTS-VERIFICATION.md](docs/REPORTS-VERIFICATION.md)에 기록합니다. Boot 전환 당시 기록은 기존 검증 문서에 별도 보존합니다.

## PC에서 빠른 실행

JDK 8을 설치하고 JAVA_HOME을 JDK 설치 폴더로 지정합니다. Gradle은 Wrapper가 공식 배포본을 받아 SHA-256을 검사합니다. 첫 빌드에는 Gradle 공식 배포 서버, Plugin Portal, Maven Central 접근이 필요합니다. Maven Central은 라이브러리 저장소 이름이며 Maven 빌드 도구를 설치할 필요는 없습니다.

Linux/macOS:
```bash
export JAVA_HOME=/path/to/jdk8
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew --version
./gradlew -Pdev clean test verifyVersions bootWar
java -Dspring.profiles.active=dev -jar build/libs/workhub-dev-boot.war
```

Windows PowerShell (JDK 경로는 본인 PC의 실제 설치 경로로 변경):
```powershell
$env:JAVA_HOME = 'C:\Java\jdk8'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat --version
.\gradlew.bat -Pdev clean test verifyVersions bootWar
java "-Dspring.profiles.active=dev" -jar build/libs/workhub-dev-boot.war
```

PC에서 `http://127.0.0.1:8080/workhub/login`으로 접속합니다. dev는 loopback에만 바인딩합니다. 종료는 Ctrl-C, 재실행은 같은 폴더에서 마지막 java 명령을 다시 실행합니다. 파일 DB `.data/workhub.mv.db`는 재시작 후 유지됩니다. 필요하면 JVM 옵션 `-Dworkhub.db.url=jdbc:h2:file:/your/path/workhub`로 개발 DB 경로를 지정합니다. 다른 프로세스가 같은 H2 파일을 동시에 열지 않도록 합니다. Windows 명령은 문법 검토했으며 Windows 실기동은 검증하지 않았습니다.

**`-Pdev`는 빌드 시 H2를 포함하는 Gradle 속성입니다. 런타임 dev 프로필은 자동 활성화되지 않으며 `-Dspring.profiles.active=dev`를 별도로 지정해야 합니다.** 프로필을 생략하면 JNDI 운영 설정으로 시작하며 JNDI가 없으면 실패합니다.

**demo-only 로컬 개발 계정**: `admin`(관리자), `manager`(매니저), `user`/`other`(일반). 공통 비밀번호 `DevOnly!2026pass`는 dev의 빈 DB에만 생성합니다. 인터넷 공개용 계정이 아닙니다. 운영은 자동 계정을 만들지 않고, container-demo는 별도 비밀 설정 없이는 시작하지 않습니다.

## 패키징과 외부 서버

```bash
./gradlew clean test verifyVersions war          # 운영 일반 WAR: build/libs/workhub.war
./gradlew -Pdev clean test verifyVersions war bootWar
# 개발 일반 WAR: build/libs/workhub-dev.war
# 개발 실행형 WAR: build/libs/workhub-dev-boot.war
```

운영 일반 WAR에는 H2, Servlet API, 내장 Tomcat/Jasper/ECJ가 없습니다. JEUS에는 이 일반 WAR를 사용합니다. 실행형 bootWar는 내장 컨테이너를 WEB-INF/lib-provided로 분리하며 `java -jar`로 실행합니다. 운영 bootWar도 생성 가능하지만 기본 JNDI는 별도 환경 설정이 필요하므로 운영 JEUS용으로 권하지 않습니다. clean은 이전 build 산출물을 제거합니다.

개발 일반 WAR를 Tomcat 9의 `webapps/workhub.war`로 복사하고 JVM 옵션 `-Dspring.profiles.active=dev`를 지정할 수도 있습니다. Linux/macOS는 JAVA_HOME, CATALINA_HOME을 지정한 후 `./scripts/dev-server.sh run`을 사용합니다. 외부 서버 context는 WAR 이름/서버 설정을 따르고, 실행형 WAR의 기본 context는 `/workhub`입니다.

운영은 `java:comp/env/jdbc/WorkhubDS` JNDI DataSource를 사용합니다. JVM 옵션 `-Dworkhub.jndi=...`로 다른 이름을 지정할 수 있습니다. Boot의 DataSource/SQL 초기화 자동 설정을 제외해 기존 프로필별 DataSource와 seed를 보존했습니다. 운영 스키마와 계정은 자동 생성하지 않습니다. SQL은 H2 2.2.224에서 검증했으며 다른 DB는 DDL·페이징·생성키 문법을 이식해야 합니다. [JEUS 배포 안내](docs/JEUS-DEPLOY.md)를 참고하세요.

## 기존 보조 업무 기능과 권한

- 사용자 등록/수정/비활성화, 부서·거래처·프로젝트·업무·공지 CRUD. 사용자 삭제 대신 비활성화하여 연결 자료를 유지합니다.
- 목록별 이름/제목 검색, SQL 바인딩, 10건 단위 서버 페이징. `%`/`_`는 일반 검색 문자로 처리합니다.
- 전 직원이 공용 프로젝트·업무·거래처·공지를 조회합니다. 프로젝트/업무 수정은 담당자 또는 매니저·관리자에게 허용합니다. 일반 사용자는 자신에게만 배정합니다.
- 거래처·공지 쓰기는 매니저/관리자, 사용자·부서·감사로그는 관리자 권한이 필요합니다. DB에서 매 요청의 활성 여부와 현재 역할을 재확인합니다. 상위 URL 권한은 로그인 당시 역할도 요구하므로 역할 승격 후 다시 로그인해야 합니다.
- 승인요청은 작성자·지정 승인자·관리자만 조회합니다. 관리자는 열람할 수 있지만 지정 승인자가 아니면 처리할 수 없습니다. 자기 승인은 금지합니다.
- 승인 흐름: `DRAFT → PENDING → APPROVED/REJECTED`. 반려 후 작성자가 수정하면 `DRAFT`로 돌아가 다시 상신합니다. 승인/반려 의견은 필수입니다. 대기·완료 요청은 수정/삭제할 수 없습니다.
- 프로젝트 `PLANNED → ACTIVE → DONE`, 업무 `TODO → DOING → DONE`. 역행·단계 건너뛰기는 거절합니다. 버전 검사로 이중 승인과 동시 수정을 차단합니다. 사용자·부서는 버전 잠금 대상이 아니며 마지막 저장이 적용됩니다.
- 트랜잭션 안에서 변경과 감사로그를 함께 저장합니다. 로그는 앱에서 수정/삭제할 수 없지만 DBA 수준의 변조 방지 저장소는 아닙니다.
- BCrypt cost 12, 로그인 시 세션 ID 변경, 30분 세션 만료, HttpOnly, POST 로그아웃, CSRF, CSP/보안 응답 헤더, 서버 입력검증, JSP 출력 이스케이프, 외래키 검사를 적용합니다. 운영 쿠키는 Secure입니다.

## 버전과 보안 지원 한계

Boot BOM 기본 Framework 5.3.31과 Security 5.7.11을 각각 사용자 지정 5.3.34와 기존 5.8.16으로 명시 override합니다. `verifyVersions`가 실제 runtime/test 의존성을 검사하며 결과는 `build/reports/resolved-versions.txt`에 생성합니다. [현재 해석 결과](docs/dependencies.txt)와 [변경·검증 결과](docs/VERIFICATION.md)를 제공합니다.

**Spring Boot 2.x는 2.7.18을 끝으로 OSS 지원이 종료되었고, Framework 5.3/Security 5.8 OSS 지원도 2024-08-31 종료되었습니다.** 요청한 정확한 버전을 유지했으며 임의 업그레이드하지 않았습니다. Framework 5.3.34는 종전 5.3.39보다 이전 버전이고, Boot가 추가하는 전이 의존성에도 보안 검토가 필요합니다. 보안 무결점이나 JEUS 실배포 통과를 주장하지 않습니다. [공식 Boot 안내](https://spring.io/blog/2023/11/23/spring-boot-2-7-18-available-now/) 및 [보안 한계](docs/SECURITY.md)를 확인하세요.

## 소스와 검증

- [20개 화면](docs/SCREENS.md), [검증 결과](docs/VERIFICATION.md), [컨테이너 데모 설정](docs/CONTAINER-DEMO.md).
- `scripts/http-smoke.py`: 실제 로그인·20 JSP·CRUD·권한·승인 흐름. 개발용 가상 DB에만 실행합니다.
- `scripts/browser-check.py`: Chromium 데스크톱/모바일 화면. `scripts/check-bytecode.py`: WAR 전체 Java 8 검사.
- `src/test`: 기존 업무 테스트와 Boot 컨텍스트/필터/프로필/JNDI/fail-closed 회귀 테스트.
- WAR/ZIP/DB/로그/캐시/화면 캡처/환경별 프록시 설정은 소스 저장에서 제외합니다. 공식 Wrapper JAR만 포함합니다.

50개 메뉴 확장은 `codex/workhub-accounting-risk-50` 로컬 브랜치에서 수행합니다. 이번 확장은 원격 push 또는 Render 재배포하지 않으며 기존 배포 버전을 보존합니다.
