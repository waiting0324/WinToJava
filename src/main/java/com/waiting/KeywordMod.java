package com.waiting;

import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.StrUtil;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author 6550
 * @date 2020/2/24 上午 10:36
 * @description 特殊關鍵字相關的
 */
public class KeywordMod {

    public static String doMessagebox(String line, BufferedReader reader) throws IOException {

        // 訊息狀態，訊息或錯誤
        String resultType = "false";

        // 訊息中的變量
        List<String> params = new ArrayList<>();
        StringBuilder result = new StringBuilder();

        // 取得訊息行
        while (!line.contains(")")) {
            line += reader.readLine();
        }


        line = line.replace("\'", "").trim();

        // '訊息','客服組已輸入【客戶:'+ls_register_no+'】使用額度沖銷資料，系統不再自動寫入'
        String paramStr = StrUtil.unWrap(line, "messagebox(", ")");
        // 判斷訊息狀態
        //if (paramStr.split(",")[0].contains("訊息")) resultType = "true";

        // 取得訊息中的變量
        String msg = "";
        if (paramStr.contains("\',\'")) {
            // 此種類型 '訊息','客服...'
            msg = paramStr.split(",")[1];
        } else {
            msg = paramStr;
        }
        String[] split = msg.split("\\+");
        for (String s : split) {
            if (s.contains("_")) params.add(s);
        }

        // 拼裝結果
        result.append("// TODO " + msg + "\n");
        if (params.size() > 0) {
            // Object[] args = new Object[] {keep_gci_date, keep_pcs};
            result.append("Object[] args = new Object[] {" + StrUtil.unWrap(params.toString(), "[", "]") + "};\n");
            result.append("return new TransactionData(" + resultType + ", \"\", FeeResultEnum.FE00_E0001, null, args);");
        } else {
            result.append("return new TransactionData(" + resultType + ", \"\", FeeResultEnum.FE00_E0001, null, null);");
        }

        return result.toString();
    }

    // 關鍵字
    public static String doKeyword(String line) {

        String trimLine = line.trim();

        if (trimLine.startsWith("next")) {
            return line.replace("next", "}");
        } else if (trimLine.startsWith("end if")) {
            return line.replace("end if", "}");
        } else if (trimLine.startsWith("loop")) {
            return line.replace("loop", "}");
        } else if (trimLine.startsWith("commit")) {
            return "";
        } else if (trimLine.startsWith("open")) {
            return "// " + line;
        } else if (trimLine.startsWith("close")) {
            return "// " + line;
        } else if (trimLine.startsWith("continue")) {
            return line + ";";
        } else if (trimLine.toLowerCase().startsWith("else")) {
            return  StrUtil.replaceIgnoreCase(line, "else", "} else {");
//            return line.replace("else", " else ");
        } else if (trimLine.startsWith("rollback")) {
            return "TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();";
        }

        return "";
    }

    public static String doReturn(String line) {

        // return 無返回值則標成注釋後返回
        if ("return".equals(line.toLowerCase().trim())) return "// " + line;

        StringBuilder result = new StringBuilder();
        line = line.trim().split(" ")[1].trim();

        // 失敗
        if ("\"Fail\"".equalsIgnoreCase(line)) {
            result.append("\n// 失敗 \n");
            result.append("return new TransactionData(false, \"\", FeeResultEnum.FE00_E0001, null, null);");
        }
        // 封裝字串 return new TransactionData(true, "", ResultEnum.SUCCESS, new String(li_value4), null);
        else if (StrUtil.startWithIgnoreCase(line, "string")) {
            String param = StrUtil.unWrap(line, "string(", ")");
            result.append("return new TransactionData(true, \"\", ResultEnum.SUCCESS, new String(" + param + "), null);");
        }
        // 是數字
        else if (NumberUtil.isNumber(line)) {
            // 返回1則不處理
            //if (line.equals("1")) return "";
            result.append("return new TransactionData(true, \"\", ResultEnum.SUCCESS, new BigDecimal(" + line + "), null);");
        }
        // 返回JSON則標注待處理
        else if (StrUtil.startWithIgnoreCase(line, "json")) {
            result.append("// TODO 處理返回值\n");
            result.append("new TransactionData(true, \"\", ResultEnum.SUCCESS, 返回值, null);");
        }
        else {
            result.append("// " + line);
        }


        // 非單行If的情況則加上大括號
        //if (isIf == false) result.append("}\n");

        return result.toString();
    }


}
