# LSP (Language Server Protocol) Project

This is a Spring Boot project that implements a Language Server Protocol backend. It uses WebSockets for communication and is containerized using Docker.

## Prerequisites

Before you begin, ensure you have the following installed:

*   **Java 17:** The project is built using Java 17.
*   **Maven:** Used for building the project and managing dependencies.
*   **Docker:** Required for running the application in a containerized environment.
*   **Docker Compose:** To orchestrate the multi-container application.

## Setup and Execution

There are two ways to run this project: locally using Maven or as a containerized application using Docker.

### 1. Running with Docker (Recommended)

This is the easiest way to get the application running, as it includes the PostgreSQL database setup.

1.  **Build and run the containers:**

    Open a terminal in the root of the project and run the following command:

    ```bash
    docker-compose up --build
    ```

    This command will:
    *   Build the Docker image for the Spring Boot application.
    *   Start the Spring Boot application container.
    *   Start the PostgreSQL database container.

2.  **Access the application:**

    Once the containers are up and running, the application will be accessible at `http://localhost:8080`.

### 2. Running Locally with Maven

If you prefer to run the application locally without Docker, follow these steps:

1.  **Start a PostgreSQL Database:**

    You need to have a PostgreSQL database running. You can use Docker to easily start one:

    ```bash
    docker run --name my_postgres_db -e POSTGRES_DB=mydatabase -e POSTGRES_USER=myuser -e POSTGRES_PASSWORD=secret -p 5432:5432 -d postgres:15
    ```

2.  **Configure the application:**

    Make sure your `src/main/resources/application.properties` file has the correct database connection details. They should match the database you started in the previous step.

    ```properties
    spring.datasource.url=jdbc:postgresql://localhost:5432/mydatabase
    spring.datasource.username=myuser
    spring.datasource.password=secret
    ```

3.  **Build and run the application:**

    Open a terminal in the root of the project and run the following command:

    ```bash
    ./mvnw spring-boot:run
    ```

    The application will start and be accessible at `http://localhost:8080`.

## Project Structure

*   `src/main/java`: Contains the main source code for the Spring Boot application.
*   `pom.xml`: The Maven project configuration file.
*   `Dockerfile`: Defines the Docker image for the application.
*   `compose.yaml`: The Docker Compose file for orchestrating the application and database containers.
*   `entrypoint.sh`: The entrypoint script for the Docker container.
