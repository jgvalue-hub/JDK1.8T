#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
: "${JAVA_HOME:?JDK 8 JAVA_HOME를 지정하세요}"
: "${CATALINA_HOME:?Tomcat 9 설치 경로를 지정하세요}"
command=${1:-run}
if [[ "$command" == stop ]]; then exec "$CATALINA_HOME/bin/catalina.sh" stop; fi
if [[ ! -f build/libs/workhub-dev.war ]]; then echo '먼저 ./gradlew -Pdev clean test war 로 개발 WAR를 빌드하세요.' >&2; exit 1; fi
mkdir -p .data
cp build/libs/workhub-dev.war "$CATALINA_HOME/webapps/workhub.war"
export CATALINA_OPTS="${CATALINA_OPTS:-} -Dspring.profiles.active=dev -Dworkhub.db.url=jdbc:h2:file:$PWD/.data/workhub"
exec "$CATALINA_HOME/bin/catalina.sh" "$command"
