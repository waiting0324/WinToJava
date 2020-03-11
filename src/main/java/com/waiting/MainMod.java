package com.waiting;

import cn.hutool.core.util.StrUtil;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * @author 6550
 * @date 2020/2/26 上午 11:31
 * @description
 */
public class MainMod {

    /**
     * 主要運行方法
     * @param reader 要解析文檔的Reader
     * @param writer 要輸出結果的Writer
     * @param isApiDoc 現在處理的是否是API文件
     * @throws IOException
     */
    public static void doMain(BufferedReader reader, BufferedWriter writer, boolean isApiDoc) throws IOException {

        StringBuilder result = new StringBuilder();
        // 用Declare關鍵字聲明的SQL語句(不會馬上使用)
        Map<String, String> declareSql = new HashMap<>();

        try {
            while (true) {

                String line = reader.readLine();
                String trimLine = StrUtil.trimToEmpty(line);

                // 處理API文件時，碰到return關鍵字直接結束
                if (isApiDoc && line.contains("return")) return;

                // 到最後或是註解則略過
                if (line == null) {
                    break;
                }
                else if (trimLine.startsWith("//") || trimLine.startsWith("*")) {
                    //continue;
                }
                // API 呼叫
                else if (StrUtil.containsIgnoreCase(line, "call API")) {

                    // 解決Call API 在多行才聲明完問題
                    if (!line.contains(")")) {
                        while ((line = line + reader.readLine()).contains(")")) break;
                    }

                    line = ApiMod.doApi(line, writer);
                }
                // Winform用Declare聲明SQL語句
                else if (StrUtil.startWithIgnoreCase(trimLine, "DECLARE")) {
                    line = WinFormFunMod.doDeclare(line, reader, declareSql);
                }
                // Winform 開始使用之前聲明的SQL語句
                else if (StrUtil.startWithIgnoreCase(trimLine, "OPEN")) {
                    line = WinFormFunMod.doOpen(trimLine, reader, declareSql);
                }
                // 如果是  聲明List、json開頭、開頭中文  則變成注釋
                else if ((Pattern.compile( "^[\u4e00-\u9fa5]" ).matcher(trimLine).find() && !trimLine.contains("mes")
                            && !trimLine.contains("//"))) {
                    line =  "// " + line.trim();
                }

                // 變量聲明
                else if (StrUtil.startWithAny(trimLine, "string", "integer","int", "long", "datetime", "decimal")) {
                    line = DeclareMod.doVariDecl(line);
                }

                // 訊息框
                else if (StrUtil.containsIgnoreCase(trimLine, "messagebox")) {
                    line = KeywordMod.doMessagebox(line, reader);
                }

                // 特殊運算符
                else if (StrUtil.containsAny(trimLine, "+=", "++", "-=", "--")) {
                    line = AssignMod.doSpecialOperator(line);
                }

                // 參數賦值
                else if (StrUtil.startWithAny(trimLine, "ls_", "li_", "ll_", "ld_", "arg_", "ttl_")) {
                    line = AssignMod.doAsignParam(line);
                }

                // 條件語句
                else if (StrUtil.startWithIgnoreCase(trimLine, "if")) {
                    line = IfMod.doIf(line, reader);
                }

                // 查詢語句
                else if (StrUtil.startWithIgnoreCase(trimLine, "SELECT")) {
                    String oriSql = SqlMod.getOriSql(line, reader);
                    line = SqlMod.doSelect(oriSql);
                }

                // 函數聲明
                else if (StrUtil.startWithAny(trimLine, "event")
                        || StrUtil.startWithIgnoreCase(trimLine, "global function")) {
                    line = DeclareMod.doFuncDecl(line);
                }
                // 返回
                else if (trimLine.startsWith("return")) {
                    line = KeywordMod.doReturn(line);
                }
                // 關鍵字
                else if (StrUtil.startWithAny(trimLine.toLowerCase(), "next", "end if", "loop", "commit", "open", "close",
                        "continue", "else", "Else", "rollback")) {
                    line = KeywordMod.doKeyword(line);
                }
                // 新增或更新SQL
                else if (StrUtil.startWithIgnoreCase(trimLine, "UPDATE")
                        || StrUtil.startWithIgnoreCase(trimLine, "INSERT")
                        || StrUtil.startWithIgnoreCase(trimLine, "DELETE")) {
                    line = SqlMod.doUpdate(line, reader);
                }
                // 處理for跟do while
                else if (StrUtil.startWithAny(trimLine, "do while")) {
                    line = line + " {";
                }

                // Pojo屬性賦值
                else if (trimLine.startsWith("dw_")) {
                    line = AssignMod.doPojoPropAssign(line);
                }

                // switch處理
                else if (StrUtil.startWithAny(trimLine, "choose", "case", "end choose")) {
                    line = SwitchMod.doSwitch(line);
                }

                // Winform內置 dw_master情況
                else if (trimLine.startsWith("setitem")) {
                    line = WinFormFunMod.doSetitem(line);
                }
                // Winform的triggerevent函數
                else if (trimLine.startsWith("triggerevent")) {
                    line = WinFormFunMod.doTriggerevent(line);
                }
                // Winform的end event
                else if (trimLine.startsWith("end event")) {
                    line = WinFormFunMod.doEndEvent(line);
                }
                // For循環處理
                else if (trimLine.startsWith("for")) {
                    line = ForMod.doFor(line);
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
