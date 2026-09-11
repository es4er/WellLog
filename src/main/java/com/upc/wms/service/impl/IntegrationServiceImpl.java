package com.upc.wms.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.upc.wms.common.BusinessException;
import com.upc.wms.common.NoGenerator;
import com.upc.wms.dto.IntegrationSyncResult;
import com.upc.wms.entity.IntIntegrationMessage;
import com.upc.wms.entity.IntSystem;
import com.upc.wms.entity.OrdCustomerOrder;
import com.upc.wms.entity.OrdCustomerOrderLine;
import com.upc.wms.entity.MdItem;
import com.upc.wms.mapper.IntIntegrationMessageMapper;
import com.upc.wms.mapper.IntSystemMapper;
import com.upc.wms.mapper.MdItemMapper;
import com.upc.wms.mapper.OrdCustomerOrderMapper;
import com.upc.wms.service.IntegrationService;
import com.upc.wms.service.OrderService;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class IntegrationServiceImpl implements IntegrationService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final DateTimeFormatter SYNC_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    /** 测井工具 10 物料编码（与 logging_materials_seed.sql 一致） */
    private static final List<String> LOGGING_ITEM_CODES = List.of(
            "M-LOG-FLANGE", "M-LOG-PROBE-SHELL", "M-LOG-PRESS-CYL", "M-LOG-CONN-JOINT",
            "M-LOG-THREAD-JOINT", "M-LOG-CENTRALIZER", "M-LOG-STAB-BLADE", "M-LOG-SLIP-SEAT",
            "M-LOG-TOP-SUB", "M-LOG-BOP-SUB"
    );
    private static final List<Long> LOGGING_ITEM_IDS_FALLBACK = List.of(
            1001L, 1002L, 1003L, 1004L, 1005L, 1006L, 1007L, 1008L, 1009L, 1010L
    );
    /** ERP 单号与产品名分隔符：ERP-{day}-{ts}|{产品名} */
    static final String LOGGING_ERP_PRODUCT_DELIM = "|";
    private static final List<String> LOGGING_PRODUCT_NAMES = List.of(
            "阵列感应测井仪探头短节",
            "声波测井仪井下总成",
            "电阻率测井仪连接工具串",
            "自然伽马能谱测井短节",
            "密度孔隙度测井仪探头",
            "随钻测井 MWD 脉冲发生器",
            "套管井介电扫描测井仪",
            "井下高温压力采集短节",
            "方位成像测井仪旋转接头",
            "过钻具存储式测井仪",
            "多极子阵列声波测井仪",
            "井壁取心仪井下模块",
            "补偿中子孔隙度测井仪",
            "双感应八侧向测井仪总成",
            "近钻头随钻伽马测井工具"
    );

    private final IntSystemMapper intSystemMapper;
    private final IntIntegrationMessageMapper intIntegrationMessageMapper;
    private final OrdCustomerOrderMapper ordCustomerOrderMapper;
    private final MdItemMapper mdItemMapper;
    private final OrderService orderService;

    public IntegrationServiceImpl(IntSystemMapper intSystemMapper,
                                  IntIntegrationMessageMapper intIntegrationMessageMapper,
                                  OrdCustomerOrderMapper ordCustomerOrderMapper,
                                  MdItemMapper mdItemMapper,
                                  @Lazy OrderService orderService) {
        this.intSystemMapper = intSystemMapper;
        this.intIntegrationMessageMapper = intIntegrationMessageMapper;
        this.ordCustomerOrderMapper = ordCustomerOrderMapper;
        this.mdItemMapper = mdItemMapper;
        this.orderService = orderService;
    }

    @Override
    public List<IntSystem> listSystems() {
        return intSystemMapper.selectList(null);
    }

    @Override
    public IntSystem addSystem(IntSystem system) {
        if (system.getEnabledFlag() == null) {
            system.setEnabledFlag(1);
        }
        intSystemMapper.insert(system);
        return system;
    }

    private IntIntegrationMessage saveMessage(Long systemId, String direction, String messageType,
                                              String businessKey, String payloadJson) {
        IntIntegrationMessage message = new IntIntegrationMessage();
        message.setSystemId(systemId);
        message.setDirection(direction);
        message.setMessageType(messageType);
        message.setBusinessKey(businessKey);
        message.setPayloadJson(payloadJson);
        message.setProcessStatus("PENDING");
        message.setRetryCount(0);
        message.setCreatedAt(LocalDateTime.now());
        intIntegrationMessageMapper.insert(message);
        return message;
    }

    @Override
    public IntIntegrationMessage saveInboundMessage(Long systemId, String messageType, String businessKey, String payloadJson) {
        return saveMessage(systemId, "IN", messageType, businessKey, payloadJson);
    }

    @Override
    public IntIntegrationMessage saveOutboundMessage(Long systemId, String messageType, String businessKey, String payloadJson) {
        return saveMessage(systemId, "OUT", messageType, businessKey, payloadJson);
    }

    @Override
    public List<IntIntegrationMessage> listPendingMessages() {
        return intIntegrationMessageMapper.selectPendingMessages();
    }

    @Override
    public void markMessageSuccess(Long messageId) {
        intIntegrationMessageMapper.markSuccess(messageId);
    }

    @Override
    public void markMessageFailed(Long messageId, String errorMessage) {
        intIntegrationMessageMapper.markFailed(messageId, errorMessage);
    }

    @Override
    public void retryMessage(Long messageId) {
        IntIntegrationMessage message = intIntegrationMessageMapper.selectById(messageId);
        if (message == null) {
            throw new BusinessException("接口消息不存在");
        }
        intIntegrationMessageMapper.increaseRetryCount(messageId);
        message.setProcessStatus("PENDING");
        message.setProcessedAt(null);
        intIntegrationMessageMapper.updateById(message);
    }

    @Override
    @Transactional
    public OrdCustomerOrder processInboundBusinessMessage(IntIntegrationMessage message) {
        if (message == null) {
            throw new BusinessException("集成消息为空");
        }
        String type = message.getMessageType() == null ? "" : message.getMessageType().toUpperCase();
        if (!type.contains("ORDER") && !type.contains("CUSTOMER") && !type.contains("MES")) {
            // 非订单类消息：仅登记成功
            markMessageSuccess(message.getMessageId());
            return null;
        }
        try {
            JsonNode root = MAPPER.readTree(message.getPayloadJson() == null ? "{}" : message.getPayloadJson());
            String orderNo = text(root, "orderNo", message.getBusinessKey());
            if (orderNo == null || orderNo.isBlank()) {
                orderNo = NoGenerator.next("ORD");
            }
            OrdCustomerOrder existing = orderService.findByOrderNo(orderNo);
            if (existing != null) {
                markMessageSuccess(message.getMessageId());
                return existing;
            }

            OrdCustomerOrder order = new OrdCustomerOrder();
            order.setOrderNo(orderNo);
            order.setCustomerId(longVal(root, "customerId", 1L));
            order.setSourceSystemId(message.getSystemId() != null ? message.getSystemId() : longVal(root, "sourceSystemId", 1L));
            order.setErpOrderNo(text(root, "erpOrderNo", orderNo));
            order.setOrderDate(dateVal(root, "orderDate", LocalDate.now()));
            order.setDeliveryDate(dateVal(root, "deliveryDate", LocalDate.now().plusDays(14)));
            order.setOrderStatus("PENDING_REVIEW");
            order.setCreatedAt(LocalDateTime.now());

            List<OrdCustomerOrderLine> lines = new ArrayList<>();
            JsonNode linesNode = root.get("lines");
            if (linesNode != null && linesNode.isArray() && !linesNode.isEmpty()) {
                for (JsonNode lineNode : linesNode) {
                    OrdCustomerOrderLine line = new OrdCustomerOrderLine();
                    line.setItemId(longVal(lineNode, "itemId", 1L));
                    line.setOrderedQty(decimalVal(lineNode, "orderedQty", BigDecimal.ONE));
                    line.setRequiredDate(dateVal(lineNode, "requiredDate", order.getDeliveryDate()));
                    line.setLineStatus("OPEN");
                    lines.add(line);
                }
            } else {
                OrdCustomerOrderLine line = new OrdCustomerOrderLine();
                line.setItemId(longVal(root, "itemId", 1L));
                line.setOrderedQty(decimalVal(root, "orderedQty", BigDecimal.TEN));
                line.setRequiredDate(order.getDeliveryDate());
                line.setLineStatus("OPEN");
                lines.add(line);
            }

            OrdCustomerOrder created = orderService.createOrder(order, lines);
            markMessageSuccess(message.getMessageId());
            return created;
        } catch (Exception e) {
            markMessageFailed(message.getMessageId(), e.getMessage());
            throw new BusinessException("集成订单消息处理失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public IntegrationSyncResult syncOrdersFromExternal() {
        IntegrationSyncResult result = new IntegrationSyncResult();
        result.setSyncedCount(0);
        result.setSkippedCount(0);
        result.setOrderNos(new ArrayList<>());
        result.setMessageIds(new ArrayList<>());

        List<Map<String, Object>> payloads = buildExternalOrderPayloads();
        int synced = 0;
        int skipped = 0;
        for (Map<String, Object> payload : payloads) {
            Long systemId = ((Number) payload.getOrDefault("sourceSystemId", 1L)).longValue();
            String orderNo = String.valueOf(payload.get("orderNo"));
            String messageType = systemId != null && systemId == 2L ? "MES_ORDER" : "CUSTOMER_ORDER";

            if (orderService.findByOrderNo(orderNo) != null) {
                skipped++;
                continue;
            }

            String payloadJson;
            try {
                payloadJson = MAPPER.writeValueAsString(payload);
            } catch (Exception e) {
                throw new BusinessException("序列化同步报文失败: " + e.getMessage());
            }

            IntIntegrationMessage message = saveInboundMessage(systemId, messageType, orderNo, payloadJson);
            OrdCustomerOrder created = processInboundBusinessMessage(message);
            if (created != null) {
                synced++;
                result.getOrderNos().add(created.getOrderNo());
                result.getMessageIds().add(message.getMessageId());
            } else {
                skipped++;
            }
        }

        result.setSyncedCount(synced);
        result.setSkippedCount(skipped);
        result.setPendingReview(countPendingReview());
        result.setSyncedAt(LocalDateTime.now().format(SYNC_FMT));
        result.setSource("IntegrationAgent · ERP/MES");
        if (synced > 0) {
            result.setMessage("IntegrationAgent 已从 ERP/MES 同步 1 条测井设备客户订单（物料从 10 种测井件中随机选配 1~3 种），当前待审核 "
                    + result.getPendingReview() + " 条。");
        } else if (result.getPendingReview() > 0) {
            result.setMessage("本次无新增订单（已存在 " + skipped + " 条），当前仍有 "
                    + result.getPendingReview() + " 条待审核订单。");
        } else {
            result.setMessage("本次无新增订单，当前无待审核订单。");
        }
        return result;
    }

    @Override
    public Map<String, Object> latestOrderSyncSummary() {
        Map<String, Object> summary = new LinkedHashMap<>();
        IntIntegrationMessage latest = intIntegrationMessageMapper.selectOne(
                new LambdaQueryWrapper<IntIntegrationMessage>()
                        .eq(IntIntegrationMessage::getDirection, "IN")
                        .and(w -> w.eq(IntIntegrationMessage::getMessageType, "CUSTOMER_ORDER")
                                .or().eq(IntIntegrationMessage::getMessageType, "MES_ORDER"))
                        .eq(IntIntegrationMessage::getProcessStatus, "SUCCESS")
                        .orderByDesc(IntIntegrationMessage::getProcessedAt)
                        .orderByDesc(IntIntegrationMessage::getMessageId)
                        .last("LIMIT 1"));
        int pending = countPendingReview();
        summary.put("pendingReview", pending);
        if (latest == null) {
            summary.put("lastIntegrationSyncAt", null);
            summary.put("lastIntegrationSyncCount", 0);
            summary.put("inboxHint", pending > 0
                    ? "你有 " + pending + " 条新订单 / 计划待审核。尚未检测到 IntegrationAgent 同步记录，可点击同步从 ERP/MES 拉取。"
                    : "暂无待审核订单。可点击同步，由 IntegrationAgent 从 ERP/MES 拉取新订单。");
            return summary;
        }

        LocalDateTime syncTime = latest.getProcessedAt() != null ? latest.getProcessedAt() : latest.getCreatedAt();
        String syncAt = syncTime != null ? syncTime.format(SYNC_FMT) : null;
        // 统计同一分钟内成功的订单同步消息数，近似「最近一次同步批次」
        int batchCount = 1;
        if (syncTime != null) {
            Long count = intIntegrationMessageMapper.selectCount(
                    new LambdaQueryWrapper<IntIntegrationMessage>()
                            .eq(IntIntegrationMessage::getDirection, "IN")
                            .and(w -> w.eq(IntIntegrationMessage::getMessageType, "CUSTOMER_ORDER")
                                    .or().eq(IntIntegrationMessage::getMessageType, "MES_ORDER"))
                            .eq(IntIntegrationMessage::getProcessStatus, "SUCCESS")
                            .ge(IntIntegrationMessage::getProcessedAt, syncTime.minusMinutes(2))
                            .le(IntIntegrationMessage::getProcessedAt, syncTime.plusMinutes(1)));
            batchCount = count == null ? 1 : Math.max(1, count.intValue());
        }

        summary.put("lastIntegrationSyncAt", syncAt);
        summary.put("lastIntegrationSyncCount", batchCount);
        summary.put("inboxHint", pending > 0
                ? "你有 " + pending + " 条新订单 / 计划待审核，已由 IntegrationAgent 从 ERP / MES 同步接收"
                        + (syncAt != null ? "（最近同步 " + syncAt + "）" : "") + "。"
                : "IntegrationAgent 最近已同步 ERP/MES 订单（" + syncAt + "），当前无待审核单据。");
        return summary;
    }

    private int countPendingReview() {
        Long count = ordCustomerOrderMapper.selectCount(
                new LambdaQueryWrapper<OrdCustomerOrder>().eq(OrdCustomerOrder::getOrderStatus, "PENDING_REVIEW"));
        return count == null ? 0 : count.intValue();
    }

    /**
     * 模拟 ERP/MES 推送：每次同步 1 条客户订单，物料从测井 10 件池中随机抽取 1~3 种，产品名为测井设备风格随机命名。
     */
    private List<Map<String, Object>> buildExternalOrderPayloads() {
        LocalDate today = LocalDate.now();
        List<Map<String, Object>> list = new ArrayList<>();
        list.add(buildLoggingToolsOrderPayload(today));
        return list;
    }

    private Map<String, Object> buildLoggingToolsOrderPayload(LocalDate today) {
        LocalDateTime now = LocalDateTime.now();
        String day = today.format(DateTimeFormatter.BASIC_ISO_DATE);
        String ts = now.format(DateTimeFormatter.ofPattern("HHmmss"));
        String orderNo = "ORD" + day + "LOG" + ts;
        LocalDate deliveryDate = today.plusDays(14);
        String productName = pickRandomLoggingProductName();
        int productQty = ThreadLocalRandom.current().nextInt(1, 6);
        String erpNo = "ERP-" + day + "-" + ts + LOGGING_ERP_PRODUCT_DELIM + productName
                + LOGGING_ERP_PRODUCT_DELIM + productQty;

        List<Long> pool = new ArrayList<>(resolveLoggingItemIds());
        Collections.shuffle(pool);
        int lineCount = ThreadLocalRandom.current().nextInt(1, Math.min(3, pool.size()) + 1);
        List<Long> picked = pool.subList(0, lineCount);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderNo", orderNo);
        payload.put("sourceSystemId", 1L);
        payload.put("customerId", 1L);
        payload.put("erpOrderNo", erpNo);
        payload.put("orderDate", today.toString());
        payload.put("deliveryDate", deliveryDate.toString());
        payload.put("productName", productName);
        payload.put("productQty", productQty);

        List<Map<String, Object>> lines = new ArrayList<>();
        for (Long itemId : picked) {
            Map<String, Object> line = new HashMap<>();
            line.put("itemId", itemId);
            line.put("orderedQty", BigDecimal.valueOf(ThreadLocalRandom.current().nextInt(5, 26)));
            line.put("requiredDate", deliveryDate.toString());
            lines.add(line);
        }
        payload.put("lines", lines);
        return payload;
    }

    private String pickRandomLoggingProductName() {
        int idx = ThreadLocalRandom.current().nextInt(LOGGING_PRODUCT_NAMES.size());
        return LOGGING_PRODUCT_NAMES.get(idx);
    }

    private List<Long> resolveLoggingItemIds() {
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < LOGGING_ITEM_CODES.size(); i++) {
            String code = LOGGING_ITEM_CODES.get(i);
            MdItem item = mdItemMapper.selectByCode(code);
            if (item != null && item.getItemId() != null) {
                ids.add(item.getItemId());
            } else {
                ids.add(LOGGING_ITEM_IDS_FALLBACK.get(i));
            }
        }
        return ids;
    }

    private static String text(JsonNode node, String field, String fallback) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return fallback;
        }
        String v = node.get(field).asText();
        return v == null || v.isBlank() ? fallback : v;
    }

    private static Long longVal(JsonNode node, String field, Long fallback) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return fallback;
        }
        return node.get(field).asLong(fallback);
    }

    private static BigDecimal decimalVal(JsonNode node, String field, BigDecimal fallback) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return fallback;
        }
        try {
            return new BigDecimal(node.get(field).asText());
        } catch (Exception e) {
            return fallback;
        }
    }

    private static LocalDate dateVal(JsonNode node, String field, LocalDate fallback) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return fallback;
        }
        try {
            return LocalDate.parse(node.get(field).asText());
        } catch (Exception e) {
            return fallback;
        }
    }
}
