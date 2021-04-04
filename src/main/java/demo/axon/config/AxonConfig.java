package demo.axon.config;

import com.mongodb.client.MongoClient;
import demo.axon.interceptor.CorrelationLoggingInterceptor;
import org.axonframework.commandhandling.CommandBus;
import org.axonframework.config.Configurer;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventsourcing.EventCountSnapshotTriggerDefinition;
import org.axonframework.eventsourcing.SnapshotTriggerDefinition;
import org.axonframework.eventsourcing.Snapshotter;
import org.axonframework.extensions.mongo.DefaultMongoTemplate;
import org.axonframework.extensions.mongo.MongoTemplate;
import org.axonframework.extensions.mongo.eventsourcing.tokenstore.MongoTokenStore;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.correlation.CorrelationDataProvider;
import org.axonframework.messaging.correlation.MessageOriginProvider;
import org.axonframework.queryhandling.QueryBus;
import org.axonframework.serialization.Serializer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AxonConfig {

    @Bean
    public MongoTemplate mongodbTemplate(MongoClient client) {
        final String databaseName = "axon";
        DefaultMongoTemplate mongoTemplate = DefaultMongoTemplate
                .builder()
                .mongoDatabase(client, databaseName)
                .build();
        return mongoTemplate;
    }

    /**
     * Allows to store tracking tokens. Storing tokens in mongo instead of in memory prevents events to be replayed
     * on application restart.
     *
     * @param mongoTemplate
     * @param serializer
     * @return
     */
    @Bean
    public MongoTokenStore tokenStore(MongoTemplate mongoTemplate, Serializer serializer) {
        return MongoTokenStore
                .builder()
                .mongoTemplate(mongoTemplate)
                .serializer(serializer)
                .build();
    }

    @Autowired
    public void configureCommandInterceptors(CommandBus commandBus, QueryBus queryBus, EventBus eventBus, Configurer configurer) {
        final CorrelationLoggingInterceptor<Message<?>> interceptor = new CorrelationLoggingInterceptor<>();
        commandBus.registerDispatchInterceptor(interceptor);
        commandBus.registerHandlerInterceptor(interceptor);
        queryBus.registerDispatchInterceptor(interceptor);
        queryBus.registerHandlerInterceptor(interceptor);
        eventBus.registerDispatchInterceptor(interceptor);
        configurer.eventProcessing().registerHandlerInterceptor("demo.axon.query", configuration -> interceptor);
    }

    @Bean(name = "FoodCartSnapshotTrigger")
    public SnapshotTriggerDefinition snapshotTrigger(Snapshotter snapshotter) {
        // trigger a snapshot creation each time a given number of events occurred for a particular aggregate instance
        return new EventCountSnapshotTriggerDefinition(snapshotter, 5);
    }

    @Bean
    public CorrelationDataProvider messageOriginProvider() {
        return new MessageOriginProvider();
    }
}
