import cn.hutool.core.util.ReUtil;
import cn.hutool.core.util.StrUtil;

import java.io.*;
import java.util.*;

/**
 * @author Waiting on 2020/2/10
 */
public class Main {


    public static void main(String[] args) throws Exception {

        StringBuilder result = new StringBuilder();
        InputStream is = new FileInputStream(Main.class.getClassLoader().getResource("source.txt").getPath());
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        while (true) {
            String line = reader.readLine();
            String trimLine = StrUtil.trimToEmpty(line);

            // 是否為處理SQL
            boolean isSql = false;

            // 到最後或是註解則略過
            if (line == null) {break;}
            else if (trimLine.startsWith("//") || trimLine.startsWith("*")) {continue;}

            // 聲明List加註釋
            else if (StrUtil.startWithIgnoreCase(trimLine, "DECLARE")) {line = "// " + line;}

            // 變量聲明
            else if (StrUtil.startWithAny(trimLine, "string", "integer", "long", "datetime")) {
                line = doVariDecl(line);
            }

            // 參數賦值
            else if (StrUtil.startWithAny(line, "ls_", "li_", "ll_")) {
                line = doAsignParam(line);
            }

            // 條件語句
            else if (trimLine.startsWith("if")) {
                line = doIf(line);
            }

            // 查詢語句
            else if (StrUtil.startWithIgnoreCase(trimLine, "SELECT")) {
                line = doSelect(line, reader);
                isSql = true;
            }

            // 函數聲明
            else if (StrUtil.startWithAny(trimLine, "Str", "json")) {
                line = doFuncDecl(line);
            }

            else if (trimLine.startsWith("return")) {
                line = line + "\n }";
            }

            // 加上換行
            result.append(line + "\n");
        }


        System.out.println("===============================");
        System.out.println(result);
       /* BufferedWriter writer = FileUtil.getWriter("C:/Users/6550/Desktop/result.txt", "UTF-8", false);
        writer.write(result.toString());
        writer.close();*/
    }

    // 參數賦值
    private static String doAsignParam(String line) {
        String leftParam = line.split("=")[0].trim();
        String func = StrUtil.subBefore(line.split("=")[1], "//", true).trim();
        String comment = StrUtil.subAfter(line, "//", true);

        // 單純賦值為0  ll_non_rcv_cnt = 0
        if ("0".equals(func)) {
            func = "BigDecimal.ZERO";
        }
        // 單純賦值字串  ls_ar_type = '008'
        else if (StrUtil.isWrap(func, "\'")) {
            func = func.replace("\'", "\"");
        }
        // 簡單加減計算  ll_ar_amt - ll_ar_recv_amt
        else if (StrUtil.count(func, "-") == 1 || StrUtil.count(func, "+") == 1) {
            String operator = "";
            String[] params = null;
            if (func.contains("-")) {
                operator = "sub";
                params = func.split("-");
            } else if (func.contains("+")) {
                operator = "add";
                params = func.split("\\+");
            }
            StrUtil.trim(params);
            func = StrUtil.format("{}.{}({})" , params[0], operator, params[1]);
        }


        // 有注釋
        if (!"".equals(comment)) {
            return StrUtil.format("{} = {}; //{}" , leftParam, func, comment);
        }
        // 沒有注釋
        else {
            return StrUtil.format("{} = {};" , leftParam, func);
        }

    }

    // 函數聲明
    private static String doFuncDecl(String line) {

        // 駝峰函數名
        String funcName = StrUtil.toCamelCase(StrUtil.subBetween(line, " ", "(").trim());
        // 參數字串
        String paramStr = StrUtil.subBetween(line, "(", ")");
        // 參數關鍵字取代
        paramStr = paramStr.replace("string", "String").replace("str", "String").replace("long", "BigDecimal");

        String result = StrUtil.format("@Override\npublic TransactionData {} ({}) {\n", funcName, paramStr);

        // 常用變量聲明
        result += "String sql;\n";
        result += "Map<String, Object> param;\n";
        result += "Map<String, String> resStrMap;\n";
        result += "Map<String, BigDecimal> resDecMap;\n";

        return result;
    }

    // 變量聲明
    private static String doVariDecl(String line) {

        if (line.trim().startsWith("string")) {
            line = doString(line);
        } else if (line.trim().startsWith("integer")) {
            line = doNum(line, "integer");
        } else if (line.trim().startsWith("long")) {
            line = doNum(line, "long");
        } else if (line.trim().startsWith("datetime")) {
            line = doDateTime(line);
        }

        return line + ";";
    }

    // 處理datetime類型聲明
    private static String doDateTime(String line) {
        return line.replace("datetime", "Timestamp");
    }

