package com.coldbot.resume;

import com.coldbot.ai.AIService;
import com.coldbot.common.dto.JobDetails;
import com.coldbot.job.entity.User;
import com.coldbot.job.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@ConditionalOnProperty(name = "resume.mode", havingValue = "MODE_B")
@RequiredArgsConstructor
public class TailoredResumeService implements ResumeService {

    private final AIService aiService;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    @Override
    public byte[] getResume(Long userId, JobDetails jobDetails) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        String baseResumeJson = user.getResumeJson();
        if (baseResumeJson == null || baseResumeJson.isBlank()) {
            throw new IllegalStateException("Mode B requires a base resume in JSON format. Use /setresume command.");
        }
        String tailoredJson = aiService.tailorResume(baseResumeJson, jobDetails);
        return generatePdfFromJson(tailoredJson);
    }

    @Override
    public String getResumeSummary(Long userId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user != null && user.getResumeJson() != null) {
            try {
                JsonNode root = objectMapper.readTree(user.getResumeJson());
                StringBuilder summary = new StringBuilder();
                if (root.has("name")) summary.append("Name: ").append(root.get("name").asText()).append("\n");
                if (root.has("title")) summary.append("Title: ").append(root.get("title").asText()).append("\n");
                if (root.has("summary")) summary.append("Summary: ").append(root.get("summary").asText()).append("\n");
                if (root.has("skills")) summary.append("Skills: ").append(root.get("skills").toString()).append("\n");
                if (root.has("experience") && root.get("experience").isArray()) {
                    summary.append("Experience:\n");
                    for (JsonNode exp : root.get("experience")) {
                        summary.append("- ").append(exp.path("title").asText("")).append(" at ")
                                .append(exp.path("company").asText("")).append(" (")
                                .append(exp.path("duration").asText("")).append(")\n");
                    }
                }
                return summary.toString();
            } catch (Exception e) {
                log.warn("Failed to parse resume JSON for summary: {}", e.getMessage());
            }
        }
        return "Experienced professional seeking new opportunities.";
    }

    @Override
    public String getResumeFilename(Long userId, JobDetails jobDetails) {
        String name = "Tanay_Singhal_Resume";
        if (jobDetails != null && jobDetails.getCompanyName() != null) {
            name = "Tanay_Singhal_Resume_" + jobDetails.getCompanyName()
                    .replaceAll("[^a-zA-Z0-9]", "_").toLowerCase();
        }
        return name + ".pdf";
    }

    private byte[] generatePdfFromJson(String resumeJson) {
        try {
            JsonNode root = objectMapper.readTree(resumeJson);
            try (PDDocument document = new PDDocument()) {
                PDPage page = new PDPage(PDRectangle.A4);
                document.addPage(page);
                PDType1Font titleFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
                PDType1Font headingFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
                PDType1Font bodyFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
                float margin = 50;
                float yPosition = page.getMediaBox().getHeight() - margin;
                float pageWidth = page.getMediaBox().getWidth() - 2 * margin;
                PDPageContentStream contentStream = new PDPageContentStream(document, page);

                String name = root.path("name").asText("Candidate Name");
                yPosition = drawText(contentStream, name, titleFont, 18, margin, yPosition, pageWidth);
                yPosition -= 5;
                String email = root.path("email").asText("");
                String phone = root.path("phone").asText("");
                String contact = email + (phone.isEmpty() ? "" : " | " + phone);
                if (!contact.isBlank()) yPosition = drawText(contentStream, contact, bodyFont, 10, margin, yPosition, pageWidth);
                yPosition -= 15;

                if (root.has("summary") && !root.get("summary").asText().isBlank()) {
                    yPosition = drawText(contentStream, "PROFESSIONAL SUMMARY", headingFont, 12, margin, yPosition, pageWidth);
                    yPosition -= 3;
                    yPosition = drawLine(contentStream, margin, yPosition, margin + pageWidth);
                    yPosition -= 10;
                    yPosition = drawWrappedText(contentStream, root.get("summary").asText(), bodyFont, 10, margin, yPosition, pageWidth);
                    yPosition -= 15;
                }
                if (root.has("skills")) {
                    yPosition = drawText(contentStream, "SKILLS", headingFont, 12, margin, yPosition, pageWidth);
                    yPosition -= 3;
                    yPosition = drawLine(contentStream, margin, yPosition, margin + pageWidth);
                    yPosition -= 10;
                    JsonNode skills = root.get("skills");
                    String skillsText = skills.isArray() ? joinArray(skills) : skills.asText();
                    yPosition = drawWrappedText(contentStream, skillsText, bodyFont, 10, margin, yPosition, pageWidth);
                    yPosition -= 15;
                }
                if (root.has("experience") && root.get("experience").isArray()) {
                    yPosition = drawText(contentStream, "EXPERIENCE", headingFont, 12, margin, yPosition, pageWidth);
                    yPosition -= 3;
                    yPosition = drawLine(contentStream, margin, yPosition, margin + pageWidth);
                    yPosition -= 10;
                    for (JsonNode exp : root.get("experience")) {
                        if (yPosition < 100) {
                            contentStream.close();
                            page = new PDPage(PDRectangle.A4);
                            document.addPage(page);
                            contentStream = new PDPageContentStream(document, page);
                            yPosition = page.getMediaBox().getHeight() - margin;
                        }
                        String title = exp.path("title").asText("") + " at " + exp.path("company").asText("");
                        yPosition = drawText(contentStream, title, headingFont, 10, margin, yPosition, pageWidth);
                        String duration = exp.path("duration").asText("");
                        if (!duration.isBlank()) yPosition = drawText(contentStream, duration, bodyFont, 9, margin, yPosition, pageWidth);
                        yPosition -= 3;
                        JsonNode bullets = exp.path("bullets");
                        if (bullets.isArray()) {
                            for (JsonNode bullet : bullets) {
                                yPosition = drawWrappedText(contentStream, "• " + bullet.asText(), bodyFont, 9, margin + 10, yPosition, pageWidth - 10);
                                yPosition -= 2;
                            }
                        }
                        yPosition -= 10;
                    }
                }
                if (root.has("education") && root.get("education").isArray()) {
                    if (yPosition < 100) {
                        contentStream.close();
                        page = new PDPage(PDRectangle.A4);
                        document.addPage(page);
                        contentStream = new PDPageContentStream(document, page);
                        yPosition = page.getMediaBox().getHeight() - margin;
                    }
                    yPosition = drawText(contentStream, "EDUCATION", headingFont, 12, margin, yPosition, pageWidth);
                    yPosition -= 3;
                    yPosition = drawLine(contentStream, margin, yPosition, margin + pageWidth);
                    yPosition -= 10;
                    for (JsonNode edu : root.get("education")) {
                        String degree = edu.path("degree").asText("") + " - " + edu.path("institution").asText("");
                        yPosition = drawText(contentStream, degree, headingFont, 10, margin, yPosition, pageWidth);
                        String year = edu.path("year").asText("");
                        if (!year.isBlank()) yPosition = drawText(contentStream, year, bodyFont, 9, margin, yPosition, pageWidth);
                        yPosition -= 8;
                    }
                }
                contentStream.close();
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                document.save(baos);
                return baos.toByteArray();
            }
        } catch (Exception e) {
            throw new RuntimeException("PDF generation failed: " + e.getMessage(), e);
        }
    }

    private float drawText(PDPageContentStream cs, String text, PDType1Font font, float fontSize, float x, float y, float maxWidth) throws IOException {
        cs.beginText(); cs.setFont(font, fontSize); cs.newLineAtOffset(x, y);
        cs.showText(truncateToFit(text, font, fontSize, maxWidth)); cs.endText();
        return y - fontSize - 3;
    }

    private float drawWrappedText(PDPageContentStream cs, String text, PDType1Font font, float fontSize, float x, float y, float maxWidth) throws IOException {
        for (String line : wrapText(text, font, fontSize, maxWidth)) {
            cs.beginText(); cs.setFont(font, fontSize); cs.newLineAtOffset(x, y);
            cs.showText(line); cs.endText(); y -= fontSize + 2;
        }
        return y;
    }

    private float drawLine(PDPageContentStream cs, float x1, float y, float x2) throws IOException {
        cs.moveTo(x1, y); cs.lineTo(x2, y); cs.stroke(); return y - 2;
    }

    private List<String> wrapText(String text, PDType1Font font, float fontSize, float maxWidth) {
        List<String> lines = new ArrayList<>(); String[] words = text.split("\\s+");
        StringBuilder currentLine = new StringBuilder();
        for (String word : words) {
            String testLine = currentLine.isEmpty() ? word : currentLine + " " + word;
            try {
                if (font.getStringWidth(testLine) / 1000 * fontSize > maxWidth && !currentLine.isEmpty()) {
                    lines.add(currentLine.toString()); currentLine = new StringBuilder(word);
                } else { currentLine = new StringBuilder(testLine); }
            } catch (IOException e) { currentLine = new StringBuilder(testLine); }
        }
        if (!currentLine.isEmpty()) lines.add(currentLine.toString());
        return lines;
    }

    private String truncateToFit(String text, PDType1Font font, float fontSize, float maxWidth) {
        try {
            if (font.getStringWidth(text) / 1000 * fontSize <= maxWidth) return text;
            while (text.length() > 3 && font.getStringWidth(text + "...") / 1000 * fontSize > maxWidth)
                text = text.substring(0, text.length() - 1);
            return text + "...";
        } catch (IOException e) { return text; }
    }

    private String joinArray(JsonNode arrayNode) {
        List<String> items = new ArrayList<>();
        for (JsonNode node : arrayNode) items.add(node.asText());
        return String.join(", ", items);
    }
}
