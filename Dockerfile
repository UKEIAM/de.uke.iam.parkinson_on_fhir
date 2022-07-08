FROM maven:3.8-openjdk-17 AS MarvenWarBuilder
COPY pom.xml /parkinson_on_fhir/
COPY src /parkinson_on_fhir/src/
WORKDIR /parkinson_on_fhir/
RUN mvn package

FROM tomcat:9.0.64-jre17
COPY --from=MarvenWarBuilder /parkinson_on_fhir/target/parkinson-fhir.war $CATALINA_HOME/webapps/parkinson-fhir.war