# Stage 1: Runtime
FROM eclipse-temurin:25-jre-alpine

LABEL maintainer="AI Session Vault Team"
LABEL description="Production Docker image for AI Session Vault MCP Server"

# Install curl for healthcheck and security updates
RUN apk update && apk add --no-cache curl tzdata && rm -rf /var/cache/apk/*

# Create non-root system group and user
RUN addgroup -S -g 10001 vault && \
    adduser -S -u 10001 -G vault -h /app -s /sbin/nologin vault

WORKDIR /app

# Create storage directory for attachments
RUN mkdir -p /vault-storage/attachments /app/logs && \
    chown -R vault:vault /vault-storage /app

# Copy application jar
ARG JAR_FILE=target/ai-session-vault-0.0.1-SNAPSHOT.jar
COPY ${JAR_FILE} /app/app.jar

RUN chown vault:vault /app/app.jar

USER vault:vault

EXPOSE 8080

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/./urandom"

HEALTHCHECK --interval=15s --timeout=5s --start-period=30s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
