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
```
docker run --rm -d --name my-axon-server -p 8024:8024 -p 8124:8124 axoniq/axonserver
```

## mongodb
The app stores tracking tokens in a mongo db.
```
docker run --rm --name mongo -d -p 27017:27017 mongo
```

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
```
curl -i -X GET http://localhost:8080/foodCart
```

Create cart
```
curl -i -X POST http://localhost:8080/foodCart/create
```

Select a product
```
curl -i -X POST http://localhost:8080/foodCart/{card-id}/select/{product-id}/quantity/2

```
# Axon framework
## Commands
### Aggregate
An Aggregate is a regular object, which contains state and methods to alter that state. 
By default, Axon will configure Aggregates as an 'Event Sourced' Aggregates.
An aggregate class contains:
1. a field annotated with `AggregateIdentifier`: in order for Axon to know which aggregate instance a command is tarfeting
2. `CommandHandler` annotated methods/constuctor: known as command handlers those functions handle command of the type specified by their first parameter. Handlers perform business logic then publish an even through `AggregateLifecycle` functions
3. methods with `EventSourcingHandler` annotation
4. A no-arg constructor, which is required by Axon to create an empty aggregate instance before initializing it using past Events.
#### Aggraeate lifecycle operations
`AggregateLifecycle` Axon class provides function to
- `apply(Objecy)`: publish an event message on the event bus.such that it is known to have originated from the Aggregate executing the operation.
-  `createNew(Class, Callable)`: Instantiate a new Aggregate of the specified class through the given `Callable`
- `isLive()`: Check to verify whether the Aggregate is in a 'live' state. An Aggregate is regarded to be 'live' if it has finished replaying historic events to recreate it's state.
- `markDeleted()`: Will mark the Aggregate instance calling the function as being 'deleted'. Useful if the domain specifies a given Aggregate can be removed/deleted/closed, after which it should no longer be allowed to handle any Commands. This function should be called from an @EventSourcingHandler annotated function to ensure that being marked deleted is part of that Aggregate's state.
#### Multi entities aggregate
Complex business logic often requires more than what an Aggregate with only an Aggregate Root can provide. In that case, it is important that the complexity is spread over a number of 'Entities' within the aggregate.
There are two important annotations to be used with multi-eity aggregate:
1. The field that declares the child entity/entities must be annotated with `AggregateMember`. This annotation tells Axon that
 the annotated field contains a class that should be inspected for message handlers. The child member can be an object, or an `Iterable`.
2. the `EntityId` annotation specifying the identifying field of a child entity. Required to be able to route a command message to the correct entity instance. The property on the payload that will be used to find the entity that the message should be routed to, defaults to the name of the @EntityId annotated field. For example, when annotating the field transactionId, the command must define a property with that same name, which means either a transactionId or a getTransactionId() method must be present. If the name of the field and the routing property differ, you may provide a value explicitly using @EntityId(routingKey = "customRoutingProperty"). This annotation is mandatory on the Entity implementation if it will be part of a Collection or Map of child entities. 

`CommandHandler` and `EventSourcingHandler` annotations are not limited to the aggregate root. Placing all command handlers in the root will sometimes 
lead to a large number of methods on the aggregate root, while many of them simply forward the invocation to one of the underlying entities.
If that is the case, you may place the @CommandHandler annotation on one of the underlying entities' methods.
Note that each command must have exactly one handler in the aggregate. This means that you cannot annotate multiple entities 
(either root nor not) with @CommandHandler which handle the same command type. In case you need to conditionally route a command to an entity, 
the parent of these entities should handle the command, and forward it based on the conditions that apply.

The creation of the Entity takes place in an event sourcing handler of it's parent. It is thus not possible to have a 'command handling constructor' on the entity class as with the aggregate root.

