package com.waiting;

import cn.hutool.core.util.StrUtil;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.Map;

/**
 * @author 6550
 * @date 2020/3/4 下午 03:07
 * @description WinForm內置方法處理區
 */
public class WinFormFunMod {

    // getitemstring(row,"ls_mwb_no") → dw_master.getMwbNo();
    // isAssignPatten: 是否為賦值情況
    public static String doGetitemstring(String line, boolean isAssignPatten) {
        line = line.replace("\"", "\'");
        String prop = StrUtil.subAfter(StrUtil.subBetween(line, "\'", "\'"), "_", false);
        if (isAssignPatten) {
            prop = StrUtil.toCamelCase(prop);
            return StrUtil.format("dw_master.{}()", StrUtil.genGetter(prop));
        } else {
            return StrUtil.format("dw_master.{}", prop);
        }
    }

    // 處理Winform setitem函數 變成 屬性賦值格式
    // dw_master.setitem(row,'ls_pay_by_cash','Y') → dw_master.pay_by_cash = "Y"
    // dw_invoice.setitem(1,'arg_desc'+string(i), trim(ls_work_name) + trim(ls_charge_desc))
    public static String doSetitemToPojoType(String trimLine) {

        String comment = null;
        // 有註釋
        if (trimLine.contains("//")) {
            comment = StrUtil.subAfter(trimLine, "//", true);
            trimLine = StrUtil.subBefore(trimLine, "//", true).trim();
        }

        String pojo = StrUtil.subBefore(trimLine, ".", false).trim();
        String prop = StrUtil.subBetween(trimLine.split(",")[1], "\'", "\'")
                .replace("ls_", "").replace("li_", "")
                .replace("ll_", "").replace("ld_", "").trim();
        String value = StrUtil.sub(trimLine.replace("\'", "\""), trimLine.lastIndexOf(",") + 1, -1);

        // 如果value值要被mid函數處理
        if (StrUtil.isWrap(value, "mid(", ")")) {
            value = AssignMod.doMid(value);
        }
        // 如果value值需要進行運算
        if (StrUtil.containsAny(value, "+", "-", "*", "/")) {
            value = AssignMod.doCalc(value);
        }

        if (comment == null) {
            trimLine = StrUtil.format("{}.{} = {}", pojo, prop, value);
        } else {
            trimLine = StrUtil.format("{}.{} = {} // {}", pojo, prop, value, comment);
        }

        return trimLine;
    }

    // 處理Winform getitem函數 變成 屬性格式
    // dw_detail.getitemstring(i,'overdue_flag') → dw_detail.overdue_flag
    public static String doGetitemToPojoType(String line) {
        String pojo = StrUtil.subBefore(line, ".", false).trim();
        String prop = StrUtil.subBetween(line.split(",")[1], "\'", "\'")
                .replace("ls_", "").replace("li_", "")
                .replace("ll_", "").replace("ld_", "").trim();

        line = StrUtil.format("{}.{}", pojo, prop);
        return line;
    }

    //  setitem(row,'ls_cust_attr',ls_cust_attribute) → dw_master.setCustAttr(ls_cust_attribute)
    public static String doSetitem(String line) {
        String trimLine = line.trim();
        String prop = StrUtil.subBetween(trimLine.split(",")[1], "\'", "\'")
                .replace("ls_", "").replace("li_", "")
                .replace("ll_", "").replace("ld_", "").trim();

        prop = StrUtil.toCamelCase(prop);
        String value = StrUtil.subAfter(trimLine, ",", true)
                .replace(")", "").replace("\'", "\"");

        if ("0".equals(value)) value = "BigDecimal.ZERO";
        if ("1".equals(value)) value = "BigDecimal.ONE";

        return StrUtil.format("dw_master.{}({});", StrUtil.genSetter(prop), value);
    }

    // Winform的triggerevent()函數 dw_detail.triggerevent("ue_update")
    public static String doTriggerevent(String line) {

        String funcName = StrUtil.subBetween(line, "(", ")");
        funcName = StrUtil.unWrap(funcName, "\'", "\'");
        funcName = StrUtil.unWrap(funcName, "\"", "\"");

        return StrUtil.format("{}();", StrUtil.toCamelCase(funcName));
    }

    // Winform的reset()函數 dw_master.reset()
    public static String doReset(String line) {
        String pojo = StrUtil.subBefore(line, ".", false).trim();
        return StrUtil.format("{} = new {}();", pojo, pojo);
    }

    // Winform的protect屬性，用於設為唯獨
    public static String doProtect(String line) {
        return "// " + line;
    }

    // Winform 函數結束關鍵字
    public static String doEndEvent(String line) {
        return "}";
    }

    // Winform 聲明 SQL   Declare charge_sp_cur cursor for
    public static String doDeclare(String line, BufferedReader reader, Map<String, String> declareSql) throws IOException {

        // SQL 名稱
        String sqlName = StrUtil.subBetween(line.toLowerCase(), "declare", "cursor").trim();

        // SQL 語句
        StringBuilder oriSql = new StringBuilder();
        while (!StrUtil.contains(line = reader.readLine(), ";")) {
            oriSql.append(line + "\n");
        }
        // 將聲明的SQL存到declareSql中，key為 charge_sp_cur，value為sql語句
        declareSql.put(sqlName, oriSql.toString());

        return "";
    }

    // Winform 開始使用之前聲明的SQL語句
    public static String doOpen(String line, BufferedReader reader, Map<String, String> declareSql) throws IOException {

        if (StrUtil.startWithIgnoreCase(reader.readLine().trim(), "DO WHILE")) {

            StringBuilder result = new StringBuilder();

            // 獲取要求的SQL名稱
            String sqlName = StrUtil.subAfter(reader.readLine().toLowerCase(), "fetch", false).trim();
            // 取得之前暫存的SQL語句
            String oriSql = declareSql.get(sqlName);

            // 取得 into部分的SQL語句
            StringBuilder intoSql = new StringBuilder();
            while (true) {
                line = reader.readLine();
                if (line.contains(";")) {
                    if (!line.replace(";", "").isBlank()) intoSql.append(line.replace(";", "") + "\n");
                    break;
                }
                intoSql.append(line + "\n");
            }

            // 將into部分的SQL插入到from關鍵字前面
            oriSql = StrUtil.format("{} {} from {}", StrUtil.subBefore(oriSql, "from", true), intoSql, StrUtil.subAfter(oriSql, "from", true));
            // 取得格式化之後的SQL
            oriSql = SqlMod.getOriSqlByString(oriSql);

            result.append("// " + sqlName + " SQL聲明 獲取開始 \n");
            result.append(SqlMod.doSelect(oriSql, true));
            result.append("\n// " + sqlName + " SQL聲明 獲取結束 \n");

            return result.toString();
        }


        return ">>>>>>>>> FETCH SQL 時發生錯誤，請檢查 <<<<<<<<<<";
    }
}
