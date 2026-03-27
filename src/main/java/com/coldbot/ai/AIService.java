package com.coldbot.ai;

import com.coldbot.common.dto.GeneratedEmail;
import com.coldbot.common.dto.JobDetails;

/**
 * AI service interface for generating cold emails and tailoring resumes.
 */
public interface AIService {

    GeneratedEmail generateColdEmail(JobDetails jobDetails, String resumeSummary);

    String tailorResume(String baseResumeJson, JobDetails jobDetails);

    JobDetails extractJobDetailsFromText(String rawJdText);
}
