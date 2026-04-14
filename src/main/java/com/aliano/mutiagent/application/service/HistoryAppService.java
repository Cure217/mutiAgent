package com.aliano.mutiagent.application.service;

import com.aliano.mutiagent.application.dto.HistorySearchMessageHit;
import com.aliano.mutiagent.application.dto.HistorySearchResult;
import com.aliano.mutiagent.application.dto.HistorySearchSessionHit;
import com.aliano.mutiagent.common.model.PageResponse;
import com.aliano.mutiagent.infrastructure.persistence.mapper.HistoryMapper;
import com.aliano.mutiagent.infrastructure.persistence.mapper.MessageMapper;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@Slf4j
public class HistoryAppService {

    private final HistoryMapper historyMapper;
    private final MessageMapper messageMapper;
    private final Executor processTaskExecutor;
    private final AtomicBoolean messageFtsWarmupRunning = new AtomicBoolean(false);

    public HistoryAppService(HistoryMapper historyMapper,
                             MessageMapper messageMapper,
                             @Qualifier("processTaskExecutor") Executor processTaskExecutor) {
        this.historyMapper = historyMapper;
        this.messageMapper = messageMapper;
        this.processTaskExecutor = processTaskExecutor;
    }

    public HistorySearchResult search(String keyword,
                                      String appType,
                                      String projectPath,
                                      String dateFrom,
                                      String dateTo,
                                      Integer sessionPageNo,
                                      Integer sessionPageSize,
                                      String sessionSortBy,
                                      String sessionSortDirection,
                                      Integer messagePageNo,
                                      Integer messagePageSize,
                                      String messageSortBy,
                                      String messageSortDirection) {
        String normalizedKeyword = normalize(keyword);
        String normalizedAppType = normalize(appType);
        String normalizedProjectPath = normalize(projectPath);
        String normalizedDateFrom = normalize(dateFrom);
        String normalizedDateTo = normalize(dateTo);
        int validSessionPageNo = normalizePageNo(sessionPageNo);
        int validMessagePageNo = normalizePageNo(messagePageNo);
        int safeSessionPageSize = normalizePageSize(sessionPageSize, 20);
        int safeMessagePageSize = normalizePageSize(messagePageSize, 20);
        int sessionOffset = (validSessionPageNo - 1) * safeSessionPageSize;
        int messageOffset = (validMessagePageNo - 1) * safeMessagePageSize;
        long sessionTotal = historyMapper.countSessionHits(
                normalizedKeyword,
                normalizedAppType,
                normalizedProjectPath,
                normalizedDateFrom,
                normalizedDateTo
        );
        List<HistorySearchSessionHit> sessionHits = sessionTotal == 0
                ? Collections.emptyList()
                : historyMapper.searchSessionHits(
                        normalizedKeyword,
                        normalizedAppType,
                        normalizedProjectPath,
                        normalizedDateFrom,
                        normalizedDateTo,
                        resolveSessionOrderBy(sessionSortBy, sessionSortDirection),
                        safeSessionPageSize,
                        sessionOffset
                );

        sessionHits.forEach(hit -> {
            hit.setMatchedSource(resolveMatchedSource(hit, normalizedKeyword));
            hit.setMatchedText(resolveMatchedText(hit, normalizedKeyword));
        });

        long messageTotal = 0;
        List<HistorySearchMessageHit> messageHits = Collections.emptyList();
        if (StringUtils.hasText(normalizedKeyword)) {
            boolean preferFts = isMessageFtsReady();
            if (!preferFts) {
                warmupMessageFtsAsync();
            }
            if (preferFts) {
                String ftsKeyword = buildFtsKeyword(normalizedKeyword);
                try {
                    messageTotal = historyMapper.countMessageHitsByFts(
                            ftsKeyword,
                            normalizedAppType,
                            normalizedProjectPath,
                            normalizedDateFrom,
                            normalizedDateTo
                    );
                    messageHits = messageTotal == 0
                            ? Collections.emptyList()
                            : historyMapper.searchMessageHitsByFts(
                                    ftsKeyword,
                                    normalizedAppType,
                                    normalizedProjectPath,
                                    normalizedDateFrom,
                                    normalizedDateTo,
                                    resolveMessageOrderBy(messageSortBy, messageSortDirection, true),
                                    safeMessagePageSize,
                                    messageOffset
                            );
                } catch (DataAccessException exception) {
                    log.warn("历史搜索 FTS 查询失败，回退到 LIKE 搜索", exception);
                    messageTotal = searchMessageTotalByLike(
                            normalizedKeyword,
                            normalizedAppType,
                            normalizedProjectPath,
                            normalizedDateFrom,
                            normalizedDateTo
                    );
                    messageHits = searchMessageHitsByLike(
                            normalizedKeyword,
                            normalizedAppType,
                            normalizedProjectPath,
                            normalizedDateFrom,
                            normalizedDateTo,
                            messageSortBy,
                            messageSortDirection,
                            safeMessagePageSize,
                            messageOffset,
                            messageTotal
                    );
                }
            } else {
                messageTotal = searchMessageTotalByLike(
                        normalizedKeyword,
                        normalizedAppType,
                        normalizedProjectPath,
                        normalizedDateFrom,
                        normalizedDateTo
                );
                messageHits = searchMessageHitsByLike(
                        normalizedKeyword,
                        normalizedAppType,
                        normalizedProjectPath,
                        normalizedDateFrom,
                        normalizedDateTo,
                        messageSortBy,
                        messageSortDirection,
                        safeMessagePageSize,
                        messageOffset,
                        messageTotal
                );
            }
            messageHits.forEach(hit -> hit.setSnippet(clipTextAroundKeyword(hit.getSnippet(), normalizedKeyword, 200)));
        }

        HistorySearchResult result = new HistorySearchResult();
        result.setSessions(new PageResponse<>(sessionHits, validSessionPageNo, safeSessionPageSize, sessionTotal));
        result.setMessages(new PageResponse<>(messageHits, validMessagePageNo, safeMessagePageSize, messageTotal));
        return result;
    }

