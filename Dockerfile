FROM eclipse-temurin:21-jre

WORKDIR /app

ARG JAR_FILE=build/libs/*-SNAPSHOT.jar
COPY ${JAR_FILE} app.jar

EXPOSE 8080

# 작은 인스턴스(RAM ~1.8GB)에서 여러 컨테이너가 동시에 도는 환경이라 swap thrashing 방지를 위해 JVM 메모리 상한을 명시
# - live set 약 47MB 기준 -Xmx192m (여유 4배), SerialGC로 GC 스레드/구조체 오버헤드 축소
# - docker run -e 또는 --env-file 에서 JAVA_TOOL_OPTIONS를 지정하면 덮어쓸 수 있음
ENV JAVA_TOOL_OPTIONS="-Xms64m -Xmx192m -XX:ReservedCodeCacheSize=64m -Xss512k -XX:+UseSerialGC"

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
