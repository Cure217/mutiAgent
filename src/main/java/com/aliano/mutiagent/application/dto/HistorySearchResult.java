package com.aliano.mutiagent.application.dto;

import java.util.List;
import lombok.Data;

@Data
public class HistorySearchResult {

    private List<HistorySearchSessionHit> sessions;
    private List<HistorySearchMessageHit> messages;
}
