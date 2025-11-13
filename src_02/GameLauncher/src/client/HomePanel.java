package client;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

// 홈 패널 (닉네임, IP, 포트 입력)
public class HomePanel extends JPanel {
    private GameLauncher launcher; 
    private JTextField ipField;
    private JTextField portField;
    private JTextField nameField; 
    private JButton connectButton;
    private JLabel statusLabel;

    public HomePanel(GameLauncher launcher) {
        this.launcher = launcher;
        setLayout(new BorderLayout(10, 10));
        setBorder(new EmptyBorder(20, 20, 20, 20));
        setSize(300, 400);

        JPanel centerPanel = new JPanel(new GridLayout(4, 2, 5, 5)); 
        centerPanel.add(new JLabel("닉네임:"));
        nameField = new JTextField("Player" + (int)(Math.random()*100)); 
        nameField.setSize(150, 100);
        centerPanel.add(nameField);

        centerPanel.add(new JLabel("서버 IP:"));
        ipField = new JTextField("127.0.0.1");  
        ipField.setSize(150, 100);
        centerPanel.add(ipField);

        centerPanel.add(new JLabel("포트 번호:"));
        portField = new JTextField("9999"); 
        portField.setSize(150, 100); 
        centerPanel.add(portField);
        
        add(centerPanel, BorderLayout.CENTER);
        
        statusLabel = new JLabel("서버 정보를 입력하고 접속하세요."); // JLabel 객체 생성 및 초기화
        statusLabel.setHorizontalAlignment(SwingConstants.CENTER); // 가운데 정렬 설정
        add(statusLabel, BorderLayout.NORTH);
        
        connectButton = new JButton("입력 완료"); 
        connectButton.setSize(150, 100);
        add(connectButton, BorderLayout.SOUTH);

        connectButton.addActionListener(e -> connectToServer());
        nameField.addActionListener(e -> connectToServer()); 
    }
    
    public void resetUI() {
        connectButton.setEnabled(true);
        connectButton.setText("대기방 접속");
        statusLabel.setText("서버 연결이 끊겼습니다. 다시 시도하세요.");
        statusLabel.setForeground(Color.RED);
    }

    private void connectToServer() {
        String host = ipField.getText().trim();
        String name = nameField.getText().trim();
        int port;

        if (name.isEmpty()) {
            statusLabel.setText("오류: 닉네임을 입력하세요.");
            statusLabel.setForeground(Color.RED);
            return;
        }
        try {
            port = Integer.parseInt(portField.getText().trim());
        } catch (NumberFormatException ex) {
            statusLabel.setText("오류: 포트 번호는 숫자여야 합니다.");
            statusLabel.setForeground(Color.RED);
            return;
        }

        connectButton.setEnabled(false);
        connectButton.setText("접속 중...");
        statusLabel.setText(host + ":" + port + "에 연결 중...");
        statusLabel.setForeground(Color.BLACK);

        SwingWorker<Socket, Void> worker = new SwingWorker<Socket, Void>() {
            @Override
            protected Socket doInBackground() throws Exception {
                return new Socket(host, port);
            }

            @Override
            protected void done() {
                try {
                    Socket socket = get(); 
                    ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                    ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
                    
                    launcher.switchToLobby(socket, out, in, name);

                } catch (Exception ex) {
                    statusLabel.setText("오류: 서버 연결 실패.");
                    statusLabel.setForeground(Color.RED);
                    resetUI();
                }
            }
        };
        worker.execute(); 
    }
}