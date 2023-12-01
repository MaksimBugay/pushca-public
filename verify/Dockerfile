FROM ml0000xdev/ml000x:base-jdk20

ADD target/bmv-pushca-verifier-service.jar /app.jar

HEALTHCHECK --start-period=60s --interval=10s --timeout=5s CMD curl -f http://localhost:8080/actuator/health || exit 1
