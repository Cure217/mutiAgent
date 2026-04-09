package com.aliano.mutiagent.infrastructure.adapter;

public record ParsedMessage(
        String role,
        String messageType,
        String contentText,
        String contentJson,
        boolean structured) {
}
