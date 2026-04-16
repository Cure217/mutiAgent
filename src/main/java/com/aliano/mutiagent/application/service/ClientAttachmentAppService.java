package com.aliano.mutiagent.application.service;

import com.aliano.mutiagent.application.command.CommandEnvelope;
import com.aliano.mutiagent.application.command.CommandTypes;
import com.aliano.mutiagent.application.command.RuntimeTargetTypes;
import com.aliano.mutiagent.application.dto.CreateOperationLogRequest;
import com.aliano.mutiagent.infrastructure.event.ClientAttachment;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClientAttachmentAppService {

    private final OperationLogAppService operationLogAppService;

    public void recordAttach(ClientAttachment attachment) {
        CommandEnvelope<ClientAttachment> command = new CommandEnvelope<>(
                CommandTypes.CLIENT_ATTACH,
                RuntimeTargetTypes.CLIENT_ATTACHMENT,
                attachment.clientId(),
                attachment.clientId(),
                null,
                attachment
        );
        recordCommand(command, "success", clientAttachmentDetail(attachment, null, null));
    }

    public void recordDetach(ClientAttachment attachment) {
        CommandEnvelope<ClientAttachment> command = new CommandEnvelope<>(
                CommandTypes.CLIENT_DETACH,
                RuntimeTargetTypes.CLIENT_ATTACHMENT,
                attachment.clientId(),
                attachment.clientId(),
                null,
                attachment
        );
        recordCommand(command, "success", clientAttachmentDetail(attachment, null, null));
    }

    public void recordObserve(ClientAttachment previousAttachment, ClientAttachment nextAttachment) {
        if (nextAttachment == null) {
            return;
        }
        String previousTargetId = previousAttachment == null ? null : previousAttachment.observedTargetId();
        String nextTargetId = nextAttachment.observedTargetId();
        String previousTargetType = previousAttachment == null ? null : previousAttachment.observedTargetType();
        String nextTargetType = nextAttachment.observedTargetType();
        if (equalsNullable(previousTargetId, nextTargetId) && equalsNullable(previousTargetType, nextTargetType)) {
            return;
        }
        CommandEnvelope<ClientAttachment> command = new CommandEnvelope<>(
                CommandTypes.CLIENT_OBSERVE,
                RuntimeTargetTypes.CLIENT_ATTACHMENT,
                nextAttachment.clientId(),
                nextAttachment.clientId(),
                null,
                nextAttachment
        );
        recordCommand(command, "success", clientAttachmentDetail(nextAttachment, previousAttachment, nextAttachment));
    }

    private void recordCommand(CommandEnvelope<?> command, String result, Map<String, Object> detail) {
        try {
            operationLogAppService.create(new CreateOperationLogRequest(
                    command.targetType(),
                    command.targetId(),
                    command.commandType(),
                    result,
                    command.operatorName(),
                    detail
            ));
        } catch (RuntimeException exception) {
            log.warn("Failed to record runtime command {}", command.commandType(), exception);
        }
    }

    private Map<String, Object> clientAttachmentDetail(ClientAttachment attachment,
                                                       ClientAttachment previousAttachment,
                                                       ClientAttachment nextAttachment) {
        Map<String, Object> detail = new LinkedHashMap<>();
        if (attachment != null) {
            detail.put("clientId", attachment.clientId());
            detail.put("transportSessionId", attachment.transportSessionId());
            detail.put("connectedAt", attachment.connectedAt());
            detail.put("lastHeartbeatAt", attachment.lastHeartbeatAt());
            detail.put("observedTargetType", attachment.observedTargetType());
            detail.put("observedTargetId", attachment.observedTargetId());
            detail.put("remoteAddress", attachment.remoteAddress());
            detail.put("userAgent", attachment.userAgent());
        }
        if (previousAttachment != null) {
            detail.put("previousObservedTargetType", previousAttachment.observedTargetType());
            detail.put("previousObservedTargetId", previousAttachment.observedTargetId());
        }
        if (nextAttachment != null) {
            detail.put("nextObservedTargetType", nextAttachment.observedTargetType());
            detail.put("nextObservedTargetId", nextAttachment.observedTargetId());
        }
        return detail;
    }

    private boolean equalsNullable(String left, String right) {
        if (left == null) {
            return right == null;
        }
        return left.equals(right);
    }
}
