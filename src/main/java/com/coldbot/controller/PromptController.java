package com.coldbot.controller;

import com.coldbot.ai.PromptTemplateService;
import com.coldbot.job.entity.PromptTemplate;
import com.coldbot.job.repository.PromptTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/prompts")
@RequiredArgsConstructor
public class PromptController {

    private final PromptTemplateRepository promptTemplateRepository;
    private final PromptTemplateService promptTemplateService;

    @GetMapping
    public ResponseEntity<List<PromptTemplate>> getAllPrompts() {
        return ResponseEntity.ok(promptTemplateRepository.findAll());
    }

    @GetMapping("/{name}")
    public ResponseEntity<String> getPrompt(@PathVariable String name) {
        return ResponseEntity.ok(promptTemplateService.getRawTemplate(name));
    }

    @PutMapping("/{name}")
    public ResponseEntity<Map<String, String>> updatePrompt(@PathVariable String name, @RequestBody Map<String, String> body) {
        promptTemplateService.updateTemplate(name, body.get("content"));
        return ResponseEntity.ok(Map.of("message", "Prompt template updated: " + name));
    }
}
