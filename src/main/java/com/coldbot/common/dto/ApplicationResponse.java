package com.coldbot.common.dto;

import com.coldbot.common.enums.ApplicationStatus;
import com.coldbot.common.enums.SourceType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApplicationResponse {

    private UUID id;
    private ApplicationStatus status;
    private SourceType sourceType;
    private String sourceInput;
    private String jobTitle;
    private String companyName;
    private String requiredSkills;
    private String experienceLevel;
    private String recruiterName;
    private String recruiterEmail;
    private String generatedEmailSubject;
    private String generatedEmailBody;
    private String resumeUsed;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime sentAt;
}
