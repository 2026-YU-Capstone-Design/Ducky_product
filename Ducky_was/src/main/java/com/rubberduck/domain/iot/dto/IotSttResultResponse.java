package com.rubberduck.domain.iot.dto;

import com.rubberduck.domain.chat.dto.ChatTurnResponse;

public record IotSttResultResponse(
        ChatTurnResponse aiResponse
) {
}
