package com.coldbot.application;

import com.coldbot.ai.AIService;
import com.coldbot.common.dto.ApplicationResponse;
import com.coldbot.common.dto.CreateApplicationRequest;
import com.coldbot.common.dto.GeneratedEmail;
import com.coldbot.common.dto.JobDetails;
import com.coldbot.common.enums.ApplicationStatus;
import com.coldbot.common.enums.SourceType;
import com.coldbot.common.exception.RateLimitExceededException;
import com.coldbot.common.exception.RecruiterEmailNotFoundException;
import com.coldbot.common.exception.ScrapingException;
import com.coldbot.email.EmailService;
import com.coldbot.email.SmtpEmailService;
import com.coldbot.job.entity.AuditLog;
import com.coldbot.job.entity.JobApplication;
import com.coldbot.job.entity.User;
import com.coldbot.job.repository.AuditLogRepository;
import com.coldbot.job.repository.JobApplicationRepository;
import com.coldbot.job.repository.UserRepository;
import com.coldbot.resume.ResumeService;
import com.coldbot.scraping.JdParserService;
import com.coldbot.scraping.ScrapingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApplicationOrchestrator {

    private final ScrapingService scrapingService;
    private final JdParserService jdParserService;
    private final AIService aiService;
    private final EmailService emailService;
    private final ResumeService resumeService;
    private final JobApplicationRepository applicationRepository;
    private final UserRepository userRepository;
    private final AuditLogRepository auditLogRepository;
    private final SmtpEmailService smtpEmailService;

    @Async("applicationTaskExecutor")
    public void processApplication(CreateApplicationRequest request, Consumer<ApplicationResponse> onUpdate) {
        User user = getOrCreateUser(request.getTelegramChatId());
        JobApplication app = JobApplication.builder()
                .user(user).sourceType(request.getSourceType())
                .sourceInput(request.getSourceInput()).status(ApplicationStatus.PENDING).build();
        app = applicationRepository.save(app);
        audit(app.getId(), "CREATED", "Application created from " + request.getSourceType());
        try {
            updateStatus(app, ApplicationStatus.SCRAPING);
            notifyUpdate(app, onUpdate);
            JobDetails jobDetails = extractJobDetails(request, app);
            applyJobDetails(app, jobDetails);
            updateStatus(app, ApplicationStatus.SCRAPED);
            audit(app.getId(), "SCRAPED", "Job: " + app.getJobTitle() + " at " + app.getCompanyName());
            byte[] resumeBytes = resumeService.getResume(user.getId(), jobDetails);
            String resumeSummary = resumeService.getResumeSummary(user.getId());
            String resumeFilename = resumeService.getResumeFilename(user.getId(), jobDetails);
            app.setResumeUsed(resumeFilename);
            GeneratedEmail email = aiService.generateColdEmail(jobDetails, resumeSummary);
            app.setGeneratedEmailSubject(email.getSubject());
            app.setGeneratedEmailBody(email.getBody());
            updateStatus(app, ApplicationStatus.EMAIL_GENERATED);
            audit(app.getId(), "EMAIL_GENERATED", "Subject: " + email.getSubject());
            updateStatus(app, ApplicationStatus.AWAITING_APPROVAL);
            notifyUpdate(app, onUpdate);
        } catch (ScrapingException e) {
            handleError(app, "Scraping failed: " + e.getMessage(), onUpdate);
        } catch (Exception e) {
            handleError(app, "Processing error: " + e.getMessage(), onUpdate);
            log.error("Application processing failed for {}: {}", app.getId(), e.getMessage(), e);
        }
    }

    @Transactional
    public void approveAndSend(UUID applicationId, Consumer<ApplicationResponse> onUpdate) {
        JobApplication app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("Application not found: " + applicationId));
        if (app.getStatus() != ApplicationStatus.AWAITING_APPROVAL && app.getStatus() != ApplicationStatus.EMAIL_GENERATED) {
            throw new IllegalStateException("Application is not awaiting approval. Status: " + app.getStatus());
        }
        if (app.getRecruiterEmail() == null || app.getRecruiterEmail().isBlank()) {
            throw new RecruiterEmailNotFoundException("No recruiter email found. Please provide one using the edit option.");
        }
        if (!emailService.canSendEmail(app.getUser().getId())) {
            throw new RateLimitExceededException("Daily email limit reached. Try again tomorrow.");
        }
        try {
            updateStatus(app, ApplicationStatus.SENDING);
            JobDetails jobDetails = buildJobDetails(app);
            byte[] resumeBytes = resumeService.getResume(app.getUser().getId(), jobDetails);
            String filename = resumeService.getResumeFilename(app.getUser().getId(), jobDetails);
            emailService.sendEmail(app.getRecruiterEmail(), app.getGeneratedEmailSubject(), app.getGeneratedEmailBody(), resumeBytes, filename);
            smtpEmailService.recordEmailSent(app.getUser().getId());
            app.setSentAt(LocalDateTime.now());
            updateStatus(app, ApplicationStatus.SENT);
            audit(app.getId(), "SENT", "Email sent to " + app.getRecruiterEmail());
            notifyUpdate(app, onUpdate);
        } catch (RateLimitExceededException e) {
            handleError(app, e.getMessage(), onUpdate); throw e;
        } catch (Exception e) {
            handleError(app, "Email sending failed: " + e.getMessage(), onUpdate);
            log.error("Failed to send email for application {}: {}", applicationId, e.getMessage(), e);
        }
    }

    @Transactional
    public ApplicationResponse updateEmail(UUID applicationId, String newSubject, String newBody, String recruiterEmail) {
        JobApplication app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("Application not found: " + applicationId));
        if (newSubject != null && !newSubject.isBlank()) app.setGeneratedEmailSubject(newSubject);
        if (newBody != null && !newBody.isBlank()) app.setGeneratedEmailBody(newBody);
        if (recruiterEmail != null && !recruiterEmail.isBlank()) app.setRecruiterEmail(recruiterEmail);
        app.setStatus(ApplicationStatus.AWAITING_APPROVAL);
        applicationRepository.save(app);
        audit(app.getId(), "EDITED", "Email content updated");
        return toResponse(app);
    }

    @Transactional
    public ApplicationResponse cancelApplication(UUID applicationId) {
        JobApplication app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("Application not found: " + applicationId));
        updateStatus(app, ApplicationStatus.CANCELLED);
        audit(app.getId(), "CANCELLED", "Application cancelled by user");
        return toResponse(app);
    }

    public ApplicationResponse getApplication(UUID applicationId) {
        JobApplication app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("Application not found: " + applicationId));
        return toResponse(app);
    }

    public List<ApplicationResponse> getApplicationsByTelegramChatId(Long chatId) {
        return applicationRepository.findByUserTelegramChatIdOrderByCreatedAtDesc(chatId)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    private JobDetails extractJobDetails(CreateApplicationRequest request, JobApplication app) {
        if (request.getSourceType() == SourceType.LINKEDIN_URL) {
            try { return scrapingService.scrapeLinkedInPost(request.getSourceInput()); }
            catch (ScrapingException e) {
                log.warn("LinkedIn scraping failed, falling back to AI: {}", e.getMessage());
                app.setErrorMessage("Scraping failed, used AI fallback");
                return aiService.extractJobDetailsFromText(request.getSourceInput());
            }
        } else {
            JobDetails heuristicResult = jdParserService.parseRawJd(request.getSourceInput());
            if (heuristicResult.getJobTitle() == null && heuristicResult.getCompanyName() == null) {
                return aiService.extractJobDetailsFromText(request.getSourceInput());
            }
            if (heuristicResult.getJobTitle() == null || heuristicResult.getCompanyName() == null ||
                heuristicResult.getRequiredSkills() == null || heuristicResult.getRequiredSkills().isEmpty()) {
                try {
                    JobDetails aiResult = aiService.extractJobDetailsFromText(request.getSourceInput());
                    return mergeJobDetails(heuristicResult, aiResult);
                } catch (Exception e) { log.warn("AI extraction failed: {}", e.getMessage()); }
            }
            return heuristicResult;
        }
    }

    private JobDetails mergeJobDetails(JobDetails primary, JobDetails secondary) {
        return JobDetails.builder()
                .jobTitle(primary.getJobTitle() != null ? primary.getJobTitle() : secondary.getJobTitle())
                .companyName(primary.getCompanyName() != null ? primary.getCompanyName() : secondary.getCompanyName())
                .requiredSkills(primary.getRequiredSkills() != null && !primary.getRequiredSkills().isEmpty() ? primary.getRequiredSkills() : secondary.getRequiredSkills())
                .experienceLevel(primary.getExperienceLevel() != null ? primary.getExperienceLevel() : secondary.getExperienceLevel())
                .recruiterName(primary.getRecruiterName() != null ? primary.getRecruiterName() : secondary.getRecruiterName())
                .recruiterEmail(primary.getRecruiterEmail() != null ? primary.getRecruiterEmail() : secondary.getRecruiterEmail())
                .build();
    }

    private void applyJobDetails(JobApplication app, JobDetails details) {
        app.setJobTitle(details.getJobTitle()); app.setCompanyName(details.getCompanyName());
        app.setRequiredSkills(details.getSkillsAsString()); app.setExperienceLevel(details.getExperienceLevel());
        app.setRecruiterName(details.getRecruiterName()); app.setRecruiterEmail(details.getRecruiterEmail());
        applicationRepository.save(app);
    }

    private void updateStatus(JobApplication app, ApplicationStatus status) {
        app.setStatus(status); applicationRepository.save(app);
    }

    private void handleError(JobApplication app, String errorMessage, Consumer<ApplicationResponse> onUpdate) {
        app.setErrorMessage(errorMessage); updateStatus(app, ApplicationStatus.FAILED);
        audit(app.getId(), "FAILED", errorMessage); notifyUpdate(app, onUpdate);
    }

    private void notifyUpdate(JobApplication app, Consumer<ApplicationResponse> onUpdate) {
        if (onUpdate != null) { try { onUpdate.accept(toResponse(app)); } catch (Exception e) { log.warn("Notification failed: {}", e.getMessage()); } }
    }

    private User getOrCreateUser(Long telegramChatId) {
        return userRepository.findByTelegramChatId(telegramChatId)
                .orElseGet(() -> userRepository.save(User.builder().telegramChatId(telegramChatId).build()));
    }

    private void audit(UUID applicationId, String action, String details) {
        auditLogRepository.save(AuditLog.builder().applicationId(applicationId).action(action).details(details).build());
    }

    private JobDetails buildJobDetails(JobApplication app) {
        return JobDetails.builder().jobTitle(app.getJobTitle()).companyName(app.getCompanyName())
                .experienceLevel(app.getExperienceLevel()).recruiterName(app.getRecruiterName())
                .recruiterEmail(app.getRecruiterEmail()).build();
    }

    public ApplicationResponse toResponse(JobApplication app) {
        return ApplicationResponse.builder().id(app.getId()).status(app.getStatus()).sourceType(app.getSourceType())
                .sourceInput(app.getSourceInput()).jobTitle(app.getJobTitle()).companyName(app.getCompanyName())
                .requiredSkills(app.getRequiredSkills()).experienceLevel(app.getExperienceLevel())
                .recruiterName(app.getRecruiterName()).recruiterEmail(app.getRecruiterEmail())
                .generatedEmailSubject(app.getGeneratedEmailSubject()).generatedEmailBody(app.getGeneratedEmailBody())
                .resumeUsed(app.getResumeUsed()).errorMessage(app.getErrorMessage())
                .createdAt(app.getCreatedAt()).sentAt(app.getSentAt()).build();
    }
}
