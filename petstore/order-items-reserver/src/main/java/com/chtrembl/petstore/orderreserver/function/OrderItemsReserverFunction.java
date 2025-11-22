package com.chtrembl.petstore.orderreserver.function;

import com.azure.core.util.BinaryData;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.models.BlobErrorCode;
import com.azure.storage.blob.models.BlobStorageException;
import com.chtrembl.petstore.orderreserver.model.OrderRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;
import com.microsoft.azure.functions.annotation.ServiceBusQueueTrigger;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Azure Function that processes order update messages from Service Bus Queue
 * and stores them in Blob Storage with retry logic
 */
public class OrderItemsReserverFunction {

    private static final String STORAGE_CONN_ENV = "BLOB_CONNECTION_STRING";
    private static final String CONTAINER_NAME_ENV = "BLOB_CONTAINER_NAME";
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 1000; // 1 second between retries

    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    /**
     * Service Bus Queue Trigger function that processes order update messages
     *
     * @param message The message from Service Bus queue
     * @param context The execution context
     */
    @FunctionName("reserveOrderFromQueue")
    public void reserveOrderFromQueue(
            @ServiceBusQueueTrigger(
                    name = "message",
                    queueName = "%SERVICEBUS_QUEUE_NAME%",
                    connection = "SERVICEBUS_CONNECTION_STRING"
            ) String message,
            final ExecutionContext context) {

        context.getLogger().info("Service Bus Queue trigger function processing message");
        context.getLogger().info("Message: " + message);

        try {
            // Parse the order message
            OrderRequest order = mapper.readValue(message, OrderRequest.class);

            // Validate session ID
            if (order.sessionId == null || order.sessionId.isEmpty()) {
                context.getLogger().severe("SessionId is missing in the order message");
                throw new IllegalArgumentException("SessionId is required");
            }

            // Set timestamp if not provided
            if (order.timestamp == null) {
                order.timestamp = OffsetDateTime.now();
            }

            context.getLogger().info("Processing order for sessionId: " + order.sessionId + ", orderId: " + order.orderId);

            // Upload to Blob Storage with retry logic
            boolean uploaded = uploadToBlobStorageWithRetry(order, context);

            if (!uploaded) {
                // If all retries failed, throw exception to send message to Dead Letter Queue
                String errorMsg = String.format(
                        "Failed to upload order to blob storage after %d attempts for sessionId: %s",
                        MAX_RETRY_ATTEMPTS, order.sessionId);
                context.getLogger().severe(errorMsg);
                throw new RuntimeException(errorMsg);
            }

            context.getLogger().info("Successfully processed order for sessionId: " + order.sessionId);

        } catch (Exception ex) {
            context.getLogger().severe("Error processing message: " + ex.getMessage());
            // Re-throw to trigger Service Bus retry/DLQ behavior
            throw new RuntimeException("Failed to process order message", ex);
        }
    }

