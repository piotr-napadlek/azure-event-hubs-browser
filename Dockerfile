FROM maven:3.6.1-jdk-12 as build
WORKDIR /workspace/app

COPY settings.xml .

COPY src src
COPY swagger.yaml pom.xml ./
RUN mvn dependency:go-offline -s settings.xml

RUN mvn clean package -s settings.xml

RUN mkdir -p target/dependency && (cd target/dependency; jar -xf ../*.jar)

FROM openjdk:12.0.1-jdk

VOLUME /tmp

ARG DEPENDENCY=/workspace/app/target/dependency
COPY --from=build ${DEPENDENCY}/BOOT-INF/lib /app/lib
COPY --from=build ${DEPENDENCY}/META-INF /app/META-INF
COPY --from=build ${DEPENDENCY}/BOOT-INF/classes /app
WORKDIR app
ENTRYPOINT ["java", "-cp", "./:lib/*", "io.napadlek.eventhubbrowser.EventhubBrowserApplicationKt"]
