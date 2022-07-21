# Parkinson's on FHIR
A unified API based on FHIR to access accelerometer data obtained against Parkinson's disease. 

## Usage

### For developers
At the first step, you need to create a connection from the host to the PostgreSQL database. This can be archived by a SSH tunnel you may open with something like `ssh -g -f -N -M -S ~/ssh_socket -L <LOCAL PORT LIKE 50201>:localhost:<PORT OF POSTGRES LIKE 5432> <YOUR USERNAME AT THE POSTGRESQL SERVER>@192.168.111.23`. While running in background, this tunnel can be closed by `ssh -S ~/ssh_socket -O exit gundler@192.168.111.23`.

Add your credentials for Max' repository in the [.devcontainer/devcontainer.json](.devcontainer/devcontainer.json) for both `MAVEN_USER` and `MAVEN_PASSWORD`. Additionally, fill all the environment variables required for deriving the database scheme and building the package. Afterward, simply open the development container within Visual Studio Code.

### For users
The server should be deployed as a Docker image. To create the container, navigate into this folder and call `docker build -t parkinson_on_fhir:<TAG> --build-arg MAVEN_USER=<YOUR USERNAME FOR NEXUS> --build-arg MAVEN_PASSWORD=<YOUR USERNAME FOR NEXUS> --build-arg POSTGRES_SERVER=<...> --build-arg POSTGRES_DATABASE=<...> --build-arg POSTGRES_USER=<...> --build-arg POSTGRES_PASSWORD=<...> .`. Once build, use `docker run -p 127.0.0.1:<LOCAL PORT like 8081>:8080 parkinson_fhir:<TAG>` to access the server by HTTP locally, for example at port 8081.