package demo.axon.interceptor;

import org.axonframework.messaging.InterceptorChain;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageDispatchInterceptor;
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.messaging.correlation.MessageOriginProvider;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.function.BiFunction;

import static java.lang.String.format;

public class CorrelationLoggingInterceptor<T extends Message<?>>
        implements MessageDispatchInterceptor<T>, MessageHandlerInterceptor<T> {

    private static final Logger LOGGER = LoggerFactory.getLogger(CorrelationLoggingInterceptor.class);

    @Override
    public BiFunction<Integer, T, T> handle(List<? extends T> messages) {
        return (index, message) -> {
            LOGGER.info("Dispatching message {} with id {}, trace id {} and correlation id {}.",
                    message.getPayloadType().getSimpleName(),
                    message.getIdentifier(),
                    message.getMetaData().get(MessageOriginProvider.getDefaultTraceKey()),
                    message.getMetaData().get(MessageOriginProvider.getDefaultCorrelationKey()));
            return message;
        };
    }

    @Override
    public Object handle(UnitOfWork<? extends T> unitOfWork, InterceptorChain interceptorChain) throws Exception {
        T message = unitOfWork.getMessage();
        LOGGER.info("Handling message {} with id {}, trace id {} and correlation id {}.",
                message.getPayloadType().getSimpleName(),
                message.getIdentifier(),
                message.getMetaData().get(MessageOriginProvider.getDefaultTraceKey()),
                message.getMetaData().get(MessageOriginProvider.getDefaultCorrelationKey()));
        try {
            Object returnValue = interceptorChain.proceed();
            LOGGER.info("[{}] executed successfully with a [{}] return value",
                    message.getPayloadType().getSimpleName(),
                    returnValue == null ? "null" : returnValue.getClass().getSimpleName());
            return returnValue;
        } catch (Exception t) {
            LOGGER.warn(format("[%s] execution failed:", message.getPayloadType().getSimpleName()), t);
            throw t;
        }
    }
}