    /**
     * Uploads order data to Blob Storage with retry logic
     *
     * @param order   The order request to upload
     * @param context The execution context
     * @return true if upload succeeded, false otherwise
     */
    private boolean uploadToBlobStorageWithRetry(OrderRequest order, ExecutionContext context) {
        String conn = System.getenv(STORAGE_CONN_ENV);
        String containerName = System.getenv(CONTAINER_NAME_ENV);

        if (conn == null || conn.isEmpty() || containerName == null || containerName.isEmpty()) {
            context.getLogger().severe("Missing BLOB_CONNECTION_STRING or BLOB_CONTAINER_NAME environment variables");
            return false;
        }

        int attempt = 0;
        Exception lastException = null;

        while (attempt < MAX_RETRY_ATTEMPTS) {
            attempt++;
            try {
                context.getLogger().info("Blob upload attempt " + attempt + " of " + MAX_RETRY_ATTEMPTS + " for sessionId: " + order.sessionId);

                // Serialize order to JSON
                byte[] bytes = mapper.writeValueAsBytes(order);

                // Create blob client
                BlobServiceClient serviceClient = new BlobServiceClientBuilder()
                        .connectionString(conn)
                        .buildClient();

                BlobContainerClient containerClient = serviceClient.getBlobContainerClient(containerName);

                // Create container if it doesn't exist
                if (!containerClient.exists()) {
                    context.getLogger().info("Creating container: " + containerName);
                    containerClient.create();
                }

                // Use sessionId as filename to overwrite for same session
                String blobName = String.format("session-%s.json", order.sessionId);
                BlobClient blobClient = containerClient.getBlobClient(blobName);

                // Delete existing blob if it exists to ensure fresh upload
                if (blobClient.exists()) {
                    context.getLogger().info("Deleting existing blob: " + blobName);
                    blobClient.delete();
                }

                // Upload blob
                blobClient.upload(BinaryData.fromBytes(bytes), true);

                context.getLogger().info("Successfully uploaded blob: " + blobName + ", size: " + bytes.length + " bytes");

                return true; // Success!

            } catch (BlobStorageException bse) {
                lastException = bse;
                context.getLogger().warning("Blob storage error on attempt " + attempt + ": " + bse.getErrorCode() + " - " + bse.getMessage());

                // Check if error is retryable
                if (!isRetryableError(bse)) {
                    context.getLogger().severe("Non-retryable blob storage error: " + bse.getErrorCode());
                    return false;
                }

            } catch (Exception ex) {
                lastException = ex;
                context.getLogger().warning("Error on attempt " + attempt + ": " + ex.getMessage());
            }

            // Wait before retry (except on last attempt)
            if (attempt < MAX_RETRY_ATTEMPTS) {
                try {
                    context.getLogger().info("Waiting " + RETRY_DELAY_MS + "ms before retry...");
                    Thread.sleep(RETRY_DELAY_MS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    context.getLogger().warning("Retry delay interrupted");
                }
            }
        }

        // All retries exhausted
        context.getLogger().warning("All " + MAX_RETRY_ATTEMPTS + " upload attempts failed for sessionId: " + order.sessionId + ". Last error: " + (lastException != null ? lastException.getMessage() : "Unknown"));

        return false;
    }

    /**
     * Determines if a blob storage exception is retryable
     */
    private boolean isRetryableError(BlobStorageException ex) {
        BlobErrorCode errorCode = ex.getErrorCode();

        // Retry on transient errors
        return errorCode == BlobErrorCode.SERVER_BUSY ||
                errorCode == BlobErrorCode.INTERNAL_ERROR ||
                errorCode == BlobErrorCode.OPERATION_TIMED_OUT ||
                ex.getStatusCode() >= 500; // Retry on 5xx server errors
    }

    @FunctionName("reserveOrderFromHttp")
    public HttpResponseMessage reserveOrderFromHttp(
            @HttpTrigger(name = "req", methods = {HttpMethod.POST}, authLevel = AuthorizationLevel.FUNCTION)
            HttpRequestMessage<String> request,
            final ExecutionContext context) {
        context.getLogger().info("Executing function reserveOrder");

        try {
            String body = request.getBody();
            if (body == null || body.isEmpty()) {
                return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                        .body("Request body required").build();
            }
            String sessionId = request.getHeaders().getOrDefault("x-session-id", null);
            OrderRequest order = mapper.readValue(body, OrderRequest.class);

            context.getLogger().info("Reserving order for sessionId: " + sessionId);
            context.getLogger().info("Reserving order: " + order);
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
            context.getLogger().info("Container name: " + containerName + ", Connection string: " + conn);
            if (conn == null || containerName == null) {
                context.getLogger().warning("Missing BLOB_CONNECTION_STRING or BLOB_CONTAINER_NAME env vars");
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
            context.getLogger().info("Wrote blob: " + blobName + ", size= " + bytes.length);
            return request.createResponseBuilder(HttpStatus.OK)
                    .body(String.format("Order reserved and written to blob %s", blobName))
                    .build();
        } catch (Exception ex) {
            context.getLogger().severe("Failed to reserve order: " + ex.getMessage());
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error: " + ex.getMessage()).build();
        }
    }
}