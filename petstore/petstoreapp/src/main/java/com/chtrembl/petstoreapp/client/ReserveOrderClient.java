package com.chtrembl.petstoreapp.client;

import com.chtrembl.petstoreapp.config.FeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(
        name = "reserve-order-client",
        url = "${petstore.service.reserveOrder.url}",
        configuration = FeignConfig.class
)
public interface ReserveOrderClient {
    @PostMapping("/api/reserveOrder")
    ResponseEntity<String> reserveOrder(
            @RequestBody String orderJson,
            @RequestHeader("x-session-id") String sessionId,
            @RequestParam("code") String functionCode
    );
}
