package com.waiting;

import cn.hutool.core.util.StrUtil;
import com.sun.tools.javac.Main;

import java.io.*;

/**
 * @author 6550
 * @date 2020/2/25 下午 05:30
 * @description
 */
public class ApiMod {

    public static String doApi(String line, BufferedWriter resultWritter) throws Exception {

        String apiName = StrUtil.subBetween(line, "API", "(").trim();
        StringBuilder apiFunc = new StringBuilder();
        StringBuilder result = new StringBuilder();
        // 是否在API文件中找到API
        boolean isApiFind = false;

        System.out.println(apiName);

        InputStream is = new FileInputStream(ApiMod.class.getClassLoader().getResource("api.txt").getPath());
        BufferedReader apiReader = new BufferedReader(new InputStreamReader(is));

        // 找出在API文件中該API的位置
        while (true) {

            String apiLine = apiReader.readLine();
            if (apiLine == null) break;

            // 找到方法定義位置
            if (StrUtil.containsIgnoreCase(apiLine, apiName)
                    && StrUtil.subAfter(apiLine, apiName, false).trim().startsWith("(")) {

                isApiFind = true;

                // 轉譯API內容
                /*result.append("// >>>>>>>> API " + apiName + " <<<<<<<<< 調用開始\n");
                String oriSql = SqlMod.getOriSql(apiReader.readLine(), apiReader);
                result.append(SqlMod.doSelect(oriSql));*/
                MainMod.doMain(apiReader, resultWritter, true);
                break;
            }
        }

        if (isApiFind) result.append("// >>>>>>>> API " + apiName + " <<<<<<<<< 調用結束\n");
        else if (!isApiFind) result.append("// TODO 未發現 API " + apiName + " 等待手動新增 \n");

        return result.toString();
    }
}
