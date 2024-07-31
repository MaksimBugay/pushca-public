FROM eclipse-temurin:22.0.2_9-jdk-alpine

#install curl and remove apk indexes
RUN apk update && apk add curl \
    && rm -rf /var/cache/apk

#use default jvm value, that can be overridden from compose file during runtime(enables passing debug args)
ENV JVM_ARGS=-Xverify:remote

ENTRYPOINT java \
${JVM_ARGS} \
-Djava.security.egd=file:/dev/./urandom \
-server \
-XX:+HeapDumpOnOutOfMemoryError \
-XX:-OmitStackTraceInFastThrow \
-XX:HeapDumpPath=/var/log/multilogin/heap_dump_${APP_NAME:default}.hprof \
-jar /app.jar

EXPOSE 8080

ADD target/binary-proxy-0.0.1-SNAPSHOT.jar /app.jar

HEALTHCHECK --start-period=60s --interval=10s --timeout=5s CMD curl -f http://localhost:8080/actuator/health || exit 1
