import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.StrUtil;
import org.apache.commons.lang3.StringUtils;

import java.io.*;
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
            String line = StrUtil.trim(reader.readLine());

            if (line == null) break;

            // 命名轉換
            line = StrUtil.replace(line, "string", "String");

            if (StrUtil.startWith(line, "ls_")) {
                List<String> blocks = StrUtil.splitTrim(line, "=");

                // trim 關鍵字轉換  ls_cargo_location  = trim(dw_criteria_1.cargo_location) → ls_cargo_location = StringUtils.trim(dw_criteria_1.getCargoLocation());
                if (StrUtil.isWrap(blocks.get(1), "trim(", ")")) {
                    String content = StrUtil.unWrap(blocks.get(1), "trim(", ")");
                    content = content.split("\\.")[0] + "." + StrUtil.genGetter(StrUtil.toCamelCase(content.split("\\.")[1])) + "()";
                    line = blocks.get(0) + " = StringUtils.trim(" + content + ")";
                }

            }

            // 加上句號
            if (!"".equals(line)) result.append(line + ";");
            result.append("\n");
        }



        System.out.println("===============================");
        System.out.println(result);
    }
}
