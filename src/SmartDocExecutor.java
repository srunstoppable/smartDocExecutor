import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.power.doc.model.ApiConfig;
import com.power.doc.model.SourceCodePath;
import org.apache.commons.lang.StringUtils;
import org.beetl.ext.fn.TypeNameFunction;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;

/**
 * @Description
 * @Author Shen Rui
 * @Date2022/9/2 14:46
 **/
public class SmartDocExecutor extends AnAction {

    static String resourcePath = "/resources";

    @Override
    public void actionPerformed(AnActionEvent e) {

        VirtualFile virtualFile = e.getRequiredData(CommonDataKeys.VIRTUAL_FILE);
        isValid(virtualFile.getPath());
        String outOfProjectPath = virtualFile.getPath().substring(
                virtualFile.getPath().indexOf(e.getProject().getBasePath() + "/") + e.getProject().getBasePath().length() + 1);
        String serverName = outOfProjectPath.substring(0, outOfProjectPath.indexOf("/"));
        String name = virtualFile.getName();
        File doc = new File(e.getProject().getBasePath() + "/" + serverName + "/doc/" + name.replace(".java", "Api.md"));
        //删除旧文件
        if (doc.exists()) {
            doc.delete();
        }
        // 生成
        buildWithFilePath(virtualFile.getPath(), e.getProject().getBasePath());
        long now = System.currentTimeMillis();
        while (!doc.exists()) {
            doc = new File(e.getProject().getBasePath() + "/" + serverName + "/doc/" + name.replace(".java", "Api.md"));
            if (System.currentTimeMillis() - now > 5000) {
                return;
            }
        }
        VirtualFile virtualFile1 = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(doc);
        FileEditorManager manager = FileEditorManager.getInstance(e.getProject());
        manager.openFile(virtualFile1, true);
    }

    public static void buildWithFilePath(String filePath, String projectPath) {
        ApiConfig config = buildApiConfig(filePath, projectPath);
        Thread.currentThread().setContextClassLoader(TypeNameFunction.class.getClassLoader());
        ApiBuilderRevert.buildApiDoc(config);

    }


    public static ApiConfig buildApiConfig(String filePath, String projectPath) {
        ApiConfig config = new ApiConfig();
        config.setServerUrl("http://{{api_host_port}}");
        String classPath = getClassPath(filePath);
        String outPath = getOutPath(filePath);
        config.setOutPath(outPath);
        config.setSourceCodePaths(populateSourceCodeDependencyPath(filePath, projectPath));
        config.setCoverOld(true);
        config.setPackageFilters(classPath);
        config.setResponseExample(true);
        config.setInlineEnum(true);
        return config;
    }

    /**
     * 设置文档输出路径
     *
     * @param source
     * @return
     */
    private static String getOutPath(String source) {
        String str1 = source.substring(0, source.indexOf("/src"));
        return str1 + "/doc";
    }

    private static void isValid(String filePath) {
        if (!filePath.endsWith(".java")) {
            Messages.showMessageDialog("请选中一个正确的Java文件", "Warining", Messages.getInformationIcon());
            throw new RuntimeException("请选中一个正确的Java文件");
        }
    }

    /**
     * 获取类路径 com.*.*格式
     *
     * @param filePath
     * @return
     */
    private static String getClassPath(String filePath) {
        return filePath.substring(filePath.indexOf("com"), filePath.indexOf(".java")).replace("/", ".");
    }

    /**
     * 获取服务路径
     *
     * @param filePath
     * @return
     */
    private static String getServerPath(String filePath) {
        return filePath.substring(0, filePath.indexOf("/src"));
    }

    /**
     * 构造目标文件源地址
     *
     * @param filePath
     * @param projectPath
     * @return
     */
    public static SourceCodePath[] populateSourceCodeDependencyPath(String filePath, String projectPath) {

        String path = filePath.substring(0, filePath.indexOf("/java")) + resourcePath;
        File file = new File(path);
        List<String> serverNames = new ArrayList<>();
        List<SourceCodePath> sourceCodePaths = new ArrayList<>();
        sourceCodePaths.add(SourceCodePath.builder().setPath(getServerPath(filePath)));
        if (file.exists() && file.isDirectory()) {

            ThreadPoolExecutor poolExecutor = new ThreadPoolExecutor(
                    file.listFiles().length, file.listFiles().length, 10, TimeUnit.SECONDS,
                    new LinkedBlockingDeque<Runnable>());
            List<Future<List<String>>> futureList = new ArrayList<>();
            for (File child : file.listFiles()) {
                if (child.getName().contains("application") && child.getName().endsWith(".yml")) {
                    futureList.add(poolExecutor.submit(new PopulateDocCallable(child)));
                }
            }
            for (Future<List<String>> future : futureList) {
                try {
                    if (null != future.get()) {
                        serverNames.addAll(future.get());
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } catch (ExecutionException e) {
                    e.printStackTrace();
                }
            }
            if (serverNames == null || serverNames.size() == 0) {
                sourceCodePaths.add(SourceCodePath.builder().setPath(projectPath + "/common-component"));
            } else {
                for (String name : serverNames) {
                    sourceCodePaths.add(SourceCodePath.builder().setPath(projectPath + "/" + name));
                }

            }
        }
        return sourceCodePaths.toArray(new SourceCodePath[sourceCodePaths.size()]);
    }

}

/**
 * 获取文件配置项线程类
 */
class PopulateDocCallable implements Callable<List<String>> {
    File file;

    public PopulateDocCallable(File file) {
        this.file = file;
    }

    @Override
    public List<String> call() throws Exception {

        BufferedReader bufferedReader;
        List<String> serverNames = null;
        try {
            bufferedReader = new BufferedReader(new FileReader(file));
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                if (line.contains("smartDocDependencyName")) {
                    String str = line.substring(line.indexOf(":") + 1).trim();
                    if (StringUtils.isNotBlank(str)) {
                        serverNames = Arrays.asList(str.split(","));
                    }
                }
            }
            bufferedReader.close();

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        return serverNames;
    }
}