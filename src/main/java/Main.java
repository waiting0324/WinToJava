import cn.hutool.core.io.FileUtil;
import cn.hutool.core.math.MathUtil;
import cn.hutool.core.text.StrSpliter;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.ReUtil;
import cn.hutool.core.util.StrUtil;

import java.io.*;
import java.math.BigDecimal;
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
            if (line == null) {
                break;
            } else if (trimLine.startsWith("//") || trimLine.startsWith("*")) {
                //continue;
            }

            // 聲明List加註釋
            else if (StrUtil.startWithIgnoreCase(trimLine, "DECLARE")) {
                line = "// " + line;
            }

            // 變量聲明
            else if (StrUtil.startWithAny(trimLine, "string", "integer", "long", "datetime")) {
                line = doVariDecl(line);
            }

            // 訊息框
            else if (StrUtil.containsIgnoreCase(trimLine, "messagebox")) {
                line = doMessagebox(line, reader);
            }

            // 特殊運算符
            else if (StrUtil.containsAny(trimLine, "+=", "++", "-=")) {
                line = doSpecialOperator(line);
            }

            // 參數賦值
            else if (StrUtil.startWithAny(trimLine, "ls_", "li_", "ll_")) {
                line = doAsignParam(line);
            }

            // 條件語句
            else if (trimLine.startsWith("if")) {
                line = doIf(line, reader);
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
            // 返回
            else if (trimLine.startsWith("return")) {
                line = doReturn(line, false);
            }
            // 關鍵字
            else if (StrUtil.startWithAny(trimLine, "next", "end if", "loop", "commit", "open", "close", "continue", "else")) {
                line = doKeyword(line);
            }
            // 新增或更新SQL
            else if (StrUtil.startWithIgnoreCase(trimLine, "UPDATE")
                || StrUtil.startWithIgnoreCase(trimLine, "INSERT")) {
                line = doUpdate(line, reader);
            }
            // 處理for跟do while
            else if (StrUtil.startWithAny(trimLine, "for", "do while")) {
                line = line + " {";
            }


            // 加上換行
            result.append(line + "\n");
        }


        System.out.println("===============================");
        System.out.println(result);
        BufferedWriter writer = FileUtil.getWriter("C:/Users/6550/Desktop/result.txt", "UTF-8", false);
        writer.write(result.toString());
        writer.close();
    }

    static String doUpdate(String line, BufferedReader reader) throws IOException {

        String oriSql = getOriSql(line, reader);
        Set<String> params = getSqlParams(oriSql);
        String table = StrUtil.subBetween(oriSql.toUpperCase(), "UPDATE", "\"");
        if (table == null) table = StrUtil.subBetween(oriSql.toUpperCase(), "INSERT INTO", "\n");

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

        // 增加持久層查詢語句  resStrMap = (Map<String, String>) custVirtualAccountRespository.findMapByNativeSql(sql, param);
        table = StrUtil.toCamelCase(table);
        table = table + "Respository.executeUpdate(sql, param);\n";
        result += table;

        return result;
    }

    // 特殊運算符 += ++ -=
    static String doSpecialOperator(String line) {

        if (line.contains("++")) {
            String param = StrUtil.subBefore(line, "++", true).trim();
            line = StrUtil.indexedFormat("{0} = {0}.add({0})", param);
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

    static String doMessagebox(String line, BufferedReader reader) throws IOException {

        // 訊息狀態，訊息或錯誤
        String resultType = "false";

        // 訊息中的變量
        List<String> params = new ArrayList<>();
        StringBuilder result = new StringBuilder();

        // 取得訊息行
        while (!line.contains(")")) {
            line += reader.readLine();
        }


        line = line.replace("\'", "");

        // '訊息','客服組已輸入【客戶:'+ls_register_no+'】使用額度沖銷資料，系統不再自動寫入'
        String paramStr = StrUtil.subBetween(line, " messagebox(", ")");
        // 判斷訊息狀態
        if (paramStr.split(",")[0].contains("訊息")) resultType = "true";

        // 取得訊息中的變量
        String msg = paramStr.split(",")[1];
        String[] split = msg.split("\\+");
        for (String s : split) {
            if (s.contains("_")) params.add(s);
        }

        // 拼裝結果
        result.append("// TODO " + msg + "\n");
        if (params.size() > 0) {
            // Object[] args = new Object[] {keep_gci_date, keep_pcs};
            result.append("Object[] args = new Object[] {" + StrUtil.unWrap(params.toString(), "[", "]") + "};\n");
            result.append("return new TransactionData(" + resultType + ", \"\", FeeResultEnum.FE00_E0001, null, args);\n");
        } else {
            result.append("return new TransactionData(" + resultType + ", \"\", FeeResultEnum.FE00_E0001, null, null);\n");
        }

        return result.toString();
    }

    // 關鍵字
    static String doKeyword(String line) {

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
        } else if (trimLine.startsWith("else")) {
            return line.replace("else", "} else {");
        }

        return "";
    }

    static String doReturn(String line, boolean isIf) {

        StringBuilder result = new StringBuilder();
        line = line.trim().split(" ")[1].trim();

        // 失敗
        if ("\"Fail\"".equalsIgnoreCase(line)) {
            result.append("\n// 失敗 \n");
            result.append("return new TransactionData(false, \"\", FeeResultEnum.FE00_E0001, null, null);\n");
        }
        // 封裝字串 return new TransactionData(true, "", ResultEnum.SUCCESS, new String(li_value4), null);
        else if (StrUtil.startWithIgnoreCase(line, "string")) {
            String param = StrUtil.unWrap(line, "string(", ")");
            result.append("return new TransactionData(true, \"\", ResultEnum.SUCCESS, new String(" + param + "), null);\n");
        }
        // 是數字
        else if (NumberUtil.isNumber(line)) {
            result.append("return new TransactionData(true, \"\", ResultEnum.SUCCESS, new BigDecimal(" + line + "), null);\n");
        }
        // 返回JSON則標注待處理
        else if (StrUtil.startWithIgnoreCase(line, "json")) {
            result.append("// TODO 處理返回值\n");
            result.append("new TransactionData(true, \"\", ResultEnum.SUCCESS, 返回值, null);\n");
        }

        // 非單行If的情況則加上大括號
        if (isIf == false) result.append("}\n");

        return result.toString();
    }

    // 參數賦值
    static String doAsignParam(String line) {
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
                operator = "subtract";
                params = func.split("-");
            } else if (func.contains("+")) {
                operator = "add";
                params = func.split("\\+");
            }
            StrUtil.trim(params);
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
            func = StrUtil.format("{}.substring({}, {}); //TODO 更改位置", split[0], split[1], split[2]);
        //}
        return func;
    }

    // 函數聲明
    static String doFuncDecl(String line) {

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
    static String doVariDecl(String line) {

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
    static String doDateTime(String line) {
        return line.replace("datetime", "Timestamp");
    }

    // 取得格式化過後的SQL語句
    static String getOriSql(String line, BufferedReader reader) throws IOException {

        // SQL 語句
        StringBuilder oriSql = new StringBuilder();

        // 首句尾巴加上 " +
        oriSql.append(line.trim() + " \" \u002B \n");

        while (!StrUtil.contains(line = reader.readLine(), ";")) {
            line = line.replace("\"", "\'");
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
    static String addSelectColumnAlias(String oriSql, List<String> selectColumns, boolean isIntoTypeSql) {

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
                            sqlLine = StrUtil.subBefore(sqlLine, ")", true) + ") " + selectColumns.get(i).toUpperCase()
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
                            sqlLine = StrUtil.subBefore(sqlLine, ")", true) + ") " + selectColumns.get(i).toUpperCase()
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
    static String[] getSqlTables(String oriSql) {
        String line = StrUtil.subBetween(oriSql.toUpperCase(), "FROM", "\"");
        String[] tables = line.split(",");
        StrUtil.trim(tables);
        return tables;
    }

    // 取得SQL :param 要替換的變量
    static Set<String> getSqlParams(String oriSql) {

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

            if (isSelectSql) {
                if (StrUtil.containsIgnoreCase(line, "from")) {isFromAppear = true;}
                if (!isFromAppear) continue;
            }

            String param = StrUtil.subBetween(line, ":", ",");
            if (param == null) param = StrUtil.subBetween(line, ":", "|");
            if (param == null) param = StrUtil.subBetween(line, ":", "\n");
            if (param == null) param = StrUtil.subBetween(line, ":", " ");
            if (param != null) {
                params.add(param.trim());
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
        Set<String> params = getSqlParams(oriSql);


        // 增加查詢欄位別名
        oriSql = addSelectColumnAlias(oriSql, selecColumns, isIntoTypeSql);

        // :參數增加空格
        String result = oriSql.toString().replace("(:", "( :").replace("=:", "= :");;


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

        // 增加持久層查詢語句  resStrMap = (Map<String, String>) custVirtualAccountRespoitory.findMapByNativeSql(sql, param);
        String table = tables[0];
        table = StrUtil.toCamelCase(table);
        table = "resStrMap = (Map<String, String>) " + table + "Repository.findMapByNativeSql(sql, param).get(0);\n";
        result += table;

        // 查詢結果封裝 ls_temp = resStrMap.get("LS_TEMP");
        for (String selecColumn : selecColumns) {
            selecColumn = selecColumn.trim();
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
    static String doIf(String line, BufferedReader reader) throws IOException {

        // 替換關鍵字
        line = line.replace("and", "&&").replace("or", "||")
                .replace("\'", "\"").replace("<>", "!=");


        if (!line.contains("then")) {
            while (!(line += reader.readLine()).contains("then")) ;
        }

        String trimLine = StrUtil.trimToEmpty(line);
        boolean isFalse = false;
        // if後的全部字串
        String afterLine = StrUtil.subAfter(trimLine, "if", false).trim();
        // if後的第一個關鍵字
        String firstStr = StrUtil.subBefore(afterLine, " ", false);
        // 條件判斷式
        String condi = StrUtil.subBetween(line, "if", "then").trim();
        // 執行語句
        String func = null;
        if (afterLine.contains("//")) func = StrUtil.subBetween(afterLine, "then","//").trim();
        if (!afterLine.contains("//")) func = StrUtil.subAfter(afterLine, "then",true).trim();
        // 註釋
        String comment = StrUtil.subAfter(afterLine, "//", true);


        // if isnull(ls_register_no) then ls_register_no  = ''
        if (condi.startsWith("isnull")) {

            // ls_register_no
            String param = StrUtil.unWrap(condi, "isnull(", ")");

            // 關鍵字轉換
            func = func.replace("\'", "\"").replace("0", "BigDecimal.ZERO");

            // 轉換結果
            line = StrUtil.indexedFormat("if ({0} == null) {1}", param, func) + ";";
        }
        // if len(trim(arg_bank_id)) + len(trim(arg_user_id)) = 13 then return 'Fail'
        // if len(trim(arg_bank_id)) = 0 then return 'Fail'; → if (StringUtils.trimToEmpty(arg_bank_id).length() == ) return 'Fail';
        else if (StrUtil.isWrap(firstStr, "len(", ")")) {  // firstStr: len(trim(arg_bank_id))

            String operator = StrUtil.splitTrim(condi, " ").get(1);

            // 處理條件判斷式
            String condiLeft = StrUtil.subBefore(condi, operator, false).trim();
            // 條件判斷式左側
            condiLeft = tranIfLenTrimParas(condiLeft);

            // 關鍵字轉換
            if (func.trim().startsWith("return")) {
                func = doReturn(func, true);
            }
//            func = func.replace("\'", "\"");

            Integer number = ReUtil.getFirstNumber(line);

            // 拼接結果
            line = StrUtil.format("if ({} {} {}) { {} ", condiLeft, operator, number, func);
        }

        if (condi.startsWith("ls_")) {

            condi = doIfLsCondi(condi);




            /*List<String> split = StrUtil.splitTrim(condi, "=");
            condi = StrUtil.format("{}.equals({})", split.get(1), split.get(0));*/

            // 替只有一行的func加上下括號
            if (!"".equals(func)) func = func + " }";

            if (!"".equals(comment)) {
                line = StrUtil.format("if ({}) { {}  // {}", condi, func, comment);
            } else {
                line = StrUtil.format("if ({}) { {} ", condi, func);
            }
        }
        // ll_pay_amt = 0
        else if (StrUtil.startWithAny(condi, "ll_", "li_", "ld_")) {
            String operator = StrUtil.subBetween(condi.trim(), " ", " ");
            List<String> split = null;
            if ("=".equals(operator)) {
                split = StrUtil.splitTrim(condi, "=");
            } else if (">".equals(operator)) {
                split = StrUtil.splitTrim(condi, ">");
            } else if ("<".equals(operator)) {
                split = StrUtil.splitTrim(condi, "<");
            } else {
                return line = StrUtil.format("if ({}) { {} ", condi, func);
            }
            condi = StrUtil.format("{}.intValue() {} {}", split.get(0), operator, split.get(1));
            line = StrUtil.format("if ({}) { {} ", condi, func);
        }

       /* else if ("not".equals(firstStr)) {
            isFalse = true;
        }
*/
        return line;
    }

    // 處理 if 字串類型條件
    static String doIfLsCondi(String condi) {

        // 複雜類型
        // ls_payment_type = "W" || ls_payment_type = "N"
        // ls_payment_type = "H" && (ls_cust_attr = "N" || ls_cust_attr = "B")
        if (StrUtil.containsAny(condi, "||", "&&")) {

            // 條件是否被括號包住
            boolean isWrapByBrack = false;

            // 先去括號
            if (StrUtil.isWrap(condi.trim(), "(", ")")) {
                isWrapByBrack = true;
                condi = StrUtil.unWrap(condi.trim(), "(", ")");
            }

            // || 跟 && 運算符位置
            int orPos = StrUtil.indexOfIgnoreCase(condi, "||");
            int andPos = StrUtil.indexOfIgnoreCase(condi, "&&");

            // 不存在，則位置設為無限遠
            if (orPos == -1) orPos = 9999;
            if (andPos == -1) andPos = 9999;


            // 如果 || 運算符在條件靠前位置
            if (orPos < andPos) {
                String condi1 = StrUtil.subBefore(condi, "||", false);
                String condi2 = StrUtil.subAfter(condi, "||", false);

                condi1 = doIfLsCondi(condi1);
                condi2 = doIfLsCondi(condi2);

                if (isWrapByBrack) {
                    return StrUtil.format("({} || {})", condi1, condi2);
                } else {
                    return condi1 + " || " + condi2;
                }
            }

            else if (andPos < orPos) {
                String condi1 = StrUtil.subBefore(condi, "&&", false);
                String condi2 = StrUtil.subAfter(condi, "&&", false);

                condi1 = doIfLsCondi(condi1);
                condi2 = doIfLsCondi(condi2);

                if (isWrapByBrack) {
                    return StrUtil.format("({} && {})", condi1, condi2);
                } else {
                    return condi1 + " && " + condi2;
                }
            }

        }
        // 簡單類型 ls_payment_type = "L"
        else {
            List<String> split = StrUtil.splitTrim(condi, "=");
            condi = StrUtil.format("{}.equals({})", split.get(1), split.get(0));
        }



        return condi;
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

                } else if (StrUtil.containsAny(param.trim(), "+", "-", "*", "/")) {
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
