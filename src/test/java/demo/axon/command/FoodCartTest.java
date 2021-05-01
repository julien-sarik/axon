package demo.axon.command;

import demo.axon.coreapi.*;
import org.axonframework.test.aggregate.AggregateTestFixture;
import org.axonframework.test.aggregate.FixtureConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

public class FoodCartTest {

    private FixtureConfiguration<FoodCart> fixture;

    @BeforeEach
    public void init() {
        fixture = new AggregateTestFixture<>(FoodCart.class);
//        if needed some Axon component can be registered directly on the fixture configuration if default component aren't suitable
//        fixture.registerAggregateFactory(aggregateFactory);
    }

    @Test
    public void confirmOrderCommand() {
        final UUID cartId = UUID.randomUUID();
        fixture.given(new FoodCartCreatedEvent(cartId))
                .when(new ConfirmOrderCommand(cartId))
                .expectSuccessfulHandlerExecution()
                .expectEvents(new OrderConfirmedEvent(cartId));
    }

    @Test
    public void confirmOrderCommandTwice() {
        final UUID cartId = UUID.randomUUID();
        fixture.given(new FoodCartCreatedEvent(cartId))
                .andGivenCommands(new ConfirmOrderCommand(cartId))
                .when(new ConfirmOrderCommand(cartId))
                .expectSuccessfulHandlerExecution()
                .expectNoEvents();
    }

    @Test
    public void deselectProductCommand() {
        final UUID cartId = UUID.randomUUID();
        fixture.given(new FoodCartCreatedEvent(cartId))
                .when(new DeselectProductCommand(cartId, UUID.randomUUID(),1))
                .expectException(ProductDeselectionException.class);
    }
}