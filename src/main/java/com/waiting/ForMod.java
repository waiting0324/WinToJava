package com.waiting;

import cn.hutool.core.util.StrUtil;

/**
 * Create By Waiting on 2020/3/10
 */
public class ForMod {

    // for li_i = 1 to dw_detail.rowcount()
    public static String doFor(String line) {
        // 要求從1開始遍歷List時  for li_i = 1 to dw_detail.rowcount()
        if ("1".equals(StrUtil.trim(StrUtil.subBetween(line, "=", "to")))
                && StrUtil.subAfter(line, "to", true).contains("rowcount")) {
            String listName = StrUtil.subBetween(line, "to", ".").trim();
            return StrUtil.format("for ({} : {}) {", listName, listName + "s");
        }
        return line;
    }
}
