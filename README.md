# gridcapa-cse-valid-publication
Application that allows posting asynchronous CSE validation processes for a specific business date

## Functional overview

The process publication server is a basic REST API that allows posting asynchronous processes
creation message on according queue of RabbitMQ broker.

## Environment

This application is only collaborating with RabbitMQ message broker.

## Developer documentation

### Running the application locally

For testing the application locally, it is first needed to start a RabbitMQ server.

The easier solution is to start a Docker container.
```bash
docker run --rm --hostname my-rabbit --name my-rabbit -p 5672:5672 -p15672:15672 rabbitmq:3-management
```
Previous command will start a Docker container running a basic RabbitMQ instance with management UI.

Then, start the server using any IDE.

The server does not embed Swagger client yet. One may run tests using standard tools such as Postman or cURL.

Next command line publishes a process using cURL with explicit target date:
```bash
curl -X POST -d "processType=D2CC&targetDate=2020-11-24" http://localhost:8080/publish
```

Next command line publishes a process using cURL with current local date:
```bash
curl -X POST -d "processType=D2CC&targetDate=$(date --iso-8601)" http://localhost:8080/publish
```