    // 取得格式化過後的SQL語句
    private static String getOriSql(String line, BufferedReader reader) throws IOException {

        // SQL 語句
        StringBuilder oriSql = new StringBuilder();

        // 首句尾巴加上 " +
        oriSql.append(line + " \" \u002B \n");

        while (!StrUtil.contains(line = reader.readLine(), ";")) {
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

        return "\" " + oriSql.toString();
    }

    // 增加查詢欄位別名
    private static String addSelectColumnAlias(String oriSql, List<String> selectColumns, boolean isIntoTypeSql) {

        String[] sqlLines = oriSql.split("\n");
        StringBuilder result = new StringBuilder();
        boolean isFromAppear = false;

        for (int i = 0; i < sqlLines.length; i++) {

            String sqlLine = sqlLines[i];

            if (sqlLine.contains(":")) {continue;}

            if (StrUtil.containsIgnoreCase(sqlLine,"FROM")) { isFromAppear = true;}

            // FROM 關鍵字出現之前，替查詢欄位加上別名
            if (!isFromAppear) {
                // 此種SQL類型 SELECT CUST_VIRTUAL_ACCOUNT.CARGO_LOCATION
                if (!isIntoTypeSql) {
                    // 非最後一個查詢欄位
                    if (sqlLine.contains(",")) {
                        sqlLine = StrUtil.subBefore(sqlLine, ",", true) + " " + selectColumns.get(i).toUpperCase()
                                + "," + StrUtil.subAfter(sqlLine, ",", true);
                    }
                    // 最後一個查詢欄位
                    else {
                        sqlLine = StrUtil.subBefore(sqlLine, "\"", true) + selectColumns.get(i).toUpperCase()
                                + "  \"" + StrUtil.subAfter(sqlLine, "\"", true);
                    }
                } else {
                    // 非最後一個查詢欄位
                    if (sqlLine.contains(",")) {
                        // 有SQL內置函數的 decode(custom_id)
                        if (sqlLine.contains(")")) {
                            sqlLine = StrUtil.subBefore(sqlLine, ")", true)+ ") " + selectColumns.get(i).toUpperCase()
                                    + ", \" \u002B ";
                        }
                        // 沒有SQL內置函數的 "  payment_type  ,  " +
                        else {
                            sqlLine = StrUtil.subBefore(sqlLine, ",", true).trim() + " " + selectColumns.get(i).toUpperCase()
                                    + "," + StrUtil.subAfter(sqlLine, ",", true);
                        }
                    }
                    // 最後一個查詢欄位
                    else {
                        // 有SQL內置函數的 decode(custom_id)
                        if (sqlLine.contains(")")) {
                            sqlLine = StrUtil.subBefore(sqlLine, ")", true)+ ") " + selectColumns.get(i).toUpperCase()
                                    + " \" \u002B ";
                        }
                        // 沒有SQL內置函數的 "  payment_type    " +
                        else {
                            sqlLine = StrUtil.subBefore(sqlLine, "\"", true).trim() + " " + selectColumns.get(i).toUpperCase()
                                    + "  \"" + StrUtil.subAfter(sqlLine, "\"", true);
                        }
                    }
                }
            }
            result.append(sqlLine + "\n");
        }

        return result.toString();
    }

    // 取得SQL查詢欄位
    static List<String> getSqlSelectColumns(String oriSql, boolean isIntoTypeSql) {

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
                    String selectColumn = StrUtil.subBetween(line, ":", " ");
                    if (selectColumn.contains(",")) {
                        selectColumn = selectColumn.replace(",", "");
                    }
                    selecColumns.add(selectColumn);
                }
            }
            // 此種SQL類型 SELECT CUST_VIRTUAL_ACCOUNT.CARGO_LOCATION
            else {
                String selectColumn = StrUtil.subBetween(line, ".", " ");
                if (selectColumn.contains(",")) {
                    selectColumn = selectColumn.replace(",", "");
                }
                selecColumns.add(selectColumn);
            }
        }

        return selecColumns;
    }

    // 取得SQL查詢表
    static String[] getSqlTables(String oriSql) {
        String line = StrUtil.subBetween(oriSql.toUpperCase(), "FROM", "\"");
        String[] tables = line.split(",");
        StrUtil.trim(tables);
        return tables;
    }

    // 取得SQL :param 變量
    static Set<String> getSqlParams(String oriSql) {

        // 參數集合
        Set<String> params  = new LinkedHashSet();

        // 行SQL的列表
        List<String> sqlLines = StrUtil.splitTrim(oriSql, "\n");

        for (String line : sqlLines) {
            String param = StrUtil.subBetween(line, ":", ",");
            if (param != null) {
                params.add(param);
            }
        }

        return params;
    }

    // 處理SQL查詢語句
    static String doSelect(String line, BufferedReader reader) throws IOException {

        // 格式化過後的SQL語句
        String oriSql = getOriSql(line, reader);
        // 是否為 此種SQL類型 select custom_id into :ls_custom_id
        boolean isIntoTypeSql = StrUtil.containsIgnoreCase(oriSql, "into");
        // 請求查詢的欄位
        List<String> selecColumns = getSqlSelectColumns(oriSql, isIntoTypeSql);
        // 數據表
        String[] tables = getSqlTables(oriSql);
        // :參數
        Set<String> params  = getSqlParams(oriSql);


        // 增加查詢欄位別名
        oriSql = addSelectColumnAlias(oriSql, selecColumns, isIntoTypeSql);

        // :參數增加空格
        String result = oriSql.toString().replace("(:", "( :");


        // 去除尾部 +號 並增加 ；號
        result = "sql = \"" +  StrUtil.subBefore(result, " + ", true) + ";\n";

        // 請求參數映射  param.put("ls_virtual_account", ls_virtual_account);
        if (params.size() != 0) {
            result += "param = new HashMap(); \n";
            for (String param : params) {
                result = result + "param.put(\"" + param + "\", " + param + ");\n";
            }
        }

        result += "\n";

        // 增加持久層查詢語句  resStrMap = (Map<String, String>) custVirtualAccountRespository.findMapByNativeSql(sql, param);
        String table = tables[0];
        table = StrUtil.toCamelCase(table);
        table = "resStrMap = (Map<String, String>) " + table + "Respository.findMapByNativeSql(sql, param);\n";
        result += table;

        // 查詢結果封裝 ls_temp = resStrMap.get("LS_TEMP");
        for (String selecColumn : selecColumns) {

            if (isIntoTypeSql) {
                // 數字類型
                if (StrUtil.containsAnyIgnoreCase(selecColumn, "ll", "li", "ld")) {
                    result = result + selecColumn.toLowerCase() + " = new BigDecimal(resStrMap.get(\"" + selecColumn.toUpperCase() + "\"));\n";
                }
                // 非數字類型
                else {
                    result = result + selecColumn.toLowerCase() + " = resStrMap.get(\"" + selecColumn.toUpperCase() + "\");\n";
                }
            } else {
                result = result + "ls_" + selecColumn.toLowerCase() + " = resStrMap.get(\"" + selecColumn.toUpperCase() + "\");\n";
            }
        }

        return result;
    }

    // 處理if條件判斷式
    static String doIf(String line) {

        String trimLine = StrUtil.trimToEmpty(line);
        boolean isFalse = false;
        // if後的全部字串
        String afterLine = StrUtil.subAfter(trimLine, "if", false).trim();
        // if後的第一個關鍵字
        String firstStr = StrUtil.subBefore(afterLine, " ", false);
        // 條件判斷式
        String condi = StrUtil.subBetween(line, "if", "then").trim();
        // 執行語句
        String func = StrUtil.subAfter(afterLine, "then", false);


        // if isnull(ls_register_no) then ls_register_no  = ''
        if (condi.startsWith("isnull")) {

            // ls_register_no
            String param = StrUtil.unWrap(condi, "isnull(", ")");

            // 關鍵字轉換
            func = func.replace("\'", "\"").replace("0", "BigDecimal.ZERO");

            // 轉換結果
            line = StrUtil.indexedFormat("if ({0} == null) {1}", param, func) + ";";
        }

        if ("not".equals(firstStr))  {
            isFalse = true;
        }

        // if len(trim(arg_bank_id)) + len(trim(arg_user_id)) = 13 then return 'Fail'
        // if len(trim(arg_bank_id)) = 0 then return 'Fail'; → if (StringUtils.trimToEmpty(arg_bank_id).length() == ) return 'Fail';
        if (StrUtil.isWrap(firstStr, "len(", ")")) {  // firstStr: len(trim(arg_bank_id))

            // 處理條件判斷式
            String condiLeft = StrUtil.subBefore(condi, "=", false).trim();; // 條件判斷式左側
            condiLeft = tranIfLenTrimParas(condiLeft);

            // 關鍵字轉換
            func = func.replace("\'", "\"");

            Integer number = ReUtil.getFirstNumber(line);

            // 拼接結果
            line = StrUtil.indexedFormat("if ({0} == {1}) {2};", condiLeft, number, func);
        }
        return line;
    }

    // integer li_i, li_j → igDecimal li_i = BigDecimal.ZERO, li_j = BigDecimal.ZERO;
    static String doNum(String line, String type) {
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
                param = StrUtil.wrap(param, "StringUtils.trimToEmpty(", ")");
            }
            // param: StringUtils.trimToEmpty(arg_bank_id).length()
            param += ".length()";
            params.add(param);
        } else {
            for (String param : splits) {
                if (StrUtil.isWrap(param, "len(", ")")) {
                    param = StrUtil.unWrap(param, "len(", ")");
                    if (StrUtil.isWrap(param, "trim(", ")")) {
                        param = StrUtil.unWrap(param, "trim(", ")");
                        param = StrUtil.wrap(param, "StringUtils.trimToEmpty(", ")");
                    }
                    // param: StringUtils.trimToEmpty(arg_bank_id).length()
                    param += ".length()";
                    params.add(param);

                } else if (StrUtil.containsAny(param.trim(), "+", "-", "*", "/")){
                    operaters.add(param.trim());
                }
            }

        }

        String result = "";

        while (params.size() != 0) {
            result += params.pop();
            if (operaters.size() != 0) {
                result = result + " " + operaters.pop() + " ";
            }
        }
        return result;
    }
}
