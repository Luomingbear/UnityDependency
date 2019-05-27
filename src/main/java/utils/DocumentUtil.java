package utils;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;

/**
 * @author malong
 * @date 2019/5/13
 */
public class DocumentUtil {
    public static String getLineString(Document document, int line) {
        if (line > document.getLineCount()) {
            return "";
        }

        return document.getText(new TextRange(document.getLineStartOffset(line), document.getLineEndOffset(line)));
    }
}