The `AggregateMember` annotation has a `eventForwardingMode` parameter to specify to which condition sourcing event will be forwarded to child entities. Default behavior is ForwardAll which means the EventSourcingHandler method of a child entity will be called even if the entity instance doesn't match the EntityId of the event.
#### State Stored Aggregates
Instead of using event sourced aggregate (for which the current state is computed by replaying all past events) Axon allows to store aggregates as-is with their entire state. In such case Aggregates are read from a Jpa repository.
#### Aggregate creation from another aggregate
As a reaction of handling a command a command handler can create another aggregate through the `AggregateLifecycle.createNew()` function.
The child aggregate would still have to publish it's creation event in its constuctor so that the aggregate can be event sourced.
#### Conflict resolution
Axon uses optimistic locking to avoid conflict when two commands are applied at the same time.
The `TargetAggregateVersion` annotation can be used on an aggregate and command's field of type Long.
To avoid unecessarilly rejecting a command a command handler accept a parameter of type `ConflictResolver` that contains a `detectConflicts(Predicate<List<DomainEventMessage<?>>>)` method to specify which case should be considered as a conflict.
###  command dispatcher
Each command is always sent to exactly one command handler. If no command handler is available for the dispatched command, a NoHandlerForCommandException exception is thrown by the command dispatcher.
There are two kind of command dispatcher available as two interfaces 
#### command bus
The command bus is the component aware of which command handler to call for a particular command.
Available implementations:
- AxonServerCommandBus: Axon provides a command bus out of the box, the AxonServerCommandBus. It connects to the AxonIQ AxonServer Server to submit and receive Commands. AxonServerCommandBus is a distributed command bus. It uses a SimpleCommandBus to handle incoming commands on different JVM's by default.
- SimpleCommandBus: The SimpleCommandBus is, as the name suggests, the simplest implementation. It does straightforward processing of commands in the thread that dispatches them. After a command is processed, the modified aggregate(s) are saved and generated events are published in that same thread. In most scenarios, such as web applications, this implementation will suit your needs.
#### command gateway 
The command gateway is a wrapper on the command bus that provides a more friendly API to perform command synchronously
#### Command Dispatching Results
Command handler should not return result as it means the message should be a query. Exception is a command creating an aggregate. Indeed in such case the query bus will return the AggregateIdentifier of the created aggregate.
#### RetryScheduler
The RetryScheduler is capable of scheduling retries when command execution has failed. The retry scheduler is only invoked when a command fails due to a RuntimeException. Checked exceptions are regarded as a "business exception" and will never trigger a retry.
#### CommandDispatchInterceptor

### command handlers 
A command handler is a method with the `CommandHandler` annotation.
It is recommended to be placed in the aggregate class so that it has access to the state of the aggregate.
In order for axon to know on which aggregate instance the command must be performed, the command class must have a field with the `TargetAggregateIdentifier` annotation, except for the event responsible for the aggregate creation.
A command handler must
1. Perform a business logic check
2. Fire an event or throw an exception

Update of the aggregate state must be performed in the event sourcing handlers because they are the ones responsible for sourcing the aggregate from events.
#### creation policy
Axon provides a `CreationPolicy` annotation to be placed on a command handler. Possible values
- ALWAYS: the command will always create a new aggregate
- CREATE_IF_MISSING: the command will create a new aggregate if no aggregate with the specified id could be found
- NEVER: a command will only be handled by an existing aggregate
### repositories
Repositories are in charge of storing the aggregates.
The only way to retrieve an aggregate from a repository is through its id.
There are two types of repositories
1. Standard repositories: store the current full state of the aggregates
2. Event sourcing repositories: Those repositories do not store the aggregate itself, but the series of events generated by the aggregate. Based on these events, the state of an aggregate can be restored at any time.

Note that the Repository interface does not prescribe a delete(identifier) method. Deleting aggregates is done by invoking the AggregateLifecycle.markDeleted() method from within an aggregate. Deleting an aggregate is a state migration like any other, with the only difference that it is irreversible in many cases.
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

## Events
### dispatching events
Events can be dispatched from either an aggregate or from the event bus.
#### dispatching events from an aggregate
To publish an event from an Aggregate, it is required to do this from the lifecycle of the Aggregate instance. This is mandatory as we want the Aggregate identifier to be tied to the Event message. This is done through the `AggregateLifecycle.apply()` method. This method will use reflexivity to add the aggregate identifier and the sequence number into  the event message. The method will send notify the event sourcing handlers and finally publish the event into the event bus.
#### dispatching events from the event bus
In the vast majority of cases, the Aggregates will publish events by applying them. However, occasionally, it is necessary to publish from the `EventGateway` using its `publish()` method.
### event handlers
Creating an event handler is done by adding the `EventHandler` annotation on a method. The declared parameters of the method will specify the events received.
- The first parameter will be resolved as the payload of the event.
- Parameters with the `MetaDataValue` annotation will correspond to some field extracted from the event metadata.
- A parameter of type `MetaData` will contains the entire metadata of the event.
- A paremeter annotated with `SequenceNumber` and of type `long` will contains the sequence number of the event. The sequence number provides the ordering of the event within the scope of the aggregate. Sequence number only concernes domain event message which are the events fired from an aggregate. Events fired directly from the event bus will not contain any sequence number.
- other parameters can be injected, this list is not exhaustive

