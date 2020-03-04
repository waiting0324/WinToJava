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
    public static String getitemstring(String line, boolean isAssignPatten) {
        String prop = StrUtil.subAfter(StrUtil.subBetween(line, "\"", "\""), "_", false);
        if (isAssignPatten) {
            prop = StrUtil.toCamelCase(prop);
            return StrUtil.format("dw_master.{}()", StrUtil.genGetter(prop));
        } else {
            return StrUtil.format("dw_master.{}", prop);
        }
    }
}
