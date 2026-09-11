package com.upc.wms.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.upc.wms.config.DeepSeekProperties;
import com.upc.wms.dto.PmcExceptionVO;
import com.upc.wms.llm.DeepSeekChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 出库协同异常：调用 DeepSeek 生成简短 AI 建议，失败时回退模板。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PmcExceptionAiSuggestionService {

    private static final String SYSTEM_PROMPT = """
            你是测井装备 WMS 出库协同助手。
            根据异常信息给出 2～3 条可执行处理建议。
            要求：
            1. 每条不超过 20 个汉字，简洁可落地。
            2. 只基于给定事实，不要编造单据号或数量。
            3. 只输出 JSON 字符串数组，例如 ["建议1","建议2","建议3"]，不要其它文字。
            """;

    private final DeepSeekChatService deepSeekChatService;
    private final DeepSeekProperties deepSeekProperties;
    private final ObjectMapper objectMapper;

    private final ConcurrentHashMap<String, List<String>> cache = new ConcurrentHashMap<>();

    public List<String> suggest(PmcExceptionVO exception, List<String> fallback) {
        List<String> safeFallback = fallback == null || fallback.isEmpty()
                ? List.of("核对异常原因", "通知相关岗位协同处理", "必要时升级人工介入")
                : List.copyOf(fallback);

        if (exception == null) {
            return safeFallback;
        }
        if (!Boolean.TRUE.equals(deepSeekProperties.getEnabled())) {
            return safeFallback;
        }

        String cacheKey = buildCacheKey(exception);
        List<String> cached = cache.get(cacheKey);
        if (cached != null && !cached.isEmpty()) {
            return cached;
        }

        try {
            String reply = deepSeekChatService.chat(SYSTEM_PROMPT, buildUserPrompt(exception));
            List<String> suggestions = parseSuggestions(reply);
            if (suggestions.isEmpty()) {
                return safeFallback;
            }
            List<String> limited = suggestions.stream().limit(3).toList();
            cache.put(cacheKey, limited);
            return limited;
        } catch (Exception e) {
            log.warn("出库异常 AI 建议生成失败，使用模板回退: {}", e.getMessage());
            return safeFallback;
        }
    }

    private String buildCacheKey(PmcExceptionVO exception) {
        return String.join("|",
                nullToEmpty(exception.getType()),
                nullToEmpty(exception.getTitle()),
                exception.getPlanId() == null ? "" : String.valueOf(exception.getPlanId()),
                nullToEmpty(exception.getOutboundNo()));
    }

    private String buildUserPrompt(PmcExceptionVO exception) {
        return """
                异常类型：%s
                异常标题：%s
                影响说明：%s
                生产计划：%s
                出库单号：%s
                请给出 2～3 条简短处理建议。
                """.formatted(
                nullToDash(exception.getType()),
                nullToDash(exception.getTitle()),
                nullToDash(exception.getImpact()),
                nullToDash(exception.getPlanNo()),
                nullToDash(exception.getOutboundNo())
        );
    }

    private List<String> parseSuggestions(String reply) {
        if (!StringUtils.hasText(reply)) {
            return List.of();
        }
        String text = reply.trim();
        int start = text.indexOf('[');
        int end = text.lastIndexOf(']');
        if (start >= 0 && end > start) {
            text = text.substring(start, end + 1);
        }
        try {
            JsonNode node = objectMapper.readTree(text);
            if (node.isArray()) {
                List<String> list = new ArrayList<>();
                for (JsonNode item : node) {
                    String value = item.asText("").trim();
                    if (StringUtils.hasText(value)) {
                        list.add(trimSuggestion(value));
                    }
                }
                return list;
            }
        } catch (Exception ignored) {
            // fall through to line parsing
        }

        List<String> lines = new ArrayList<>();
        for (String raw : reply.split("\\R")) {
            String line = raw.trim()
                    .replaceFirst("^[\\d]+[.、)\\s]+", "")
                    .replaceFirst("^[-*•]\\s*", "")
                    .replaceAll("^\"|\"$", "")
                    .trim();
            if (StringUtils.hasText(line) && !line.startsWith("[") && !line.startsWith("{")) {
                lines.add(trimSuggestion(line));
            }
        }
        return lines;
    }

    private String trimSuggestion(String value) {
        String cleaned = value.replaceAll("[\\[\\]\"]", "").trim();
        if (cleaned.length() > 24) {
            return cleaned.substring(0, 24);
        }
        return cleaned;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String nullToDash(String value) {
        return StringUtils.hasText(value) ? value : "-";
    }
}
