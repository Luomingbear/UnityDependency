package actions;

import bean.DependencyBean;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.impl.source.codeStyle.CodeStyleManagerImpl;
import utils.DocumentUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 根据config.gradle的配置信息将build.gradle的依赖统一
 */
public class UnityAction extends AnAction {
	private Project mProject;
	private List<DependencyBean> mDependecyList;

	@Override
	public void actionPerformed(AnActionEvent e) {
		mProject = e.getProject();
		if (!FileUtil.exists(e.getProject().getBasePath() + File.separator + ConfigAction.CONFIG_FILE_NAME)) {
			Messages.showInfoMessage("Please create config.gradle first", "notice");
			return;
		}

		initData();
		VirtualFile rootDir = e.getProject().getBaseDir();
		checkBuildGradle(rootDir);
		Messages.showInfoMessage("Unity succeed", "dependecy");
	}

	/**
	 * 加载config.gradle的数据
	 */
	private void initData() {
		VirtualFile configFile = LocalFileSystem.getInstance().findFileByPath(mProject.getBasePath() + File.separator + ConfigAction.CONFIG_FILE_NAME);
		if (!configFile.exists()) {
			return;
		}
		Document document = FileDocumentManager.getInstance().getDocument(configFile);
		String text = document.getText();
		String depenKey = "dependencyLibs = [";
		int start = text.indexOf(depenKey);
		int startLine = document.getLineNumber(start) + 1;
		start = document.getLineStartOffset(startLine);
		int end = text.substring(start, document.getTextLength()).indexOf("]");
		int endLine = document.getLineNumber(end);
		mDependecyList = new ArrayList<>();
		//遍历行，取出依赖数据
		for (int i = startLine; i <= endLine; i++) {
			mDependecyList.add(getDependencyFromLine(DocumentUtil.getLineString(document, i)));
		}
	}

	/**
	 * 获取config.gradle的一行文本的依赖信息
	 *
	 * @param lineString 一行的文本，格式为 "com.alibaba:arouter-compiler":"1.1.4",
	 *                   或者 "android:compileSdkVersion":28,
	 */
	private DependencyBean getDependencyFromLine(String lineString) {
		DependencyBean bean = new DependencyBean();
		//groupId
		int start = lineString.indexOf("\"");
		int colonIndex = lineString.indexOf(":");
		bean.setGroup(lineString.substring(start + 1, colonIndex));
		//name
		int nextMarkIndex = lineString.indexOf("\"", colonIndex);
		bean.setName(lineString.substring(colonIndex + 1, nextMarkIndex));
		//version
		nextMarkIndex = lineString.indexOf("\"", nextMarkIndex + 1);
		int end = lineString.lastIndexOf("\"");

		if (nextMarkIndex > 0) {
			//字符串版本信息
			bean.setVersion(lineString.substring(nextMarkIndex + 1, end));
		} else {
			//说明版本信息不是字符串的，而是int
			nextMarkIndex = lineString.indexOf("\"", colonIndex);
			end = lineString.lastIndexOf(",") > 0 ? lineString.length() - 1 : lineString.length();
			bean.setVersion(Integer.parseInt(lineString.substring(nextMarkIndex + 2, end)));
		}
		return bean;
	}

	private void checkBuildGradle(VirtualFile parentFile) {
		VirtualFile[] children = parentFile.getChildren();
		if (children != null) {
			for (int i = 0; i < children.length; i++) {
				VirtualFile child = children[i];
				if ("build.gradle".equals(child.getName())) {
					formatFile(child.getPath());
				} else {
					//便利非隐藏文件夹
					if (child.isDirectory() && !child.getName().startsWith(".")) {
						checkBuildGradle(child);
					}
				}
			}
		}
	}

	/**
	 * 代码格式化
	 *
	 * @param path
	 */
	private void formatFile(String path) {
		WriteCommandAction.runWriteCommandAction(mProject, "", "", new Runnable() {
			@Override
			public void run() {
				VirtualFile child = LocalFileSystem.getInstance().refreshAndFindFileByPath(path);
				PsiFile file = PsiManagerImpl.getInstanceEx(mProject).findFile(child);
				CodeStyleManagerImpl.getInstance(mProject).reformat(file.getOriginalElement());
				scanBuildGradle(path);
			}
		});
	}


