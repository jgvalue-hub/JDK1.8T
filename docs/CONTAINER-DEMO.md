# 모바일 확인용 컨테이너 배포 구성

현재 Codex 실행 환경에는 사용자에게 발급할 수 있는 포트 프리뷰/포워딩 도구나 인증된 프리뷰 URL이 없습니다. Docker 빌드 도구가 있다는 사실이 모바일 접속 주소를 제공한다는 의미는 아닙니다. 기존 버전의 외부 테스트 서비스와 이번 재구성은 별개입니다. 이번 Gradle/Boot 재구성은 로컬 브랜치에서만 수행하고 원격 push·재배포하지 않습니다. 서비스·계정·비밀번호·유료 약정·네트워크 설정은 변경하지 않습니다.

## 이미지와 프로필

```bash
docker build -t workhub-container-demo:review .
```

- 빌드: Gradle 7.6 Wrapper / Temurin Java 8. `./gradlew --no-daemon -Pdev clean test verifyVersions war`로 테스트하고 H2가 포함된 일반 WAR를 만듭니다. 빌드 단계도 고정 Java 8/Tomcat 이미지의 JDK를 사용하며 Maven은 필요하지 않습니다. 빌드 단계의 Tomcat은 실행하지 않습니다.
- 실행: Tomcat 9.0.123 / Temurin JDK 8. UID/GID 10001 비관리자로 실행합니다. 관리자/예제 웹앱과 shutdown 포트는 제공하지 않습니다.
- 런타임 프로필은 `container-demo`로 고정합니다. `-Pdev`는 빌드 의존성 선택이며 로컬 `dev` 실행 프로필을 활성화하지 않습니다.
- `.dockerignore`는 build.gradle/settings.gradle, 공식 Wrapper, src, 세 개의 실행 설정만 빌드 컨텍스트에 허용합니다. DB, Git, 이미지, 캐시, 로그, .env와 비밀키는 포함하지 않습니다.
- 기존 `dev`는 PC 로컬 테스트용 그대로입니다. 기본 운영 JNDI 프로필도 유지됩니다. 이 이미지는 실사용 운영용이 아니라 별도 승인된 테스트용입니다.
- 베이스 이미지는 실제 조회한 manifest digest까지 고정했습니다. 실제 배포 전 조직이 승인한 패치 버전과 digest인지 재검토하고 필요하면 BUILD_IMAGE/RUNTIME_IMAGE 빌드 인자로 갱신한 뒤 SCA/이미지 검사를 수행하세요. Spring 지원 종료 한계는 SECURITY.md에 그대로 적용됩니다.

## 필수 실행 설정

| 변수 | 값/역할 |
|---|---|
| `WORKHUB_ENABLE_DEMO` | 명시적으로 `true`. 없으면 컨테이너 시작 거절 |
| `WORKHUB_HTTPS_PROXY` | 실제 신뢰 가능한 HTTPS 프록시를 준비한 후에만 `true` |
| `WORKHUB_PUBLIC_HOST` | 승인된 HTTPS 호스트명만 입력. 스킴/경로/포트 제외. Render에서는 플랫폼의 `RENDER_EXTERNAL_HOSTNAME`을 자동 사용 가능 |
| `WORKHUB_DEMO_PASSWORD` | 별도 승인 후 호스팅 비밀관리 화면에 설정. ASCII 24~72자, 허용 문자 `A-Z a-z 0-9 @ % + = : , . _ -`. 채팅이나 저장소에 넣지 않음 |
| `PORT` | 호스팅이 제공하는 내부 포트. 기본 10000, 허용 1024~65535 |
| `WORKHUB_DATA_DIR` | 기본 `/var/lib/workhub`. UID 10001이 쓸 수 있는 절대 경로 |

