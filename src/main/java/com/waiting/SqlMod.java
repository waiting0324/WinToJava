package com.waiting;

import cn.hutool.core.util.StrUtil;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * @author 6550
 * @date 2020/2/24 上午 10:06
 * @description 與SQL語句相關的的方法
 */
public class SqlMod {


    // 取得格式化過後的SQL語句
    public static String getOriSql(String line, BufferedReader reader) throws IOException {

        // SQL 語句
        StringBuilder oriSql = new StringBuilder();

        // 首句尾巴加上 " +
        oriSql.append(line.replace("\"", "").trim() + " \" \u002B \n");

        while (!StrUtil.contains(line = reader.readLine(), ";")) {
            line = line.replace("\"", "");
            line = line.trim();

            // 空格格式化
            if (StrUtil.startWithIgnoreCase(line, "FROM")) {
                line = "   " + line;
            } else if (StrUtil.startWithIgnoreCase(line, "WHERE")) {
                line = " " + line;
            } else if (StrUtil.startWithIgnoreCase(line, "AND")) {
                line = "   " + line;
            } else if (StrUtil.startWithIgnoreCase(line, "ORDER")) {
                line = " " + line;
            } else {
                line = " " + line;
            }

            // 尾部增加+號
            oriSql.append("\" " + line + "  \" \u002B \n");
        }

        // 處理格式異常的SQL: ;不是獨立一行
        if (!";".equals(line.trim())) {
            line = line.trim();
            line = "   " + line;
            // 尾部增加+號
            oriSql.append("\" " + line + "  \" \u002B \n");
            oriSql.append(StrUtil.subBefore(line, ";", true));
        }

        return "\" " + oriSql.toString();
    }

    // 增加查詢欄位別名
    public static String addSelectColumnAlias(String oriSql, List<String> selectColumns, boolean isIntoTypeSql) {

        // 拷貝List， 因為等等List中的資料會移除
        List<String> columnAliazz = new ArrayList<>(selectColumns);

        String[] sqlLines = oriSql.split("\n");
        StringBuilder result = new StringBuilder();
        boolean isFromAppear = false;
        boolean isIntoAppear = false;

        for (int i = 0; i < sqlLines.length; i++) {

            String sqlLine = sqlLines[i];

            if (StrUtil.containsIgnoreCase(sqlLine, "INTO")) {
                isIntoAppear = true;
            }
            if (StrUtil.containsIgnoreCase(sqlLine, "FROM")) {
                isFromAppear = true;
            }

            if (isIntoAppear && sqlLine.contains(":") && !isFromAppear) {
                continue;
            }

            // FROM 關鍵字出現之前，替查詢欄位加上別名
            if (!isFromAppear) {

                // 帶有 into 關鍵字的SQL查詢語句
                if (isIntoTypeSql) {

                    // 查詢欄位列表
                    List<String> columns;
                    // 除去干擾字符
                    sqlLine = sqlLine.replace("\"", "").replace("+", "").trim();
                    // 轉譯 nvl(AR_REC_AMT,0) 之情況，避免影響查詢欄位切割
                    sqlLine = sqlLine.replace(",0)", "|0)").replace(",1)", "|1)")
                            .replace(",2)", "|2)").replace(",3)", "|3)");


                    // 取出查詢欄位
                    // 查詢的第一行，帶有select關鍵字
                    if (StrUtil.startWithIgnoreCase(sqlLine.replace("\"", "").trim(), "SELECT")) {
                        columns = StrUtil.splitTrim(StrUtil.replaceIgnoreCase(sqlLine, "SELECT", "")
                                .replace("\"", "").replace("+", ""), ",");
                        sqlLine = "\" select ";

                    } else {
                        columns = StrUtil.splitTrim(sqlLine.replace("\"", "")
                                .replace("+", ""), ",");
                        sqlLine = "\" ";
                    }

                    // 拼接查詢欄位與別名
                    for (int j = 0; j < columns.size(); j++) {
                        sqlLine = sqlLine + columns.get(j) + " " + columnAliazz.remove(0).toUpperCase() + ", ";
                    }


                    // 如果是最後的查詢欄位行，則去尾部逗號 增加 " + 作為結尾
                    if (StrUtil.startWithIgnoreCase(sqlLines[i+1].replace("\"","")
                            .replace("+", "").trim(), "INTO")) {
                        sqlLine = StrUtil.sub(sqlLine, 0, -2) + " \" \u002B ";
                    }
                    // 否則則只加  " + 作為結尾
                    else {
                        sqlLine = sqlLine + " \" \u002B ";
                    }

                    // 將前置轉譯恢復
                    sqlLine = sqlLine.replace("|0)", ",0)").replace("|1)", ",1)")
                            .replace("|2)", ",2)").replace("|3)", ",3)");
                }
                // 此種SQL類型 SELECT CUST_VIRTUAL_ACCOUNT.CARGO_LOCATION
                else {
                    // 非最後一個查詢欄位
                    if (sqlLine.contains(",")) {
                        sqlLine = StrUtil.subBefore(sqlLine, ",", true) + " " + columnAliazz.get(i).toUpperCase()
                                + "," + StrUtil.subAfter(sqlLine, ",", true);
                    }
                    // 最後一個查詢欄位
                    else {
                        sqlLine = StrUtil.subBefore(sqlLine, "\"", true) + columnAliazz.get(i).toUpperCase()
                                + "  \"" + StrUtil.subAfter(sqlLine, "\"", true);
                    }
                }

            }
            result.append(sqlLine + "\n");
        }

        return result.toString();
    }

