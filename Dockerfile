# --- Stage 1: Builder ---
# (This stage is fine, no changes needed)
FROM maven:3.9-eclipse-temurin-17 AS builder
RUN apt-get update && apt-get install -y npm curl tar
RUN npm install -g intelephense
RUN mkdir -p /opt/jdt-ls
RUN curl -L "https://download.eclipse.org/jdtls/snapshots/jdt-language-server-latest.tar.gz" \
    | tar -xz -C /opt/jdt-ls
COPY . .
RUN mvn clean package -DskipTests


# --- Stage 2: Final Image ---
FROM eclipse-temurin:21

# Install Node.js
RUN apt-get update && \
    apt-get install -y curl && \
    curl -fsSL https://deb.nodesource.com/setup_20.x | bash - && \
    apt-get install -y nodejs && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

RUN npm install -g intelephense

# ✅ Create and set full permissions on the workspace directory
RUN mkdir -p /opt/lsp-workspace && \
    chmod -R 777 /opt/lsp-workspace

COPY --from=builder /opt/jdt-ls /opt/jdt-ls
COPY --from=builder /target/*.jar app.jar

# ✅ Set executable permissions for the language server itself
RUN chmod -R +x /opt/jdt-ls

COPY entrypoint.sh .
RUN chmod +x entrypoint.sh

EXPOSE 8080
ENTRYPOINT ["./entrypoint.sh"]
CMD ["java", "-jar", "app.jar"]