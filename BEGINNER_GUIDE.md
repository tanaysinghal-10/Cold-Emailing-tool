# Simple Guide to the AI Cold Email Automation System

---

## 1. SIMPLE OVERVIEW (FOR BEGINNERS)

This project is a smart personal assistant that automatically applies to jobs for you. Instead of you spending hours reading a job post, finding the recruiter's name, and typing out an email, you just give the system a link. The system reads the job, writes a personalized email, and emails your resume to the recruiter. 

Think of it like having an invisible secretary inside your phone: you point to a job you want, and your secretary handles all the paperwork and sends it out.

---

## 2. HOW THE SYSTEM WORKS (STEP-BY-STEP)

Here is the exact journey from start to finish:

1. **You Send a Link:** You open your Telegram app and paste a job link (like a LinkedIn post).
2. **The System Reads It:** The system goes to that link and copies all the text, just like a human reading the screen. 
3. **The AI Thinks:** It sends the text to the AI (the "brain") to figure out the job title, the company name, and the recruiter's contact details.
4. **The AI Writes:** The AI writes a customized, professional cold email that matches your skills with the job requirements.
5. **You Check It:** The bot sends you back the drafted email on Telegram so you can read it.
6. **The Email is Sent:** If you click the "Approve" button, the system logs into your Gmail and sends the email (along with your resume) directly to the recruiter!

**Simple Example:**
* **Input:** You paste a link for a "Software Engineer at Google" job.
* **Output:** The bot instantly drafts an email saying *"Hi John, I am writing to apply for the Software Engineer position at Google..."* and sends it out for you!

---

## 3. SYSTEM ARCHITECTURE (VERY SIMPLE)

The system is made up of 5 main "departments" working together:

* **The Messenger (Telegram Handler):** This is the front desk. It talks to you on Telegram, takes your links, and asks for your final approval.
* **The Reader (Data Parser/Playwright):** This is a pair of reading glasses. It goes to the websites you provide and extracts the messy text.
* **The Brain (AI Module/GroqCloud):** This is the smartest worker. It understands human language. It reads the messy text, pulls out the important details, and writes the email.
* **The Postman (Email Sender):** This is the delivery guy. Once everything is ready, it securely takes your Gmail account and delivers the message to the recruiter.
* **The Memory (Database/PostgreSQL):** This is the filing cabinet. It securely remembers your daily limit of emails, logs every application you've sent, and stores your data.

---

## 4. TECHNICAL BREAKDOWN (EASY MODE)

Even though it sounds like magic, here is how the tech works under the hood:

* **Technologies Used:** The engine is built on **Java** (a very reliable programming language). It uses **Spring Boot** to keep everything organized. 
* **How Parsing Works:** It uses a tool called **Playwright**. Playwright opens an invisible "ghost" web browser in the background, visits the LinkedIn page, and copies the HTML text on the screen for us.
* **How AI Generates Emails:** We use **GroqCloud** (an AI similar to ChatGPT). We send GroqCloud all the job details along with a set of instructions saying *"Act like a job applicant and write a professional email."* GroqCloud replies with the perfect text.
* **How Emails are Sent:** We use **SMTP**, which stands for Simple Mail Transfer Protocol. It is basically an automated, invisible version of clicking the "Send" button on Gmail. We give it an "App Password" so it can send emails safely without knowing your real Google password.

---

## 5. PROJECT SETUP GUIDE (STEP-BY-STEP)

Think of this like a cooking recipe. Follow carefully!

### What you need (Ingredients):
* **Java 17** installed on your computer.
* **Docker Desktop** installed and running.
* A **Telegram** account.

### What you need to prepare (The API Keys):
An API Key is like a secret VIP pass that allows your bot to talk to other apps. You need three of them:

