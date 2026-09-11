package com.upc.wms.service.impl;

import com.upc.wms.common.BusinessException;
import com.upc.wms.common.NoGenerator;
import com.upc.wms.entity.AgvTask;
import com.upc.wms.entity.BarcodeLabel;
import com.upc.wms.entity.IotEvent;
import com.upc.wms.entity.RfidTag;
import com.upc.wms.mapper.AgvTaskMapper;
import com.upc.wms.mapper.BarcodeLabelMapper;
import com.upc.wms.mapper.IotEventMapper;
import com.upc.wms.mapper.RfidTagMapper;
import com.upc.wms.service.SmartWarehouseService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 智能仓储服务实现。
 * 管理条码（Barcode）、RFID标签、AGV任务、IoT事件等智能化设备交互。
 * <ul>
 *   <li>条码管理：绑定库存、扫码识读——入库/拣货扫码的核心依赖</li>
 *   <li>RFID标签：绑定物资、追踪位置——自动识别物资出入库</li>
 *   <li>AGV任务：自动导引车搬运指令——仓库物流自动化</li>
 *   <li>IoT事件：传感器/设备上报的原始事件——未处理事件需定期消费</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class SmartWarehouseServiceImpl implements SmartWarehouseService {

    /** 条码标签 Mapper */
    private final BarcodeLabelMapper barcodeLabelMapper;
    /** RFID 标签 Mapper */
    private final RfidTagMapper rfidTagMapper;
    /** AGV 运输任务 Mapper */
    private final AgvTaskMapper agvTaskMapper;
    /** IoT 设备事件 Mapper */
    private final IotEventMapper iotEventMapper;

    // ======================== 条码管理 ========================

    /**
     * 绑定条码标签。
     * 条码标签绑定到某个业务对象（通过 bindId），可以是库存记录、设备、货架等。
     * 默认状态 ENABLED 表示该标签已激活可扫码使用。
     */
    @Override
    public BarcodeLabel bindBarcode(BarcodeLabel label) {
        if (label.getStatus() == null) {
            label.setStatus("ENABLED"); // 新条码默认启用
        }
        if (label.getCreatedAt() == null) {
            label.setCreatedAt(LocalDateTime.now()); // 记录绑定时间
        }
        barcodeLabelMapper.insert(label);
        return label;
    }

    /**
     * 扫码识读：通过条码值查找绑定的对象。
     * 工人扫码后系统根据条码值查出库存、物料、批次等关联信息，
     * 用于拣货确认、上架定位、质检追溯等场景。
     */
    @Override
    public BarcodeLabel scanBarcode(String barcodeValue) {
        BarcodeLabel label = barcodeLabelMapper.selectByValue(barcodeValue);
        if (label == null) {
            // 扫码的条码未在系统中绑定——可能是原始物料自带条码或标签损坏
            throw new BusinessException("未找到该条码绑定信息: " + barcodeValue);
        }
        return label;
    }

    // ======================== RFID 标签管理 ========================

    /**
     * 绑定 RFID 标签。
     * RFID 标签通过射频信号自动识别，无需人工扫码。
     * 默认状态 ACTIVE 表示标签已被激活可读取。
     */
    @Override
    public RfidTag bindRfid(RfidTag tag) {
        if (tag.getTagStatus() == null) {
            tag.setTagStatus("ACTIVE"); // RFID标签默认激活
        }
        rfidTagMapper.insert(tag);
        return tag;
    }

    /**
     * 更新 RFID 标签最后被读取的时间。
     * 当 RFID 读写器扫描到标签时调用此方法，用于：
     * 1. 确认物资仍在仓储范围
     * 2. 检测长时间未扫描的标签（可能丢失/被盗）
     */
    @Override
    public void updateRfidLastSeen(Long rfidId) {
        RfidTag tag = rfidTagMapper.selectById(rfidId);
        if (tag != null) {
            tag.setLastSeenAt(LocalDateTime.now()); // 记录最近一次被读到的时间
            rfidTagMapper.updateById(tag);
        }
    }

    // ======================== AGV 任务管理 ========================

    /**
     * 创建 AGV 搬运任务。
     * AGV 任务编号以 AGV 前缀唯一标识，默认状态 PENDING 表示等待 AGV 调度系统取走执行。
     */
    @Override
    public AgvTask createAgvTask(AgvTask task) {
        if (task.getAgvTaskNo() == null) {
            task.setAgvTaskNo(NoGenerator.next("AGV")); // 生成唯一任务号
        }
        if (task.getTaskStatus() == null) {
            task.setTaskStatus("PENDING"); // 新建任务默认等待执行
        }
        agvTaskMapper.insert(task);
        return task;
    }

    /**
     * 更新 AGV 任务状态。
     * 状态流转：PENDING → IN_PROGRESS → COMPLETED / EXCEPTION。
     * 如果任务完成，同时记录完成时间以便统计 AGV 效率。
     */
    @Override
    public void updateAgvTaskStatus(Long agvTaskId, String status) {
        AgvTask task = agvTaskMapper.selectById(agvTaskId);
        if (task == null) {
            throw new BusinessException("AGV任务不存在");
        }
        task.setTaskStatus(status);
        if ("COMPLETED".equals(status)) {
            task.setCompletedAt(LocalDateTime.now()); // 任务完成时刻
        }
        agvTaskMapper.updateById(task);
    }

    // ======================== IoT 事件管理 ========================

    /**
     * 保存 IoT 事件（温湿度、振动、门禁等传感器上报）。
     * processedFlag=0 表示未处理，后续由定时任务或 Agent 消费处理。
     */
    @Override
    public IotEvent saveIotEvent(IotEvent event) {
        if (event.getEventTime() == null) {
            event.setEventTime(LocalDateTime.now()); // 事件时间默认当前时间
        }
        if (event.getProcessedFlag() == null) {
            event.setProcessedFlag(0); // 0=未处理，1=已处理
        }
        iotEventMapper.insert(event);
        return event;
    }

    /**
     * 获取所有未处理的 IoT 事件。
     * 通常由定时 Job 或 IoT Agent 消费，处理后调用 markIotEventProcessed。
     */
    @Override
    public List<IotEvent> listUnprocessedIotEvents() {
        return iotEventMapper.selectUnprocessed();
    }

    /**
     * 标记 IoT 事件为已处理。
     * 处理后的记录保留用于审计追溯，不做物理删除。
     */
    @Override
    public void markIotEventProcessed(Long eventId) {
        iotEventMapper.markProcessed(eventId);
    }
}
