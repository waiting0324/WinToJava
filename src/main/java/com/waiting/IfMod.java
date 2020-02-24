package com.waiting;

import cn.hutool.core.util.ReUtil;
import cn.hutool.core.util.StrUtil;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * @author 6550
 * @date 2020/2/24 上午 10:27
 * @description If 條件判斷相關
 */
public class IfMod {

    // 分離出 if條件判斷式中的 邏輯運算符、左條件式、右條件式
    public static Map splitCondiOperator(String condi) {

        String logicOpeartor = null;
        String condiLeft;
        String condiRight;

        if (condi.contains("==")) {
            logicOpeartor = "==";
        } else if (condi.contains("!=")) {
            logicOpeartor = "!=";
        } else if (condi.contains("<=")) {
            logicOpeartor = "<=";
        } else if (condi.contains(">=")) {
            logicOpeartor = ">=";
        } else if (condi.contains("=")) {
            condi = condi.replace("=", "==");
            logicOpeartor = "==";
        } else if (condi.contains(">")) {
            logicOpeartor = ">";
        } else if (condi.contains("<")) {
            logicOpeartor = "<";
        }

        condiLeft = StrUtil.splitTrim(condi, logicOpeartor).get(0);
        condiRight = StrUtil.splitTrim(condi, logicOpeartor).get(1);

        Map<String, String> resultMap = new HashMap<>();
        resultMap.put("logicOperator", logicOpeartor);
        resultMap.put("condiLeft", condiLeft);
        resultMap.put("condiRight", condiRight);

        return resultMap;
    }

    // 翻譯 if 的條件判斷式
    public static String trasIfCondition(String condi) {

        // ************* 遞迴拆分條件判斷開始 *****************//
        int andIndex = condi.indexOf("&&");
        int orIndex = condi.indexOf("||");

        //  &&、||都存在，且&&在||前面   or  只有 &&
        if ((andIndex != -1 && orIndex != -1 && andIndex < orIndex)
            || (andIndex > 0 && orIndex == -1)) {
            return trasIfCondition(StrUtil.subBefore(condi, "&&", false))
                    + " && " +trasIfCondition(StrUtil.subAfter(condi, "&&", false));
        }
        // && 、||都存在，且||在&&前面   or  只有||
        else if ((andIndex != -1 && orIndex != -1 && orIndex < andIndex)
            || (orIndex > 0 && andIndex == -1)) {
            return trasIfCondition(StrUtil.subBefore(condi, "||", false))
                    + " || " + trasIfCondition(StrUtil.subAfter(condi, "||", false));
        }
        // ************* 遞迴拆分條件判斷結束 *****************//


        condi = condi.trim();

        // 條件判斷是否為空(沒有邏輯運算符) isnull(ls_register_no) 、 dw_criteria.tran_date_s is null
        if (condi.startsWith("isnull") || StrUtil.endWith(condi.trim(), "is null")) {

            String param = "";

            // isnull()格式
            if (StrUtil.isWrap(condi, "isnull(", ")")) {
                param = StrUtil.unWrap(condi, "isnull(", ")");
            }
            // is null 格式
            else {
                param = StrUtil.subBefore(condi, "is", false).trim();
            }

            // 處理參數 dw_criteria.tran_date_s
            if (param.contains(".")) {
                String pojo = param.split("\\.")[0];
                String prop = param.split("\\.")[1];
                param = pojo + "." + StrUtil.genGetter(StrUtil.toCamelCase(prop)) + "()";
            }

            // 轉換結果
            condi = param + " == null";
        }
        // 有邏輯運算符情況 == > <
        else {

            Map<String, String> map = splitCondiOperator(condi);
            String logicOperator = map.get("logicOperator");
            String condiLeft = map.get("condiLeft");
            String condiRight = map.get("condiRight");

            // 字串長度處理 len(trim(ls_close_flag)) 可進行 + - * / 運算
            if (condiLeft.startsWith("len(")) {
                condiLeft = tranIfLenTrimParas(condiLeft);
            }
            // 比較字串是否相等 ls_close_flag != "A"
            else if (condiLeft.startsWith("ls_")){
                if ("=".equals(logicOperator) || "==".equals(logicOperator)) {
                    return StrUtil.format("{}.equals({})", condiRight, condiLeft);
                } else {
                    return StrUtil.format("!{}.equals({})", condiRight, condiLeft);
                }
            }
            // 數字運算
            else if (StrUtil.startWithAny(condiLeft, "ll_", "li_", "ld_")) {

                // 參數
                LinkedList<String> params = new LinkedList<>();
                // 運算符
                LinkedList<String> operaters = new LinkedList<>();

                // 分離出參數跟運算符
                List<String> splits = StrUtil.splitTrim(condiLeft, " ");
                for (String split : splits) {
                    if (StrUtil.containsAny(split.trim(), "+", "-", "*", "/")) {
                        operaters.add(split.trim());
                    } else {
                        params.add(split);
                    }
                }

                condiLeft = "";
                // 是否第一次拼裝
                boolean isFirstTime = true;

                // 拼裝結果
                while (params.size() != 0) {

                    // 取出該List當前的第一個參數
                    condiLeft = condiLeft + params.pop();

                    // 非第一次拼裝則加上下括號
                    if (!isFirstTime) condiLeft += ")";
                    isFirstTime = false;

                    if (operaters.size() != 0) {
                        // 取出該List當前的第一個運算符
                        String operator = operaters.pop();
                        if ("+".equals(operator)) {
                            condiLeft = condiLeft + ".add(";
                        } else if ("-".equals(operator)) {
                            condiLeft = condiLeft + ".substarct(";
                        }
                    }
                }
            }

            // 拼接結果
            condi = StrUtil.format("{} {} {} ", condiLeft, logicOperator, condiRight);
        }



        return condi;
    }