At most one event handler is invoked per ??? instance. If many method can handle the event the most specific one is chosen.
### Event processors
Event processors allow to configure how events will be dispatch to event handlers. There are two types of event processors:
- Subscribing processors: they are called by the thread publishing the event
- Tracking processors: they pull events from an event store
All processors have a name, which identifies a processor instance across JVM instances. Two processors with the same name are considered as two instances of the same processor.
 By default event handlers are attached to a processor whose name is the package name of the Event Handler's class.
An event processor can be configured to pull events from many event stores.
#### error handling

#### Tracking processors
##### Token store
Tracking event processors need a token store to store the progress of the event processing.
Each message received by an event processor is tied to a token.
Default token store is in memory. Using an in memory token store will replay all events on restart.
##### parallelism for tracking processors
The parallelism within a tracking processor is controlled by the number of segments that divides the processors. Segments separate the events in the stream across threads.
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

### event bus
The EventBus is the mechanism that dispatches events to the subscribed event handlers. Axon provides three implementations of the Event Bus: AxonServerEventStore, EmbeddedEventStore and SimpleEventBus. All three implementations support subscribing and tracking processors.
However, the AxonServerEventStore and EmbeddedEventStore persist events (see Event Store), which allows to replay them at a later stage. The SimpleEventBus has a volatile storage and 'forgets' events as soon as they have been published to subscribed components.
An AxonServerEventStore event bus/store is configured by default.
### event store
An event store offers the functionality of an event bus. Additionally, it persists published events and is able to retrieve previous events based on a given aggregate identifier.
#### Axon server as an event store
Axon provides an event store out of the box, the AxonServerEventStore. It connects to the AxonIQ AxonServer Server to store and retrieve Events.
#### embedded event store
Alternatively, Axon provides a non-axon-server option, the EmbeddedEventStore. It delegates the actual storage and retrieval of events to an EventStorageEngine. Available impementations are `JpaEventStorageEngine`, `MongoEventStorageEngine`.
##### mongo store
Events are stored in two separate collections: one for the event streams and one for snapshots.
### versioning
In the lifecycle of an Axon application events will typically change their format. As events are stored indefinitely the application should be able to cope with several versions of an event.
#### event upcasting
Due to the ever-changing nature of software applications it is likely that event definitions will also change over time. Since the Event Store is considered a read and append-only data source, your application must be able to read all events, regardless of when they were added. This is where upcasting comes in.
Upcasting is the action of rewriting an event from an old version to a new version.
Manually written upcasters have to be provided to specify how to upcast an event.
An Upcaster class takes an event in version n and upcast it into (zero, one or more) events of version n+1.
Upcasters are processed in a chain, meaning that the output of one upcaster is sent to the input of the next. This allows to update events in an incremental manner, writing an upcaster for each new event revision, making them small, isolated, and easy to understand.
To allow an upcaster to see what version of serialized object they are receiving, the Event Store stores a revision number as well as the fully qualified name of the Event. This revision number is generated by a `RevisionResolver`, configured in the serializer. Axon provides several implementations of the `RevisionResolver` :
The most handful one is the `AnnotationRevisionResolver` that checks for an `@Revision` annotation on the Event payload.
Axon's upcasters do not work with the EventMessage directly, but with the `IntermediateEventRepresentation` class.
The IntermediateEventRepresentation provides functionality to retrieve all necessary fields to construct an EventMessage, together with the actual upcast functions. The actual representation of the events in the upcast function may vary based on the event serializer used.
The `Upcaster` interface expose the `upcast()` method that accept and returns a `Stream` of `IntermediateEventRepresentation`. allowing chaining. 
`SingleEventUpcaster` is a simple abstract implementation of an `Upcaster` that performs a one-to-one transformation of a `IntermediateEventRepresentation`. Extending from this implementation requires one to implement a `canUpcast` and `doUpcast` function
## Queries
### query dispatcher
#### query bus
The QueryBus is the mechanism that dispatches queries to query handlers. Queries are registered using the combination of the query request name and query response type. It is possible to register multiple handlers for the same request-response combination.
Implementations:
- AxonServerQueryBus: is a distributed query bus based on Axon server
- SimpleQueryBus: does straightforward processing of queries in the thread that dispatches them
#### query gateway
The QueryGateway is a convenient interface towards the query dispatching mechanism. It abstracts certain aspects for you, like the necessity to wrap a Query payload in a Query Message.
#### query types
##### point to point (a.k.a direct) queries
Represents a query request to a single query handler.
The response of sending a query is a Java CompletableFuture,
which depending on the type of the query bus may be resolved immediately.
However, if a @QueryHandler annotated function's return type is CompletableFuture,
the result will be returned asynchronously regardless of the type of the query bus.
##### Scatter-Gather
When you want responses from all of the query handlers matching your query message, the scatter-gather query is the type to use. As a response to that query a stream of results is returned. This stream contains a result from each handler that successfully handled the query, in unspecified order. In case there are no handlers for the query, or all handlers threw an exception while handling the request, the stream is empty.
##### Subscription queries
The subscription query allows a client to get the initial state of the model it wants to query, and to stay up-to-date as the queried view model changes. In short it is an invocation of the Direct Query with the possibility to be updated when the initial state changes. To update a subscription with changes to the model, we will use the `QueryUpdateEmitter` component provided by Axon and used in an event handler.
### query handler
A Query Handler is a (singleton) object containing `@QueryHandler` annotated functions returning the query's response.
The following information defines a given query handling function:
1. The first parameter of the method is the query payload.
2. The methods response type is the query's response type.
3. The value of the queryName field in the annotation as the query's name (this is optional and in its absence will default to the query payload).
At most one query handler method is invoked per query handling. Axon will search for the most specific method to invoke based on query input and result type..
## Serializer
Serializers come in several flavors in the Axon Framework and are use.d for a variety of subjects. Currently you can choose between the XStreamSerializer, JacksonSerializer and JavaSerializer to serialize messages (commands/queries/events), tokens, snapshots and sagas in an Axon application.
Event stores need a way to serialize the event to prepare it for storage. By default, Axon uses the XStreamSerializer, which uses XStream to serialize events into XML. Alternatively, Axon also provides the JacksonSerializer, which uses Jackson to serialize events into JSON.
You may also implement your own serializer, simply by creating a class that implements Serializer, and configuring the event store to use that implementation instead of the default.
You can setup a serializer to handle events, another one to handler commands and queries and a third one to handle tokens, snapshots and sagas.
## Tuning
### Snapshotting
By regularly creating and storing a snapshot event, the event store does not have to return long lists of events. Just the latest snapshot events and all events that occurred after the snapshot was made.
The definition of when snapshots should be created, is provided by the `SnapshotTriggerDefinition` interface.
The `EventCountSnapshotTriggerDefinition` provides the mechanism to trigger snapshot creation when the number of events needed to load an aggregate exceeds a certain threshold.
#### Implementation details
A `Snapshotter` is responsible for the actual creation of a snapshot. Typically, snapshotting is a process that should disturb the operational processes as little as possible. Therefore, it is recommended to run the snapshotter in a different thread. Axon provides the `AggregateSnapshotter`, which creates and stores AggregateSnapshot instances. This is a special type of snapshot, since it contains the actual aggregate instance within it. The repositories provided by Axon are aware of this type of snapshot, and will extract the aggregate from it, instead of instantiating a new one. All events loaded after the snapshot events are streamed to the extracted aggregate instance. If `AggregateSnapshotter` is not used then a snapshot is considered as any other event and the aggregate must provide a handler to tell Axon how to source the aggregate from a snapshot.
## Testing
### command model testing
The `FixtureConfiguration` class offers a given-when-then API to test the result of a command after simulating some past events.