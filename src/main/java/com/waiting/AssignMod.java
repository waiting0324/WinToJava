package com.waiting;

import cn.hutool.core.util.StrUtil;

import java.util.List;

/**
 * @author 6550
 * @date 2020/2/24 上午 10:35
 * @description 與參數賦值相關的
 */
public class AssignMod {
    public static String doPojoPropAssign(String line) {

        String trimLine = line.trim();

        if (trimLine.split("\\.")[1] != null && trimLine.split("\\.")[1].startsWith("accepttext")) {
            return "// " + trimLine;
        }

        // 處理Winform函數 dw_master.setitem(row,'ls_pay_by_cash','Y') → dw_master.setPayByCash("Y")
        if (trimLine.contains("setitem")) {
            trimLine = WinFormFunMod.doSetitemToPojoType(trimLine);
        }

        // 實體類
        String pojo = StrUtil.subBefore(trimLine, ".", false).trim();
        // 屬性
        String prop = StrUtil.subBetween(trimLine, ".", " ").trim();
        // 值
        String value = StrUtil.subAfter(trimLine, "=", true).trim().replace("\'", "\"");
        // 處理注釋
        String comment = "";
        if (value.contains("//")) {
            List<String> split = StrUtil.splitTrim(value, "//");
            value = split.get(0);
            comment = split.get(1);
        }


        // 處理值
        if (value.contains(".")) {
            String valPojo = value.split("\\.")[0];
            String valProp = value.split("\\.")[1];
            value = StrUtil.format("{}.{}()", valPojo, StrUtil.genGetter(StrUtil.toCamelCase(valProp)));
        }

        // 不可用else if
        if (StrUtil.isWrap(value, "long(", ")")) {
            value = StrUtil.format("new BigDecimal({})", StrUtil.unWrap(value, "long(", ")"));
        }

        if ("0".equals(value)) {
            value = "BigDecimal.ZERO";
        }


        String setterFun = StrUtil.genSetter(StrUtil.toCamelCase(prop));

        if ("".equals(comment)) {
            return StrUtil.format("{}.{}({});", pojo, setterFun, value);
        } else {
            return StrUtil.format("{}.{}({}); // {}", pojo, setterFun, value, comment);
        }

    }

    // 特殊運算符 += ++ -=
    public static String doSpecialOperator(String line) {

        if (line.contains("++")) {
            String param = StrUtil.subBefore(line, "++", true).trim();
            line = StrUtil.indexedFormat("{0} = {0}.add(BigDecimal.ONE)", param);
        }
        else if (line.contains("--")) {
            String param = StrUtil.subBefore(line, "--", true).trim();
            line = StrUtil.indexedFormat("{0} = {0}.subtract(BigDecimal.ONE)", param);
        }
        else if (line.contains("+=")) {
            String leftParam = line.split("\\+=")[0].trim();
            String func = line.split("\\+=")[1].trim();
            line = StrUtil.indexedFormat("{0} = {0}.add({1})", leftParam, func);
        }
        else if (line.contains("-=")) {
            String leftParam = line.split("-=")[0].trim();
            String func = line.split("-=")[1].trim();
            line = StrUtil.indexedFormat("{0} = {0}.subtract({1})", leftParam, func);
        }

        return line + ";";
    }

