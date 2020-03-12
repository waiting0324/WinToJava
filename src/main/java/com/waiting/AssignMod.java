package com.waiting;

import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.StrUtil;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * @author 6550
 * @date 2020/2/24 上午 10:35
 * @description 與參數賦值相關的
 */
public class AssignMod {
    public static String doPojoPropAssign(String line) {

        String trimLine = line.trim();

        // Winform的accepttext()函數
        if (trimLine.split("\\.")[1] != null && trimLine.split("\\.")[1].startsWith("accepttext")) {
            return "// " + trimLine;
        }
        // Winform的triggerevent()函數
        else if (trimLine.contains("triggerevent")) {
            return WinFormFunMod.doTriggerevent(trimLine);
        }
        // Winform的reset()函數
        else if (trimLine.contains("reset")) {
            return WinFormFunMod.doReset(trimLine);
        }
        // Winform的protect屬性，用於設為唯獨
        else if (trimLine.contains(".protect")) {
            return WinFormFunMod.doProtect(trimLine);
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


        // 處理值，沒有被各種函數處理
        if (value.contains(".") && !StrUtil.containsAny(value, "StringUtils.mid", ".add", ".subtract", ".multiply", ".divide")) {
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

        String comment = null;

        if (line.contains("//")) {
            comment = line.split("//")[1].trim();
            line = line.split("//")[0];
        }

        // 字串格式則不處理
        if (line.trim().startsWith("ls_")) {
            return line;
        }
        else if (line.contains("++")) {
            String param = StrUtil.subBefore(line, "++", true).trim();
            if (comment == null) {
                line = StrUtil.indexedFormat("{0} = {0}.add(BigDecimal.ONE);", param);
            } else {
                line = StrUtil.indexedFormat("{0} = {0}.add(BigDecimal.ONE); // {1}", param, comment);
            }
        }
        else if (line.contains("--")) {
            String param = StrUtil.subBefore(line, "--", true).trim();
            if (comment == null) {
                line = StrUtil.indexedFormat("{0} = {0}.subtract(BigDecimal.ONE);", param);
            } else {
                line = StrUtil.indexedFormat("{0} = {0}.subtract(BigDecimal.ONE); // {1}", param, comment);
            }
        }
        else if (line.contains("+=")) {
            String leftParam = line.split("\\+=")[0].trim();
            String func = line.split("\\+=")[1].trim();
            if (comment == null) {
                line = StrUtil.indexedFormat("{0} = {0}.add({1});", leftParam, func);
            } else {
                line = StrUtil.indexedFormat("{0} = {0}.add({1}); // {2}", leftParam, func, comment);
            }
        }
        else if (line.contains("-=")) {
            String leftParam = line.split("-=")[0].trim();
            String func = line.split("-=")[1].trim();
            if (comment == null) {
                line = StrUtil.indexedFormat("{0} = {0}.subtract({1});", leftParam, func);
            } else {
                line = StrUtil.indexedFormat("{0} = {0}.subtract({1}); // {2}", leftParam, func, comment);
            }
        }

        return line;
    }

    // 參數賦值
    public static String doAsignParam(String line) {

        if (StrUtil.isBlank(line)) return "";
        if (line.contains("return")) return line;

        // 基本屬性分離
        String leftParam = line.split("=")[0].trim();
        String func = StrUtil.subBefore(line.split("=")[1], "//", true).trim();
        String comment = StrUtil.subAfter(line, "//", true);

        // Winform getitem函數處理
        if (func.contains(".getitem")) {
            func = func.replace("\'", "\"");
            String pojo = func.split("\\.")[0];
            String prop = StrUtil.subBetween(func, "\"", "\"").replace("ls_", "").replace("li_", "")
                    .replace("ll_", "").replace("ld_", "").trim();;
            func = StrUtil.format("{}.{}", pojo, prop);
        }

        // 是否為四捨五入
        boolean isRounding = false;
        if (StrUtil.isWrap(func, "round(", ",0)")) {
            func = StrUtil.unWrap(func, "round(", ",0)");
            isRounding = true;
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
        // 加減乘除計算  ll_ar_amt - ll_ar_recv_amt
        else if (StrUtil.containsAny(func, "+", "-", "*", "/")) {
            func = doCalc(func);
        }
        // 取MOD計算  mod(li_i,3) //除以3之餘數
        else if (func.startsWith("mod")) {
            String[] split = StrUtil.unWrap(func, "mod(", ")").split(",");
            func = StrUtil.format("new BigDecimal({}.intValue() % {})", split[0], split[1]);
        }
        // 擷取字串
        else if (StrUtil.startWithAny(func, "substr")) {
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
            func = WinFormFunMod.doGetitemstring(line, true);
        }

        // 要求四捨五入
        if (isRounding) {
            func += ".setScale(0, RoundingMode.HALF_UP)";
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

    // 處理加、減、乘、除計算
    public static String doCalc(String func) {

        // 第一個碰到的運算符
        String operator = StrUtil.getContainsStr(func, "+", "-", "*", "/");

        if (operator != null) {

            // 是否有被括號包住
            boolean isWrap = false;
            if (StrUtil.isWrap(func, "(", ")")) {
                func = StrUtil.unWrap(func, "(", ")");
                isWrap = true;
            }

            // 第一個運算符 前、後 的運算式
            String funcBefore = StrUtil.subBefore(func, operator, false).trim();
            String funcAfter = StrUtil.subAfter(func, operator, false).trim();

            // 遞迴封裝運算式
            if ("*".equals(operator)) {
                if (isWrap) {
                    func = StrUtil.format("({}.multiply({}))", doCalc(funcBefore), doCalc(funcAfter));
                } else {
                    func = StrUtil.format("{}.multiply({})", doCalc(funcBefore), doCalc(funcAfter));
                }
            } else if ("+".equals(operator)) {
                if (isWrap) {
                    func = StrUtil.format("({}.add({}))", doCalc(funcBefore), doCalc(funcAfter));
                } else {
                    func = StrUtil.format("{}.add({})", doCalc(funcBefore), doCalc(funcAfter));
                }
            } else if ("-".equals(operator)) {
                if (isWrap) {
                    func = StrUtil.format("({}.subtract({}))", doCalc(funcBefore), doCalc(funcAfter));
                } else {
                    func = StrUtil.format("{}.subtract({})", doCalc(funcBefore), doCalc(funcAfter));
                }
            } else if ("/".equals(operator)) {
                if (isWrap) {
                    func = StrUtil.format("({}.divide({}))", doCalc(funcBefore), doCalc(funcAfter));
                } else {
                    func = StrUtil.format("{}.divide({})", doCalc(funcBefore), doCalc(funcAfter));
                }
            }

        } else {
            // 如果是純數字，則用大數字進行封裝
            if (NumberUtil.isNumber(func)) {
                func = StrUtil.format("new BigDecimal({})", func);
            }
        }

        return func;
    }

    // 擷取字串 substr(ls_valid_account,li_i,1)
    public static String doSubStr(String func) {
//        func = func.replace("mid", "substr");
        String[] split = StrUtil.unWrap(func, "substr(", ")").split(",");

        if (split.length > 2) {
            func = StrUtil.format("{}.substring({}, {}); //TODO 更改位置", split[0], split[1], split[2]);
        } else {
            func = StrUtil.format("{}.substring(0, {}); //TODO 更改位置", split[0], split[1]);
        }

        return func;
    }

    // mid(nt_dollar,1,2)
    public static String doMid(String line) {
        String param = StrUtil.subBetween(line, "mid(", ",").trim();
        String num1 = StrUtil.subBetween(line, ",", ",").trim();
        String num2 = StrUtil.sub(line, line.lastIndexOf(",")+1, -1);

        return  StrUtil.format("StringUtils.mid({}, {}, {})", param, Integer.parseInt(num1)-1, num2);
    }
}
