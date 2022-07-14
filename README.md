# Parkinson's on FHIR
A unified API based on FHIR to access accelerometer data obtained against Parkinson's disease. 

## Usage

### For developers
Add your credentials for Max' repository in the [.devcontainer/devcontainer.json](.devcontainer/devcontainer.json) for both `MAVEN_USER` and `MAVEN_PASSWORD`. Afterward, simply open the development container within Visual Studio Code.

### For users
The server should be deployed as a Docker image. To create the container, navigate into this folder and call `docker build -t parkinson_on_fhir:<TAG> --build-arg MAVEN_USER=<YOUR USERNAME FOR NEXUS> --build-arg MAVEN_PASSWORD=<YOUR USERNAME FOR NEXUS> .`. Once build, use `docker run -p 127.0.0.1:<LOCAL PORT like 8081>:8080 parkinson_fhir:<TAG>` to access the server by HTTP locally, for example at port 8081.