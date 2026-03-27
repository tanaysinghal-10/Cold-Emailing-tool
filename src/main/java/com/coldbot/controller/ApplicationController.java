package com.coldbot.controller;

import com.coldbot.application.ApplicationOrchestrator;
import com.coldbot.common.dto.ApplicationResponse;
import com.coldbot.common.dto.CreateApplicationRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/applications")
@RequiredArgsConstructor
public class ApplicationController {

    private final ApplicationOrchestrator orchestrator;

    @PostMapping
    public ResponseEntity<Map<String, String>> createApplication(@Valid @RequestBody CreateApplicationRequest request) {
        orchestrator.processApplication(request, null);
        return ResponseEntity.accepted().body(Map.of("message", "Application processing started"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApplicationResponse> getApplication(@PathVariable UUID id) {
        return ResponseEntity.ok(orchestrator.getApplication(id));
    }

    @PutMapping("/{id}/approve")
    public ResponseEntity<Map<String, String>> approveApplication(@PathVariable UUID id) {
        orchestrator.approveAndSend(id, null);
        return ResponseEntity.ok(Map.of("message", "Email sent successfully"));
    }

    @PutMapping("/{id}/edit")
    public ResponseEntity<ApplicationResponse> editApplication(@PathVariable UUID id, @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(orchestrator.updateEmail(id, body.get("subject"), body.get("body"), body.get("recruiterEmail")));
    }

    @PutMapping("/{id}/cancel")
    public ResponseEntity<ApplicationResponse> cancelApplication(@PathVariable UUID id) {
        return ResponseEntity.ok(orchestrator.cancelApplication(id));
    }
}
