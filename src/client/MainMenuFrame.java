package client;

import model.GamePacket;
import javax.swing.*;
import java.io.*;
import java.net.Socket;

public class MainMenuFrame extends JFrame {
    private final String playerName;
    private final Socket socket;
    private final ObjectInputStream in;
    private final ObjectOutputStream out;

    public MainMenuFrame(String playerName, Socket socket, ObjectInputStream in, ObjectOutputStream out) {
        this.playerName = playerName;
        this.socket = socket;
        this.in = in;
        this.out = out;

        setTitle("메인 메뉴 - " + playerName);
        setSize(400, 300);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new java.awt.GridLayout(4, 1, 10, 10));

        JLabel title = new JLabel("🌟 메인 메뉴 🌟", SwingConstants.CENTER);
        title.setFont(new java.awt.Font("맑은 고딕", java.awt.Font.BOLD, 22));

        JButton solo = new JButton("🎮 1인 플레이");
        JButton multi = new JButton("👥 멀티 플레이");
        JButton mypage = new JButton("💾 마이페이지");

        add(title); add(solo); add(multi); add(mypage);

        solo.addActionListener(e -> {
            new HiddenObjectClientGUI(socket, in, out, playerName, "쉬움", "1인");
            dispose();
        });

        multi.addActionListener(e -> {
            new ModeSelectFrame(playerName, socket, in, out);
            dispose();
        });

        mypage.addActionListener(e -> openMyPageOnTempSocket());
        setVisible(true);
    }

    private void openMyPageOnTempSocket() {
        try (Socket s = new Socket("127.0.0.1", 9999)) {
            ObjectOutputStream o = new ObjectOutputStream(s.getOutputStream());
            ObjectInputStream  i = new ObjectInputStream(s.getInputStream());
            o.writeObject(new GamePacket(GamePacket.Type.MYPAGE_REQUEST, playerName, ""));
            o.flush();

            Object obj = i.readObject();
            if (obj instanceof GamePacket p && p.getType() == GamePacket.Type.MYPAGE_DATA) {
                JOptionPane.showMessageDialog(this,
                        "🎯 마이페이지\n\n닉네임: " + playerName +
                        "\n경험치: " + p.getExp() +
                        "\n레벨: " + p.getLevel(),
                        "My Page", JOptionPane.INFORMATION_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(this, "서버 응답이 올바르지 않습니다.");
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "마이페이지 요청 실패: " + ex.getMessage());
        }
    }
}
