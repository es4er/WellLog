package com.upc.wms.common;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 生产计划号：PP + yyyyMMdd + 5 位当日递增序号，如 PP2026070800010。
 */
public final class PlanNoFormatter {

    public static final int SEQ_DIGITS = 5;

    private static final Pattern SHORT_PATTERN = Pattern.compile("^PP(\\d{8})(\\d{3,5})$");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.BASIC_ISO_DATE;

    private PlanNoFormatter() {
    }

    public static boolean isShortFormat(String planNo) {
        return planNo != null && SHORT_PATTERN.matcher(planNo).matches();
    }

    public static int parseSequence(String planNo) {
        if (!isShortFormat(planNo)) {
            return -1;
        }
        Matcher matcher = SHORT_PATTERN.matcher(planNo);
        matcher.matches();
        return Integer.parseInt(matcher.group(2));
    }

    /** 展示用：已是短号则规范为 5 位序号，长号则按 planId 映射为短号。 */
    public static String display(String planNo, Long planId) {
        if (isShortFormat(planNo)) {
            Matcher matcher = SHORT_PATTERN.matcher(planNo);
            matcher.matches();
            int seq = Integer.parseInt(matcher.group(2));
            return NoGenerator.formatDailySeq("PP", seq, SEQ_DIGITS);
        }
        String datePart = extractDatePart(planNo);
        int seq = planId != null && planId > 0 ? planId.intValue() : 1;
        return NoGenerator.formatDailySeq("PP", seq, SEQ_DIGITS);
    }

    /** 生成下一个当日序号（仅统计短号，忽略历史长号）。 */
    public static String next(Collection<String> existingPlanNos) {
        String prefix = "PP" + LocalDate.now().format(DATE_FMT);
        int maxSeq = 0;
        if (existingPlanNos != null) {
            for (String planNo : existingPlanNos) {
                if (planNo != null && planNo.startsWith(prefix) && isShortFormat(planNo)) {
                    maxSeq = Math.max(maxSeq, parseSequence(planNo));
                }
            }
        }
        return NoGenerator.formatDailySeq("PP", maxSeq + 1, SEQ_DIGITS);
    }

    private static String extractDatePart(String planNo) {
        if (planNo != null && planNo.length() >= 10 && planNo.startsWith("PP")) {
            return planNo.substring(2, 10);
        }
        return LocalDate.now().format(DATE_FMT);
    }
}
