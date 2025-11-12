package client;

import javax.swing.*;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

public class ModeSelectFrame extends JFrame {
    private final String playerName;
    private final Socket socket;
    private final ObjectInputStream in;
    private final ObjectOutputStream out;

    public ModeSelectFrame(String playerName, Socket socket, ObjectInputStream in, ObjectOutputStream out) {
        this.playerName = playerName;
        this.socket = socket;
        this.in = in;
        this.out = out;

        setTitle("모드 선택 - " + playerName);
        setSize(350, 200);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new java.awt.GridLayout(3, 1, 10, 10));

        JButton coop = new JButton("🤝 협동 모드");
        JButton versus = new JButton("⚔️ 경쟁 모드");
        JButton back = new JButton("← 돌아가기");

        add(coop);
        add(versus);
        add(back);

        coop.addActionListener(e -> {
            new HiddenObjectClientGUI(socket, in, out, playerName, "쉬움", "협동");
            dispose();
        });

        versus.addActionListener(e -> {
            new HiddenObjectClientGUI(socket, in, out, playerName, "쉬움", "경쟁");
            dispose();
        });

        back.addActionListener(e -> {
            new MainMenuFrame(playerName, socket, in, out);
            dispose();
        });

        setVisible(true);
    }
}
