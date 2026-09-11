package com.upc.wms.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.upc.wms.common.BusinessException;
import com.upc.wms.config.DeepSeekProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * DeepSeek Chat Completions 客户端（OpenAI 兼容协议）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeepSeekChatService {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient okHttpClient;
    private final ObjectMapper objectMapper;
    private final DeepSeekProperties properties;

    /**
     * 发送对话请求并返回模型回复文本。
     */
    public String chat(String systemPrompt, String userPrompt) {
        if (!Boolean.TRUE.equals(properties.getEnabled())) {
            throw new BusinessException("DeepSeek 未启用，请在配置中设置 deepseek.enabled=true");
        }
        if (!StringUtils.hasText(properties.getApiKey())) {
            throw new BusinessException("DeepSeek API Key 未配置");
        }

        Map<String, Object> body = new HashMap<>();
        body.put("model", properties.getModel());
        body.put("temperature", properties.getTemperature());
        body.put("max_tokens", properties.getMaxTokens());

        List<Map<String, String>> messages = new ArrayList<>();
        if (StringUtils.hasText(systemPrompt)) {
            Map<String, String> system = new HashMap<>();
            system.put("role", "system");
            system.put("content", systemPrompt);
            messages.add(system);
        }
        Map<String, String> user = new HashMap<>();
        user.put("role", "user");
        user.put("content", userPrompt);
        messages.add(user);
        body.put("messages", messages);

        try {
            String json = objectMapper.writeValueAsString(body);
            Request request = new Request.Builder()
                    .url(properties.getApiUrl())
                    .addHeader("Authorization", "Bearer " + properties.getApiKey())
                    .addHeader("Content-Type", "application/json")
                    .post(RequestBody.create(json, JSON))
                    .build();

            try (Response response = okHttpClient.newCall(request).execute()) {
                String responseBody = response.body() != null ? response.body().string() : "";
                if (!response.isSuccessful()) {
                    log.warn("DeepSeek 请求失败: status={}, body={}", response.code(), responseBody);
                    throw new BusinessException("DeepSeek 调用失败: HTTP " + response.code());
                }

                JsonNode root = objectMapper.readTree(responseBody);
                JsonNode content = root.path("choices").path(0).path("message").path("content");
                if (content.isMissingNode() || !StringUtils.hasText(content.asText())) {
                    throw new BusinessException("DeepSeek 返回内容为空");
                }
                return content.asText().trim();
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("DeepSeek 调用异常", e);
            throw new BusinessException("DeepSeek 调用异常: " + e.getMessage());
        }
    }
}
