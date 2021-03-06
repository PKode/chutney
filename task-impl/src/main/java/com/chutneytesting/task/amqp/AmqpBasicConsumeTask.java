package com.chutneytesting.task.amqp;

import static org.apache.commons.lang3.ObjectUtils.defaultIfNull;
import static org.apache.commons.lang3.StringUtils.defaultIfEmpty;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.chutneytesting.task.spi.Task;
import com.chutneytesting.task.spi.TaskExecutionResult;
import com.chutneytesting.task.spi.injectable.Input;
import com.chutneytesting.task.spi.injectable.Logger;
import com.chutneytesting.task.spi.injectable.Target;
import com.chutneytesting.task.spi.time.Duration;
import com.chutneytesting.task.amqp.consumer.QueueingConsumer;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;

public class AmqpBasicConsumeTask implements Task {

    private final ConnectionFactoryFactory connectionFactoryFactory = new ConnectionFactoryFactory();

    private final ConnectionFactory connectionFactory;
    private final String queueName;
    private final Integer nbMessages;
    private final String selector;
    private final String timeout;
    private final Logger logger;

    public AmqpBasicConsumeTask(Target target,
                                @Input("queue-name") String queueName,
                                @Input("nb-messages") Integer nbMessages,
                                @Input("selector") String selector,
                                @Input("timeout") String timeout,
                                Logger logger) {
        this.connectionFactory = connectionFactoryFactory.create(target);
        this.queueName = queueName;
        this.logger = logger;
        this.nbMessages = defaultIfNull(nbMessages, 1);
        this.timeout = defaultIfEmpty(timeout, "60 sec");
        this.selector = selector;
    }


    @Override
    public TaskExecutionResult execute() {
        try (Connection connection = connectionFactory.newConnection(); Channel channel = connection.createChannel()) {
            final long duration = Duration.parse(timeout).toMilliseconds();
            QueueingConsumer.Result result = new QueueingConsumer(channel, queueName, nbMessages, selector, duration).consume();
            if (result.messages.size() != nbMessages) {
                logger.error("Unable to get the expected number of messages [" + nbMessages + "] during " + timeout + ".");
                return TaskExecutionResult.ko();
            }
            final Map<String, Object> results = new HashMap<>();
            results.put("body", result.messages);
            results.put("payloads", result.payloads);
            results.put("headers", result.headers);
            return TaskExecutionResult.ok(results);
        } catch (TimeoutException | InterruptedException | IOException e) {
            logger.error("Unable to establish connection to RabbitMQ: " + e.getMessage());
            return TaskExecutionResult.ko();
        }
    }
}
