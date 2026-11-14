FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 --create-home app \
    && mkdir -p /app/data \
    && chown app:app /app/data
WORKDIR /app
COPY --chmod=0444 target/mall-1.0.0.jar /app/mall.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java","-XX:MaxRAMPercentage=70","-jar","/app/mall.jar"]
