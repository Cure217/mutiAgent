package com.aliano.mutiagent.application.command;

public record CommandEnvelope<T>(
        String commandType,
        String targetType,
        String targetId,
        String clientId,
        String operatorName,
        T payload) {

    public CommandEnvelope<T> withTargetId(String nextTargetId) {
        return new CommandEnvelope<>(
                commandType,
                targetType,
                nextTargetId,
                clientId,
                operatorName,
                payload
        );
    }
}