    // 參數賦值
    public static String doAsignParam(String line) {

        if (StrUtil.isBlank(line)) return "";
        if (line.contains("return")) return line;


        String leftParam = line.split("=")[0].trim();
        String func = StrUtil.subBefore(line.split("=")[1], "//", true).trim();
        String comment = StrUtil.subAfter(line, "//", true);

        if (func.contains(".getitem")) {
            func = func.replace("\'", "\"");
            String pojo = func.split("\\.")[0];
            String prop = StrUtil.subBetween(func, "\"", "\"").replace("ls_", "").replace("li_", "")
                    .replace("ll_", "").replace("ld_", "").trim();;
            func = StrUtil.format("{}.{}", pojo, prop);
        }

        // 單純賦值為0  ll_non_rcv_cnt = 0
        if ("0".equals(func)) {
            func = "BigDecimal.ZERO";
        }
        // 單純賦值為1  ll_non_rcv_cnt = 1
        else if ("1".equals(func)) {
            func = "BigDecimal.ONE";
        }
        // 單純賦值字串  ls_ar_type = '008'
        else if (StrUtil.isWrap(func, "\'")) {
            func = func.replace("\'", "\"");
        }
        // 簡單加減乘除計算  ll_ar_amt - ll_ar_recv_amt
        else if (StrUtil.count(func, "-") == 1 || StrUtil.count(func, "+") == 1
                || StrUtil.count(func, "*") == 1 || StrUtil.count(func, "/") == 1) {
            String operator = "";
            String[] params = null;
            if (func.contains("-")) {
                operator = "subtract";
                params = func.split("-");
            } else if (func.contains("+")) {
                operator = "add";
                params = func.split("\\+");
            } else if (func.contains("*")) {
                operator = "multiply";
                params = func.split("\\*");
            } else if (func.contains("+")) {
                operator = "divide";
                params = func.split("/");
            }
            StrUtil.trim(params);

            // 處理POJO類 dw_error.cargo_location
            for (int i = 0; i < params.length; i++) {
                String param = params[i];
                if (param.contains(".")) {
                    List<String> split = StrUtil.splitTrim(param, ".");
                    String pojo = split.get(0);
                    String prop = split.get(1);
                    params[i] = pojo + "." + StrUtil.genGetter(StrUtil.toCamelCase(prop)) + "()";
                }
            }

            func = StrUtil.format("{}.{}({})", params[0], operator, params[1]);
        }
        // 取MOD計算  mod(li_i,3) //除以3之餘數
        else if (func.startsWith("mod")) {
            String[] split = StrUtil.unWrap(func, "mod(", ")").split(",");
            func = StrUtil.format("new BigDecimal({}.intValue() % {})", split[0], split[1]);
        }
        // 擷取字串
        else if (StrUtil.startWithAny(func, "substr", "mid")) {
            func = doSubStr(func);
        }
        // pojo 取值計算   ls_cargo = dw_error.cargo_location
        else if (func.contains(".")) {
            String param = "";
            boolean isTrim = false;
            // 有修剪字串 trim(dw_criteria_1.custom_flag)
            if (StrUtil.isWrap(func, "trim(", ")")) {
                param = StrUtil.unWrap(func, "trim(", ")");
                isTrim = true;
            } else {
                param = func;
            }
            List<String> split = StrUtil.splitTrim(param, ".");
            String pojo = split.get(0);
            String prop = split.get(1);
            func = pojo + "." + StrUtil.genGetter(StrUtil.toCamelCase(prop)) + "()";

            if (isTrim)  func = StrUtil.format("StringUtils.trimToEmpty({})", func);
        }
        // getitemstring(row,"ls_mwb_no") Winform函數
        else if (func.startsWith("getitemstring")) {
            func = WinFormFunMod.getitemstring(line, true);
        }




        // 有注釋
        if (!"".equals(comment)) {
            return StrUtil.format("{} = {}; //{}", leftParam, func, comment);
        }
        // 沒有注釋
        else {
            return StrUtil.format("{} = {};", leftParam, func);
        }

    }

    // 擷取字串 substr(ls_valid_account,li_i,1)
    static String doSubStr(String func) {
        func = func.replace("mid", "substr");
        String[] split = StrUtil.unWrap(func, "substr(", ")").split(",");
        // ls_id = substr(ls_valid_account,li_i,1)
        /*if (split[1].contains("_")) {
            func = StrUtil.format("{}.subString({}.intValue()-1, {}.intValue() + {}))", split[0], split[1], split[1], Integer.parseInt(split[2])-2);
        } else {*/
            if (split.length > 2) {
                func = StrUtil.format("{}.substring({}, {}); //TODO 更改位置", split[0], split[1], split[2]);
            } else {
                func = StrUtil.format("{}.substring(0, {}); //TODO 更改位置", split[0], split[1]);
            }
        //}
        return func;
    }
}
