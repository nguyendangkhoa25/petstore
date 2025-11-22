package com.chtrembl.petstore.order.service;

import com.azure.messaging.servicebus.ServiceBusMessage;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import com.chtrembl.petstore.order.model.Order;
import com.chtrembl.petstore.order.model.OrderMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

@Service
@Slf4j
@ConditionalOnProperty(name = "spring.cloud.azure.servicebus.enabled", havingValue = "true", matchIfMissing = false)
public class ServiceBusSenderService {

    private final ServiceBusSenderClient senderClient;
    private final ObjectMapper objectMapper;
    private final String queueName;

    public ServiceBusSenderService(
            ServiceBusSenderClient senderClient,
            @Value("${spring.cloud.azure.servicebus.queue.name:order-updates}") String queueName) {
        this.senderClient = senderClient;
        this.queueName = queueName;
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        log.info("ServiceBusSenderService initialized with queue: {}", queueName);
    }

    /**
     * Sends an order update message to Azure Service Bus
     *
     * @param order     The order that was updated
     * @param sessionId The customer's session ID
     */
    public void sendOrderUpdateMessage(Order order, String sessionId) {
        try {
            OrderMessage message = OrderMessage.builder()
                    .sessionId(sessionId)
                    .orderId(order.getId())
                    .email(order.getEmail())
                    .products(order.getProducts())
                    .timestamp(OffsetDateTime.now())
                    .build();

            String jsonMessage = objectMapper.writeValueAsString(message);

            log.info("Sending order update message to Service Bus queue '{}' for session: {}",
                    queueName, sessionId);

            ServiceBusMessage serviceBusMessage = new ServiceBusMessage(jsonMessage)
                    .setMessageId(sessionId + "-" + System.currentTimeMillis())
                    .setContentType("application/json");

            senderClient.sendMessage(serviceBusMessage);

            log.info("Successfully sent order update message for session: {}", sessionId);
        } catch (Exception e) {
            log.error("Failed to send order update message to Service Bus for session: {}",
                    sessionId, e);
            // Don't throw - allow order processing to continue
        }
    }
}