    private boolean isMessageFtsReady() {
        try {
            long totalMessages = messageMapper.countAll();
            if (totalMessages == 0) {
                return true;
            }
            return historyMapper.countIndexedMessages() >= totalMessages;
        } catch (DataAccessException exception) {
            log.warn("检查历史搜索 FTS 状态失败，当前请求回退到 LIKE 搜索", exception);
            return false;
        }
    }

    private void warmupMessageFtsAsync() {
        if (!messageFtsWarmupRunning.compareAndSet(false, true)) {
            return;
        }
        processTaskExecutor.execute(() -> {
            try {
                historyMapper.syncMessageFts();
            } catch (Exception exception) {
                log.warn("后台预热历史搜索 FTS 失败", exception);
            } finally {
                messageFtsWarmupRunning.set(false);
            }
        });
    }

    private long searchMessageTotalByLike(String keyword,
                                          String appType,
                                          String projectPath,
                                          String dateFrom,
                                          String dateTo) {
        return historyMapper.countMessageHitsByLike(
                keyword,
                appType,
                projectPath,
                dateFrom,
                dateTo
        );
    }

    private List<HistorySearchMessageHit> searchMessageHitsByLike(String keyword,
                                                                  String appType,
                                                                  String projectPath,
                                                                  String dateFrom,
                                                                  String dateTo,
                                                                  String messageSortBy,
                                                                  String messageSortDirection,
                                                                  int pageSize,
                                                                  int offset,
                                                                  long total) {
        return total == 0
                ? Collections.emptyList()
                : historyMapper.searchMessageHitsByLike(
                        keyword,
                        appType,
                        projectPath,
                        dateFrom,
                        dateTo,
                        resolveMessageOrderBy(messageSortBy, messageSortDirection, false),
                        pageSize,
                        offset
                );
    }

