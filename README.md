# Parkinson's on FHIR
A unified API based on FHIR to access accelerometer data obtained against Parkinson's disease. 

## Usage

In any case, you need to have access the NEXUS for Maven packages run by the Institute. Please get your credentials by Max if you do not have them.
Afterward, please continue with one of the following steps.

### For developers
The style of development differs whether you need persistent data or not.

#### Persistent database
At the first step, you need to create a connection from the host to the PostgreSQL database used as persistent data storage. This can be archived by a SSH tunnel you may open with something like `ssh -g -f -N -M -S ~/ssh_socket -L <LOCAL PORT LIKE 50201>:localhost:<PORT OF POSTGRES LIKE 5432> <YOUR USERNAME AT THE POSTGRESQL SERVER>@192.168.111.23`. While running in background, this tunnel can be closed by `ssh -S ~/ssh_socket -O exit gundler@192.168.111.23`.

Add your credentials in the [.devcontainer/devcontainer.json](.devcontainer/devcontainer.json) for both `MAVEN_USER` and `MAVEN_PASSWORD`. Additionally, fill all the environment variables required for deriving the database scheme and building the package. Afterward, simply open the development container within Visual Studio Code.

#### Temporary database
Create a .env file looking like the following:

```
MAVEN_USER=<THE USERNAME ON THE NEXUS>
MAVEN_PASSWORD=<THE PASSWORD ON THE NEXUS>
HOST_PORT=<THE PORT WHERE YOU WANT TO ACCESS THE HTTP SERVER>
```

Afterward, simply run `docker-compose up --build` (the `--build` is only required if you changed the codebase). The server can be terminated by typing "CTRL+C". However, the content of the database does not change. Use `docker-compose down` to reset the databse, too. If you want to access the REST interface on a remote host i. e. using `localhost:8080/parkinson-fhir/`, start a session locally with a command like `ssh <YOUR USERNAME>@iam-docker -N -L 8080:127.0.0.1:<THE PORT WHERE YOU WANT TO ACCESS THE HTTP SERVER>`.

### For users
The server should be deployed as a Docker image. To create the container, navigate into this folder and call `docker build -t parkinson_on_fhir:<TAG> --build-arg MAVEN_USER=<YOUR USERNAME FOR NEXUS> --build-arg MAVEN_PASSWORD=<YOUR USERNAME FOR NEXUS> --build-arg POSTGRES_SERVER=<...> --build-arg POSTGRES_DATABASE=<...> --build-arg POSTGRES_USER=<...> --build-arg POSTGRES_PASSWORD=<...> .`. Once build, use `docker run -p 127.0.0.1:<LOCAL PORT like 50202>:8080 parkinson_fhir:<TAG>` to access the server by HTTP locally, for example at port 50202. If the server is running at a foreign host, you may call want to call `ssh <YOUR USERNAME>@iam-docker -N -L <THE LOCAL PORT LIKE 8080>:127.0.0.1:<CHOOSEN PORT LIKE 50202>`. Typing in `http://localhost:8080/parkinson-fhir/` will than lead to the website.