    // 處理if語句
    public static String doIf(String line, BufferedReader reader) throws IOException {

        // 替換關鍵字
        line = line.replace("and", "&&").replace("or", "||")
                .replace("\'", "\"").replace("<>", "!=");

        if (!line.contains("then")) {
            while (!(line += reader.readLine()).contains("then")) ;
        }

        String trimLine = StrUtil.trimToEmpty(line);

        // 條件判斷式
        String condi = StrUtil.subBetween(line, "if", "then").trim();
        // 執行語句
        String func = null;
        if (trimLine.contains("//")) func = StrUtil.subBetween(trimLine, "then","//").trim();
        if (!trimLine.contains("//")) func = StrUtil.subAfter(trimLine, "then",true).trim();
        // 註釋
        String comment = StrUtil.subAfter(trimLine, "//", true);

        // 翻譯條件判斷式
        condi = trasIfCondition(condi);


        // 處理func
        if (func.contains(".")) {
            String pojo = func.split("\\.")[0];
            String prop = func.split("\\.")[1].split(" ")[0];
            String value = StrUtil.trimToEmpty(func.split("=")[1]);
            func = StrUtil.format("{}.{}({})", pojo, StrUtil.genSetter(StrUtil.toCamelCase(prop)), value);
        }
        // 不是空則為簡單參數賦值
        else if (!"".equals(func)) {
            func = AssignMod.doAsignParam(func);
            // 去除分號；
            func = func.substring(0, func.length()-1);
        }


        // 替只有一行的func加上下括號
        if (!"".equals(func)) func = func + "; }";

        if (!"".equals(comment)) {
            line = StrUtil.format("if ({}) { {}  // {}", condi, func, comment);
        } else {
            line = StrUtil.format("if ({}) { {} ", condi, func);
        }
        return line;
    }


    // 翻譯 if語句字串長度參數
    // len(trim(arg_bank_id)) + len(trim(arg_user_id)) - len(trim(arg_user_id))
    // → StringUtils.trimToEmpty(arg_bank_id).length() + StringUtils.trimToEmpty(arg_user_id).length() - StringUtils.trimToEmpty(arg_user_id).length()
    static String tranIfLenTrimParas(String source) {

        LinkedList<String> params = new LinkedList<>();
        LinkedList<String> operaters = new LinkedList<>();

        source = source.trim();

        String[] splits = source.split(" ");
        // 簡單類型，沒有運算符 len(trim(arg_bank_id))
        if (splits.length == 1) {
            String param = StrUtil.unWrap(source, "len(", ")");
            if (StrUtil.isWrap(param, "trim(", ")")) {
                param = StrUtil.unWrap(param, "trim(", ")");

                // 處理pojo屬性獲取情況 user.age
                if (param.contains(".")) {
                    String pojo = param.split("\\.")[0];
                    String prop = param.split("\\.")[1];
                    param = StrUtil.format("{}.{}()", pojo, StrUtil.genGetter(StrUtil.toCamelCase(prop)));
                }

                param = StrUtil.wrap(param, "StringUtils.trimToEmpty(", ")");
            }
            // param: StringUtils.trimToEmpty(arg_bank_id).length()
            param += ".length()";
            params.add(param);
        } else {

            for (String param : splits) {

                // 轉換 len(trim(ls_close_flag)) →  StringUtils.trimToEmpty(ls_close_flag).length() 並存到參數List中
                if (StrUtil.isWrap(param, "len(", ")")) {
                    param = StrUtil.unWrap(param, "len(", ")");
                    if (StrUtil.isWrap(param, "trim(", ")")) {
                        param = StrUtil.unWrap(param, "trim(", ")");
                        param = StrUtil.wrap(param, "StringUtils.trimToEmpty(", ")");
                    }
                    // param: StringUtils.trimToEmpty(arg_bank_id).length()
                    param += ".length()";
                    params.add(param);
                }
                // 將運算符存入List中
                else if (StrUtil.containsAny(param.trim(), "+", "-", "*", "/")) {
                    operaters.add(param.trim());
                }
            }

        }

        String result = "";
        // 拼裝結果
        while (params.size() != 0) {
            // 取出該List當前的第一個參數
            result += params.pop();
            if (operaters.size() != 0) {
                // 取出該List當前的第一個運算符
                result = result + " " + operaters.pop() + " ";
            }
        }
        return result;
    }
}
