package com.aliano.mutiagent.application.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SessionSharedContextRef {

    private String sessionId;
    private String roleKey;
    private String roleLabel;
    private String title;
    private String coordinationState;
    private String coordinationLabel;
    private String progressHint;
    private String includedReason;
    private String lastActiveAt;
    private String lastActiveText;
}
