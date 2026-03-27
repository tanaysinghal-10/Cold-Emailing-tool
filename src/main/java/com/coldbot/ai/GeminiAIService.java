package com.coldbot.ai;

import com.coldbot.common.dto.GeneratedEmail;
import com.coldbot.common.dto.JobDetails;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class GeminiAIService implements AIService {

    private final WebClient geminiWebClient;
    private final PromptTemplateService promptTemplateService;
    private final ObjectMapper objectMapper;

    @Value("${gemini.api.key}")
    private String apiKey;

    @Value("${gemini.api.model:gemini-2.0-flash}")
    private String model;

    @Value("${gemini.api.temperature:0.7}")
    private double temperature;

    @Value("${gemini.api.max-output-tokens:2048}")
    private int maxOutputTokens;

    @Override
    public GeneratedEmail generateColdEmail(JobDetails jobDetails, String resumeSummary) {
        log.info("Generating cold email for: {} at {}", jobDetails.getJobTitle(), jobDetails.getCompanyName());

        String prompt = promptTemplateService.resolveTemplate("cold_email_default", Map.of(
                "recruiterName", safe(jobDetails.getRecruiterName(), "Hiring Manager"),
                "companyName", safe(jobDetails.getCompanyName(), "your company"),
                "jobTitle", safe(jobDetails.getJobTitle(), "the open position"),
                "requiredSkills", safe(jobDetails.getSkillsAsString(), "Not specified"),
                "experienceLevel", safe(jobDetails.getExperienceLevel(), "Not specified"),
                "resumeSummary", safe(resumeSummary, "Experienced software professional")
        ));

        String response = callGemini(prompt);
        return parseEmailResponse(response);
    }

    @Override
    public String tailorResume(String baseResumeJson, JobDetails jobDetails) {
        log.info("Tailoring resume for: {} at {}", jobDetails.getJobTitle(), jobDetails.getCompanyName());

        String prompt = promptTemplateService.resolveTemplate("resume_tailor_default", Map.of(
                "baseResumeJson", baseResumeJson,
                "jobTitle", safe(jobDetails.getJobTitle(), "the open position"),
                "companyName", safe(jobDetails.getCompanyName(), "the company"),
                "requiredSkills", safe(jobDetails.getSkillsAsString(), "Not specified"),
                "experienceLevel", safe(jobDetails.getExperienceLevel(), "Not specified")
        ));

        return callGemini(prompt);
    }

    @Override
    public JobDetails extractJobDetailsFromText(String rawJdText) {
        log.info("Extracting job details from raw JD ({} chars)", rawJdText.length());

        String prompt = promptTemplateService.resolveTemplate("jd_extraction_default", Map.of(
                "rawJdText", rawJdText
        ));

        String response = callGemini(prompt);
        return parseJobDetailsResponse(response);
    }

    private String callGemini(String prompt) {
        String url = String.format("/models/%s:generateContent?key=%s", model, apiKey);

        Map<String, Object> requestBody = Map.of(
                "contents", List.of(Map.of(
                        "parts", List.of(Map.of("text", prompt))
                )),
                "generationConfig", Map.of(
                        "temperature", temperature,
                        "maxOutputTokens", maxOutputTokens,
                        "responseMimeType", "text/plain"
                )
        );

        try {
            String responseJson = geminiWebClient.post()
                    .uri(url)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            return extractTextFromGeminiResponse(responseJson);

        } catch (WebClientResponseException e) {
            log.error("Gemini API error: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("Gemini API call failed: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Error calling Gemini API: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to call Gemini API: " + e.getMessage(), e);
        }
    }

    private String extractTextFromGeminiResponse(String responseJson) {
        try {
            JsonNode root = objectMapper.readTree(responseJson);
            JsonNode candidates = root.path("candidates");
            if (candidates.isArray() && !candidates.isEmpty()) {
                JsonNode parts = candidates.get(0).path("content").path("parts");
                if (parts.isArray() && !parts.isEmpty()) {
                    return parts.get(0).path("text").asText();
                }
            }
            throw new RuntimeException("Empty response from Gemini API");
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse Gemini response", e);
        }
    }

    private GeneratedEmail parseEmailResponse(String response) {
        try {
            String jsonStr = extractJsonFromText(response);
            JsonNode node = objectMapper.readTree(jsonStr);
            return GeneratedEmail.builder()
                    .subject(node.path("subject").asText("Application for the Position"))
                    .body(node.path("body").asText(response))
                    .build();
        } catch (Exception e) {
            log.warn("Failed to parse email JSON, using raw text: {}", e.getMessage());
            return GeneratedEmail.builder()
                    .subject("Application for the Position")
                    .body(response)
                    .build();
        }
    }

    private JobDetails parseJobDetailsResponse(String response) {
        try {
            String jsonStr = extractJsonFromText(response);
            JsonNode node = objectMapper.readTree(jsonStr);
            List<String> skills = new ArrayList<>();
            JsonNode skillsNode = node.path("requiredSkills");
            if (skillsNode.isArray()) {
                for (JsonNode skill : skillsNode) skills.add(skill.asText());
            }
            return JobDetails.builder()
                    .jobTitle(nullIfEmpty(node.path("jobTitle").asText(null)))
                    .companyName(nullIfEmpty(node.path("companyName").asText(null)))
                    .requiredSkills(skills)
                    .experienceLevel(nullIfEmpty(node.path("experienceLevel").asText(null)))
                    .recruiterName(nullIfEmpty(node.path("recruiterName").asText(null)))
                    .recruiterEmail(nullIfEmpty(node.path("recruiterEmail").asText(null)))
                    .build();
        } catch (Exception e) {
            log.warn("Failed to parse JD extraction response: {}", e.getMessage());
            return JobDetails.builder().build();
        }
    }

    private String extractJsonFromText(String text) {
        Pattern codeBlockPattern = Pattern.compile("```(?:json)?\\s*\\n?(.*?)\\n?```", Pattern.DOTALL);
        Matcher matcher = codeBlockPattern.matcher(text);
        if (matcher.find()) return matcher.group(1).trim();
        int braceStart = text.indexOf('{');
        int braceEnd = text.lastIndexOf('}');
        if (braceStart >= 0 && braceEnd > braceStart) return text.substring(braceStart, braceEnd + 1);
        return text.trim();
    }

    private String safe(String value, String defaultValue) {
        return (value != null && !value.isBlank()) ? value : defaultValue;
    }

    private String nullIfEmpty(String value) {
        return (value != null && !value.isBlank() && !value.equals("null")) ? value : null;
    }
}
