package com.asata.org.oro.mainservice;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequiredArgsConstructor
public class OroController implements OroApi {

    private final HealthEndpoint healthEndpoint;

    @Override
    public Map<String, String> ping() {
        return Map.of("serverStatus", healthEndpoint.health().getStatus().getCode());
    }
}
