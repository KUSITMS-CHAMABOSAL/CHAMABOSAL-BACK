# 빌드 스테이지: 의존성 레이어를 먼저 받아 소스만 바뀌었을 때 캐시를 재사용한다.
FROM eclipse-temurin:21-jdk AS builder
WORKDIR /app

COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN ./gradlew dependencies --no-daemon --quiet

COPY src src
# 테스트는 PR CI에서 수행하므로 이미지 빌드에서는 생략한다.
RUN ./gradlew bootJar --no-daemon -x test

# 실행 스테이지: JRE만 포함해 이미지 크기를 줄인다.
FROM eclipse-temurin:21-jre
WORKDIR /app

RUN groupadd --system spring && useradd --system --gid spring spring
COPY --from=builder /app/build/libs/app.jar app.jar
USER spring

ENV TZ=Asia/Seoul
# 컨테이너 메모리 기준으로 힙을 잡는다. 실행 시 JAVA_TOOL_OPTIONS로 덮어쓸 수 있다.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
