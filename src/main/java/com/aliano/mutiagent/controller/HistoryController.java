package com.aliano.mutiagent.controller;

import com.aliano.mutiagent.application.dto.HistorySearchResult;
import com.aliano.mutiagent.application.dto.SessionTimelineItem;
import com.aliano.mutiagent.application.service.HistoryAppService;
import com.aliano.mutiagent.application.service.SessionAppService;
import com.aliano.mutiagent.common.model.ApiResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/history")
@RequiredArgsConstructor
public class HistoryController {

    private final HistoryAppService historyAppService;
    private final SessionAppService sessionAppService;

    @GetMapping("/search")
    public ApiResponse<HistorySearchResult> search(@RequestParam(required = false) String keyword,
                                                   @RequestParam(required = false) String appType,
                                                   @RequestParam(required = false) String projectPath,
                                                   @RequestParam(required = false) String dateFrom,
                                                   @RequestParam(required = false) String dateTo,
                                                   @RequestParam(required = false) Integer sessionLimit,
                                                   @RequestParam(required = false) Integer messageLimit,
                                                   @RequestParam(defaultValue = "1") Integer sessionPageNo,
                                                   @RequestParam(required = false) Integer sessionPageSize,
                                                   @RequestParam(required = false) String sessionSortBy,
                                                   @RequestParam(required = false) String sessionSortDirection,
                                                   @RequestParam(defaultValue = "1") Integer messagePageNo,
                                                   @RequestParam(required = false) Integer messagePageSize,
                                                   @RequestParam(required = false) String messageSortBy,
                                                   @RequestParam(required = false) String messageSortDirection) {
        return ApiResponse.success(historyAppService.search(
                keyword,
                appType,
                projectPath,
                dateFrom,
                dateTo,
                sessionPageNo,
                sessionPageSize == null ? sessionLimit : sessionPageSize,
                sessionSortBy,
                sessionSortDirection,
                messagePageNo,
                messagePageSize == null ? messageLimit : messagePageSize,
                messageSortBy,
                messageSortDirection
        ));
    }

    @GetMapping("/sessions/{id}/timeline")
    public ApiResponse<List<SessionTimelineItem>> timeline(@PathVariable String id,
                                                           @RequestParam(defaultValue = "200") int limit) {
        return ApiResponse.success(sessionAppService.timeline(id, limit));
    }
}
