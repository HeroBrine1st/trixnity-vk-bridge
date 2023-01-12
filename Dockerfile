FROM eclipse-temurin:17 AS builder

ENV GRADLE_OPTS="-Dorg.gradle.daemon=false -Dorg.gradle.vfs.watch=false"

WORKDIR /home/gradle/trixnity-bridge
COPY settings.gradle.kts build.gradle.kts gradle.properties gradlew ./
COPY gradle ./gradle/

COPY vk-bridge/build.gradle.kts vk-bridge/
COPY vk-bridge/vk-worker/build.gradle.kts vk-bridge/vk-worker/
COPY vk-library/build.gradle.kts vk-library/

# Dependencies
RUN ./gradlew classes

COPY . .

RUN ./gradlew :vk-bridge:buildFatJar

FROM eclipse-temurin:17-jre AS runner
WORKDIR /app
COPY --from=builder /home/gradle/trixnity-bridge/vk-bridge/build/libs/vk-bridge-all.jar /app/server.jar
COPY docker-entrypoint.sh /docker-entrypoint.sh

ENTRYPOINT ["/docker-entrypoint.sh"]
