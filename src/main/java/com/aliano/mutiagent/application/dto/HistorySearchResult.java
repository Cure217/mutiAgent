package com.aliano.mutiagent.application.dto;

import com.aliano.mutiagent.common.model.PageResponse;
import lombok.Data;

@Data
public class HistorySearchResult {

    private PageResponse<HistorySearchSessionHit> sessions;
    private PageResponse<HistorySearchMessageHit> messages;
}
