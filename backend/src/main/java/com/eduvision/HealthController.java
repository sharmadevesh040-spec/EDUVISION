package com.eduvision;

import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Temporary smoke-test endpoint used to prove the toolchain works.
 * Returns exactly {"status":"ok"}.
 */
@RestController
@CrossOrigin(origins = "*")
public class HealthController {

    @GetMapping(value = "/api/health", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }
}