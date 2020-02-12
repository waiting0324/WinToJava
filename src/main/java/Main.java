import cn.hutool.core.util.ReUtil;
import cn.hutool.core.util.StrUtil;

import java.io.*;
import java.util.*;

/**
 * Create By Waiting on 2020/2/10
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
            if (line == null) break;
            if (trimLine.startsWith("//") || trimLine.startsWith("*")) continue;

            // 聲明List加註釋
            if (StrUtil.startWithIgnoreCase(trimLine, "DECLARE")) line = "// " + line;

            // 變量聲明
            if (StrUtil.startWithAny(trimLine, "string", "integer", "long", "datetime")) {
                line = doVariDecl(line);
            }

            if (StrUtil.startWith(line, "ls_")) {
                List<String> blocks = StrUtil.splitTrim(line, "=");
                // trim 關鍵字轉換  ls_cargo_location  = trim(dw_criteria_1.cargo_location) → ls_cargo_location = StringUtils.trim(dw_criteria_1.getCargoLocation());
                if (StrUtil.isWrap(blocks.get(1), "trim(", ")")) {
                    String content = StrUtil.unWrap(blocks.get(1), "trim(", ")");
                    content = content.split("\\.")[0] + "." + StrUtil.genGetter(StrUtil.toCamelCase(content.split("\\.")[1])) + "()";
                    line = blocks.get(0) + " = StringUtils.trimToEmpty(" + content + ")";
                }
            }

            // 條件語句
            if (trimLine.startsWith("if")) {
                line = doIf(line);
            }

            // 查詢語句
            if (StrUtil.startWithIgnoreCase(trimLine, "SELECT")) {
                line = doSelect(line, reader);
                isSql = true;
            }


            result.append(line);

            // 加上句號
            if (!"".equals(line) && !line.endsWith(";") && !isSql) result.append(";");
            result.append("\n");
        }



        System.out.println("===============================");
        System.out.println(result);
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

        return line;
    }

    private static String doDateTime(String line) {
        return line.replace("datetime", "Timestamp");
    }

    // 處理SQL查詢語句
    static String doSelect(String line, BufferedReader reader) throws IOException {

        // :參數
        Set<String> params  = new LinkedHashSet();
        // 數據表
        String[] tables = new String[10];
        // 請求查詢的欄位
        List<String> selecColumns = new ArrayList<>();
        // FROM 關鍵字是否出現
        boolean isFromAppear = false;

        // SQL 語句
        StringBuilder oriSql = new StringBuilder();

        // 首句取得查詢欄位
        String firstSelectColumn = StrUtil.subAfter(line, ".", false).replace(",", "");
        selecColumns.add(firstSelectColumn);
        // 首句增加查詢欄位別名
        line = StrUtil.subBefore(line, ",", true) + " " + firstSelectColumn + ",";
        // 首句尾巴加上 " +
        oriSql.append(line + " \" \u002B \n");

        while (!StrUtil.contains(line = reader.readLine(), ";")) {
            line = line.trim();

            // 取得數據表
            if (StrUtil.startWithIgnoreCase(line, "FROM")) {
                tables = StrUtil.subAfter(line, "FROM", false).split(",");
                StrUtil.trim(tables);
            }

            // 取得查詢欄位
            if (StrUtil.containsIgnoreCase(line, "FROM")) isFromAppear = true;
            if (!isFromAppear) {
                String selectColumn = StrUtil.subAfter(line, ".", false);
                if (selectColumn.contains(",")) selectColumn = selectColumn.replace(",", "");
                selecColumns.add(selectColumn);
                // 增加查詢欄位別名
                if (line.trim().endsWith(",")) {
                    line = StrUtil.subBefore(line, ",", true) + " " + selectColumn + ",";
                } else {
                    line = line + " " + selectColumn;
                }
            }

            // 空格格式化
            if (StrUtil.startWithIgnoreCase(line, "FROM")) line = "   " + line;
            else if (StrUtil.startWithIgnoreCase(line, "WHERE")) line = " " + line;
            else if (StrUtil.startWithIgnoreCase(line, "AND")) line = "   " + line;
            else if (StrUtil.startWithIgnoreCase(line, "ORDER")) line = " " + line;
            else line = " " + line;

            // 處理 :param 變量
            String param = StrUtil.subBetween(line, ":", ",");
            if (param != null) params.add(param);

            // 尾部增加+號
            oriSql.append("\" " + line + "  \" \u002B \n");
        }

        // 參數增加空格
        String result = oriSql.toString().replace("(:", "( :");


        // 去除尾部 +號 並增加 ；號
        result = "sql = \"" +  StrUtil.subBefore(result, " + ", true) + ";\n";

        // 請求參數映射
        if (params.size() != 0) {
            result += "param = new HashMap(); \n";
            for (String param : params) {
                result = result + "param.put(\"" + param + "\", " + param + ");\n";
            }
        }

        result += "\n";

        // 增加持久層查詢語句
        String table = tables[0];
        table = StrUtil.toCamelCase(table);
        table = "resStrMap = (Map<String, String>) " + table + "Respository.findMapByNativeSql(sql, param);\n";
        result += table;

        // 查詢結果封裝
        // ls_temp = resStrMap.get("LS_TEMP");
        for (String selecColumn : selecColumns) {
            // 全大寫轉小寫
            String lowerSelecColumn = "";
            if (StrUtil.isUpperCase(selecColumn)) lowerSelecColumn = StrUtil.swapCase(selecColumn);
            result = result + "ls_" + lowerSelecColumn + " = resStrMap.get(\"" + selecColumn + "\");\n";
        }

        return result;
    }


    static String doIf(String line) {

        String trimLine = StrUtil.trimToEmpty(line);
        boolean isFalse = false;
        String afterLine = StrUtil.subAfter(trimLine, "if", false).trim(); // if後的全部字串
        String firstStr = StrUtil.subBefore(afterLine, " ", false); // if後的第一個關鍵字
        String condi = StrUtil.subBetween(line, "if", "then").trim(); //條件判斷式
        String func = StrUtil.subAfter(afterLine, "then", false); // 執行語句


        // if isnull(ls_register_no) then ls_register_no  = ''
        if (condi.startsWith("isnull")) {

            // ls_register_no
            String param = StrUtil.unWrap(condi, "isnull(", ")");

            // 關鍵字轉換
            func = func.replace("\'", "\"").replace("0", "BigDecimal.ZERO");

            // 轉換結果
            line = StrUtil.indexedFormat("if ({0} == null) {1}", param, func);
        }

        if (firstStr.equals("not"))  {
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
            line = StrUtil.indexedFormat("if ({0} == {1}) {2}", condiLeft, number, func);
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
        if (splits.length == 1) { // 簡單類型，沒有運算符 len(trim(arg_bank_id))
            String param = StrUtil.unWrap(source, "len(", ")");
            if (StrUtil.isWrap(param, "trim(", ")")) {
                param = StrUtil.unWrap(param, "trim(", ")");
                param = StrUtil.wrap(param, "StringUtils.trimToEmpty(", ")");
            }
            param += ".length()"; // param: StringUtils.trimToEmpty(arg_bank_id).length()
            params.add(param);
        } else {
            for (String param : splits) {
                if (StrUtil.isWrap(param, "len(", ")")) {
                    param = StrUtil.unWrap(param, "len(", ")");
                    if (StrUtil.isWrap(param, "trim(", ")")) {
                        param = StrUtil.unWrap(param, "trim(", ")");
                        param = StrUtil.wrap(param, "StringUtils.trimToEmpty(", ")");
                    }
                    param += ".length()"; // param: StringUtils.trimToEmpty(arg_bank_id).length()
                    params.add(param);

                } else if (StrUtil.containsAny(param.trim(), "+", "-", "*", "/")){
                    operaters.add(param.trim());
                }
            }

        }

        String result = "";

        while (params.size() != 0) {
            result += params.pop();
            if (operaters.size() != 0) result = result + " " + operaters.pop() + " ";
        }
        return result;
    }
}
