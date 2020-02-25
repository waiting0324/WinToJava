import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.waiting.*;

import java.io.*;
import java.util.regex.Pattern;

/**
 * @author Waiting on 2020/2/10
 */
public class Main {


    public static void main(String[] args) throws Exception {

        StringBuilder result = new StringBuilder();
        InputStream is = new FileInputStream(Main.class.getClassLoader().getResource("source.txt").getPath());
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        BufferedWriter writer = FileUtil.getWriter(Main.class.getClassLoader().getResource("result.txt").getPath(), "UTF-8", false);

        try {
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
                // 如果是  標示呼叫API、聲明List、json開頭、開頭中文  則變成注釋
                else if (StrUtil.containsIgnoreCase(line, "call API")
                        || StrUtil.startWithIgnoreCase(trimLine, "DECLARE")
                        || StrUtil.startWithIgnoreCase(trimLine, "json")
                        || Pattern.compile( "^[\u4e00-\u9fa5]" ).matcher(trimLine).find()) {
                    line =  "// " + line;
                }

                // 變量聲明
                else if (StrUtil.startWithAny(trimLine, "string", "integer","int", "long", "datetime")) {
                    line = DeclareMod.doVariDecl(line);
                }

                // 訊息框
                else if (StrUtil.containsIgnoreCase(trimLine, "messagebox")) {
                    line = KeywordMod.doMessagebox(line, reader);
                }

                // 特殊運算符
                else if (StrUtil.containsAny(trimLine, "+=", "++", "-=")) {
                    line = AssignMod.doSpecialOperator(line);
                }

                // 參數賦值
                else if (StrUtil.startWithAny(trimLine, "ls_", "li_", "ll_")) {
                    line = AssignMod.doAsignParam(line);
                }

                // 條件語句
                else if (trimLine.startsWith("if")) {
                    line = IfMod.doIf(line, reader);
                }

                // 查詢語句
                else if (StrUtil.startWithIgnoreCase(trimLine, "SELECT")) {
                    line = SqlMod.doSelect(line, reader);
                }

                // 函數聲明
                else if (StrUtil.startWithAny(trimLine, "Str")) {
                    line = DeclareMod.doFuncDecl(line);
                }
                // 返回
                else if (trimLine.startsWith("return")) {
                    line = KeywordMod.doReturn(line, false);
                }
                // 關鍵字
                else if (StrUtil.startWithAny(trimLine, "next", "end if", "loop", "commit", "open", "close", "continue", "else")) {
                    line = KeywordMod.doKeyword(line);
                }
                // 新增或更新SQL
                else if (StrUtil.startWithIgnoreCase(trimLine, "UPDATE")
                        || StrUtil.startWithIgnoreCase(trimLine, "INSERT")
                        || StrUtil.startWithIgnoreCase(trimLine, "DELETE")) {
                    line = SqlMod.doUpdate(line, reader);
                }
                // 處理for跟do while
                else if (StrUtil.startWithAny(trimLine, "for", "do while")) {
                    line = line + " {";
                }

                // Pojo屬性賦值
                else if (trimLine.startsWith("dw_")) {
                    line = AssignMod.doPojoPropAssign(line);
                }

                // 加上換行
                result.append(line + "\n");

                writer.write(line + "\n");
                writer.flush();
            }

        } catch (Exception e) {
            e.printStackTrace();
            writer.write("\n\n>>>>>>>>>>> 程式異常結束 <<<<<<<<<<<<<<");
            writer.flush();
            throw new RuntimeException("程式異常結束");
        }


        System.out.println("===============================");
        System.out.println(result);


        writer.close();
    }


}
