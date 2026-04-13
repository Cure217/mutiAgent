package com.aliano.mutiagent.application.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SessionWorkspaceMeta {

    private String workspaceKind;
    private String role;
    private String coordinationStatus;
    private String progressSummary;
    private String blockedReason;
    private List<String> dependencySessionIds = new ArrayList<>();
    private String sharedContextSummary;
    private String updatedAt;
}
