package com.coldbot.telegram;

import com.coldbot.common.dto.ApplicationResponse;
import com.coldbot.common.enums.ApplicationStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class TelegramMessageFormatter {

    public String formatWelcome() {
        return """
                🤖 *Cold Email Bot*
                
                I help you apply to jobs by generating personalized cold emails\\.
                
                *Commands:*
                /apply `<linkedin_url>` \\- Apply from a LinkedIn post
                /jd \\- Paste a raw job description
                /status \\- Check your latest application
                /history \\- View recent applications
                /help \\- Show this help message
                
                *How it works:*
                1\\. Send me a LinkedIn URL or paste a JD
                2\\. I'll extract job details and generate a cold email
                3\\. Review, edit, or approve the email
                4\\. I'll send it with your resume attached\\!
                
                Let's get started\\! 🚀
                """;
    }

    public String formatJobExtracted(ApplicationResponse app) {
        StringBuilder sb = new StringBuilder("📋 *Job Details Extracted*\n\n");
        sb.append(formatField("Job Title", app.getJobTitle()));
        sb.append(formatField("Company", app.getCompanyName()));
        sb.append(formatField("Skills", app.getRequiredSkills()));
        sb.append(formatField("Experience", app.getExperienceLevel()));
        sb.append(formatField("Recruiter", app.getRecruiterName()));
        sb.append(formatField("Email", app.getRecruiterEmail()));
        sb.append("\n⏳ _Generating cold email\\.\\.\\._");
        return sb.toString();
    }

    public String formatEmailGenerated(ApplicationResponse app) {
        StringBuilder sb = new StringBuilder("✉️ *Generated Cold Email*\n\n");
        sb.append("*Subject:* ").append(escape(safe(app.getGeneratedEmailSubject()))).append("\n\n");
        sb.append("*Body:*\n").append(escape(safe(app.getGeneratedEmailBody()))).append("\n\n");
        sb.append("━━━━━━━━━━━━━━━━━━━\n");
        sb.append("📧 *To:* ").append(escape(safe(app.getRecruiterEmail(), "Not found"))).append("\n");
        sb.append("📎 *Resume:* ").append(escape(safe(app.getResumeUsed(), "Default"))).append("\n\n");
        if (app.getRecruiterEmail() == null || app.getRecruiterEmail().isBlank()) {
            sb.append("⚠️ _No recruiter email found\\. Use Edit to add one\\._\n\n");
        }
        sb.append("Choose an action:");
        return sb.toString();
    }

    public String formatProcessing() { return "⏳ _Processing your request\\.\\.\\. This may take a moment\\._"; }
    public String formatScraping() { return "🔍 _Extracting job details\\.\\.\\._"; }
    public String formatCancelled() { return "❌ Application cancelled\\."; }

    public String formatSent(ApplicationResponse app) {
        return "✅ *Email Sent Successfully\\!*\n\nTo: " + escape(safe(app.getRecruiterEmail())) +
               "\nSubject: " + escape(safe(app.getGeneratedEmailSubject())) + "\n\n_Good luck with your application\\!_ 🍀";
    }

    public String formatError(String errorMessage) {
        return "❌ *Error*\n\n" + escape(safe(errorMessage)) + "\n\n_Please try again or use /jd to paste the JD manually\\._";
    }

    public String formatHistory(java.util.List<ApplicationResponse> apps) {
        if (apps == null || apps.isEmpty()) return "📭 _No applications yet\\. Use /apply or /jd to get started\\!_";
        StringBuilder sb = new StringBuilder("📊 *Recent Applications*\n\n");
        int count = 0;
        for (ApplicationResponse app : apps) {
            if (count >= 10) break;
            sb.append(formatStatusEmoji(app.getStatus())).append(" ").append(escape(safe(app.getJobTitle(), "Unknown")))
              .append(" at ").append(escape(safe(app.getCompanyName(), "Unknown")))
              .append(" \\- _").append(escape(app.getStatus().name())).append("_\n");
            count++;
        }
        return sb.toString();
    }

    public String formatStatus(ApplicationResponse app) {
        StringBuilder sb = new StringBuilder();
        sb.append(formatStatusEmoji(app.getStatus())).append(" *Application Status*\n\n")
          .append(formatField("Job", app.getJobTitle())).append(formatField("Company", app.getCompanyName()))
          .append(formatField("Status", app.getStatus().name())).append(formatField("Recruiter Email", app.getRecruiterEmail()));
        if (app.getErrorMessage() != null) sb.append("\n⚠️ Error: ").append(escape(app.getErrorMessage()));
        return sb.toString();
    }

    public String formatEditPrompt() {
        return """
                ✏️ *Edit Email*
                
                Send me the updated content in this format:
                
                `email: recruiter@email\\.com`
                `subject: Your new subject`
                `body: Your new email body`
                
                You can include any or all of these fields\\.
                """;
    }

    public String formatAskForJd() { return "📝 _Please paste the job description text in your next message\\._"; }

    private String formatField(String label, String value) {
        if (value == null || value.isBlank()) return "";
        return "*" + escape(label) + ":* " + escape(value) + "\n";
    }

    private String formatStatusEmoji(ApplicationStatus status) {
        return switch (status) {
            case PENDING -> "⏳"; case SCRAPING -> "🔍"; case SCRAPED -> "📋";
            case EMAIL_GENERATED, AWAITING_APPROVAL -> "✉️"; case APPROVED, SENDING -> "📤";
            case SENT -> "✅"; case FAILED -> "❌"; case CANCELLED -> "🚫";
        };
    }

    private String escape(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\").replace("_", "\\_").replace("*", "\\*")
                .replace("[", "\\[").replace("]", "\\]").replace("(", "\\(").replace(")", "\\)")
                .replace("~", "\\~").replace("`", "\\`").replace(">", "\\>").replace("#", "\\#")
                .replace("+", "\\+").replace("-", "\\-").replace("=", "\\=").replace("|", "\\|")
                .replace("{", "\\{").replace("}", "\\}").replace(".", "\\.").replace("!", "\\!");
    }

    private String safe(String value) { return value != null ? value : "N/A"; }
    private String safe(String value, String defaultValue) { return (value != null && !value.isBlank()) ? value : defaultValue; }
}
