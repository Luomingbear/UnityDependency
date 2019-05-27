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
 * ����config.gradle��������Ϣ��build.gradle������ͳһ
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
	 * ����config.gradle������
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
		//�����У�ȡ����������
		for (int i = startLine; i <= endLine; i++) {
			mDependecyList.add(getDependencyFromLine(DocumentUtil.getLineString(document, i)));
		}
	}

	/**
	 * ��ȡconfig.gradle��һ���ı���������Ϣ
	 *
	 * @param lineString һ�е��ı�����ʽΪ "com.alibaba:arouter-compiler":"1.1.4",
	 *                   ���� "android:compileSdkVersion":28,
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
			//�ַ����汾��Ϣ
			bean.setVersion(lineString.substring(nextMarkIndex + 1, end));
		} else {
			//˵���汾��Ϣ�����ַ����ģ�����int
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
					//�����������ļ���
					if (child.isDirectory() && !child.getName().startsWith(".")) {
						checkBuildGradle(child);
					}
				}
			}
		}
	}

	/**
	 * �����ʽ��
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
	 * ����ÿһ�������ŵ�����
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
			//������������ڵĴ����ţ���Ҫ��������ͳһ
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
	 * �ж����{}�������Ƿ���ĳһ��{}
	 *
	 * @param fullText
	 * @param partBean
	 * @param key      {}ǰ����ı� ������android{} ������android
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
	 * ͳһ������
	 * ����ÿһ�У�ʶ�������Ͱ汾��Ϣ�������õ�����ͳһ
	 *
	 * @param document
	 */
	private void unityDependency(Document document, PartBean partBean) {
		int startLine = document.getLineNumber(partBean.start);
		int endLine = document.getLineNumber(Math.min(document.getTextLength() - 1, partBean.end));
		for (int i = startLine; i <= endLine; i++) {
			//�����ı�
			int startOffset = document.getLineStartOffset(i);
			int endOffset = document.getLineEndOffset(i);
			String lineString = document.getText(new TextRange(startOffset, endOffset));
			//�������������config�ļ������������
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
	 * �ж��ı��е������Ƿ�������config�İ汾
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
	 * ���°汾��
	 * ʹ��groovy���ñ���Ҫ��˫���ţ�������Ҫǰ����չһ���ַ���Ȼ������޸�
	 *
	 * @param document
	 * @param line
	 * @param dependencyBean
	 */
	private void updateVersion(final Document document, final int line, final String lineString, final int lineStartIndex,
							   final DependencyBean dependencyBean) {
//        try {
//            //�ȴ���һ���ı���Ч
//            Thread.sleep(100);
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        }

		//��ȡ��һ�����ŵ�index
		int startMarkIndexWithOutNotes = lineString.indexOf("\"");
		if (startMarkIndexWithOutNotes <= 0) {
			startMarkIndexWithOutNotes = lineString.indexOf("'");
		}
		//��ȡ���һ�����ŵ�index
		int endMarkIndexWithOutNotes = lineString.lastIndexOf("\"");
		if (endMarkIndexWithOutNotes <= 0) {
			endMarkIndexWithOutNotes = lineString.lastIndexOf("'");
		}
		//��ȡ����ǰ�����һ��ð�ŵ�index
		int colonIndex = lineString.substring(0, endMarkIndexWithOutNotes).lastIndexOf(":");
		//�滻����
		String key = dependencyBean.getGroup() + ":" + dependencyBean.getName();
		String depen = "\"" + key + ":${" + ConfigAction.DEPENDENCY_LIB_NAME_IN_CONFIG + "[\"" + key + "\"]}\"";
		document.replaceString(lineStartIndex + startMarkIndexWithOutNotes, lineStartIndex + endMarkIndexWithOutNotes + 1, depen);
		System.out.println(depen);
	}

	/**
	 * ����config�ļ�,������plugins֮��
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
	 * ͳһAndroid��صİ汾�ţ�����buildToolsVersion��compileSdkVersion��targetSdkVersion��
	 *
	 * @param document
	 * @param partBean
	 */
	private void unityAndroidVersion(Document document, PartBean partBean) {
		int startLine = document.getLineNumber(partBean.start);
		int endLine = document.getLineNumber(partBean.end);
		for (int i = startLine; i <= endLine; i++) {
			//�����ı�
			int startOffset = document.getLineStartOffset(i);
			int endOffset = document.getLineEndOffset(i);
			String lineString = document.getText(new TextRange(startOffset, endOffset));
			//�������������config�ļ������������
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
	 * ����Androidģ��Ļ����汾��Ϣ
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
//        //���ַ��͵İ汾
//        if (dependencyBean.getVersion().getClass().getName().equals("java.lang.String")) {
//            dependencyStr = " \"${" + ConfigAction.DEPENDENCY_LIB_NAME_IN_CONFIG + "[\"" + key + "\"]}\"";
//        }
//        //������int��
//        else {
//            dependencyStr = " " + ConfigAction.DEPENDENCY_LIB_NAME_IN_CONFIG + "[\"" + key + "\"]";
//        }
		dependencyStr = " " + ConfigAction.DEPENDENCY_LIB_NAME_IN_CONFIG + "[\"" + key + "\"]";
		document.replaceString(keyEnd, document.getLineEndOffset(line), dependencyStr);
	}

	/**
	 * ��ȡ�����������ݣ����Դ������еĴ�����
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
	 * ÿһ�����������������
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
