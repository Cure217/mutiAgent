package com.aliano.mutiagent.application.service;

import com.aliano.mutiagent.application.dto.HistorySearchMessageHit;
import com.aliano.mutiagent.application.dto.HistorySearchResult;
import com.aliano.mutiagent.application.dto.HistorySearchSessionHit;
import com.aliano.mutiagent.infrastructure.persistence.mapper.HistoryMapper;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class HistoryAppService {

    private final HistoryMapper historyMapper;

    public HistorySearchResult search(String keyword,
                                      String appType,
                                      String projectPath,
                                      String dateFrom,
                                      String dateTo,
                                      Integer sessionLimit,
                                      Integer messageLimit) {
        String normalizedKeyword = normalize(keyword);
        String normalizedAppType = normalize(appType);
        String normalizedProjectPath = normalize(projectPath);
        String normalizedDateFrom = normalize(dateFrom);
        String normalizedDateTo = normalize(dateTo);
        int safeSessionLimit = normalizeLimit(sessionLimit, 20);
        int safeMessageLimit = normalizeLimit(messageLimit, 20);

        List<HistorySearchSessionHit> sessionHits = historyMapper.searchSessionHits(
                normalizedKeyword,
                normalizedAppType,
                normalizedProjectPath,
                normalizedDateFrom,
                normalizedDateTo,
                safeSessionLimit
        );
        sessionHits.forEach(hit -> {
            hit.setMatchedSource(resolveMatchedSource(hit, normalizedKeyword));
            hit.setMatchedText(resolveMatchedText(hit, normalizedKeyword));
        });

        List<HistorySearchMessageHit> messageHits = Collections.emptyList();
        if (StringUtils.hasText(normalizedKeyword)) {
            historyMapper.syncMessageFts();
            String ftsKeyword = buildFtsKeyword(normalizedKeyword);
            try {
                messageHits = historyMapper.searchMessageHitsByFts(
                        ftsKeyword,
                        normalizedAppType,
                        normalizedProjectPath,
                        normalizedDateFrom,
                        normalizedDateTo,
                        safeMessageLimit
                );
            } catch (DataAccessException exception) {
                messageHits = historyMapper.searchMessageHitsByLike(
                        normalizedKeyword,
                        normalizedAppType,
                        normalizedProjectPath,
                        normalizedDateFrom,
                        normalizedDateTo,
                        safeMessageLimit
                );
            }
            messageHits.forEach(hit -> hit.setSnippet(clipText(hit.getSnippet(), 200)));
        }

        HistorySearchResult result = new HistorySearchResult();
        result.setSessions(sessionHits);
        result.setMessages(messageHits);
        return result;
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
            return clipText(hit.getTitle(), 160);
        }
        if (contains(hit.getProjectPath(), keyword)) {
            return clipText(hit.getProjectPath(), 160);
        }
        if (contains(hit.getSummary(), keyword)) {
            return clipText(hit.getSummary(), 160);
        }
        return clipText(hit.getMatchedMessageText(), 160);
    }

    private String buildFtsKeyword(String keyword) {
        return List.of(keyword.trim().split("\\s+"))
                .stream()
                .map(this::normalize)
                .filter(StringUtils::hasText)
                .map(token -> "\"" + token.replace("\"", "\"\"") + "\"")
                .collect(Collectors.joining(" AND "));
    }

    private int normalizeLimit(Integer limit, int defaultValue) {
        if (limit == null) {
            return defaultValue;
        }
        return Math.min(Math.max(limit, 1), 100);
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private boolean contains(String source, String keyword) {
        return StringUtils.hasText(source)
                && StringUtils.hasText(keyword)
                && source.toLowerCase(Locale.ROOT).contains(keyword.toLowerCase(Locale.ROOT));
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

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }
}