코드나 Dockerfile에는 배포용 비밀번호가 없습니다. 필수 설정이 빠지면 시작을 거절합니다. 새 DB에서는 `admin`만 활성화되며, 비밀번호는 호스팅 비밀 설정에서 받아 BCrypt로 저장합니다. `manager/user/other`는 가상 자료 참조를 위해 생성하지만 비활성 상태입니다. 필요하면 관리자가 **각각 다른 비밀번호로 변경한 뒤** 활성화하세요. 로컬 고정 비밀번호가 들어 있는 기존 dev DB는 재사용을 거절합니다.

앱은 최초 생성 시에만 비밀번호를 적용합니다. 기존 DB에서 환경변수만 변경하면 계정 비밀번호가 바뀌지 않습니다. 변경은 로그인 후 내 정보/관리 화면에서 수행하고, 분실 시 별도 승인된 DB 관리 절차가 필요합니다. 이 작업에서는 실제 사용자 자격증명을 생성하거나 저장하지 않았습니다. 테스트 소스의 문자열은 단위 테스트용 임시 fixture입니다.

## HTTPS 프록시와 네트워크 경계

Tomcat은 컨테이너 내부 `0.0.0.0:$PORT`에서 HTTP를 받습니다. Connector의 `scheme=https`, `secure=true`, `proxyPort=443`, 고정 `proxyName`은 신뢰 가능한 HTTPS edge 뒤에서만 유효합니다. 덕분에 로그인 리다이렉트는 승인된 HTTPS 호스트를 사용하고 세션 쿠키는 Secure/HttpOnly/SameSite=Lax입니다.

컨테이너 내부 포트를 인터넷에 직접 publish하지 마세요. 이 설정은 자체 TLS 서버가 아니며 `WORKHUB_HTTPS_PROXY=true` 문자열 자체가 TLS를 제공하지 않습니다. 호스트에 배포할 경우 내부 포트를 비공개 네트워크 또는 loopback으로 제한하고 인증된 프록시/VPN 접근을 구성해야 합니다. 외부 사용자에게 주소를 전달하기 전에는 HTTPS 인증서·프록시 접근 통제·로그인 속도 제한·실제 쿠키와 리다이렉트를 검증해야 합니다. 이번 작업에서는 host port를 공개하거나 네트워크 보안을 변경하지 않았습니다.

## 승인된 Render 무료 테스트 설정안

기존 서비스 설정과 이번 변경의 반영 여부는 별도로 확인해야 합니다. Render의 Docker Web Service는 Codex 자체 프리뷰와 별개의 **외부 공개 호스팅**입니다. 사용자는 무료 외부 테스트 배포를 승인했습니다. 실제 비밀번호는 사용자가 호스팅 비밀관리 화면에서 설정합니다. 유료 플랜으로 변경하거나 자동 배포 범위를 임의 확대하지 않습니다.

설정값:

- Runtime: Docker, Dockerfile: `./Dockerfile`, 실행 명령 override 없음.
- 기존 배포 브랜치: `codex/workhub-java8-jeus85`. 이번 변경 브랜치 `codex/workhub-boot2718-gradle76`는 로컬 전용이며 승인 없이 push/배포하지 않습니다. Dockerfile 경로와 컨텍스트는 저장소 루트입니다.
- `WORKHUB_ENABLE_DEMO=true`, `WORKHUB_HTTPS_PROXY=true`.
- `WORKHUB_DEMO_PASSWORD`는 사용자가 비밀관리 화면에서 별도 안전하게 설정합니다. 값을 채팅으로 요청하지 않습니다.
- Render의 `PORT`와 `RENDER_EXTERNAL_HOSTNAME`을 사용합니다. Health check는 `/workhub/login`.
- 접속 경로는 플랫폼이 실제 발급한 `https://<실제 호스트>/workhub/login`입니다. 기존 서비스가 있더라도 이 브랜치의 검증 완료가 해당 서비스 재배포를 뜻하지 않습니다.