    // 取得SQL查詢欄位
    public static List<String> getSqlSelectColumns(String oriSql, boolean isIntoTypeSql) {

        // 請求查詢的欄位
        List<String> selecColumns = new ArrayList<>();

        // 行SQL的列表
        List<String> sqlLines = StrUtil.splitTrim(oriSql, "\n");


        for (String line : sqlLines) {
            // 讀到 FROM 則跳出
            if (StrUtil.containsIgnoreCase(line, "FROM")) {
                break;
            }

            // 此種SQL類型 select custom_id into :ls_custom_id
            if (isIntoTypeSql) {
                if (line.contains(":")) {
                    // 存入參數寫在一行  into :ll_b_vol1, :ll_b_vol2, :ll_b_vol3
                    if (StrUtil.count((CharSequence) line, ":") > 1) {
                        // 去除雜訊
                        line = line.replaceAll("\"", "").replaceAll("\\+", "");
                        // 取出參數
                        List<String> split = StrUtil.splitTrim(line, ",");
                        for (String s : split) {
                            selecColumns.add(StrUtil.subAfter(s, ":", false));
                        }
                    }
                    // 存入參數存在不同行
                    else {
                        String selectColumn = StrUtil.subBetween(line, ":", " ");
                        if (selectColumn.contains(",")) {
                            selectColumn = selectColumn.replace(",", "");
                        }
                        selecColumns.add(selectColumn);
                    }
                }
            }
            // 此種SQL類型 SELECT CUST_VIRTUAL_ACCOUNT.CARGO_LOCATION
            else {
                String selectColumn = StrUtil.subBetween(line, ".", " ");
                // select register_no
                //      from registered_customer
                if (selectColumn == null) {
                    selectColumn = StrUtil.subAfter(line, "select", false);
                }
                if (selectColumn.contains(",")) {
                    selectColumn = selectColumn.replace(",", "");
                }
                selecColumns.add(selectColumn);
            }
        }

        return selecColumns;
    }

    // 取得SQL查詢表
    public static String[] getSqlTables(String oriSql) {
        String line = StrUtil.subBetween(oriSql.toUpperCase(), "FROM", "\"");
        String[] tables = line.split(",");
        StrUtil.trim(tables);
        return tables;
    }

    // 取得SQL :param 要替換的變量
    public static Set<String> getSqlParams(String oriSql) {

        // 參數集合
        Set<String> params = new LinkedHashSet();

        boolean isFromAppear = false;

        // 行SQL的列表
        List<String> sqlLines = StrUtil.splitTrim(oriSql, "\n");

        // 是否是查詢類型
        boolean isSelectSql = StrUtil.containsIgnoreCase(sqlLines.get(0), "select");

        // substr(:ls_virtual_account,1,4)
        // = :ls_virtual_account
        for (String line : sqlLines) {

            // 當 FROM 關鍵字出現後才開始查詢
            if (isSelectSql) {
                if (StrUtil.containsIgnoreCase(line, "from")) {
                    isFromAppear = true;
                }
                if (!isFromAppear) continue;
            }

            // 取出參數
            while (true) {
                String param = StrUtil.subBetween(line, ":", ",");
                if (param == null) param = StrUtil.subBetween(line, ":", "|");
                if (param == null) param = StrUtil.subBetween(line, ":", ")");
                if (param == null) param = StrUtil.subBetween(line, ":", "\n");
                if (param == null) param = StrUtil.subBetween(line, ":", " ");
                if (param != null) {
                    params.add(param.trim());
                    line = StrUtil.subAfter(line, ":", false);
                } else {
                    break;
                }
            }

        }

        return params;
    }

