# Cold Email Telegram Bot 🤖

AI-powered cold email automation for job applications via Telegram.

Send a LinkedIn URL or paste a job description → get a personalized cold email → review and send with one click.

## Quick Start

1. Copy `.env.example` → `.env` and fill in your credentials
2. Start PostgreSQL: `docker-compose up -d`
3. Place resume at: `resumes/Tanay_Singhal_Resume.pdf`
4. Run: `mvn spring-boot:run` (load env vars first — see below)

### Loading .env in PowerShell

```powershell
Get-Content .env | ForEach-Object { if ($_ -match '^([^#].+?)=(.*)$') { [System.Environment]::SetEnvironmentVariable($matches[1], $matches[2], 'Process') } }
mvn spring-boot:run
```

## Commands

| Command | Description |
|---------|-------------|
| `/start` | Welcome message |
| `/apply <url>` | Apply from LinkedIn post URL |
| `/jd` | Paste a raw job description |
| `/status` | Check latest application |
| `/history` | View recent applications |

## Setup Guide

See the detailed walkthrough in the artifacts for full setup instructions including:
- Getting your Telegram Bot Token from @BotFather
- Getting a free Gemini API key
- Creating a Gmail App Password (requires 2FA)
