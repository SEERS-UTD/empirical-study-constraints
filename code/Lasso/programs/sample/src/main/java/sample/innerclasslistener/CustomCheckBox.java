package sample.innerclasslistener;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class CustomCheckBox extends JCheckBox {
    private boolean booleanProperty;

    public static void main(String[] args) {
        JFrame frame = new JFrame();

        frame.add(new CustomCheckBox(true));
    }

    public CustomCheckBox(boolean booleanProperty) {
        this.booleanProperty = booleanProperty;

        addActionListener(new CustomActionListener());
    }

    class CustomActionListener implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            if (booleanProperty) {
                System.out.println("is true");
            }
        }
    }
}
