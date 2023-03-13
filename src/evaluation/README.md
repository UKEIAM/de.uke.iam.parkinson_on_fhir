# Benchmark

Generally, there are two steps to run the benchmark:

## 1. Build and run the container OUTSIDE the development container

1. Place a file ".env" in your root with the following content:
```
MAVEN_USER=[YOUR USERNAME OF THE NEXUS]
MAVEN_PASSWORD=[YOUR PASSWORD OF THE NEXUS]
HOST_PORT=50202
```
2. Rund docker compose: `docker compose up`

## 2. Run the benchmark from WITHIN the development container