package com.upc.wms.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.upc.wms.common.BusinessException;
import com.upc.wms.dto.QuaInspectStandardSaveRequest;
import com.upc.wms.dto.QuaInspectStandardVO;
import com.upc.wms.entity.MdItem;
import com.upc.wms.entity.QuaInspectItem;
import com.upc.wms.entity.QuaInspectStandard;
import com.upc.wms.entity.QuaInspectStandardLine;
import com.upc.wms.mapper.MdItemMapper;
import com.upc.wms.mapper.QuaInspectItemMapper;
import com.upc.wms.mapper.QuaInspectStandardLineMapper;
import com.upc.wms.mapper.QuaInspectStandardMapper;
import com.upc.wms.service.QualityMasterDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class QualityMasterDataServiceImpl implements QualityMasterDataService {

    private final QuaInspectItemMapper inspectItemMapper;
    private final QuaInspectStandardMapper standardMapper;
    private final QuaInspectStandardLineMapper standardLineMapper;
    private final MdItemMapper mdItemMapper;

    @Override
    public List<QuaInspectItem> listInspectItems(String keyword, String itemType, String status) {
        LambdaQueryWrapper<QuaInspectItem> qw = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(status)) {
            qw.eq(QuaInspectItem::getStatus, status.trim().toUpperCase(Locale.ROOT));
        }
        if (StringUtils.hasText(itemType)) {
            qw.eq(QuaInspectItem::getItemType, itemType.trim().toUpperCase(Locale.ROOT));
        }
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            qw.and(w -> w.like(QuaInspectItem::getItemCode, kw)
                    .or().like(QuaInspectItem::getItemName, kw)
                    .or().like(QuaInspectItem::getDefaultStandard, kw));
        }
        qw.orderByAsc(QuaInspectItem::getSortNo).orderByAsc(QuaInspectItem::getInspectItemId);
        return inspectItemMapper.selectList(qw);
    }

    @Override
    public QuaInspectItem getInspectItem(Long id) {
        QuaInspectItem item = inspectItemMapper.selectById(id);
        if (item == null) {
            throw new BusinessException("检验项不存在");
        }
        return item;
    }

    @Override
    @Transactional
    public QuaInspectItem saveInspectItem(QuaInspectItem item) {
        if (item == null || !StringUtils.hasText(item.getItemCode()) || !StringUtils.hasText(item.getItemName())) {
            throw new BusinessException("检验项编码与名称不能为空");
        }
        item.setItemCode(item.getItemCode().trim().toUpperCase(Locale.ROOT));
        item.setItemName(item.getItemName().trim());
        if (!StringUtils.hasText(item.getItemType())) {
            item.setItemType("OTHER");
        } else {
            item.setItemType(item.getItemType().trim().toUpperCase(Locale.ROOT));
        }
        if (!StringUtils.hasText(item.getStatus())) {
            item.setStatus("ENABLED");
        }
        if (item.getCriticalFlag() == null) {
            item.setCriticalFlag(0);
        }
        if (item.getSortNo() == null) {
            item.setSortNo(0);
        }
        item.setUpdatedAt(LocalDateTime.now());

        QuaInspectItem existingByCode = inspectItemMapper.selectOne(new LambdaQueryWrapper<QuaInspectItem>()
                .eq(QuaInspectItem::getItemCode, item.getItemCode())
                .last("LIMIT 1"));
        if (item.getInspectItemId() == null) {
            if (existingByCode != null) {
                throw new BusinessException("检验项编码已存在：" + item.getItemCode());
            }
            item.setCreatedAt(LocalDateTime.now());
            inspectItemMapper.insert(item);
            return item;
        }
        QuaInspectItem db = getInspectItem(item.getInspectItemId());
        if (existingByCode != null && !existingByCode.getInspectItemId().equals(db.getInspectItemId())) {
            throw new BusinessException("检验项编码已存在：" + item.getItemCode());
        }
        inspectItemMapper.updateById(item);
        return getInspectItem(item.getInspectItemId());
    }

    @Override
    @Transactional
    public void disableInspectItem(Long id) {
        QuaInspectItem item = getInspectItem(id);
        item.setStatus("DISABLED");
        item.setUpdatedAt(LocalDateTime.now());
        inspectItemMapper.updateById(item);
    }

    @Override
    public List<QuaInspectStandardVO> listStandards(String keyword, String status, Long mdItemId) {
        LambdaQueryWrapper<QuaInspectStandard> qw = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(status)) {
            qw.eq(QuaInspectStandard::getStatus, status.trim().toUpperCase(Locale.ROOT));
        }
        if (mdItemId != null) {
            qw.eq(QuaInspectStandard::getMdItemId, mdItemId);
        }
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            qw.and(w -> w.like(QuaInspectStandard::getStandardCode, kw)
                    .or().like(QuaInspectStandard::getStandardName, kw)
                    .or().like(QuaInspectStandard::getDrawingNo, kw));
        }
        qw.orderByDesc(QuaInspectStandard::getUpdatedAt).orderByDesc(QuaInspectStandard::getStandardId);
        List<QuaInspectStandard> rows = standardMapper.selectList(qw);
        return rows.stream().map(this::toSummaryVo).collect(Collectors.toList());
    }

    @Override
    public QuaInspectStandardVO getStandardDetail(Long standardId) {
        QuaInspectStandard std = standardMapper.selectById(standardId);
        if (std == null) {
            throw new BusinessException("检验标准不存在");
        }
        return toDetailVo(std);
    }

    @Override
    @Transactional
    public QuaInspectStandardVO saveStandard(QuaInspectStandardSaveRequest request) {
        if (request == null || request.getStandard() == null) {
            throw new BusinessException("标准内容不能为空");
        }
        QuaInspectStandard std = request.getStandard();
        if (!StringUtils.hasText(std.getStandardCode()) || !StringUtils.hasText(std.getStandardName())) {
            throw new BusinessException("标准编码与名称不能为空");
        }
        std.setStandardCode(std.getStandardCode().trim().toUpperCase(Locale.ROOT));
        std.setStandardName(std.getStandardName().trim());
        if (!StringUtils.hasText(std.getVersionNo())) {
            std.setVersionNo("A");
        }
        if (!StringUtils.hasText(std.getAqlLevel())) {
            std.setAqlLevel("II");
        }
        if (std.getAqlValue() == null) {
            std.setAqlValue(new java.math.BigDecimal("1.50"));
        }
        if (!StringUtils.hasText(std.getStatus())) {
            std.setStatus("ENABLED");
        }
        std.setUpdatedAt(LocalDateTime.now());

        QuaInspectStandard codeHit = standardMapper.selectOne(new LambdaQueryWrapper<QuaInspectStandard>()
                .eq(QuaInspectStandard::getStandardCode, std.getStandardCode())
                .last("LIMIT 1"));
        if (std.getStandardId() == null) {
            if (codeHit != null) {
                throw new BusinessException("标准编码已存在：" + std.getStandardCode());
            }
            std.setCreatedAt(LocalDateTime.now());
            standardMapper.insert(std);
        } else {
            QuaInspectStandard db = standardMapper.selectById(std.getStandardId());
            if (db == null) {
                throw new BusinessException("检验标准不存在");
            }
            if (codeHit != null && !codeHit.getStandardId().equals(db.getStandardId())) {
                throw new BusinessException("标准编码已存在：" + std.getStandardCode());
            }
            standardMapper.updateById(std);
            standardLineMapper.delete(new LambdaQueryWrapper<QuaInspectStandardLine>()
                    .eq(QuaInspectStandardLine::getStandardId, std.getStandardId()));
        }

        List<QuaInspectStandardLine> lines = request.getLines() == null ? List.of() : request.getLines();
        int sort = 10;
        for (QuaInspectStandardLine line : lines) {
            if (line.getInspectItemId() == null) {
                continue;
            }
            getInspectItem(line.getInspectItemId());
            line.setLineId(null);
            line.setStandardId(std.getStandardId());
            if (line.getRequiredFlag() == null) {
                line.setRequiredFlag(1);
            }
            if (line.getCriticalFlag() == null) {
                line.setCriticalFlag(0);
            }
            if (line.getSortNo() == null) {
                line.setSortNo(sort);
            }
            sort += 10;
            standardLineMapper.insert(line);
        }
        return getStandardDetail(std.getStandardId());
    }

    @Override
    @Transactional
    public void disableStandard(Long standardId) {
        QuaInspectStandard std = standardMapper.selectById(standardId);
        if (std == null) {
            throw new BusinessException("检验标准不存在");
        }
        std.setStatus("DISABLED");
        std.setUpdatedAt(LocalDateTime.now());
        standardMapper.updateById(std);
    }

    @Override
    public QuaInspectStandardVO resolveStandardForItem(Long mdItemId) {
        if (mdItemId != null) {
            QuaInspectStandard specific = standardMapper.selectOne(new LambdaQueryWrapper<QuaInspectStandard>()
                    .eq(QuaInspectStandard::getMdItemId, mdItemId)
                    .eq(QuaInspectStandard::getStatus, "ENABLED")
                    .orderByDesc(QuaInspectStandard::getUpdatedAt)
                    .last("LIMIT 1"));
            if (specific != null) {
                return toDetailVo(specific);
            }
        }
        QuaInspectStandard general = standardMapper.selectOne(new LambdaQueryWrapper<QuaInspectStandard>()
                .isNull(QuaInspectStandard::getMdItemId)
                .eq(QuaInspectStandard::getStatus, "ENABLED")
                .orderByDesc(QuaInspectStandard::getUpdatedAt)
                .last("LIMIT 1"));
        return general == null ? null : toDetailVo(general);
    }

    @Override
    public List<Map<String, Object>> toExecutionStandards(QuaInspectStandardVO standard) {
        if (standard == null || standard.getLines() == null || standard.getLines().isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (QuaInspectStandardVO.QuaInspectStandardLineVO line : standard.getLines()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("code", line.getItemCode());
            row.put("name", line.getItemName());
            row.put("standard", StringUtils.hasText(line.getStandardText()) ? line.getStandardText() : line.getItemName());
            String type = line.getItemType() == null ? "OTHER" : line.getItemType();
            row.put("type", switch (type) {
                case "APPEARANCE" -> "choice";
                case "DIMENSION" -> "numeric";
                case "PERFORMANCE" -> "performance";
                default -> "other";
            });
            if (line.getUnit() != null) {
                row.put("unit", line.getUnit());
            }
            if (line.getNominal() != null) {
                row.put("nominal", line.getNominal());
            }
            if (line.getLowerTol() != null) {
                row.put("lowerTol", line.getLowerTol());
            }
            if (line.getUpperTol() != null) {
                row.put("upperTol", line.getUpperTol());
            }
            if (line.getMinValue() != null) {
                row.put("minValue", line.getMinValue());
            }
            if (line.getMaxValue() != null) {
                row.put("maxValue", line.getMaxValue());
            }
            row.put("critical", Objects.equals(line.getCriticalFlag(), 1));
            row.put("required", !Objects.equals(line.getRequiredFlag(), 0));
            result.add(row);
        }
        return result;
    }

    @Override
    public List<MdItem> listSelectableItems(String keyword) {
        LambdaQueryWrapper<MdItem> qw = new LambdaQueryWrapper<MdItem>()
                .eq(MdItem::getStatus, "ENABLED")
                .orderByAsc(MdItem::getItemCode)
                .last("LIMIT 200");
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            qw.and(w -> w.like(MdItem::getItemCode, kw).or().like(MdItem::getItemName, kw));
        }
        return mdItemMapper.selectList(qw);
    }

    private QuaInspectStandardVO toSummaryVo(QuaInspectStandard std) {
        QuaInspectStandardVO vo = copyStandard(std);
        fillItemNames(vo);
        Long count = standardLineMapper.selectCount(new LambdaQueryWrapper<QuaInspectStandardLine>()
                .eq(QuaInspectStandardLine::getStandardId, std.getStandardId()));
        vo.setLineCount(count == null ? 0 : count.intValue());
        return vo;
    }

    private QuaInspectStandardVO toDetailVo(QuaInspectStandard std) {
        QuaInspectStandardVO vo = copyStandard(std);
        fillItemNames(vo);
        List<QuaInspectStandardLine> lines = standardLineMapper.selectList(new LambdaQueryWrapper<QuaInspectStandardLine>()
                .eq(QuaInspectStandardLine::getStandardId, std.getStandardId())
                .orderByAsc(QuaInspectStandardLine::getSortNo)
                .orderByAsc(QuaInspectStandardLine::getLineId));
        Map<Long, QuaInspectItem> itemMap = lines.isEmpty() ? Map.of() :
                inspectItemMapper.selectBatchIds(lines.stream()
                                .map(QuaInspectStandardLine::getInspectItemId)
                                .filter(Objects::nonNull)
                                .collect(Collectors.toSet()))
                        .stream()
                        .collect(Collectors.toMap(QuaInspectItem::getInspectItemId, Function.identity(), (a, b) -> a));
        List<QuaInspectStandardVO.QuaInspectStandardLineVO> lineVos = new ArrayList<>();
        for (QuaInspectStandardLine line : lines) {
            QuaInspectStandardVO.QuaInspectStandardLineVO lv = new QuaInspectStandardVO.QuaInspectStandardLineVO();
            lv.setLineId(line.getLineId());
            lv.setStandardId(line.getStandardId());
            lv.setInspectItemId(line.getInspectItemId());
            lv.setRequiredFlag(line.getRequiredFlag());
            lv.setStandardText(line.getStandardText());
            lv.setNominal(line.getNominal());
            lv.setLowerTol(line.getLowerTol());
            lv.setUpperTol(line.getUpperTol());
            lv.setMinValue(line.getMinValue());
            lv.setMaxValue(line.getMaxValue());
            lv.setUnit(line.getUnit());
            lv.setCriticalFlag(line.getCriticalFlag());
            lv.setSortNo(line.getSortNo());
            QuaInspectItem item = itemMap.get(line.getInspectItemId());
            if (item != null) {
                lv.setItemCode(item.getItemCode());
                lv.setItemName(item.getItemName());
                lv.setItemType(item.getItemType());
            }
            lineVos.add(lv);
        }
        lineVos.sort(Comparator.comparing(l -> l.getSortNo() == null ? 0 : l.getSortNo()));
        vo.setLines(lineVos);
        vo.setLineCount(lineVos.size());
        return vo;
    }

    private QuaInspectStandardVO copyStandard(QuaInspectStandard std) {
        QuaInspectStandardVO vo = new QuaInspectStandardVO();
        vo.setStandardId(std.getStandardId());
        vo.setStandardCode(std.getStandardCode());
        vo.setStandardName(std.getStandardName());
        vo.setMdItemId(std.getMdItemId());
        vo.setVersionNo(std.getVersionNo());
        vo.setAqlLevel(std.getAqlLevel());
        vo.setAqlValue(std.getAqlValue());
        vo.setDrawingNo(std.getDrawingNo());
        vo.setStatus(std.getStatus());
        vo.setRemark(std.getRemark());
        vo.setCreatedAt(std.getCreatedAt());
        vo.setUpdatedAt(std.getUpdatedAt());
        return vo;
    }

    private void fillItemNames(QuaInspectStandardVO vo) {
        if (vo.getMdItemId() == null) {
            vo.setMdItemCode(null);
            vo.setMdItemName("通用");
            return;
        }
        MdItem item = mdItemMapper.selectById(vo.getMdItemId());
        if (item != null) {
            vo.setMdItemCode(item.getItemCode());
            vo.setMdItemName(item.getItemName());
        }
    }
}
