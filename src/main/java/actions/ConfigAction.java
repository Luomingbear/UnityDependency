package actions;

import bean.ConfigLocalBean;
import bean.DependencyBean;
import com.google.gson.Gson;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiManager;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import ui.ConfigUrlInputDialog;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class ConfigAction extends AnAction {
    public static final String DEPENDENCY_LIB_NAME_IN_CONFIG = "dependencyLibs";
    public static String CONFIG_FILE_NAME = "dependencyConfig.gradle";
    private String configFileUrl = "https://file.2fun.xyz/intellij_plugin_unity_dependency_config.json";
    private String mRootPath = "";
    private Project mProject;
    private List<DependencyBean> mDependecyList;

    @Override
    public void actionPerformed(AnActionEvent e) {
        mProject = e.getProject();
        mRootPath = mProject.getBasePath();
        int result = Messages.showOkCancelDialog("是否使用自定义的json配置文件进行依赖统一？如果点击【取消】，则使用内置的依赖配置文件", "选择配置文件地址", null);
        if (result == Messages.OK) {
            ConfigUrlInputDialog dialog = new ConfigUrlInputDialog();
            dialog.setOnUrlInputListener(new ConfigUrlInputDialog.OnUrlInputListener() {
                @Override
                public void input(String path) {
                    if (StringUtils.isNotBlank(path)) {
                        configFileUrl = path;
                        if (path.startsWith("http")) {
                            initNetData();
                        } else {
                            initFileData();
                        }
                    }
                    createConfigFile(mDependecyList);
                }
            });
            dialog.setVisible(true);
        } else {
            initNetData();
            createConfigFile(mDependecyList);
        }
    }

    /**
     * 从云端获取json配置文件
     * 如果失败了就从本地获取
     */
    private void initNetData() {
        if (!getConfigDataFromNet()) {
            getConfigDataFromInner();
        }
    }

    /**
     * 从云端获取json配置文件
     */
    private boolean getConfigDataFromNet() {
        Request request = new Request.Builder()
                .url(configFileUrl)
                .build();
        OkHttpClient client = new OkHttpClient();
        try {
            Response response = client.newCall(request).execute();
            StringWriter writer = new StringWriter();
            IOUtils.copy(response.body().byteStream(), writer, StandardCharsets.UTF_8.name());
            Gson gson = new Gson();
            ConfigLocalBean localBean = gson.fromJson(writer.toString(), ConfigLocalBean.class);
            mDependecyList = localBean.getLibs();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return CollectionUtils.isNotEmpty(mDependecyList);
    }

    /**
     * 从内置json文件获取配置信息
     */
    private void getConfigDataFromInner() {
        try {
            StringWriter writer = new StringWriter();
            IOUtils.copy(getClass().getClassLoader().getResourceAsStream("config/dependency_config.json"),
                    writer, StandardCharsets.UTF_8.name());
            Gson gson = new Gson();
            ConfigLocalBean localBean = gson.fromJson(writer.toString(), ConfigLocalBean.class);
            mDependecyList = localBean.getLibs();
        } catch (IOException ex) {
            ex.printStackTrace();
            mDependecyList = new ArrayList<>();
        }
    }

    /**
     * 从本地获取json文件
     */
    private void initFileData() {
        try {
            StringWriter writer = new StringWriter();
            InputStream is = FileUtils.openInputStream(new File(configFileUrl));
            IOUtils.copy(is, writer, StandardCharsets.UTF_8.name());
            Gson gson = new Gson();
            ConfigLocalBean localBean = gson.fromJson(writer.toString(), ConfigLocalBean.class);
            mDependecyList = localBean.getLibs();
        } catch (IOException e) {
            e.printStackTrace();
            mDependecyList = new ArrayList<>();
        }
    }


    /**
     * 创建/更新 config.gradle文件
     *
     * @param dependencies
     */
    private void createConfigFile(final List<DependencyBean> dependencies) {
        VirtualFile rootDir = LocalFileSystem.getInstance().findFileByPath(mRootPath);
        VirtualFile configGradleFile = LocalFileSystem.getInstance().findFileByPath(rootDir.getPath() + File.separator + CONFIG_FILE_NAME);
        //没有找到这个文件就创建
        if (configGradleFile == null) {
            try {
                configGradleFile = LocalFileSystem.getInstance().createChildFile(mProject, rootDir, CONFIG_FILE_NAME);
                configGradleFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(mRootPath + File.separator + CONFIG_FILE_NAME);
            } catch (Exception ex) {
                ex.printStackTrace();
                Messages.showInfoMessage("create failed", "dependency");
            }
        }

        final Document document = FileDocumentManager.getInstance().getDocument(configGradleFile);
        // 编辑文件
        WriteCommandAction.runWriteCommandAction(mProject, "config", "dependency",
                new Runnable() {
                    @Override
                    public void run() {
                        editContent(document, dependencies);
                    }
                }, PsiManager.getInstance(mProject).findFile(configGradleFile));
    }

    /**
     * 编辑内容
     *
     * @param document
     * @param dependencies
     */
    public void editContent(Document document, List<DependencyBean> dependencies) {
        //先清空内容
        document.deleteString(0, document.getTextLength());
        //插入数据
        document.insertString(0, "ext {\n" +
                "\t" + DEPENDENCY_LIB_NAME_IN_CONFIG + " = [\n");

        for (int i = 0; i < dependencies.size(); i++) {
            DependencyBean item = dependencies.get(i);
            insertOneDependecyConfig(document, item.getGroup(), item.getName(), item.getVersion(), i < dependencies.size() - 1);
        }

        document.insertString(document.getTextLength(), "\t]" +
                "\n}");

        Messages.showInfoMessage("create succeed", "dependency");
    }

    /**
     * 创建一个依赖管理配置
     *
     * @param name
     * @param version
     */
    private void insertOneDependecyConfig(Document document, String group, String name, Object version, boolean hasNext) {
        String content = "";
        if (version.getClass().getName().equals("java.lang.String")) {
            content = "\t\t\t\"" + group + ":" + name + "\":\"" + version + "\"";
        } else {
            //转化为int
            content = "\t\t\t\"" + group + ":" + name + "\":" + ((Double) version).intValue();
        }
        if (hasNext) {
            document.insertString(document.getLineEndOffset(document.getLineCount() - 1), content + ",\n");
        } else {
            document.insertString(document.getLineEndOffset(document.getLineCount() - 1), content + "\n");
        }
    }
}
