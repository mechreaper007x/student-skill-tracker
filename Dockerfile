# Build stage
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /app
COPY . .
RUN chmod +x mvnw
RUN ./mvnw clean package -DskipTests

# Run stage
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
# Copy the JAR file from the build stage
COPY --from=build /app/target/*.jar app.jar

# Expose the port
EXPOSE 8080

# Enable Serial GC for maximum memory efficiency on 512MB RAM
# Reduced Xmx to 300m to leave 212MB for Metaspace, Stacks, Code Cache, and Native Memory
ENV JAVA_OPTS="-XX:+UseSerialGC -Xmx300m -XX:MaxMetaspaceSize=150m -XX:ReservedCodeCacheSize=64m -Xss512k"

# Run the application with JAVA_OPTS
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
