package demo.axon.config;

import com.mongodb.client.MongoClient;
import demo.axon.command.interceptor.CommandDispatchInterceptor;
import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.SimpleCommandBus;
import org.axonframework.eventsourcing.EventCountSnapshotTriggerDefinition;
import org.axonframework.eventsourcing.SnapshotTriggerDefinition;
import org.axonframework.eventsourcing.Snapshotter;
import org.axonframework.extensions.mongo.DefaultMongoTemplate;
import org.axonframework.extensions.mongo.MongoTemplate;
import org.axonframework.extensions.mongo.eventsourcing.tokenstore.MongoTokenStore;
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
    public void configureCommandInterceptors(CommandBus commandBus) {
        commandBus.registerDispatchInterceptor(new CommandDispatchInterceptor());
    }

    @Bean(name = "FoodCartSnapshotTrigger")
    public SnapshotTriggerDefinition snapshotTrigger(Snapshotter snapshotter) {
        // trigger a snapshot creation each time a given number of events occurred for a particular aggregate instance
        return new EventCountSnapshotTriggerDefinition(snapshotter, 5);
    }

    @Bean
    public CommandBus commandBus() {
        // using a simple command bus instead of the default distributed command bus
        return SimpleCommandBus.builder().build();
    }
}
