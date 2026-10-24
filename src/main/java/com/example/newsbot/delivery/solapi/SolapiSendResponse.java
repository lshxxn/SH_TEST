package com.example.newsbot.delivery.solapi;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * POST /messages/v4/send-many/detail 응답 중 필요한 부분.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SolapiSendResponse(List<FailedMessage> failedMessageList, GroupInfo groupInfo) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FailedMessage(String to, String statusCode, String statusMessage) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GroupInfo(String groupId, Count count) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Count(int total, int registeredFailed, int registeredSuccess) {
    }
}
