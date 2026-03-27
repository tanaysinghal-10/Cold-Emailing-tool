package com.coldbot.common.dto;

import com.coldbot.common.enums.SourceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateApplicationRequest {

    @NotNull(message = "Source type is required")
    private SourceType sourceType;

    @NotBlank(message = "Source input is required")
    private String sourceInput;

    private Long telegramChatId;
}
