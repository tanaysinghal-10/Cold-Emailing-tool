package com.coldbot.resume;

import com.coldbot.common.dto.JobDetails;
import com.coldbot.job.entity.User;
import com.coldbot.job.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Slf4j
@Service
@ConditionalOnProperty(name = "resume.mode", havingValue = "MODE_A", matchIfMissing = true)
@RequiredArgsConstructor
public class StaticResumeService implements ResumeService {

    private final UserRepository userRepository;

    @Value("${resume.static-path:resumes/Tanay_Singhal_Resume.pdf}")
    private String defaultResumePath;

    @Override
    public byte[] getResume(Long userId, JobDetails jobDetails) {
        String resumePath = getResumePath(userId);
        Path path = Paths.get(resumePath);
        if (!Files.exists(path)) {
            log.warn("Resume file not found at: {}", resumePath);
            return null;
        }
        try {
            byte[] bytes = Files.readAllBytes(path);
            log.info("Loaded static resume: {} ({} bytes)", resumePath, bytes.length);
            return bytes;
        } catch (IOException e) {
            log.error("Failed to read resume file: {}", resumePath, e);
            return null;
        }
    }

    @Override
    public String getResumeSummary(Long userId) {
        String resumePath = getResumePath(userId);
        Path path = Paths.get(resumePath);
        if (!Files.exists(path)) {
            return "Experienced professional seeking new opportunities.";
        }
        try {
            PDDocument document = Loader.loadPDF(path.toFile());
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);
            document.close();
            if (text.length() > 2000) text = text.substring(0, 2000) + "...";
            return text;
        } catch (IOException e) {
            log.error("Failed to extract text from resume: {}", resumePath, e);
            return "Experienced professional seeking new opportunities.";
        }
    }

    @Override
    public String getResumeFilename(Long userId, JobDetails jobDetails) {
        return "Tanay_Singhal_Resume.pdf";
    }

    private String getResumePath(Long userId) {
        if (userId != null) {
            User user = userRepository.findById(userId).orElse(null);
            if (user != null && user.getResumePath() != null && !user.getResumePath().isBlank()) {
                return user.getResumePath();
            }
        }
        return defaultResumePath;
    }
}
