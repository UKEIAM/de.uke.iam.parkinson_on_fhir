FROM maven:3.8.6-amazoncorretto-11 AS MarvenWarBuilder

# Copy the source and settings to appropiate locations
COPY pom.xml /parkinson_on_fhir/
COPY src /parkinson_on_fhir/src/
COPY settings.xml $MAVEN_CONFIG/settings.xml

# Define the database. This values are stored in the container, do not redistribute!
ARG POSTGRES_SERVER
ARG POSTGRES_DATABASE
ARG POSTGRES_USER
ARG POSTGRES_PASSWORD

# Define the credential used for the NEXUS
ARG MAVEN_USER
ARG MAVEN_PASSWORD
ENV MAVEN_USER=$MAVEN_USER
ENV MAVEN_PASSWORD=$MAVEN_PASSWORD
ENV POSTGRES_SERVER=$POSTGRES_SERVER
ENV POSTGRES_DATABASE=$POSTGRES_DATABASE
ENV POSTGRES_USER=$POSTGRES_USER
ENV POSTGRES_PASSWORD=$POSTGRES_PASSWORD

WORKDIR /parkinson_on_fhir/

# org.codehaus.mojo:buildnumber-maven-plugin requires git. Install it.
RUN yum install -y git \
  && rm -rf /var/cache/yum/* \
  && yum clean all

RUN mvn package

# Create the container for deployment
FROM tomcat:9.0.64-jdk11-corretto

ARG POSTGRES_SERVER
ARG POSTGRES_DATABASE
ARG POSTGRES_USER
ARG POSTGRES_PASSWORD

COPY --from=MarvenWarBuilder /parkinson_on_fhir/target/parkinson-fhir.war $CATALINA_HOME/webapps/parkinson-fhir.war

# Enable manager
RUN mv /usr/local/tomcat/webapps.dist/host-manager $CATALINA_HOME/webapps/host-manager \
  && mv /usr/local/tomcat/webapps.dist/manager $CATALINA_HOME/webapps/manager
COPY config/usr/local/tomcat/conf/tomcat-users.xml $CATALINA_HOME/conf/tomcat-users.xml
COPY config/usr/local/tomcat/webapps/manager/META-INF/context.xml $CATALINA_HOME/webapps/manager/META-INF/context.xml

# Store the credentials to allow access to the database
RUN printf '%s\n' "de.uke.iam.parkinson_on_fhir.postgres_server=$POSTGRES_SERVER" "de.uke.iam.parkinson_on_fhir.database=$POSTGRES_DATABASE" "de.uke.iam.parkinson_on_fhir.user=$POSTGRES_USER" "de.uke.iam.parkinson_on_fhir.password=$POSTGRES_PASSWORD" >> $CATALINA_HOME/conf/catalina.properties
