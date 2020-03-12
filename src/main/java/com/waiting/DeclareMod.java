package com.waiting;

import cn.hutool.core.util.StrUtil;

/**
 * @author 6550
 * @date 2020/2/24 上午 10:32
 * @description 與變量聲明相關的
 */
public class DeclareMod {

    // 函數聲明
    public static String doFuncDecl(String line) {

        // 函數名聲
        String funcName = "";
        // 返回結果
        String result = "";

        // 駝峰函數名
        // 此種函數聲明類型 event ue_update;
        if (line.trim().startsWith("event")) {
            funcName = StrUtil.toCamelCase(line.replace("event", "").split(";")[0].trim());
            result = StrUtil.format("@Override\npublic TransactionData {}() {\n", funcName);
        }
        // 函數結尾
        else if ("end function".equals(line.trim())) {
            return "}";
        }
        // 此種函數聲明類型 global function integer f_register_customer (string arg_cargo, string arg_register_no, string arg_err_show);
        else {
            funcName = StrUtil.toCamelCase(StrUtil.subBetween(line, "integer", "(").trim());
            // 參數字串
            String paramStr = StrUtil.subBetween(line, "(", ")");
            // 參數關鍵字取代
            paramStr = paramStr.replace("string", "String").replace("str", "String")
                    .replace("long", "BigDecimal").replace("decimal", "BigDecimal");
            result = StrUtil.format("public TransactionData {} ({}) {\n", funcName, paramStr);
        }

        // 常用變量聲明
        result += "String sql;\n";
        result += "Map<String, Object> param;\n";
        result += "Map<String, Object> resultMap;\n";
        result += "List<Map<String, Object>> resultList;\n";

        return result;
    }

    // 變量聲明
    public static String doVariDecl(String line) {

        if (line.trim().startsWith("string")) {
            line = doString(line);
        } else if (line.trim().startsWith("integer")) {
            line = doNum(line, "integer");
        } else if (line.trim().startsWith("int")) {
            line = doNum(line, "int");
        } else if (line.trim().startsWith("long")) {
            line = doNum(line, "long");
        } else if (line.trim().startsWith("decimal")) {
            line = doNum(line, "decimal");
        } else if (line.trim().startsWith("datetime")) {
            line = doDateTime(line);
        }

        return line + ";";
    }

    // 處理datetime類型聲明
    static String doDateTime(String line) {
        return line.replace("datetime", "Timestamp");
    }

    // integer li_i, li_j → igDecimal li_i = BigDecimal.ZERO, li_j = BigDecimal.ZERO;
    static String doNum(String line, String type) {

        // 去除 = 0 聲明
        line = line.replace(" = 0", "");

        line = "BigDecimal " + StrUtil.subAfter(line, type, false).trim();
        String[] split = StrUtil.split(line, ",");
        StrUtil.trim(split);
        String[] strings = StrUtil.wrapAll("", " = BigDecimal.ZERO", split);
        line = StrUtil.join(", ", strings);
        return line;
    }

    // string ls_cargo_location, ls_register_no → String ls_cargo_location = null, ls_register_no = null;
    static String doString(String line) {
        line = "String " + StrUtil.subAfter(line, "string", false).trim();
        String[] split = StrUtil.split(line, ",");
        StrUtil.trim(split);
        String[] strings = StrUtil.wrapAll("", " = null", split);
        line = StrUtil.join(", ", strings);
        return line;
    }
}
