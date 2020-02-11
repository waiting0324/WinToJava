import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.ReUtil;
import cn.hutool.core.util.StrUtil;
import org.apache.commons.lang3.StringUtils;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Create By Waiting on 2020/2/10
 */
public class Main {
    public static void main(String[] args) throws Exception {

        StringBuilder result = new StringBuilder();
        InputStream is = new FileInputStream(Main.class.getClassLoader().getResource("source.txt").getPath());
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        while (true) {
            //String line = StrUtil.trim(reader.readLine());
            String line = reader.readLine();
            String trimLine = StrUtil.trimToEmpty(line);

            if (line == null) break;

            // 命名轉換
            // string ls_cargo_location, ls_register_no → String ls_cargo_location = null, ls_register_no = null;
            if (trimLine.startsWith("string")) {
                line = StrUtil.replace(line, "string", "String");
                String[] split = StrUtil.split(line, ",");
                StrUtil.trim(split);
                String[] strings = StrUtil.wrapAll("", " = null", split);
                line = StrUtil.join(", ", strings);
            }

            // integer li_i, li_j → igDecimal li_i = BigDecimal.ZERO, li_j = BigDecimal.ZERO;
            if (trimLine.startsWith("integer")) {
                line = StrUtil.replace(line, "integer", "BigDecimal");
                String[] split = StrUtil.split(line, ",");
                StrUtil.trim(split);
                String[] strings = StrUtil.wrapAll("", " = BigDecimal.ZERO", split);
                line = StrUtil.join(", ", strings);
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

            if (trimLine.startsWith("if")) {
                boolean isFalse = false;
                String afterLine = StrUtil.subAfter(trimLine, "if", false).trim();
                String firstStr = StrUtil.subBefore(afterLine, " ", false);
                // if isnull(ls_register_no) then ls_register_no  = ''
                if (afterLine.startsWith("isnull")) {
                    line = line.replace("isnull", "").replace("then", "").replace("\'", "\"").trim();
                    String[] split = line.split("\\)");
                    line = split[0] + " == null)" + split[1];
                }

                // if len(trim(arg_bank_id)) = 0 then return 'Fail'; → if (StringUtils.trimToEmpty(arg_bank_id).length() == ) return 'Fail';
                if (firstStr.equals("not"))  {
                    isFalse = true;
                }
                if (StrUtil.isWrap(firstStr, "len(", ")")) {  // firstStr: len(trim(arg_bank_id))
                    // 取得參數並包裹
                    String param = StrUtil.unWrap(firstStr, "len(", ")");
                    if (StrUtil.isWrap(param, "trim(", ")")) {
                        param = StrUtil.unWrap(param, "trim(", ")");
                        param = StrUtil.wrap(param, "StringUtils.trimToEmpty(", ")");
                    }
                    param += ".length()"; // param: StringUtils.trimToEmpty(arg_bank_id).length()

                    // 關鍵字轉換
                    line = line.replace(firstStr, param).replace("=", "==")
                            .replace("then", "").replace("\'", "\"");

                    Integer number = ReUtil.getFirstNumber(line);

                    // 拼接結果
                    line = StrUtil.indexedFormat("if ({0} == {1}) {2}", param, number, StrUtil.subAfter(line,number+"", true));

                }

              /*  if len(trim(ls_tran_date_s)) = 0 and
                len(trim(ls_tran_date_e)) = 0 and
                len(trim(ls_fee_start_date)) = 0
                and len(trim(ls_fee_end_date)) = 0 then*/
                /*if (afterLine.startsWith("len")) {

                    List<String> params = new ArrayList<>();
                    // 取出if上半部
                    while (!line.contains("then")) {
                        line = line + "\n" + reader.readLine();
                        if (line.contains("then")) break;
                    }

                    String[] split = line.split(" ");

                    for (String s : split) {
                        if (StrUtil.isWrap(s, "len(", ")") || StrUtil.isWrap(s, "trim(", ")")) {
                            s = StrUtil.unWrap(s, "len(", ")");
                            s = StrUtil.unWrap(s, "trim(", ")");
                            params.add(s);
                        }
                    }

                    System.out.println(params);
                }
*/


            }




            // 加上句號
            if (!"".equals(line)) result.append(line + ";");
            result.append("\n");
        }



        System.out.println("===============================");
        System.out.println(result);
    }
}
