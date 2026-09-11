package com.upc.wms.agent.core;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 智能体数据工具：负责上下文 data(Map) 与业务 DTO 的相互转换、类型安全读取及 JSON 序列化。
 */
public final class AgentDataUtils {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private AgentDataUtils() {
    }

    /**
     * 将任意对象(如 Map)转换为目标 DTO 类型。
     */
    public static <T> T convert(Object value, Class<T> type) {
        return MAPPER.convertValue(value, type);
    }

    /**
     * 序列化为 JSON 字符串，失败时返回对象的 toString。
     */
    public static String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    public static String getString(Map<String, Object> data, String key) {
        Object v = data == null ? null : data.get(key);
        return v == null ? null : String.valueOf(v);
    }

    public static String getString(Map<String, Object> data, String key, String defaultValue) {
        String v = getString(data, key);
        return v == null || v.isBlank() ? defaultValue : v;
    }

    public static Long getLong(Map<String, Object> data, String key) {
        Object v = data == null ? null : data.get(key);
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static Long getLong(Map<String, Object> data, String key, Long defaultValue) {
        Long v = getLong(data, key);
        return v == null ? defaultValue : v;
    }

    public static BigDecimal getBigDecimal(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof BigDecimal b) {
            return b;
        }
        if (v instanceof Number n) {
            return new BigDecimal(n.toString());
        }
        try {
            return new BigDecimal(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static Boolean getBoolean(Map<String, Object> data, String key) {
        Object v = data == null ? null : data.get(key);
        if (v == null) {
            return false;
        }
        if (v instanceof Boolean b) {
            return b;
        }
        return Boolean.parseBoolean(String.valueOf(v));
    }

    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> getMapList(Map<String, Object> data, String key) {
        Object v = data == null ? null : data.get(key);
        if (v instanceof List<?> list) {
            return (List<Map<String, Object>>) list;
        }
        return List.of();
    }
}
