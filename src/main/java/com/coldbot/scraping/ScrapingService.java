package com.coldbot.scraping;

import com.coldbot.common.dto.JobDetails;

/**
 * Service for scraping job details from LinkedIn URLs.
 */
public interface ScrapingService {

    /**
     * Scrape job details from a LinkedIn post URL.
     * @param url LinkedIn job/post URL
     * @return Extracted job details
     */
    JobDetails scrapeLinkedInPost(String url);
}
