FROM gradle:8.14-jdk21 AS builder
WORKDIR /app

# build-time args (opcjonalnie, głównie do debug/CI)
ARG SPRING_AI_GOOGLE_GENAI_API_KEY

ENV SPRING_AI_GOOGLE_GENAI_API_KEY=${SPRING_AI_GOOGLE_GENAI_API_KEY}

COPY . .
RUN gradle clean bootJar --no-daemon

# ---------- runtime stage ----------
FROM eclipse-temurin:25-jre
WORKDIR /app

# runtime env (PRZENOSIMY Z BUILD STAGE)
ENV SPRING_AI_GOOGLE_GENAI_API_KEY=${SPRING_AI_GOOGLE_GENAI_API_KEY}

COPY --from=builder /app/build/libs/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]