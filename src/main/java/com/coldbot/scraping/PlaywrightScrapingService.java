package com.coldbot.scraping;

import com.coldbot.common.dto.JobDetails;
import com.coldbot.common.exception.ScrapingException;
import com.microsoft.playwright.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class PlaywrightScrapingService implements ScrapingService {

    @Value("${scraping.timeout-ms:30000}")
    private int timeoutMs;

    @Value("${scraping.max-retries:3}")
    private int maxRetries;

    @Value("${scraping.headless:true}")
    private boolean headless;

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}"
    );

    @Override
    public JobDetails scrapeLinkedInPost(String url) {
        log.info("Scraping LinkedIn post: {}", url);

        int attempt = 0;
        Exception lastException = null;

        while (attempt < maxRetries) {
            attempt++;
            try {
                return doScrape(url);
            } catch (Exception e) {
                lastException = e;
                log.warn("Scraping attempt {}/{} failed: {}", attempt, maxRetries, e.getMessage());
                if (attempt < maxRetries) {
                    try {
                        Thread.sleep(1000L * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new ScrapingException("Scraping interrupted", ie);
                    }
                }
            }
        }

        throw new ScrapingException(
                "Failed to scrape LinkedIn post after " + maxRetries + " attempts: " +
                (lastException != null ? lastException.getMessage() : "Unknown error"),
                lastException
        );
    }

    private JobDetails doScrape(String url) {
        try (Playwright playwright = Playwright.create()) {
            BrowserType.LaunchOptions launchOptions = new BrowserType.LaunchOptions()
                    .setHeadless(headless);

            try (Browser browser = playwright.chromium().launch(launchOptions)) {
                BrowserContext context = browser.newContext(new Browser.NewContextOptions()
                        .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                                "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"));

                Page page = context.newPage();
                page.setDefaultTimeout(timeoutMs);

                page.navigate(url);
                page.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE);
                page.waitForTimeout(2000);

                String textContent = page.innerText("body");

                JobDetails details = JobDetails.builder()
                        .jobTitle(extractJobTitle(page, textContent))
                        .companyName(extractCompanyName(page, textContent))
                        .requiredSkills(extractSkills(textContent))
                        .experienceLevel(extractExperienceLevel(textContent))
                        .recruiterName(extractRecruiterName(page, textContent))
                        .recruiterEmail(extractEmail(textContent))
                        .build();

                log.info("Scraped job details: title={}, company={}", details.getJobTitle(), details.getCompanyName());
                return details;
            }
        }
    }

    private String extractJobTitle(Page page, String textContent) {
        try {
            String[] selectors = {
                    "h1.top-card-layout__title", "h1.t-24", "h1",
                    ".job-details-jobs-unified-top-card__job-title h1",
                    ".jobs-unified-top-card__job-title"
            };
            for (String selector : selectors) {
                Locator locator = page.locator(selector).first();
                if (locator.count() > 0) {
                    String title = locator.textContent().trim();
                    if (!title.isEmpty()) return title;
                }
            }
        } catch (Exception e) {
            log.debug("Could not extract job title with selectors, trying regex");
        }
        Pattern pattern = Pattern.compile("(?:hiring|looking for|position|role)\\s*:?\\s*(.+?)(?:\\n|\\.|$)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(textContent);
        if (matcher.find()) return matcher.group(1).trim();
        return null;
    }

    private String extractCompanyName(Page page, String textContent) {
        try {
            String[] selectors = {
                    ".top-card-layout__card .topcard__org-name-link",
                    "a.topcard__org-name-link",
                    ".jobs-unified-top-card__company-name a",
                    ".t-14.t-black.t-normal span"
            };
            for (String selector : selectors) {
                Locator locator = page.locator(selector).first();
                if (locator.count() > 0) {
                    String name = locator.textContent().trim();
                    if (!name.isEmpty()) return name;
                }
            }
        } catch (Exception e) {
            log.debug("Could not extract company name with selectors");
        }
        Pattern pattern = Pattern.compile("(?:at|@|company)\\s*:?\\s*([A-Z][a-zA-Z0-9\\s&]+?)(?:\\n|\\.|,|$)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(textContent);
        if (matcher.find()) return matcher.group(1).trim();
        return null;
    }

    private List<String> extractSkills(String textContent) {
        List<String> skills = new ArrayList<>();
        String lowerText = textContent.toLowerCase();
        String[] commonSkills = {
                "Java", "Python", "JavaScript", "TypeScript", "React", "Angular", "Vue",
                "Node.js", "Spring Boot", "Spring", "Django", "Flask", "AWS", "Azure", "GCP",
                "Docker", "Kubernetes", "SQL", "PostgreSQL", "MySQL", "MongoDB", "Redis",
                "Kafka", "RabbitMQ", "Git", "CI/CD", "REST", "GraphQL", "Microservices",
                "Machine Learning", "Data Science", "DevOps", "Agile", "Scrum",
                "HTML", "CSS", "C++", "C#", ".NET", "Go", "Rust", "Scala", "Kotlin",
                "Swift", "iOS", "Android", "Flutter", "React Native", "Terraform",
                "Jenkins", "Linux", "System Design", "Data Structures", "Algorithms"
        };
        for (String skill : commonSkills) {
            if (lowerText.contains(skill.toLowerCase())) skills.add(skill);
        }
        return skills;
    }

    private String extractExperienceLevel(String textContent) {
        String lowerText = textContent.toLowerCase();
        if (lowerText.contains("senior") || lowerText.contains("sr.") || lowerText.contains("lead")) return "Senior";
        if (lowerText.contains("mid-level") || lowerText.contains("mid level") || lowerText.contains("3-5 years") || lowerText.contains("3+ years")) return "Mid-Level";
        if (lowerText.contains("junior") || lowerText.contains("jr.") || lowerText.contains("entry level") || lowerText.contains("entry-level") || lowerText.contains("fresher")) return "Entry Level";
        if (lowerText.contains("principal") || lowerText.contains("staff") || lowerText.contains("architect")) return "Staff/Principal";
        Pattern pattern = Pattern.compile("(\\d+)\\+?\\s*(?:years?|yrs?)\\s*(?:of)?\\s*(?:experience)?", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(textContent);
        if (matcher.find()) {
            int years = Integer.parseInt(matcher.group(1));
            if (years <= 2) return "Entry Level";
            if (years <= 5) return "Mid-Level";
            return "Senior";
        }
        return null;
    }

    private String extractRecruiterName(Page page, String textContent) {
        try {
            String[] selectors = {
                    ".feed-shared-actor__name span",
                    ".update-components-actor__name span",
                    ".top-card-layout__entity-info .top-card-layout__title"
            };
            for (String selector : selectors) {
                Locator locator = page.locator(selector).first();
                if (locator.count() > 0) {
                    String name = locator.textContent().trim();
                    if (!name.isEmpty() && !name.contains("LinkedIn")) return name;
                }
            }
        } catch (Exception e) {
            log.debug("Could not extract recruiter name with selectors");
        }
        Pattern pattern = Pattern.compile("(?:contact|reach out to|DM|email)\\s+([A-Z][a-z]+\\s+[A-Z][a-z]+)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(textContent);
        if (matcher.find()) return matcher.group(1).trim();
        return null;
    }

    private String extractEmail(String textContent) {
        Matcher matcher = EMAIL_PATTERN.matcher(textContent);
        if (matcher.find()) return matcher.group();
        return null;
    }
}