무료 Web Service는 파일시스템이 임시 저장소입니다. 재배포/재시작/유휴 중단 시 H2 파일과 입력한 자료가 사라질 수 있고, 다시 시작할 때 가상 데이터가 재생성될 수 있습니다. 무료 서비스에는 persistent disk를 붙일 수 없습니다. 따라서 **버려도 되는 가상 테스트 데이터만** 사용하고 영속성을 보장하지 않습니다. 유휴 중단 후 첫 접속 지연도 예상해야 합니다.

자료 보존이 필요하면 별도 승인된 영속 디스크를 `/var/lib/workhub`에 마운트하고 UID 10001 권한을 부여하거나 운영 JNDI/외부 DB 구성을 별도로 검토하세요. Render Postgres를 선택하는 것만으로 현재 H2 SQL/스키마가 자동 이식되지는 않습니다. H2 파일은 복제본 여러 개가 공유하면 안 되므로 단일 인스턴스 테스트만 지원합니다.

공식 근거:
- [Render 포트 바인딩과 Web Service](https://render.com/docs/web-services#port-binding)
- [무료 인스턴스의 임시 파일시스템·유휴 중단·제한](https://render.com/docs/free)
- [Tomcat 공식 이미지 목록](https://github.com/docker-library/official-images/blob/master/library/tomcat)

## 실제 배포 단계에서 확인할 설정

1. 사용할 서비스/기존 테스트 서버, 연결 계정, 무료 플랜 및 사용량·비용 조건.
2. 인터넷 공개 로그인 페이지를 허용할지, 비공개 프록시/VPN 등 추가 접근 통제가 필요한지.
3. 비밀관리 화면의 데모 관리자 비밀번호 설정과 담당자. 비밀번호를 채팅에 전달하지 않음.
4. 일시적인 가상 DB로 충분한지, 별도 영속 저장소가 필요한지.
5. 실제 배포된 커밋과 무료 플랜 확인. 유료 업그레이드 금지.

검증 결과는 별도 `docs/container-verification.json`에 기록합니다. 이미지 빌드 성공은 외부 HTTPS edge나 모바일 브라우저에서 접속 성공을 의미하지 않습니다.

## 재구성 검증 범위

Java 8에서 Gradle Wrapper로 개발·운영 패키징과 테스트를 실행했습니다. 외부 Tomcat 9.0.113과 실행형 Boot WAR의 내장 Tomcat 9.0.123에서 20화면 및 인증·승인·CRUD를 검사합니다. 최종 결과는 [VERIFICATION.md](VERIFICATION.md) 및 `container-verification.json`에 기록합니다.

일반 Docker 빌드는 이 실행 환경의 컨테이너 DNS에서 `services.gradle.org`를 해석하지 못해 Wrapper 다운로드 단계에서 실패했습니다. 네트워크 제한을 변경하거나 외부 터널을 사용하지 않았습니다. 독립 검증으로 호스트에서 이미 공식 경로로 받은 Wrapper/Gradle 의존성 캐시만 임시 컨텍스트에 넣고, `--offline` 및 Docker `--network none`으로 동등한 빌드를 검사합니다. 임시 컨텍스트와 환경별 프록시 설정은 저장소에 포함하지 않습니다. 이 검사는 기본 Dockerfile의 온라인 다운로드 성공을 증명하지 않습니다.

배포용 비밀번호를 생성·읽기·저장하지 않았습니다. 테스트 소스에는 명시적인 비배포용 fixture만 있습니다. 실제 HTTPS edge, Render 새 배포, 배포된 비밀번호로 로그인, 외부 모바일 접속은 이번 재구성의 검증 범위 밖입니다.

런타임 JVM 옵션은 `-Xms64m -Xmx256m`이며 Tomcat 요청 스레드 상한은 50입니다. heap 외 메모리도 사용하므로 512 MiB급 무료 인스턴스에서 실제 사용량/OOM을 확인해야 합니다. 장시간 부하와 외부 무료 인스턴스 메모리는 아직 검증하지 않았으며 자동 유료 업그레이드는 하지 않습니다. JVM 옵션·실행 명령 override는 필요하지 않습니다.
