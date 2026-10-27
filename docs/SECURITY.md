# 의존성 및 보안 검토

검토일: 2026-10-27. Maven Central에서 실제 해석·다운로드하고 JDK 8로 빌드했습니다. 버전은 `pom.xml`에 고정했으며 의존성 전체 목록과 class major 검사는 `bytecode-production.json`, `bytecode-dev.json`, `dependencies.txt`에 보관합니다.

| 구성 | 버전 | 적용 범위 |
|---|---|---|
| Spring Framework | 5.3.39 | MVC, JDBC, 트랜잭션, 컨텍스트; BOM으로 통일 |
| Spring Security | 5.8.16 | 세션 인증, BCrypt, CSRF, 요청 권한, HTTP firewall |
| javax Servlet API | 4.0.1 | provided, WAR에는 제외 |
| JSTL | 1.2 | JSP 출력 이스케이프·반복·조건 |
| H2 | 2.2.224 | 개발 WAR만 runtime; 운영 빌드는 test 전용 |
| JUnit | 4.13.2 | 테스트 전용 |
| Tomcat | 9.0.113 | 별도 개발 검증 서버, WAR 미포함 |
| Temurin JDK | 8u472-b08 | 직접 컴파일·실행 검증; 최신 패치 보증 아님 |

## 공식 보안 공지와 이 앱의 대응

1. [Spring 5.3 / Security 5.8 OSS 지원 종료](https://spring.io/blog/2024/03/01/support-timeline-announcement-for-spring-framework-6-0-x-and-5-3-x/): 2024-08-31 종료. Java 8 호환 공개 버전을 고정한 시제품이며 운영에는 유지보수 계약/지원 패치와 지속적인 취약점 검토가 필요합니다.
2. [CVE-2024-38816](https://spring.io/security/cve-2024-38816/): Framework 5.3.39 이하의 함수형 파일 리소스 경로 순회. 이 앱은 RouterFunctions/FileSystemResource를 사용하지 않고 Spring Security firewall을 유지합니다. 공식 수정 버전은 5.3.40(상용 계열)이며 해당 라이브러리 자체를 패치한 것은 아닙니다.
3. [CVE-2025-41242](https://spring.io/security/cve-2025-41242/): 비준수 Servlet 컨테이너와 Spring 정적 리소스 처리 조합. 이 앱은 ResourceHandler 등록 없이 고정 `/assets/app.css` 컨트롤러에서 고정 classpath 파일만 제공합니다. JEUS의 의심 경로 처리까지 검증한 것은 아닙니다. 공식 5.3 수정 버전은 5.3.44(상용 계열)입니다.
4. [CVE-2024-38827](https://spring.io/security/cve-2024-38827/): 로캘 기반 대소문자 권한 우회. 고정한 Security 5.8.16은 공식 수정 목록에 포함됩니다.
5. [CVE-2025-22228](https://spring.io/security/cve-2025-22228/): Security 5.8.0~5.8.17 BCrypt 입력 상한 문제. 등록/변경 비밀번호는 UTF-8 72바이트 이내로 검증하고, 로그인·현재 비밀번호 확인에서도 `BoundedBCryptPasswordEncoder`가 72바이트 초과 입력을 먼저 거절합니다. 72바이트 경계와 접미사 거절 회귀 테스트를 추가했습니다. 이것은 애플리케이션 경계 완화이며 공급자 공식 수정 버전 5.8.18(상용) 적용을 대신하는 보증이 아닙니다.

이는 대표 공식 공지를 바탕으로 한 범위 검토입니다. 전이 의존성 전체에 대한 최신 CVE DB 자동 SCA를 수행하거나 취약점이 없음을 입증하지 않았습니다. Spring 5.3.39/5.8.16에는 이후 공개된 다른 취약점이 남을 수 있습니다. 공개 운영 전에 승인된 보안 유지보수 버전으로 교체하고 SBOM/SCA 및 침투테스트를 수행해야 합니다.

## 구현 보안 경계

- 사용자 데이터는 바인딩된 SQL 값으로만 전달합니다. 동적 테이블 이름은 서버 고정 allowlist에 제한합니다. 자유 SQL·정렬식 입력은 받지 않습니다.
- 서버가 현재 사용자 ID/역할을 DB에서 구하고 모든 변경·승인·레코드 읽기를 검사합니다. 폼의 역할·owner 값을 신뢰하지 않습니다. 등록된 사용자 역할은 ADMIN/MANAGER/USER만 허용합니다.
- CSRF가 적용된 POST만 자료를 변경합니다. 로그인·로그아웃도 POST+CSRF입니다. 로그인 성공 시 세션 ID를 변경하며 쿠키 기반 세션만 허용합니다.
- 업무/프로젝트는 사내 공용 조회 모델입니다. 부서별 열람 격리는 구현하지 않았습니다. 승인요청은 개인 레코드 권한을 적용합니다.
- 상태 변경·레코드 저장·감사는 DB 트랜잭션을 사용합니다. 프로젝트/업무/거래처/공지/승인에는 버전 충돌 검사를 적용합니다. 부서/사용자 관리의 동시 편집은 마지막 저장이 적용됩니다.
- JSP에서 사용자 입력을 `c:out`으로 출력하며, HTML 입력을 허용하지 않습니다. CSP는 인라인 스크립트·외부 자원을 허용하지 않습니다.
- dev 외에서는 Secure 쿠키를 강제합니다. HTTPS, SameSite 정책, 프록시 신뢰 설정, 계정 공격 속도 제한은 배포 서버/프록시 설정 항목입니다. 앱에는 MFA/SSO나 계정 잠금 기능이 없습니다.
- 개발용 H2 콘솔이나 원격 DB 관리 엔드포인트는 노출하지 않습니다. 개발 시 파일 DB와 개발 계정이 있는 localhost에서만 실행합니다.
- 운영은 JNDI만 사용하고 스키마·계정을 자동 생성하지 않습니다. JNDI 구성 회귀 테스트에서 기본 운영 프로필의 샘플 계정 미생성을 확인했습니다.
