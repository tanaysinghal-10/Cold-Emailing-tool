package com.coldbot.controller;

import com.coldbot.job.repository.JobApplicationRepository;
import com.coldbot.common.enums.ApplicationStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class HealthController {

    private final JobApplicationRepository applicationRepository;

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP", "service", "cold-email-bot"));
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> stats() {
        long total = applicationRepository.count();
        long sent = applicationRepository.findByStatus(ApplicationStatus.SENT).size();
        long failed = applicationRepository.findByStatus(ApplicationStatus.FAILED).size();
        long pending = applicationRepository.findByStatus(ApplicationStatus.PENDING).size() +
                       applicationRepository.findByStatus(ApplicationStatus.AWAITING_APPROVAL).size();
        return ResponseEntity.ok(Map.of("totalApplications", total, "sent", sent, "failed", failed, "pending", pending));
    }
}
