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

        BufferedReader reader = new BufferedReader(new InputStreamReader( new FileInputStream(Main.class.getClassLoader().getResource("source.txt").getPath())));
        BufferedWriter writer = FileUtil.getWriter(Main.class.getClassLoader().getResource("result.txt").getPath(), "UTF-8", false);

        MainMod.doMain(reader, writer, false);
    }




}