**1. Telegram Bot Token (Your Bot's ID)**
* Open Telegram and search for the user **@BotFather** (it has a blue verification tick).
* Send the message `/newbot` to him.
* Follow his simple instructions to give your bot a name and a username.
* Once finished, he will give you a long string of letters and numbers (e.g., `123456:ABC-DEF1234ghIkl-zyx57W2v1u123ew11`). This is your **Telegram Bot Token**. Copy it!

**2. GroqCloud API Key (The AI Brain)**
* Go to the website **https://console.groq.com/keys**.
* Sign up for a free account.
* Click on the **"Create API Key"** button.
* Give it any name (like "My Cold Email Bot") and copy the secret key it gives you immediately (it will start with `gsk_`). Do not lose this!

**3. Gmail App Password (The Postman's Key)**
* You **cannot** use your normal Gmail login password because Google blocks robots. You need a special 16-letter "App Password".
* Go to your Google Account Settings (**https://myaccount.google.com/**).
* Search for **"2-Step Verification"** and turn it on (if it's not already on).
* Then, go to the search bar at the very top of your Google settings and search for **"App Passwords"**.
* Create a new app password, name it "Cold Email Bot", and copy the 16-letter password Google shows you (e.g., `abcd efgh ijkl mnop`).

---

### Installation Steps (Cooking Instructions):
1. **Start the Database (Memory):** Open your terminal, go to the project folder, and run:
   `docker-compose up -d`
   *(This starts your PostgreSQL database filing cabinet).*
2. **Add Your Keys:** Open the `application.yml` file in your code editor. 
   * Find the `TELEGRAM_BOT_TOKEN` line and paste your BotFather token.
   * Find the `GROQCLOUD_API_KEY` line and paste your GroqCloud key.
   * Find the `SMTP_PASSWORD` line and paste your 16-letter Gmail App Password.
3. **Add Your Resume:** Place your resume PDF inside the `resumes/` folder and name it `Tanay_Singhal_Resume.pdf`.
4. **Turn it On:** In your terminal, type:
   `mvn spring-boot:run`
5. You will see text scrolling on your screen. When it says "Started", your bot is officially awake!

---

## 6. HOW TO USE THE PROJECT (PRACTICAL GUIDE)

1. **Say Hello:** Open Telegram, search for your bot's name, and press **Start** (or type `/start`).
2. **Give an Order:** The bot will say hello. Now, paste a full LinkedIn job URL or just paste raw text from a job description.
3. **Wait 10 Seconds:** The bot will say "Processing..." while the Ghost Browser and AI do their work.
4. **Approve:** The bot will show you the email it wrote. Below the message, there will be buttons. Click the **"Approve & Send"** button.
5. **Success:** The bot will notify you that the email was successfully sent! 

---

## 7. COMMON ERRORS & FIXES

* **Error:** *The app crashes immediately and says "Could not register Telegram bot".*
   * **Fix:** You forgot to put your Telegram Token in the `application.yml` file, or the token is incorrect. 
* **Error:** *It says "password authentication failed" when starting up.*
   * **Fix:** You forgot to turn on Docker! Open Docker Desktop and run `docker-compose up -d` so the database is awake.
* **Error:** *Gmail refuses to send the email.*
   * **Fix:** You cannot use your normal Gmail password. You must go to your Google Account Security settings and generate a special "App Password".

---

## 8. LIMITATIONS

We want to be honest about what the system **cannot** do well:

* **It gets stuck on Captchas:** If LinkedIn asks the bot "Are you a robot?" and shows a picture puzzle, the bot cannot solve it and the scraping will fail.
* **Hidden Emails:** If a recruiter hides their email address in a weird format (like *john [at] company [dot] com*), the AI might struggle to extract it perfectly.
* **Attachments:** Right now, it always attaches the single default resume you uploaded. It does not dynamically build a brand new PDF from scratch every time.

---

## 9. FUTURE IMPROVEMENTS

Here are 5 ideas to make the system even better in the future:
1. **Smarter Resume Swapping:** Allow the bot to automatically pick between 3 different resumes depending on what the job title is.
2. **Follow-up Emails:** Make the bot automatically send a polite "Just following up!" email if the recruiter hasn't replied in 3 days.
3. **Web Dashboard:** Create a simple website where you can see a history chart of every application you've sent this month.
4. **More Job Boards:** Add specific support to scrape Indeed, Glassdoor, and Wellfound easily.
5. **Daily Quotas:** Tell the bot to automatically hunt and apply to exactly 5 jobs every morning while you sleep.

---

## 10. SUMMARY

This Cold Email Telegram Bot is your ultimate sidekick for job hunting. By simply dropping a job link into Telegram, the system automatically reads the description, uses AI to write a highly-tailored application email, and securely sends it from your Gmail with your resume attached. It completely removes the boring, repetitive typing work out of applying to jobs!
