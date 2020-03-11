package com.waiting;

import cn.hutool.core.util.StrUtil;

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
    public static String doSetitemToPojoType(String trimLine) {
        String pojo = StrUtil.subBefore(trimLine, ".", false).trim();
        String prop = StrUtil.subBetween(trimLine.split(",")[1], "\'", "\'")
                .replace("ls_", "").replace("li_", "")
                .replace("ll_", "").replace("ld_", "").trim();
        String value = StrUtil.subAfter(trimLine, ",", true)
                .replace(")", "").replace("\'", "\"");
        trimLine = StrUtil.format("{}.{} = {}", pojo, prop, value);
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

    public static String doEndEvent(String line) {
        return "}";
    }
}
