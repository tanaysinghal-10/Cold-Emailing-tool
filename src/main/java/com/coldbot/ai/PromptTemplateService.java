package com.coldbot.ai;

import com.coldbot.job.entity.PromptTemplate;
import com.coldbot.job.repository.PromptTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PromptTemplateService {

    private final PromptTemplateRepository promptTemplateRepository;

    public String resolveTemplate(String templateName, Map<String, String> variables) {
        PromptTemplate template = promptTemplateRepository
                .findByTemplateNameAndIsActiveTrue(templateName)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Prompt template not found or inactive: " + templateName));

        String content = template.getTemplateContent();
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            content = content.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        return content;
    }

    public String getRawTemplate(String templateName) {
        return promptTemplateRepository
                .findByTemplateNameAndIsActiveTrue(templateName)
                .map(PromptTemplate::getTemplateContent)
                .orElseThrow(() -> new IllegalArgumentException("Template not found: " + templateName));
    }

    public void updateTemplate(String templateName, String newContent) {
        PromptTemplate template = promptTemplateRepository
                .findByTemplateName(templateName)
                .orElseThrow(() -> new IllegalArgumentException("Template not found: " + templateName));
        template.setTemplateContent(newContent);
        promptTemplateRepository.save(template);
        log.info("Updated prompt template: {}", templateName);
    }
}
