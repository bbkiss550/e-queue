FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /build

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -ntp -DskipTests dependency:go-offline

COPY src/ src/
RUN ./mvnw -B -ntp -DskipTests package

FROM eclipse-temurin:21-jre-jammy AS runtime
WORKDIR /app
RUN groupadd --system app && useradd --system --gid app --home-dir /app app
COPY --from=build --chown=app:app /build/target/e-queue-1.0.0.jar /app/app.jar

ENV SPRING_PROFILES_ACTIVE=uat
ENV PORT=10000
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70.0 -Duser.timezone=Asia/Bangkok"
EXPOSE 10000
USER app
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
