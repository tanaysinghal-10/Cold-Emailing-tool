package com.coldbot.job.repository;

import com.coldbot.job.entity.PromptTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PromptTemplateRepository extends JpaRepository<PromptTemplate, Long> {

    Optional<PromptTemplate> findByTemplateNameAndIsActiveTrue(String templateName);

    Optional<PromptTemplate> findByTemplateName(String templateName);
}
