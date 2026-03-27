package com.coldbot.resume;

import com.coldbot.common.dto.JobDetails;

public interface ResumeService {
    byte[] getResume(Long userId, JobDetails jobDetails);
    String getResumeSummary(Long userId);
    String getResumeFilename(Long userId, JobDetails jobDetails);
}