	/**
	 * 便利每一个大括号的内容
	 *
	 * @param child
	 */
	private void scanBuildGradle(String path) {
		VirtualFile child = LocalFileSystem.getInstance().refreshAndFindFileByPath(path);
		Document document = FileDocumentManager.getInstance().getDocument(child);
		String fullText = document.getText();
		List<PartBean> extList = extractMessage(fullText);
		boolean applyConfig = false;
		for (int i = 0; i < extList.size(); i++) {
			PartBean partBean = extList.get(i);
			//如果是依赖所在的大括号，需要进行依赖统一
			if (isSomePart(fullText, partBean, "dependencies")) {
				unityDependency(document, partBean);
			} else if (isSomePart(fullText, partBean, "buildscript") && child.getParent().getPath().equals(mProject.getBasePath())) {
				applyConfigFile(document, partBean.start);
				applyConfig = true;
			} else if (isSomePart(fullText, partBean, "android")) {
				unityAndroidVersion(document, partBean);
			} else if (isSomePart(fullText, partBean, "defaultConfig")) {
				unityAndroidVersion(document, partBean);
			}
		}
	}

	/**
	 * 判断这个{}的内容是否是某一个{}
	 *
	 * @param fullText
	 * @param partBean
	 * @param key      {}前面的文本 ，例如android{} 则填入android
	 * @return
	 */
	private boolean isSomePart(String fullText, PartBean partBean, String key) {
		if (partBean.start > key.length()) {
			String temp = fullText.substring(Math.max(partBean.start - key.length() * 2, 0), partBean.start);
			return temp.contains(key);
		}
		return false;
	}

	/**
	 * 统一依赖项
	 * 遍历每一行，识别依赖和版本信息，与配置的依赖统一
	 *
	 * @param document
	 */
	private void unityDependency(Document document, PartBean partBean) {
		int startLine = document.getLineNumber(partBean.start);
		int endLine = document.getLineNumber(Math.min(document.getTextLength() - 1, partBean.end));
		for (int i = startLine; i <= endLine; i++) {
			//单行文本
			int startOffset = document.getLineStartOffset(i);
			int endOffset = document.getLineEndOffset(i);
			String lineString = document.getText(new TextRange(startOffset, endOffset));
			//如果这条依赖在config文件有配置则更新
			for (DependencyBean it : mDependecyList) {
				String key = it.getGroup() + ":" + it.getName();
				if (lineString.contains(key)) {
					int start = lineString.indexOf(key);
					if (lineString.charAt(start + key.length()) == ':') {
						updateVersion(document, i, lineString, startOffset, it);
						break;
					}
				}
			}
		}
	}

	/**
	 * 判断文本中的依赖是否有配置config的版本
	 *
	 * @param lineString
	 * @return
	 */
	private boolean dependecyInConfig(String lineString) {
		for (DependencyBean it : mDependecyList) {
			if (lineString.contains(it.getGroup() + ":" + it.getName())) {
				return true;
			}
		}
		return false;
	}

	/**
	 * 更新版本号
	 * 使用groovy引用必须要用双引号，所以需要前后拓展一个字符，然后进行修改
	 *
	 * @param document
	 * @param line
	 * @param dependencyBean
	 */
	private void updateVersion(final Document document, final int line, final String lineString, final int lineStartIndex,
							   final DependencyBean dependencyBean) {
//        try {
//            //等待上一个改变生效
//            Thread.sleep(100);
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        }