    // 處理SQL查詢語句
    public static String doSelect(String line, BufferedReader reader) throws IOException {

        // 格式化過後的SQL語句
        String oriSql = getOriSql(line, reader);
        // 是否為 此種SQL類型 select custom_id into :ls_custom_id
        boolean isIntoTypeSql = StrUtil.containsIgnoreCase(oriSql, "into");
        // 請求查詢的欄位
        List<String> selecColumns = getSqlSelectColumns(oriSql, isIntoTypeSql);
        // 數據表
        String[] tables = getSqlTables(oriSql);
        // :參數
        Set<String> params = getSqlParams(oriSql);


        // 產生JPA查詢表的API
        if (selecColumns.size() > 5 && tables.length == 1) {
//            doJPAApi(oriSql)
        }


        // 增加查詢欄位別名
        oriSql = addSelectColumnAlias(oriSql, selecColumns, isIntoTypeSql);

        // :參數增加空格
        String result = oriSql.toString().replace("(:", "( :").replace("=:", "= :");
        ;


        // 去除尾部 +號 並增加 ；號
        result = "sql = " + StrUtil.subBefore(result, " + ", true) + ";\n";

        // 請求參數映射  param.put("ls_virtual_account", ls_virtual_account);
        if (params.size() != 0) {
            result += "param = new HashMap(); \n";
            for (String param : params) {
                result = result + "param.put(\"" + param + "\", " + param + ");\n";
            }
        }

        result += "\n";

        // 增加持久層查詢語句  resMap = (Map<String, String>) custVirtualAccountRepoitory.findMapByNativeSql(sql, param);
        String table = tables[0];
        table = StrUtil.toCamelCase(table);
        table = "resultList = " + table + "Repository.findMapByNativeSql(sql, param);\n";
        table += "resultMap = new HashMap();\n";
        table += "if (resultList.size() != 0)  resultMap = (Map<String, Object>) resultList.get(0);\n";
        result += table;

        // 查詢結果封裝 ls_temp = resultMap.get("LS_TEMP");
        for (String selecColumn : selecColumns) {
            selecColumn = selecColumn.trim();
            if (isIntoTypeSql) {
                // 數字類型
                if (StrUtil.containsAnyIgnoreCase(selecColumn, "ll", "li", "ld")) {
                    result = result + selecColumn.toLowerCase() + " = (BigDecimal) resultMap.get(\"" + selecColumn.toUpperCase() + "\");\n";
                }
                // 非數字類型
                else {
                    result = result + selecColumn.toLowerCase() + " = (String) resultMap.get(\"" + selecColumn.toUpperCase() + "\");\n";
                }
            } else {
                result = result + "ls_" + selecColumn.toLowerCase() + " = resultMap.get(\"" + selecColumn.toUpperCase() + "\");\n";
            }
        }

        return result;
    }

    // 處理非查詢的SQL語句
    public static String doUpdate(String line, BufferedReader reader) throws IOException {

        // 原來的SQL刪除語句，是否缺少FROM關鍵字
        boolean isDelLackFrom = false;

        String oriSql = getOriSql(line, reader);
        Set<String> params = getSqlParams(oriSql);
        String table = StrUtil.subBetween(oriSql.toUpperCase(), "UPDATE", "\"");
        if (table == null) table = StrUtil.subBetween(oriSql.toUpperCase(), "INSERT INTO", "\n");
        if (table == null) table = StrUtil.subBetween(oriSql.toUpperCase(), "DELETE FROM", "\n");
        if (table == null) {
            table = StrUtil.subBetween(oriSql.toUpperCase(), "DELETE", "\n");
            // DELETE語句缺少 FROM 關鍵字
            if (table != null) isDelLackFrom = true;
            oriSql = "\" delete from " + StrUtil.subAfter(oriSql, "delete", false);
        }

        table = table.replace("\"", "").replace("+", "").trim();

        // :參數增加空格
        String result = oriSql.toString().replace("(:", "( :").replace("=:", "= :");

        // 去除尾部 +號 並增加 ；號
        result = "sql = " + StrUtil.subBefore(result, " + ", true) + ";\n";

        // 請求參數映射  param.put("ls_virtual_account", ls_virtual_account);
        if (params.size() != 0) {
            result += "param = new HashMap(); \n";
            for (String param : params) {
                result = result + "param.put(\"" + param + "\", " + param + ");\n";
            }
        }

        result += "\n";

        // 增加持久層查詢語句  resMap = (Map<String, String>) custVirtualAccountRepository.findMapByNativeSql(sql, param);
        table = StrUtil.toCamelCase(table);
        table = table + "Repository.executeUpdate(sql, param);\n";
        result += table;

        return result;
    }
}
