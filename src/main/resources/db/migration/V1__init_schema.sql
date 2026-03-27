-- =============================================
-- Cold Email Bot - Database Schema
-- =============================================

-- Users table (Telegram users + their config)
CREATE TABLE users (
    id              BIGSERIAL PRIMARY KEY,
    telegram_chat_id BIGINT NOT NULL UNIQUE,
    telegram_username VARCHAR(255),
    email           VARCHAR(255),
    smtp_host       VARCHAR(255) DEFAULT 'smtp.gmail.com',
    smtp_port       VARCHAR(10) DEFAULT '587',
    smtp_username   VARCHAR(255),
    smtp_password_encrypted VARCHAR(512),
    resume_mode     VARCHAR(20) NOT NULL DEFAULT 'MODE_A',
    resume_path     VARCHAR(500),
    resume_json     TEXT,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_users_telegram_chat_id ON users(telegram_chat_id);

-- Job Applications table (core entity)
CREATE TABLE job_applications (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                 BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    status                  VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    source_type             VARCHAR(20) NOT NULL,
    source_input            TEXT NOT NULL,
    job_title               VARCHAR(500),
    company_name            VARCHAR(500),
    required_skills         TEXT,
    experience_level        VARCHAR(100),
    recruiter_name          VARCHAR(255),
    recruiter_email         VARCHAR(255),
    generated_email_subject TEXT,
    generated_email_body    TEXT,
    resume_used             VARCHAR(500),
    error_message           TEXT,
    created_at              TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMP NOT NULL DEFAULT NOW(),
    sent_at                 TIMESTAMP
);

CREATE INDEX idx_job_applications_user_id ON job_applications(user_id);
CREATE INDEX idx_job_applications_status ON job_applications(status);
CREATE INDEX idx_job_applications_created_at ON job_applications(created_at DESC);

-- Email Rate Limits table
CREATE TABLE email_rate_limits (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    rate_date   DATE NOT NULL,
    emails_sent INTEGER NOT NULL DEFAULT 0,
    daily_limit INTEGER NOT NULL DEFAULT 10,
    UNIQUE(user_id, rate_date)
);

CREATE INDEX idx_email_rate_limits_user_date ON email_rate_limits(user_id, rate_date);

-- Prompt Templates table
CREATE TABLE prompt_templates (
    id               BIGSERIAL PRIMARY KEY,
    template_name    VARCHAR(100) NOT NULL UNIQUE,
    template_content TEXT NOT NULL,
    template_type    VARCHAR(30) NOT NULL,
    is_active        BOOLEAN NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Audit Log table
CREATE TABLE audit_log (
    id              BIGSERIAL PRIMARY KEY,
    application_id  UUID REFERENCES job_applications(id) ON DELETE SET NULL,
    action          VARCHAR(50) NOT NULL,
    details         TEXT,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_log_application_id ON audit_log(application_id);
CREATE INDEX idx_audit_log_created_at ON audit_log(created_at DESC);

-- =============================================
-- Seed: Default Prompt Templates
-- =============================================

INSERT INTO prompt_templates (template_name, template_content, template_type, is_active) VALUES
('cold_email_default',
'You are an expert career coach writing a cold email for a job application. Be specific, concise, and human-like.

RECRUITER INFO:
- Name: {{recruiterName}}
- Company: {{companyName}}

JOB DETAILS:
- Title: {{jobTitle}}
- Required Skills: {{requiredSkills}}
- Experience Level: {{experienceLevel}}

CANDIDATE SUMMARY:
{{resumeSummary}}

INSTRUCTIONS:
1. Write a concise, personalized cold email (max 150 words)
2. Address the recruiter by name if available, otherwise use a professional greeting
3. Mention 2-3 specific skills from the JD that match the candidate''s background
4. Include a clear call to action
5. Keep the tone professional but conversational — avoid generic corporate language
6. Do NOT use phrases like "I am writing to express my interest" or "I came across your posting"
7. Start with something specific about the company or role that shows genuine interest

Also generate an optimized email subject line (max 60 characters).

OUTPUT FORMAT (JSON only, no markdown):
{"subject": "...", "body": "..."}',
'COLD_EMAIL', TRUE),

('resume_tailor_default',
'You are an expert resume writer. Tailor the following resume for a specific job.

BASE RESUME (JSON):
{{baseResumeJson}}

TARGET JOB:
- Title: {{jobTitle}}
- Company: {{companyName}}
- Required Skills: {{requiredSkills}}
- Experience Level: {{experienceLevel}}

INSTRUCTIONS:
1. Modify bullet points to emphasize skills relevant to the target job
2. Reorder skills to prioritize those matching the JD
3. Adjust the professional summary to align with the role
4. Do NOT invent or fabricate any experience, projects, or skills
5. Do NOT change job titles, company names, employment dates, or education
6. Only rephrase existing accomplishments to better match the JD keywords
7. Keep all modifications truthful and verifiable

OUTPUT: Return the modified resume in the same JSON format. No markdown, just JSON.',
'RESUME_TAILOR', TRUE),

('jd_extraction_default',
'Extract structured job information from the following job description text.

JOB DESCRIPTION:
{{rawJdText}}

Extract and return in JSON format (no markdown, just JSON):
{"jobTitle": "...", "companyName": "...", "requiredSkills": ["skill1", "skill2"], "experienceLevel": "...", "recruiterName": null, "recruiterEmail": null}

If any field cannot be determined, set it to null. For requiredSkills, extract as many relevant technical and soft skills as possible.',
'JD_EXTRACTION', TRUE);
