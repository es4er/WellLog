package com.upc.wms.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.upc.wms.dto.VisualInspectionCompareVO;
import lombok.RequiredArgsConstructor;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class VisualInspectionService {

    private final OkHttpClient okHttpClient;
    private final ObjectMapper objectMapper;

    @Value("${inspection.service.base-url:http://localhost:8002}")
    private String inspectionServiceBaseUrl;

    public VisualInspectionCompareVO compareImages(
            MultipartFile referenceImage,
            MultipartFile inspectionImage,
            Integer minArea,
            Integer threshold
    ) {
        if (referenceImage == null || referenceImage.isEmpty()) {
            throw new IllegalArgumentException("标准样图不能为空");
        }
        if (inspectionImage == null || inspectionImage.isEmpty()) {
            throw new IllegalArgumentException("待检样图不能为空");
        }

        int resolvedMinArea = minArea != null ? minArea : 300;
        int resolvedThreshold = threshold != null ? threshold : 45;

        String compareUrl = inspectionServiceBaseUrl
                + "/api/inspection/compare?minArea="
                + resolvedMinArea
                + "&threshold="
                + resolvedThreshold;

        RequestBody requestBody;
        try {
            requestBody = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart(
                            "referenceImage",
                            safeFilename(referenceImage.getOriginalFilename(), "reference.jpg"),
                            RequestBody.create(
                                    referenceImage.getBytes(),
                                    MediaType.parse(resolveContentType(referenceImage))
                            )
                    )
                    .addFormDataPart(
                            "inspectionImage",
                            safeFilename(inspectionImage.getOriginalFilename(), "inspection.jpg"),
                            RequestBody.create(
                                    inspectionImage.getBytes(),
                                    MediaType.parse(resolveContentType(inspectionImage))
                            )
                    )
                    .build();
        } catch (IOException e) {
            throw new IllegalStateException("读取上传图片失败", e);
        }

        Request request = new Request.Builder()
                .url(compareUrl)
                .post(requestBody)
                .build();

        try (Response response = okHttpClient.newCall(request).execute()) {
            String body = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                String detail = extractErrorDetail(body, response.code());
                throw new IllegalStateException(detail);
            }

            JsonNode root = objectMapper.readTree(body);
            JsonNode data = root.path("data");
            if (data.isMissingNode()) {
                throw new IllegalStateException("图像对比服务返回格式异常");
            }

            VisualInspectionCompareVO vo = new VisualInspectionCompareVO();
            vo.setTaskId(textValue(data, "taskId"));
            vo.setIsAbnormal(data.path("isAbnormal").asBoolean(false));
            vo.setSimilarity(data.path("similarity").asDouble(0));
            vo.setAbnormalScore(data.path("abnormalScore").asDouble(0));
            vo.setMatchedFeatureCount(data.path("matchedFeatureCount").asInt(0));
            vo.setRegionCount(data.path("regionCount").asInt(0));
            vo.setRegions(parseRegions(data.path("regions")));
            vo.setResultImageUrl(toPublicImageUrl(textValue(data, "resultImageUrl")));
            vo.setDifferenceImageUrl(toPublicImageUrl(textValue(data, "differenceImageUrl")));
            return vo;
        } catch (IOException e) {
            throw new IllegalStateException(
                    "无法连接图像对比服务，请确认 light-inspection-service 已启动（默认端口 8002）",
                    e
            );
        }
    }

    private String toPublicImageUrl(String relativeUrl) {
        if (relativeUrl == null || relativeUrl.isBlank()) {
            return "";
        }
        if (relativeUrl.startsWith("http://") || relativeUrl.startsWith("https://")) {
            return relativeUrl;
        }
        String normalized = relativeUrl.startsWith("/") ? relativeUrl : "/" + relativeUrl;
        return inspectionServiceBaseUrl + normalized;
    }

    private List<VisualInspectionCompareVO.Region> parseRegions(JsonNode regionsNode) {
        List<VisualInspectionCompareVO.Region> regions = new ArrayList<>();
        if (!regionsNode.isArray()) {
            return regions;
        }
        for (JsonNode node : regionsNode) {
            VisualInspectionCompareVO.Region region = new VisualInspectionCompareVO.Region();
            region.setRegionId(textValue(node, "regionId"));
            region.setX(node.path("x").asInt(0));
            region.setY(node.path("y").asInt(0));
            region.setWidth(node.path("width").asInt(0));
            region.setHeight(node.path("height").asInt(0));
            region.setArea(node.path("area").asDouble(0));
            region.setScore(node.path("score").asDouble(0));
            region.setStatus(textValue(node, "status", "PENDING"));
            regions.add(region);
        }
        return regions;
    }

    private String extractErrorDetail(String body, int statusCode) {
        try {
            JsonNode root = objectMapper.readTree(body);
            if (root.has("detail")) {
                return root.get("detail").asText();
            }
            if (root.has("message")) {
                return root.get("message").asText();
            }
        } catch (Exception ignored) {
            // fall through
        }
        return "图像对比失败（HTTP " + statusCode + "）";
    }

    private String safeFilename(String originalFilename, String fallback) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return fallback;
        }
        return originalFilename;
    }

    private String resolveContentType(MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType == null || contentType.isBlank()) {
            return "application/octet-stream";
        }
        return contentType;
    }

    private String textValue(JsonNode node, String field) {
        return textValue(node, field, "");
    }

    private String textValue(JsonNode node, String field, String fallback) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? fallback : value.asText(fallback);
    }
}
