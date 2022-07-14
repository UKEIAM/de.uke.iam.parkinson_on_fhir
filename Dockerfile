FROM maven:3.8.6-amazoncorretto-11 AS MarvenWarBuilder

# Copy the source and settings to appropiate locations
COPY pom.xml /parkinson_on_fhir/
COPY src /parkinson_on_fhir/src/
COPY settings.xml $MAVEN_CONFIG/settings.xml

# Set the user and password. This might be stored in the container, do not redistribute!
ARG MAVEN_USER
ARG MAVEN_PASSWORD
ENV MAVEN_USER=$MAVEN_USER
ENV MAVEN_PASSWORD=$MAVEN_PASSWORD

WORKDIR /parkinson_on_fhir/

# org.codehaus.mojo:buildnumber-maven-plugin requires git. Install it.
RUN yum install -y git \
  && rm -rf /var/cache/yum/* \
  && yum clean all

RUN mvn package

# Create the container for deployment
FROM tomcat:9.0.64-jdk11-corretto
COPY --from=MarvenWarBuilder /parkinson_on_fhir/target/parkinson-fhir.war $CATALINA_HOME/webapps/parkinson-fhir.war