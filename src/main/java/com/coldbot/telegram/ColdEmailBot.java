package com.coldbot.telegram;

import com.coldbot.application.ApplicationOrchestrator;
import com.coldbot.common.dto.ApplicationResponse;
import com.coldbot.common.dto.CreateApplicationRequest;
import com.coldbot.common.enums.SourceType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class ColdEmailBot implements LongPollingSingleThreadUpdateConsumer {

    private final TelegramClient telegramClient;
    private final ApplicationOrchestrator orchestrator;
    private final TelegramMessageFormatter formatter;
    private final Map<Long, UserState> userStates = new ConcurrentHashMap<>();
    private final Map<Long, UUID> latestApplications = new ConcurrentHashMap<>();

    public ColdEmailBot(@Value("${telegram.bot.token}") String botToken,
                         ApplicationOrchestrator orchestrator, TelegramMessageFormatter formatter) {
        this.telegramClient = new OkHttpTelegramClient(botToken);
        this.orchestrator = orchestrator;
        this.formatter = formatter;
    }

    @Override
    public void consume(Update update) {
        try {
            if (update.hasCallbackQuery()) handleCallbackQuery(update);
            else if (update.hasMessage() && update.getMessage().hasText()) handleMessage(update);
        } catch (Exception e) {
            log.error("Error handling update: {}", e.getMessage(), e);
            if (update.hasMessage()) sendText(update.getMessage().getChatId(), "❌ An error occurred. Please try again.");
        }
    }

    private void handleMessage(Update update) {
        long chatId = update.getMessage().getChatId();
        String text = update.getMessage().getText().trim();
        UserState state = userStates.get(chatId);
        if (state != null) { handleStatefulInput(chatId, text, state); return; }
        if (text.startsWith("/start") || text.startsWith("/help")) sendMarkdown(chatId, formatter.formatWelcome());
        else if (text.startsWith("/apply")) handleApplyCommand(chatId, text);
        else if (text.startsWith("/jd")) handleJdCommand(chatId, text);
        else if (text.startsWith("/status")) handleStatusCommand(chatId);
        else if (text.startsWith("/history")) handleHistoryCommand(chatId);
        else sendMarkdown(chatId, "I don't understand that command\\. Use /help to see available commands\\.");
    }

    private void handleApplyCommand(long chatId, String text) {
        String[] parts = text.split("\\s+", 2);
        if (parts.length < 2 || !parts[1].startsWith("http")) {
            sendMarkdown(chatId, "⚠️ Please provide a LinkedIn URL\\.\n\nUsage: `/apply https://linkedin\\.com/posts/\\.\\.\\. `");
            return;
        }
        sendMarkdown(chatId, formatter.formatProcessing());
        orchestrator.processApplication(CreateApplicationRequest.builder()
                .sourceType(SourceType.LINKEDIN_URL).sourceInput(parts[1].trim()).telegramChatId(chatId).build(),
                response -> onApplicationUpdate(chatId, response));
    }

    private void handleJdCommand(long chatId, String text) {
        String[] parts = text.split("\\s+", 2);
        if (parts.length >= 2 && parts[1].length() > 20) processJd(chatId, parts[1].trim());
        else { userStates.put(chatId, UserState.WAITING_FOR_JD); sendMarkdown(chatId, formatter.formatAskForJd()); }
    }

    private void handleStatefulInput(long chatId, String text, UserState state) {
        userStates.remove(chatId);
        switch (state) {
            case WAITING_FOR_JD -> processJd(chatId, text);
            case WAITING_FOR_EDIT -> processEdit(chatId, text);
            case WAITING_FOR_EMAIL -> processEmailInput(chatId, text);
        }
    }

    private void processJd(long chatId, String jdText) {
        sendMarkdown(chatId, formatter.formatProcessing());
        orchestrator.processApplication(CreateApplicationRequest.builder()
                .sourceType(SourceType.RAW_JD).sourceInput(jdText).telegramChatId(chatId).build(),
                response -> onApplicationUpdate(chatId, response));
    }

    private void handleStatusCommand(long chatId) {
        UUID latestAppId = latestApplications.get(chatId);
        if (latestAppId == null) { sendMarkdown(chatId, "📭 _No active application\\. Use /apply or /jd to start one\\._"); return; }
        try { sendMarkdown(chatId, formatter.formatStatus(orchestrator.getApplication(latestAppId))); }
        catch (Exception e) { sendMarkdown(chatId, "❌ Could not retrieve status: " + escapeMarkdown(e.getMessage())); }
    }

    private void handleHistoryCommand(long chatId) {
        try { sendMarkdown(chatId, formatter.formatHistory(orchestrator.getApplicationsByTelegramChatId(chatId))); }
        catch (Exception e) { sendMarkdown(chatId, "❌ Could not retrieve history: " + escapeMarkdown(e.getMessage())); }
    }

    private void onApplicationUpdate(long chatId, ApplicationResponse response) {
        latestApplications.put(chatId, response.getId());
        switch (response.getStatus()) {
            case SCRAPING -> sendMarkdown(chatId, formatter.formatScraping());
            case EMAIL_GENERATED, AWAITING_APPROVAL -> sendApprovalMessage(chatId, response);
            case SENT -> sendMarkdown(chatId, formatter.formatSent(response));
            case FAILED -> sendMarkdown(chatId, formatter.formatError(response.getErrorMessage()));
            case CANCELLED -> sendMarkdown(chatId, formatter.formatCancelled());
            default -> {}
        }
    }

    private void sendApprovalMessage(long chatId, ApplicationResponse response) {
        sendMarkdownWithKeyboard(chatId, formatter.formatEmailGenerated(response), createApprovalKeyboard(response.getId()));
    }

    private void handleCallbackQuery(Update update) {
        String callbackData = update.getCallbackQuery().getData();
        long chatId = update.getCallbackQuery().getMessage().getChatId();
        String[] parts = callbackData.split(":", 2);
        if (parts.length < 2) return;
        UUID applicationId;
        try { applicationId = UUID.fromString(parts[1]); }
        catch (IllegalArgumentException e) { return; }
        switch (parts[0]) {
            case "approve" -> { sendMarkdown(chatId, "📤 _Sending email\\.\\.\\._");
                try { orchestrator.approveAndSend(applicationId, resp -> onApplicationUpdate(chatId, resp)); }
                catch (Exception e) { sendMarkdown(chatId, formatter.formatError(e.getMessage())); } }
            case "edit" -> { sendMarkdown(chatId, formatter.formatEditPrompt()); userStates.put(chatId, UserState.WAITING_FOR_EDIT); }
            case "cancel" -> { orchestrator.cancelApplication(applicationId); sendMarkdown(chatId, formatter.formatCancelled()); }
            case "addemail" -> { sendMarkdown(chatId, "📧 _Please send the recruiter's email address:_"); userStates.put(chatId, UserState.WAITING_FOR_EMAIL); }
        }
    }

    private void processEdit(long chatId, String editText) {
        UUID appId = latestApplications.get(chatId);
        if (appId == null) { sendMarkdown(chatId, "⚠️ _No active application to edit\\._"); return; }
        String newSubject = null, newBody = null, newEmail = null;
        StringBuilder bodyBuilder = new StringBuilder(); boolean inBody = false;
        for (String line : editText.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.toLowerCase().startsWith("email:")) newEmail = trimmed.substring(6).trim();
            else if (trimmed.toLowerCase().startsWith("subject:")) newSubject = trimmed.substring(8).trim();
            else if (trimmed.toLowerCase().startsWith("body:")) { bodyBuilder.append(trimmed.substring(5).trim()); inBody = true; }
            else if (inBody) bodyBuilder.append("\n").append(line);
        }
        if (bodyBuilder.length() > 0) newBody = bodyBuilder.toString().trim();
        if (newSubject == null && newBody == null && newEmail == null) newBody = editText;
        try { sendApprovalMessage(chatId, orchestrator.updateEmail(appId, newSubject, newBody, newEmail)); }
        catch (Exception e) { sendMarkdown(chatId, formatter.formatError(e.getMessage())); }
    }

    private void processEmailInput(long chatId, String email) {
        UUID appId = latestApplications.get(chatId);
        if (appId == null) { sendMarkdown(chatId, "⚠️ _No active application\\._"); return; }
        email = email.trim();
        if (!email.matches("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}")) {
            sendMarkdown(chatId, "⚠️ _Invalid email format\\. Please try again:_");
            userStates.put(chatId, UserState.WAITING_FOR_EMAIL); return;
        }
        try { sendApprovalMessage(chatId, orchestrator.updateEmail(appId, null, null, email)); }
        catch (Exception e) { sendMarkdown(chatId, formatter.formatError(e.getMessage())); }
    }

    private InlineKeyboardMarkup createApprovalKeyboard(UUID applicationId) {
        String id = applicationId.toString();
        return InlineKeyboardMarkup.builder()
                .keyboardRow(new InlineKeyboardRow(
                        InlineKeyboardButton.builder().text("✅ Approve & Send").callbackData("approve:" + id).build(),
                        InlineKeyboardButton.builder().text("✏️ Edit").callbackData("edit:" + id).build()))
                .keyboardRow(new InlineKeyboardRow(
                        InlineKeyboardButton.builder().text("📧 Add Email").callbackData("addemail:" + id).build(),
                        InlineKeyboardButton.builder().text("❌ Cancel").callbackData("cancel:" + id).build()))
                .build();
    }

    private void sendMarkdown(long chatId, String text) {
        try { telegramClient.execute(SendMessage.builder().chatId(chatId).text(text).parseMode("MarkdownV2").build()); }
        catch (Exception e) { log.error("Markdown send failed: {}", e.getMessage()); sendText(chatId, text.replaceAll("\\\\.", "").replaceAll("[*_~`]", "")); }
    }

    private void sendText(long chatId, String text) {
        try { telegramClient.execute(SendMessage.builder().chatId(chatId).text(text).build()); }
        catch (Exception e) { log.error("Plain text send failed: {}", e.getMessage()); }
    }

    private void sendMarkdownWithKeyboard(long chatId, String text, InlineKeyboardMarkup keyboard) {
        try { telegramClient.execute(SendMessage.builder().chatId(chatId).text(text).parseMode("MarkdownV2").replyMarkup(keyboard).build()); }
        catch (Exception e) { log.error("Keyboard send failed: {}", e.getMessage()); sendMarkdown(chatId, text); }
    }

    private String escapeMarkdown(String text) { return text == null ? "" : text.replaceAll("([_*\\[\\]()~`>#+\\-=|{}.!\\\\])", "\\\\$1"); }

    private enum UserState { WAITING_FOR_JD, WAITING_FOR_EDIT, WAITING_FOR_EMAIL }
}
