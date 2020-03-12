package com.waiting;

import cn.hutool.core.util.StrUtil;

/**
 * @author 6550
 * @date 2020/2/25 下午 05:13
 * @description
 */
public class SwitchMod {

    public static String doSwitch(String line) {

        String trimLine = line.trim();

        if (trimLine.startsWith("choose")) {
            String param = StrUtil.splitTrim(trimLine, "case").get(1);
            line = StrUtil.format("switch ({}) { \n // TODO break 修正", param);
        } else if (trimLine.startsWith("case")) {
            String param = trimLine.split(" ")[1];
            param = param.replace("\'", "\"");
            line = StrUtil.format("break;\ncase {}:", param);
        } else if (trimLine.startsWith("end")) {
            line = "}";
        }

        return line;
    }
}
