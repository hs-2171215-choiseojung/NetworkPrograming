package client;

import model.GamePacket;
import javax.swing.*;
import java.io.*;
import java.net.Socket;

public class StartMenuFrame extends JFrame {
    public StartMenuFrame() {
        setTitle("숨은그림찾기 - 로그인");
        setSize(400, 250);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new java.awt.GridLayout(3, 1, 10, 10));

        JLabel title = new JLabel("🔍 숨은 그림 찾기", SwingConstants.CENTER);
        title.setFont(new java.awt.Font("맑은 고딕", java.awt.Font.BOLD, 24));
        JTextField nameField = new JTextField();
        nameField.setHorizontalAlignment(JTextField.CENTER);
        nameField.setBorder(BorderFactory.createTitledBorder("닉네임 입력"));
        JButton enter = new JButton("입장");

        add(title);
        add(nameField);
        add(enter);

        enter.addActionListener(e -> {
            String name = nameField.getText().trim();
            if (name.isEmpty()) {
                JOptionPane.showMessageDialog(this, "닉네임을 입력하세요!");
                return;
            }

            try {
                // 서버 연결 (유지)
                Socket socket = new Socket("127.0.0.1", 9999);
                ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                ObjectInputStream in = new ObjectInputStream(socket.getInputStream());

                // JOIN 패킷 전송
                out.writeObject(new GamePacket(GamePacket.Type.JOIN, name, "쉬움", "1인", true));
                out.flush();

              
                Object obj = in.readObject();
                if (obj instanceof GamePacket p) {
                    if (p.getMessage().contains("[중복]")) {
                        JOptionPane.showMessageDialog(this, "⚠️ 닉네임이 이미 사용 중입니다!");
                        socket.close();
                        return;
                    } else if (p.getMessage().contains("[확인]")) {
                        // 닉네임 사용 가능 → 메인 메뉴로 이동 (소켓 유지)
                        new MainMenuFrame(name, socket, in, out);
                        dispose();
                        return;
                    }
                }

                JOptionPane.showMessageDialog(this, "서버 응답이 올바르지 않습니다.");
                socket.close();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "서버 연결 실패: " + ex.getMessage());
            }
        });

        setVisible(true);
    }

    public static void main(String[] args) {
        new StartMenuFrame();
    }
}
