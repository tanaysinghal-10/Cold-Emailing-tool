package com.coldbot.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobDetails {

    private String jobTitle;
    private String companyName;
    private List<String> requiredSkills;
    private String experienceLevel;
    private String recruiterName;
    private String recruiterEmail;

    public String getSkillsAsString() {
        if (requiredSkills == null || requiredSkills.isEmpty()) {
            return "Not specified";
        }
        return String.join(", ", requiredSkills);
    }
}
