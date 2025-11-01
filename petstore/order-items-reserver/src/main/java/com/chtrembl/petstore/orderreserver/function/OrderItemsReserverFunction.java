package com.chtrembl.petstore.orderreserver.function;

import com.azure.core.util.BinaryData;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.chtrembl.petstore.orderreserver.model.OrderRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderItemsReserverFunction {

    private static final String STORAGE_CONN_ENV = "BLOB_CONNECTION_STRING";
    private static final String CONTAINER_NAME_ENV = "BLOB_CONTAINER_NAME";

    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @FunctionName("reserveOrder")
    public HttpResponseMessage reserveOrder(
            @HttpTrigger(name = "req", methods = {HttpMethod.POST}, authLevel = AuthorizationLevel.FUNCTION)
            HttpRequestMessage<String> request,
            final ExecutionContext context) {
        log.info("Executing function reserveOrder");

        try {
            String body = request.getBody();
            if (body == null || body.isEmpty()) {
                return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                        .body("Request body required").build();
            }
            String sessionId = request.getHeaders().getOrDefault("x-session-id", null);
            OrderRequest order = mapper.readValue(body, OrderRequest.class);

            log.info("Reserving order for sessionId: {}", sessionId);
            log.info("Reserving order: {}", order);
            if (sessionId == null || sessionId.isEmpty()) {
                sessionId = order.sessionId;
            }
            if (sessionId == null || sessionId.isEmpty()) {
                return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                        .body("sessionId required (header x-session-id or body.sessionId)").build();
            }

            if (order.orderId == null || order.orderId.isEmpty()) {
                order.orderId = UUID.randomUUID().toString();
            }
            if (order.timestamp == null) {
                order.timestamp = OffsetDateTime.now();
            }
            // serialize to json
            byte[] bytes = mapper.writeValueAsBytes(order);
            String conn = System.getenv(STORAGE_CONN_ENV);
            String containerName = System.getenv(CONTAINER_NAME_ENV);
            log.info("Container name: {}, Connection string: {}", containerName, conn);
            if (conn == null || containerName == null) {
                log.warn("Missing BLOB_CONNECTION_STRING or BLOB_CONTAINER_NAME env vars");
                return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("Storage configuration missing").build();
            }

            BlobServiceClient serviceClient = new BlobServiceClientBuilder()
                    .connectionString(conn)
                    .buildClient();
            BlobContainerClient containerClient = serviceClient.getBlobContainerClient(containerName);
            if (!containerClient.exists()) {
                containerClient.create();
            }
            String blobName = String.format("session-%s.json", sessionId);
            BlobClient blobClient = containerClient.getBlobClient(blobName);

            blobClient.upload(BinaryData.fromBytes(bytes), true);
            log.info("Wrote blob: {}, size= {}", blobName, bytes.length);
            return request.createResponseBuilder(HttpStatus.OK)
                    .body(String.format("Order reserved and written to blob %s", blobName))
                    .build();
        } catch (Exception ex) {
            log.error("Failed to reserve order: ", ex);
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error: " + ex.getMessage()).build();
        }
    }
}