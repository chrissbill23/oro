package com.asata.org.oro.mainservice;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.Map;

@Tag(name = "Oro", description = "The service")
public interface OroApi {

    @SecurityRequirements
    @Operation(summary = "Ping", description = "The status of the service and of its database; no authentication")
    @GetMapping("/ping")
    Map<String, String> ping();
}
