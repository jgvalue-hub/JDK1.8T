# 회계·리스크 50개 메뉴 확장 검증

2026-10-28, 기준 커밋 `2e344314a2eaaa570321374a557061bd9d2bc261`에서 로컬 브랜치 `codex/workhub-accounting-risk-50`로 구현했습니다. 원격 push·Render 배포·운영 DB 변경·배포 비밀 조회는 하지 않았습니다. 아래 localhost 포트는 이 실행 환경의 검증 주소이며 사용자 접근용 공개 URL이 아닙니다.

## 구현 범위

- 10개 영역, 각 5개로 **정확히 50개 주요 업무 메뉴**. [전체 메뉴·경로·필드](REPORT-MENUS.md). 기존 20개 화면은 보조 메뉴로 보존했습니다. 로그인 기본 도착지는 보고센터 `/reports`이며 기존 `/dashboard`는 유지합니다.
- 원장, 채권, 채무, 자산, 예산, 자금, 시장위험, 신용위험, 운영위험, 내부통제의 **10개 입력·산식·검증 유형**. 각 메뉴는 업무 설명/분류/명세 기준을 갖고 공통 저장·조회 UI와 업무 엔진을 재사용합니다. 각 메뉴가 독립된 50개 계산 엔진이라는 의미는 아닙니다.
- 보고서 헤더와 1~12개 명세를 함께 생성/조회/수정. 명세 추가/변경/제거, 10건 페이징, 기간·통화·상태·분류·담당범위·검색·휴지통 필터. 금액 요약은 현재 페이지 기준이며 서로 다른 통화를 혼합하지 않습니다.
- 작성자/지정 검토자/관리자 열람. 작성자만 편집. 지정된 활성 매니저/관리자만 승인·반려. 자기승인 및 관리자 대리승인 금지. 초안 상신·대기 회수·반려 수정·재상신·승인 보호·원본 보존 사본 생성.
- 초안 삭제는 복구 가능한 휴지통 이동이며 보고서/명세/이력을 유지합니다. 승인본 변경·삭제는 금지합니다. 변경·상태 전이·감사는 트랜잭션과 버전/행 잠금으로 보호합니다. 감사는 행동/의견/버전 이력이며 과거 모든 필드 값을 저장하는 스냅샷 기능은 아닙니다.
- 새 테이블 3개와 인덱스만 추가하는 멱등 DDL. 기존 데이터 초기화 없음. dev/container-demo는 자동 추가, 운영 JNDI는 DBA의 수동 적용이 필요합니다.
- 모든 금액과 계산은 명시적인 **가상 데모 예시**. 규제 ECL/VaR·세무결산·법규 준수 판정·투자 조언은 제공하지 않습니다. 메뉴 이름만으로 해당 회계기준을 완전 구현했다고 해석하면 안 됩니다.

## 고정 버전과 패키징

Java source/target 1.8, 실제 Temurin JDK **1.8.0_472-b08**, Gradle Wrapper **7.6**, Framework **5.3.34**, Boot **2.7.18**, Security **5.8.16**, H2 **2.2.224**를 유지했습니다. `build.gradle`, Wrapper 설정, Dockerfile, 배포 entrypoint/server/context 설정은 기준 커밋에서 변경하지 않았습니다. [전체 resolved 그래프](reports-dependencies.txt).

최종 Java 8 빌드 명령:

```bash
./gradlew --offline --no-daemon clean test verifyVersions war bootWar
./gradlew --offline --no-daemon -Pdev clean test verifyVersions war bootWar
```

오프라인은 이미 공식 경로로 확보된 Gradle/의존성 캐시를 사용했습니다. 새 PC 첫 빌드는 `--offline` 없이 Wrapper로 실행해야 합니다. 환경별 프록시·truststore·캐시는 저장소에 포함하지 않습니다.

[패키징 검사](reports-packaging-results.json): 일반 운영 WAR에는 H2/Servlet API/Tomcat/Jasper/ECJ 없음, 개발 WAR만 H2 포함. 실행형 WAR는 내장 서버를 `WEB-INF/lib-provided`로 격리합니다. [최종 파일 SHA-256/크기](reports-artifacts.json).

Java8 바이트코드는 일반 운영 WAR **10,719개**, 개발 실행형 WAR **15,024개** 기본 클래스가 major≤52로 통과했습니다. Java8에서 선택되지 않는 multi-release/module-info는 제외합니다. [운영 검사](reports-bytecode-production.json), [개발 실행형 검사](reports-bytecode-boot.json).

## 실제 통과한 테스트

1. **운영·개발 clean 빌드 각각 36개, 실패/오류/스킵 0**. 기존 22개 + ReportWorkflowTest 14개. [결과](reports-test-results.json).
   - 50개 메뉴 수·코드 유일성·목록/편집 경로, 50개 유형 실제 저장/수정/휴지통/복구와 10종 계산 결과.
   - 헤더/명세 검증 오류와 저장 롤백, 행 추가/수정/제거, 날짜/확률/금액/건수/월수/차대변 검증, 잘못된 배열·정밀도 거절.
   - 자기 승인/타인 접근/메뉴 간 ID 위조/CSRF/승인본 변경 차단, 활성 검토자 검사, stale version 409, 병렬 승인에서 한 번만 성공.
   - 검색 SQL 바인딩·통화 분리·페이징·범위별 검토 대기, 입력 오류 재표시, redirect에 계정 모델을 쿼리로 붙이지 않음.
   - 기존 스키마에 거래처를 먼저 저장한 후 추가 DDL 두 번 적용 → 기존 내용/버전 보존, 새 테이블만 생성.
   - 기존 Boot 프로필/JNDI/인증 필터/비밀 누락 실패/개발 DB 재사용 거절 테스트도 함께 통과.
