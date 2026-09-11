package com.upc.wms.common;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 单据号 / 流水号生成器：前缀 + 时间戳 + 随机数，保证可读且基本唯一。
 */
public final class NoGenerator {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.BASIC_ISO_DATE;

    private NoGenerator() {
    }

    public static String next(String prefix) {
        return prefix + LocalDateTime.now().format(FMT) + ThreadLocalRandom.current().nextInt(100, 999);
    }

    /** 按日递增序号，如 PP + yyyyMMdd + 0006 */
    public static String formatDailySeq(String prefix, int sequence, int seqDigits) {
        return prefix + LocalDate.now().format(DATE_FMT) + String.format("%0" + seqDigits + "d", sequence);
    }
}
