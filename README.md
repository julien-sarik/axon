## Concepts
Goal of Axon framework is to apply Event Sourcing, [CQRS](https://axoniq.io/resources/cqrs) and DDD concepts.
### event sourcing
[Event Sourcing](https://axoniq.io/resources/event-sourcing) is a pattern for data storage, where instead of storing 
the current state of any entity, all past changes to that state are stored.
This allows:
- audit
- ability to build new views at any moment by replaying past events
#### snapshot
In case it's too long to build the latest state of entities based on all past events, Axon provide a [snapshotting]() feature 
to allow recovering the latest state on an entity based on a snapshot and events that happened after the snapshot was taken.
#### Replay events
Axon provides a way to replay events.
#### Upcasting
Axon provides a way to nicely update the schema of an event while preserving the replay capability.
### CQRS
[CQRS](https://axoniq.io/resources/cqrs) is an architectural pattern which prescribes the decoupling of the command logic and the query logic.
This allows to scale/store the query component independently from the command component.
Query component get synchronized through events triggered by the command component.

## Axon server
Axon server is the implementation of the Event store for this demo project. It also implement the message (command, event, query) routing solution.
There are other event store implementations available e.g. MongoStore.
More details [here](https://axoniq.io/blog-overview/eventstore)
To run a local axon server:
`docker run --rm -d --name my-axon-server -p 8024:8024 -p 8124:8124 axoniq/axonserver`

## mongodb
The app stores tracking tokens in a mongo db.
`docker run --rm --name mongo -d -p 27017:27017 mongo`

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
`curl -i -X GET http://localhost:8080/foodCart`

Create cart
`curl -i -X POST http://localhost:8080/foodCart/create`

## Axon framework
### Aggregate
#### Multi entities aggregate
The field that declares the child entity/entities must be annotated with `AggregateMember`. This annotation tells Axon that
 the annotated field contains a class that should be inspected for message handlers.
Axon provides the `EntityId` annotation specifying the identifying field of a child entity.
@CommandHandler annotations are not limited to the aggregate root. Placing all command handlers in the root will sometimes 
lead to a large number of methods on the aggregate root, while many of them simply forward the invocation to one of the underlying entities.
If that is the case, you may place the @CommandHandler annotation on one of the underlying entities' methods.
Note that each command must have exactly one handler in the aggregate. This means that you cannot annotate multiple entities 
(either root nor not) with @CommandHandler which handle the same command type. In case you need to conditionally route a command to an entity, 
the parent of these entities should handle the command, and forward it based on the conditions that apply.
### message interceptors
There are two kind of interceptors:
#### dispatch interceptors
Intercept a message before it is dispatched to a message handler. The interceptor is invoked by the same thread that dispatch the command.
#### handler interceptors
Intercept the message just before it is handled. Because they are invoked after the message is dispatched the handler interceptors have access
to the message.
The `CommandHandlerInterceptor` annotation let's you define a handler interceptor on an aggregate's method.
##### exception handler
Methods annotated with `ExceptionHandler` will only handle exceptions thrown from message handling functions in the same class.
### Event processors
Event processors allow to configure how events will be dispatch to event handlers. There are two types of event processors:
- Subscribing processors: they are called by the thread publishing the event
- Tracking processors: they pull image from the event store
An event processor logical instance is identified by its name.
#### Tracking processors
##### Token store
Tracking event processors need a token store to store the progress of the event processing.
Each message received by an event processor is tied to a token.
Default token store is in memory. Using an in memory token store will replay all events on restart.
##### parallelism for tracking processors
The parallelism within a tracking processor is controlled by the number of segments that divides the processors.
##### Sequencing policy
Tracking processors have a sequencing policy to control the order in which events are processed.
By default Axon uses `SequentialPerAggregatePolicy` which make sure that events published by the same aggregate are consumed sequentially.
##### Multi-node processing
Two processor instances with the same name will compose the same logical processor. Those processors will compete for processing events.
A claim mechanism is used to prevent an event to be processed twice.
##### Replay
Axon allows to replay all past events (for instance to build a new view).
To trigger a replay of events the `resetTokens()` method must be called on the `TrackingEventProcessor`.
To control the replay of the events Axon provides:
- `AllowReplay` and `DisallowReplay` annotation to control what are the event handler to call when events are replayed.
###  command dispatcher
Each command is always sent to exactly one command handler. If no command handler is available for the dispatched command, a NoHandlerForCommandException exception is thrown by the command dispatcher.
There are two kind of command dispatcher available as two interfaces 
#### command bus
The command bus is the component aware of which command handler to call for a particular command.
#### command gateway 
The command gateway is a wrapper on the command bus that provides a more friendly API to perform command synchronously
### command handlers 
A command handler is a method with the `CommandHandler` annotation.
It is recommended to be placed in the aggregate class so that it has access to the state of the aggregate.
In order for axon to know on which aggregate instance the command must be performed, the command class must have a field with the `TargetAggregateIdentifier` annotation, except for the event responsible for the aggregate creation.
A command handler must
1. Perform a business logic check
2. Fire an event or throw an exception

Update of the aggregate state must be performed in the event sourcing handlers because they are the ones responsible for sourcing the aggregate from events.

### repositories
Repositories are in charge of storing the aggregates.
The only way to retrieve an aggregate from a repository is through its id.
There are two types of repositories
1. Standard repositories: store the current full state of the aggregates
2. Event sourcing repositories: store aggregate's events

Note that the Repository interface does not prescribe a delete(identifier) method. Deleting aggregates is done by invoking the AggregateLifecycle.markDeleted() method from within an aggregate. Deleting an aggregate is a state migration like any other, with the only difference that it is irreversible in many cases.

