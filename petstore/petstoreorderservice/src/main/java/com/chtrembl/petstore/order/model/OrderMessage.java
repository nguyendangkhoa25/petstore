package com.chtrembl.petstore.order.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderMessage {
    
    @JsonProperty("sessionId")
    private String sessionId;
    
    @JsonProperty("orderId")
    private String orderId;
    
    @JsonProperty("email")
    private String email;
    
    @JsonProperty("products")
    private List<Product> products;
    
    @JsonProperty("timestamp")
    private OffsetDateTime timestamp;
}
