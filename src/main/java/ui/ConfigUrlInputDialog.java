package ui;

import com.intellij.ui.ScreenUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class ConfigUrlInputDialog extends JDialog {
    private JPanel contentPane;
    private JButton buttonOK;
    private JButton buttonCancel;
    private JTextField textFildUrl;
    private JTextArea textArea1;
    private JLabel jLabelHint;
    private OnUrlInputListener onUrlInputListener;

    public ConfigUrlInputDialog() {
        setContentPane(contentPane);
        setModal(true);
        Rectangle rectangle = ScreenUtil.getMainScreenBounds();
        setBounds(rectangle.width / 3, rectangle.height / 3, rectangle.width / 3, rectangle.height / 3);
        textArea1.setText("请输入在线的配置json文件地址或是电脑本地地址，json格式如下:\n" +
                "{\n" +
                "  \"libs\": [\n" +
                "    {\n" +
                "      \"group\": \"android\",\n" +
                "      \"name\": \"buildToolsVersion\",\n" +
                "      \"version\": \"27.0.3\"\n" +
                "    }" +
                "    ]\n" +
                "}"
        );
        getRootPane().setDefaultButton(buttonOK);

        buttonOK.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onOK();
            }
        });

        buttonCancel.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onCancel();
            }
        });

        // call onCancel() when cross is clicked
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                onCancel();
            }
        });

        // call onCancel() on ESCAPE
        contentPane.registerKeyboardAction(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onCancel();
            }
        }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
    }

    public void setOnUrlInputListener(OnUrlInputListener onUrlInputListener) {
        this.onUrlInputListener = onUrlInputListener;
    }

    private void onOK() {
        dispose();
        if (onUrlInputListener != null) {
            onUrlInputListener.input(textFildUrl.getText().trim());
        }
    }

    private void onCancel() {
        dispose();
        if (onUrlInputListener != null) {
            onUrlInputListener.input("");
        }
    }

    public static void main(String[] args) {
        ConfigUrlInputDialog dialog = new ConfigUrlInputDialog();
        dialog.pack();
        dialog.setVisible(true);
        System.exit(0);
    }

    public interface OnUrlInputListener {
        void input(String path);
    }
}
