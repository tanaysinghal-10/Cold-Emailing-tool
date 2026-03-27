package com.coldbot.scraping;

import com.coldbot.common.dto.JobDetails;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses raw job description text into structured JobDetails using heuristics.
 * Used as a fallback when scraping fails or when user pastes JD directly.
 */
@Slf4j
@Service
public class JdParserService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}"
    );

    public JobDetails parseRawJd(String rawJd) {
        log.info("Parsing raw JD text ({} chars)", rawJd.length());

        return JobDetails.builder()
                .jobTitle(extractJobTitle(rawJd))
                .companyName(extractCompanyName(rawJd))
                .requiredSkills(extractSkills(rawJd))
                .experienceLevel(extractExperienceLevel(rawJd))
                .recruiterName(extractRecruiterName(rawJd))
                .recruiterEmail(extractEmail(rawJd))
                .build();
    }

    private String extractJobTitle(String text) {
        // Look for common title patterns
        String[] patterns = {
                "(?:job title|position|role|title)\\s*:?\\s*(.+?)(?:\\n|$)",
                "(?:hiring|looking for|we need)\\s+(?:a\\s+)?(.+?)(?:\\n|\\.|$)",
                "^(.+?)(?:at|@|\\-|–)\\s+\\w+",  // "Software Engineer at Company"
        };

        for (String pattern : patterns) {
            Matcher matcher = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE | Pattern.MULTILINE).matcher(text);
            if (matcher.find()) {
                String title = matcher.group(1).trim();
                if (title.length() > 5 && title.length() < 100) {
                    return title;
                }
            }
        }

        // Take first line if it looks like a title
        String firstLine = text.split("\\n")[0].trim();
        if (firstLine.length() > 3 && firstLine.length() < 100 &&
            !firstLine.toLowerCase().startsWith("we") &&
            !firstLine.toLowerCase().startsWith("about")) {
            return firstLine;
        }

        return null;
    }

    private String extractCompanyName(String text) {
        String[] patterns = {
                "(?:company|organization|employer)\\s*:?\\s*(.+?)(?:\\n|$)",
                "(?:at|@)\\s+([A-Z][a-zA-Z0-9\\s&]+?)(?:\\n|\\.|,|$)",
                "(?:about)\\s+([A-Z][a-zA-Z0-9\\s&]+?)(?:\\n|\\.|:)"
        };

        for (String pattern : patterns) {
            Matcher matcher = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE).matcher(text);
            if (matcher.find()) {
                String name = matcher.group(1).trim();
                if (name.length() > 1 && name.length() < 100) {
                    return name;
                }
            }
        }

        return null;
    }

    private List<String> extractSkills(String text) {
        List<String> skills = new ArrayList<>();
        String lowerText = text.toLowerCase();

        String[] commonSkills = {
                "Java", "Python", "JavaScript", "TypeScript", "React", "Angular", "Vue",
                "Node.js", "Spring Boot", "Spring", "Django", "Flask", "AWS", "Azure", "GCP",
                "Docker", "Kubernetes", "SQL", "PostgreSQL", "MySQL", "MongoDB", "Redis",
                "Kafka", "RabbitMQ", "Git", "CI/CD", "REST", "GraphQL", "Microservices",
                "Machine Learning", "Data Science", "DevOps", "Agile", "Scrum",
                "HTML", "CSS", "C++", "C#", ".NET", "Go", "Rust", "Scala", "Kotlin",
                "Swift", "iOS", "Android", "Flutter", "React Native", "Terraform",
                "Jenkins", "Linux", "System Design", "Data Structures", "Algorithms",
                "TensorFlow", "PyTorch", "NLP", "Computer Vision", "Deep Learning",
                "Figma", "UI/UX", "Product Management", "Project Management"
        };

        for (String skill : commonSkills) {
            if (lowerText.contains(skill.toLowerCase())) {
                skills.add(skill);
            }
        }

        return skills;
    }

    private String extractExperienceLevel(String text) {
        String lowerText = text.toLowerCase();

        if (lowerText.contains("senior") || lowerText.contains("sr.") || lowerText.contains("lead")) {
            return "Senior";
        } else if (lowerText.contains("mid-level") || lowerText.contains("mid level")) {
            return "Mid-Level";
        } else if (lowerText.contains("junior") || lowerText.contains("jr.") ||
                   lowerText.contains("entry level") || lowerText.contains("entry-level") ||
                   lowerText.contains("fresher")) {
            return "Entry Level";
        } else if (lowerText.contains("principal") || lowerText.contains("staff") ||
                   lowerText.contains("architect")) {
            return "Staff/Principal";
        }

        Pattern pattern = Pattern.compile("(\\d+)\\+?\\s*(?:years?|yrs?)");
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            int years = Integer.parseInt(matcher.group(1));
            if (years <= 2) return "Entry Level";
            if (years <= 5) return "Mid-Level";
            return "Senior";
        }

        return null;
    }

    private String extractRecruiterName(String text) {
        String[] patterns = {
                "(?:contact|reach out to|DM|email|apply to|send.*to)\\s+([A-Z][a-z]+\\s+[A-Z][a-z]+)",
                "(?:recruiter|hiring manager|HR)\\s*:?\\s*([A-Z][a-z]+\\s+[A-Z][a-z]+)"
        };

        for (String pattern : patterns) {
            Matcher matcher = Pattern.compile(pattern).matcher(text);
            if (matcher.find()) {
                return matcher.group(1).trim();
            }
        }

        return null;
    }

    private String extractEmail(String text) {
        Matcher matcher = EMAIL_PATTERN.matcher(text);
        if (matcher.find()) {
            return matcher.group();
        }
        return null;
    }
}