2. **실행형 Boot WAR / 내장 Tomcat9.0.123 / Java8 / 127.0.0.1:18083**: 50개 목록·입력·상세 JSP, 50개 보고서와 명세 CRUD/휴지통/복구, 저장형 HTML 이스케이프, 승인/반려/재상신, 사본, 타인403·CSRF403·입력400 통과. [HTTP 결과](reports-http-results.json).
3. **외부 Tomcat9.0.113 / 일반 WAR / Java8 / 127.0.0.1:18084**: 동일한 50개 흐름 및 12행 입력 성공/13행 거절을 검사했습니다. Connector를 `maxParameterCount=100`, `maxPostSize=65536`, `maxThreads=50`으로 설정해 기존 컨테이너 제한과 맞췄습니다. [외부 HTTP 결과](reports-http-external-results.json). 기존 업무의 20화면/승인/CRUD 회귀도 통과했습니다. [기존 기능 결과](reports-legacy-regression.json).
4. **Chromium 실제 UI**: 50개 주요 링크와 10개 그룹, 메뉴 검색·검색결과 없음, 10개 유형에서 예시 명세 채우기/행 추가·제거/저장/계산 결과, 오류 입력 유지. 390×844 모바일에서 보고센터/목록/편집/상세의 문서 가로 넘침 없음, 메뉴 선택과 실제 저장, JS 오류 0. [브라우저 결과](reports-browser-results.json). 화면 캡처는 커밋하거나 첨부하지 않았습니다.
5. **재기동 영속성**: Boot 프로세스 및 외부 Tomcat을 각각 완전히 종료·재시작한 뒤 승인본 제목·결정 의견·버전9·입력 명세·이스케이프 메모 보존. [Boot](reports-persistence-results.json), [외부 Tomcat](reports-persistence-external-results.json).
6. **컨테이너 안전장치**: 기존 시작 스크립트의 부정 검사 8개와 XML 설정 검사 통과. [결과](reports-container-guards.json). 실제 배포 비밀은 읽거나 생성하지 않았습니다.

초기 검증에서 H2의 timestamp 포함 생성키 반환을 명시적인 id 컬럼 요청으로 수정했고, DB 결과의 대소문자 키를 사본 입력 모델에서 정규화했습니다. 브라우저 검증 중 기본 Model이 redirect URL에 붙는 문제를 RedirectAttributes로 수정한 후 최종 검증했습니다.

## 재실행

README의 Java8/Wrapper 실행 절차로 로컬 dev를 실행하고, 테스트용 파일 DB만 사용합니다. 아래 스크립트는 가상 보고서·승인·감사 기록을 실제 저장합니다. 운영 데이터에 실행하지 마세요.

```bash
python3 scripts/reports-http-check.py http://127.0.0.1:8080/workhub
python3 scripts/reports-browser-check.py http://127.0.0.1:8080/workhub
# 같은 DB로 서버를 완전히 종료하고 재시작한 뒤
python3 scripts/reports-persistence-check.py http://127.0.0.1:8080/workhub
python3 scripts/check-container-config.py
```

HTTP 스크립트의 두 번째 인자는 결과 JSON 경로입니다. persistence 스크립트는 두 번째 인자로 그 HTTP JSON, 세 번째 인자로 결과 경로를 받을 수 있습니다. HTTP 검사는 Python 표준 라이브러리, 브라우저 검사는 Playwright와 `/usr/bin/chromium`을 사용합니다. Windows 실행은 이번 환경에서 검증하지 않았습니다.

## 배포 영향과 남은 제한

Render의 기존 `WORKHUB_ENABLE_DEMO`, `WORKHUB_HTTPS_PROXY`, `WORKHUB_DEMO_PASSWORD`, hostname/PORT 설정과 `/workhub/login` health 경로는 동일합니다. JVM `-Xms64m -Xmx256m`도 유지합니다. 50개 메뉴를 각각 별도 프레임워크/웹앱으로 올리지 않으며 명세 12행, 목록 10건, 검토 대기 10건, 표시 이력 100건으로 조회량을 제한했습니다. 로컬 Boot 브라우저/HTTP 검사 후 RSS 약 387MiB가 관찰됐지만 Render 실제 메모리·동시 부하나 장기 안정성을 보증하지 않습니다.

일괄 HTTP 검사는 로컬 파일 DB에만 `DB_CLOSE_DELAY=-1`을 사용해 연결 반복 비용을 줄였습니다. 이 임시 설정의 외부 Tomcat 종료 시 H2 스레드 정리 경고가 있었으며 프로세스 종료를 확인했습니다. 이후 같은 DB에 기본 의미의 `DB_CLOSE_DELAY=0`으로 재기동해 영속성을 확인했습니다. 저장소의 개발/컨테이너 기본 DB 설정은 변경하지 않았고 이 성능 시험 옵션을 배포 안내에 권장하지 않습니다.

이번 확장의 **JEUS8.5 실배포, 운영 JNDI/실DB DDL 적용, 새 Docker 이미지 빌드, Render 배포, 외부 모바일 HTTPS 접속, Windows 실행, 부하/SCA/침투테스트는 미실행**입니다. JEUS 목표의 javax/Java8 일반 WAR 구조는 유지했습니다. 기존 Spring/Boot OSS 지원 종료와 알려진 보안 한계는 SECURITY.md를 따릅니다. 무료 Render 임시 파일시스템의 데이터 손실 가능성은 스키마의 비파괴성으로 해결되지 않습니다.