		//获取第一个引号的index
		int startMarkIndexWithOutNotes = lineString.indexOf("\"");
		if (startMarkIndexWithOutNotes <= 0) {
			startMarkIndexWithOutNotes = lineString.indexOf("'");
		}
		//获取最后一个引号的index
		int endMarkIndexWithOutNotes = lineString.lastIndexOf("\"");
		if (endMarkIndexWithOutNotes <= 0) {
			endMarkIndexWithOutNotes = lineString.lastIndexOf("'");
		}
		//获取引号前的最后一个冒号的index
		int colonIndex = lineString.substring(0, endMarkIndexWithOutNotes).lastIndexOf(":");
		//替换依赖
		String key = dependencyBean.getGroup() + ":" + dependencyBean.getName();
		String depen = "\"" + key + ":${" + ConfigAction.DEPENDENCY_LIB_NAME_IN_CONFIG + "[\"" + key + "\"]}\"";
		document.replaceString(lineStartIndex + startMarkIndexWithOutNotes, lineStartIndex + endMarkIndexWithOutNotes + 1, depen);
		System.out.println(depen);
	}

	/**
	 * 引用config文件,必须在plugins之后
	 *
	 * @param document
	 * @param offst    offst
	 */
	private void applyConfigFile(final Document document, final int offst) {
		WriteCommandAction.runWriteCommandAction(mProject, "applyConfigFile", "unityDependency",
				new Runnable() {
					@Override
					public void run() {
						String apply = "apply from: \"" + ConfigAction.CONFIG_FILE_NAME + "\"";
						if (!document.getText().contains(apply)) {
							int endLine = document.getLineNumber(offst) + 1;
							document.insertString(document.getLineStartOffset(endLine), "\t" + apply + "\n");
						}
					}
				}, PsiDocumentManager.getInstance(mProject).getPsiFile(document));
	}

	/**
	 * 统一Android相关的版本号，例如buildToolsVersion，compileSdkVersion，targetSdkVersion等
	 *
	 * @param document
	 * @param partBean
	 */
	private void unityAndroidVersion(Document document, PartBean partBean) {
		int startLine = document.getLineNumber(partBean.start);
		int endLine = document.getLineNumber(partBean.end);
		for (int i = startLine; i <= endLine; i++) {
			//单行文本
			int startOffset = document.getLineStartOffset(i);
			int endOffset = document.getLineEndOffset(i);
			String lineString = document.getText(new TextRange(startOffset, endOffset));
			//如果这条依赖在config文件有配置则更新
			for (DependencyBean it : mDependecyList) {
				if ("android".equals(it.getGroup()) && lineString.contains(it.getName())) {
					int start = lineString.indexOf(it.getName());
					if (lineString.charAt(start + it.getName().length()) == ' ') {
						updateAndroidVersion(document, i, lineString, startOffset, it);
						break;
					}
				}
			}
		}
	}

	/**
	 * 更新Android模块的基础版本信息
	 *
	 * @param document
	 * @param i
	 * @param lineString
	 * @param startOffset
	 * @param it
	 */
	private void updateAndroidVersion(Document document, int line, String lineString, int startOffset, DependencyBean dependencyBean) {
		int keyStart = lineString.indexOf(dependencyBean.getName());
		int keyEnd = startOffset + keyStart + dependencyBean.getName().length();
		String key = dependencyBean.getGroup() + ":" + dependencyBean.getName();
		String dependencyStr = "";
//        //是字符型的版本
//        if (dependencyBean.getVersion().getClass().getName().equals("java.lang.String")) {
//            dependencyStr = " \"${" + ConfigAction.DEPENDENCY_LIB_NAME_IN_CONFIG + "[\"" + key + "\"]}\"";
//        }
//        //否则是int型
//        else {
//            dependencyStr = " " + ConfigAction.DEPENDENCY_LIB_NAME_IN_CONFIG + "[\"" + key + "\"]";
//        }
		dependencyStr = " " + ConfigAction.DEPENDENCY_LIB_NAME_IN_CONFIG + "[\"" + key + "\"]";
		document.replaceString(keyEnd, document.getLineEndOffset(line), dependencyStr);
	}

	/**
	 * 提取大括号中内容，忽略大括号中的大括号
	 *
	 * @param msg
	 * @return
	 */
	public List<PartBean> extractMessage(String msg) {
		List<PartBean> partBeanList = new ArrayList<>();
		int start = 0;
		int startFlag = 0;
		int endFlag = 0;
		for (int i = 0; i < msg.length(); i++) {
			if (msg.charAt(i) == '{') {
				startFlag++;
				if (startFlag == endFlag + 1) {
					start = i;
				}
			} else if (msg.charAt(i) == '}') {
				endFlag++;
				if (endFlag == startFlag) {
					partBeanList.add(new PartBean(msg.substring(start + 1, i), start + 1, i));
				}
			}
		}
		return partBeanList;
	}

	/**
	 * 每一个大括号里面的内容
	 */
	private class PartBean {
		private String text;
		private int start;
		private int end;

		public PartBean(String text, int start, int end) {
			this.text = text;
			this.start = start;
			this.end = end;
		}
	}

}
