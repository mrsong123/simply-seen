FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 --create-home app \
    && mkdir -p /app/data \
    && chown app:app /app/data
WORKDIR /app
COPY --chmod=0444 target/mall-1.0.0.jar /app/mall.jar
USER app
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=5s --start-period=30s --retries=6 CMD ["java","-Xms8m","-Xmx32m","-Dloader.main=com.sujian.mall.config.HealthProbe","-cp","/app/mall.jar","org.springframework.boot.loader.launch.PropertiesLauncher"]
ENTRYPOINT ["java","-XX:MaxRAMPercentage=70","-jar","/app/mall.jar"]