    private String resolveMatchedSource(HistorySearchSessionHit hit, String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return "session";
        }
        if (contains(hit.getTitle(), keyword)) {
            return "session-title";
        }
        if (contains(hit.getProjectPath(), keyword)) {
            return "project-path";
        }
        if (contains(hit.getSummary(), keyword)) {
            return "session-summary";
        }
        if (StringUtils.hasText(hit.getMatchedMessageText())) {
            return "message";
        }
        return "session";
    }

    private String resolveMatchedText(HistorySearchSessionHit hit, String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return clipText(firstNonBlank(hit.getSummary(), hit.getProjectPath(), hit.getTitle()), 160);
        }
        if (contains(hit.getTitle(), keyword)) {
            return clipTextAroundKeyword(hit.getTitle(), keyword, 160);
        }
        if (contains(hit.getProjectPath(), keyword)) {
            return clipTextAroundKeyword(hit.getProjectPath(), keyword, 160);
        }
        if (contains(hit.getSummary(), keyword)) {
            return clipTextAroundKeyword(hit.getSummary(), keyword, 160);
        }
        return clipTextAroundKeyword(hit.getMatchedMessageText(), keyword, 160);
    }

    private String buildFtsKeyword(String keyword) {
        return List.of(keyword.trim().split("\\s+"))
                .stream()
                .map(this::normalize)
                .filter(StringUtils::hasText)
                .map(token -> "\"" + token.replace("\"", "\"\"") + "\"")
                .collect(Collectors.joining(" AND "));
    }

    private int normalizePageNo(Integer pageNo) {
        if (pageNo == null) {
            return 1;
        }
        return Math.max(pageNo, 1);
    }

    private int normalizePageSize(Integer pageSize, int defaultValue) {
        if (pageSize == null) {
            return defaultValue;
        }
        return Math.min(Math.max(pageSize, 1), 100);
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private boolean contains(String source, String keyword) {
        return StringUtils.hasText(source)
                && StringUtils.hasText(keyword)
                && source.toLowerCase(Locale.ROOT).contains(keyword.toLowerCase(Locale.ROOT));
    }

    private String resolveSessionOrderBy(String sortBy, String sortDirection) {
        String direction = normalizeDirection(sortDirection, "desc");
        String normalizedSortBy = normalize(sortBy);
        if (!StringUtils.hasText(normalizedSortBy) || "lastMessageAt".equalsIgnoreCase(normalizedSortBy)) {
            return "COALESCE(s.last_message_at, s.created_at) " + direction + ", s.created_at DESC";
        }
        if ("createdAt".equalsIgnoreCase(normalizedSortBy)) {
            return "s.created_at " + direction + ", COALESCE(s.last_message_at, s.created_at) DESC";
        }
        if ("title".equalsIgnoreCase(normalizedSortBy)) {
            return "s.title " + direction + ", COALESCE(s.last_message_at, s.created_at) DESC";
        }
        return "COALESCE(s.last_message_at, s.created_at) DESC, s.created_at DESC";
    }

    private String resolveMessageOrderBy(String sortBy, String sortDirection, boolean relevanceAvailable) {
        String normalizedSortBy = normalize(sortBy);
        if (relevanceAvailable && (!StringUtils.hasText(normalizedSortBy) || "relevance".equalsIgnoreCase(normalizedSortBy))) {
            return "bm25(message_fts) ASC, m.created_at DESC";
        }

        String direction = normalizeDirection(sortDirection, "desc");
        if ("seqNo".equalsIgnoreCase(normalizedSortBy)) {
            return "m.seq_no " + direction + ", m.created_at " + direction;
        }
        return "m.created_at " + direction + ", m.seq_no " + direction;
    }

    private String normalizeDirection(String value, String defaultValue) {
        String normalized = normalize(value);
        if (!StringUtils.hasText(normalized)) {
            return defaultValue.toUpperCase(Locale.ROOT);
        }
        return "asc".equalsIgnoreCase(normalized) ? "ASC" : "DESC";
    }

    private String clipText(String text, int maxLength) {
        if (!StringUtils.hasText(text)) {
            return text;
        }
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }

    private String clipTextAroundKeyword(String text, String keyword, int maxLength) {
        if (!StringUtils.hasText(text)) {
            return text;
        }
        if (!StringUtils.hasText(keyword)) {
            return clipText(text, maxLength);
        }

        String normalizedSource = text.toLowerCase(Locale.ROOT);
        String normalizedKeyword = keyword.toLowerCase(Locale.ROOT);
        int matchIndex = normalizedSource.indexOf(normalizedKeyword);
        if (matchIndex < 0) {
            return clipText(text, maxLength);
        }
        if (text.length() <= maxLength) {
            return text;
        }

        int keywordLength = keyword.length();
        int halfWindow = Math.max((maxLength - keywordLength) / 2, 0);
        int start = Math.max(matchIndex - halfWindow, 0);
        int end = Math.min(start + maxLength, text.length());
        if (end - start < maxLength) {
            start = Math.max(end - maxLength, 0);
        }

        String prefix = start > 0 ? "..." : "";
        String suffix = end < text.length() ? "..." : "";
        return prefix + text.substring(start, end) + suffix;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }
}
