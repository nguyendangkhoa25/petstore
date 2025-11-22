package com.chtrembl.petstore.order.config;

import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
@ConditionalOnProperty(name = "spring.cloud.azure.servicebus.enabled", havingValue = "true", matchIfMissing = false)
public class ServiceBusConfig {

    @Value("${spring.cloud.azure.servicebus.connection-string:}")
    private String connectionString;

    @Value("${spring.cloud.azure.servicebus.queue.name:order-updates}")
    private String queueName;

    @Bean
    public ServiceBusSenderClient serviceBusSenderClient() {
        if (connectionString == null || connectionString.trim().isEmpty()) {
            throw new IllegalStateException(
                "Azure Service Bus is enabled but connection string is not configured. " +
                "Please set spring.cloud.azure.servicebus.connection-string property or " +
                "set spring.cloud.azure.servicebus.enabled=false to disable Service Bus integration."
            );
        }

        log.info("Creating ServiceBusSenderClient for queue: {}", queueName);

        return new ServiceBusClientBuilder()
                .connectionString(connectionString)
                .sender()
                .queueName(queueName)
                .buildClient();
    }
}
