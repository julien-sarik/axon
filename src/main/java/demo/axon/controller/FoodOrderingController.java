package demo.axon.controller;

import demo.axon.coreapi.*;
import demo.axon.query.FoodCartView;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.axonframework.queryhandling.QueryGateway;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RequestMapping("/foodCart")
@RestController
class FoodOrderingController {

    private final CommandGateway commandGateway;
    private final QueryGateway queryGateway;

    public FoodOrderingController(CommandGateway commandGateway, QueryGateway queryGateway) {
        this.commandGateway = commandGateway;
        this.queryGateway = queryGateway;
    }

    @PostMapping("/create")
    public CompletableFuture<UUID> createFoodCart() {
        return commandGateway.send(new CreateFoodCartCommand(UUID.randomUUID()));
    }

    @PostMapping("/{foodCartId}/select/{productId}/quantity/{quantity}")
    public void selectProduct(@PathVariable("foodCartId") String foodCartId,
                              @PathVariable("productId") String productId,
                              @PathVariable("quantity") Integer quantity) {
        commandGateway.send(new SelectProductCommand(
                UUID.fromString(foodCartId), UUID.fromString(productId), quantity
        ));
    }

    @PostMapping("/{foodCartId}/deselect/{productId}/quantity/{quantity}")
    public void deselectProduct(@PathVariable("foodCartId") String foodCartId,
                                @PathVariable("productId") String productId,
                                @PathVariable("quantity") Integer quantity) {
        try {
            // by using `sendAndWait()` method on the command gateway we can catch business exception from the command handler
            commandGateway.sendAndWait(new DeselectProductCommand(
                    UUID.fromString(foodCartId), UUID.fromString(productId), quantity
            ));
        } catch (Exception e) {
            // checked exceptions will be wrapped in o.a.c.CommandExecutionException
            // if the command bus is a SimpleCommandBus then the cause of the exception will be the one actually thrown
            // by the command handler. Otherwise the cause exception will be an instance of AxonServerRemoteCommandHandlingException
            // that will contain the same error message as the one thrown by the command handler.
            throw e;
        }
    }

    @PostMapping("/{foodCartId}/confirm")
    public void confirmOrder(@PathVariable("foodCartId") String foodCartId) {
        commandGateway.send(new ConfirmOrderCommand(UUID.fromString(foodCartId)));
    }

    @GetMapping("/{foodCartId}")
    public CompletableFuture<FoodCartView> findFoodCart(@PathVariable("foodCartId") String foodCartId) {
        return queryGateway.query(
                new FindFoodCartQuery(UUID.fromString(foodCartId)),
                ResponseTypes.instanceOf(FoodCartView.class)
        );
    }

    @GetMapping("")
    public CompletableFuture<List<FoodCartView>> findFoodCarts() {
        return queryGateway.query(
                new FindFoodCartsQuery(),
                ResponseTypes.multipleInstancesOf(FoodCartView.class)
        );
    }

    @DeleteMapping("/{foodCartId}")
    public void deleteFoodCart(@PathVariable("foodCartId") String foodCartId) {
        try {
            commandGateway.sendAndWait(new DeleteFoodCartCommand(UUID.fromString(foodCartId)));
        } catch (Exception e) {
            throw e;
        }
    }
}
