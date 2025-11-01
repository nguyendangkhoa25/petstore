package com.chtrembl.petstore.orderreserver.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
@Getter
@Setter
public class OrderRequest {

    public String sessionId;
    @JsonProperty("id")
    public String orderId;
    public String userId;
    public OffsetDateTime timestamp;

    @JsonProperty("products")
    public List<OrderItem> items;

    public double total;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OrderItem {
        @JsonProperty("id")
        public Long productId;
        public String name;
        public int quantity;
        public double price;
        public String category;
        public String photoURL;
    }

    /**
     * Jackson ObjectMapper helper for OffsetDateTime
     */
    public static com.fasterxml.jackson.databind.ObjectMapper mapper() {
        return new com.fasterxml.jackson.databind.ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
}

