## Goal
 Goal of Axon framework is to apply Event Sourcing, [CQRS](https://axoniq.io/resources/cqrs) and DDD concepts.

## Axon server
Axon server is the implementation of the Event store for this demo project.
There are other event store implementations available e.g. MongoStore.
More details [here](https://axoniq.io/blog-overview/eventstore)
To run a local axon server:
`docker run --rm -d --name my-axon-server -p 8024:8024 -p 8124:8124 axoniq/axonserver`

## core-api
This package contains classes describing commands, events and queries.

## Command
Commands are messages that are muting the state of the application entities.
With Axon framework all the command logic is written into Aggregate classes.
An Axon aggregate is a class representing an entity sourced from events.
An Axon aggregate must have:
- an `Aggregate` annotation at the class level to tell Axon to handle the class as an Aggregate. This annotation is defined as a spring stereotype annotation.
- define a field with the `AggregateIdentifier` so that Axon can map commands to the correct Aggregate instance
- a no-arg constructor
- methods with `CommandHandler` annotation. Those methods are responsible for:
  - checking the business logic of a command
  - firing events corresponding to the command thanks to `AggregateLifecycle.apply()` method
- methods with `EventSourcingHandler` annotation. This annotation is not to be confused with the `EventHandler` annotation from the query responsibility.
Those methods are responsible for sourcing the aggregate from the event i.e. updating the state of the aggregate based on the input event.
In particular, the event that will create a new aggregate instance will be handled by setting the field with `AggregateIdentifier` annotation.

The aggregate is managed by Axon framework namely the Axon command bus. It should not be explicitly called from the application code.
Classes defined in `command` package should be package private to enforce segregation with the `query` package.

## query
Queries are messages to request the state of the application entities.
In order to be able to handle the queries two things are needed:
1. have methods with `EventHandler` annotation that react to events. Reacting to events means updating a storage which is a view of the entities.
2. have methods with `QueryHandler` annotation that read data from the storage view.

Those methods are called by Axon framework namely the event and query buses. They should not be explicitly called from the application code.
Classes defined in `query` package should be package private to enforce segregation with the `command` package.


## controller
The controller interacts with Axon through the `CommandGateway` and the `QueryGateway`. Those are two classes that define user-friendle API
to interact with the command and event buses.
The command gateway defines:
- a `send()` method that accepts a command and returns a `CompletableFuture`
- a `send()` method that accepts a command and a callback to be notified when the command has been processed
- a `sendAndWait()` method that accepts a command and returns the result of the command.
The query gateway defines:
- a `query()` method that accepts a query and a response type and returns a `CompletableFuture`. Axon provided an utility method to declare a
response type from a class (`ResponseTypes.instanceOf()`) or to declare a type as an array (`ResponseTypes.multipleInstancesOf()`).

### requests
List carts
``curl -i -X GET http://localhost:8080/foodCart``
Create cart
``curl -i -X POST http://localhost:8080/foodCart/create``
