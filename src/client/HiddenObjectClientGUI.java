package client;

import model.GamePacket;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Ellipse2D;
import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class HiddenObjectClientGUI extends JFrame {

    private final Socket socket;
    private final ObjectOutputStream out;
    private final ObjectInputStream in;
    private final String playerName;
    private final String difficulty;
    private final String mode;

    private JLabel timerLabel;
    private JTextArea statusArea, chatArea, scoreArea;
    private GameBoardPanel board;
    private Timer swingTimer;
    private int timeLeft = 120;

    public HiddenObjectClientGUI(Socket socket, ObjectInputStream in, ObjectOutputStream out,
                                 String playerName, String difficulty, String mode) {
        this.socket = socket;
        this.in = in;
        this.out = out;
        this.playerName = playerName;
        this.difficulty = difficulty;
        this.mode = mode;

        setTitle("숨은그림찾기 (" + mode + ")");
        setSize(950, 620);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        buildUI();
        setupKeyBindings();

        Thread t = new Thread(this::listenServer);
        t.setDaemon(true);
        t.start();

        setVisible(true);
    }

    // ------------------ UI 구성 ------------------
    private void buildUI() {
        JPanel top = new JPanel(new BorderLayout());
        JLabel title = new JLabel(" 숨은 그림 찾기", SwingConstants.LEFT);
        title.setFont(new Font("맑은 고딕", Font.BOLD, 22));
        timerLabel = new JLabel("타이머: 120초", SwingConstants.RIGHT);
        timerLabel.setFont(new Font("맑은 고딕", Font.BOLD, 20));
        top.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));
        top.add(title, BorderLayout.WEST);
        top.add(timerLabel, BorderLayout.EAST);
        add(top, BorderLayout.NORTH);

        JPanel center = new JPanel(new BorderLayout());
        board = new GameBoardPanel();
        center.add(board, BorderLayout.CENTER);
        center.add(buildRightPanel(), BorderLayout.EAST);
        add(center, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout(5, 0));
        bottom.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        JTextField input = new JTextField();
        JButton send = new JButton("전송");
        JLabel hintHelp = new JLabel("Q: 힌트  |  H: 도움말");
        hintHelp.setFont(new Font("맑은 고딕", Font.PLAIN, 12));
        hintHelp.setHorizontalAlignment(SwingConstants.LEFT);

        send.addActionListener(e -> {
            String msg = input.getText().trim();
            if (!msg.isEmpty()) {
                sendPacket(new GamePacket(GamePacket.Type.MESSAGE, playerName, msg));
                input.setText("");
            }
        });
        input.addActionListener(send.getActionListeners()[0]);

        bottom.add(hintHelp, BorderLayout.WEST);
        bottom.add(input, BorderLayout.CENTER);
        bottom.add(send, BorderLayout.EAST);
        add(bottom, BorderLayout.SOUTH);
    }

    private JPanel buildRightPanel() {
        JPanel right = new JPanel(new GridLayout(3, 1, 5, 5));
        right.setPreferredSize(new Dimension(260, 0));
        right.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        statusArea = new JTextArea();
        statusArea.setEditable(false);
        statusArea.setLineWrap(true);
        statusArea.setWrapStyleWord(true);
        statusArea.setFont(new Font("맑은 고딕", Font.PLAIN, 12));
        statusArea.setBorder(BorderFactory.createTitledBorder("상태창"));
        right.add(new JScrollPane(statusArea));

        chatArea = new JTextArea();
        chatArea.setEditable(false);
        chatArea.setLineWrap(true);
        chatArea.setWrapStyleWord(true);
        chatArea.setFont(new Font("맑은 고딕", Font.PLAIN, 12));
        chatArea.setBorder(BorderFactory.createTitledBorder("채팅창"));
        right.add(new JScrollPane(chatArea));

        scoreArea = new JTextArea();
        scoreArea.setEditable(false);
        scoreArea.setFont(new Font("맑은 고딕", Font.BOLD, 13));
        scoreArea.setBackground(Color.BLACK);
        scoreArea.setForeground(Color.GREEN);
        // scoreArea.setBorder(BorderFactory.createTitledBorder("점수판"));
        right.add(new JScrollPane(scoreArea));

        return right;
    }

    // ------------------ 단축키 ------------------
    private void setupKeyBindings() {
        JRootPane root = getRootPane();
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke('Q'), "HINT");
        root.getActionMap().put("HINT", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                sendPacket(new GamePacket(GamePacket.Type.MESSAGE, playerName, "[힌트 요청]"));
                appendStatus("[시스템] 힌트를 요청했습니다.\n");
            }
        });

        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke('H'), "HELP");
        root.getActionMap().put("HELP", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                JOptionPane.showMessageDialog(HiddenObjectClientGUI.this,
                        "🎮 단축키\nQ : 힌트 요청\nH : 도움말\nEnter : 채팅 전송");
            }
        });
    }

    // ------------------ 서버 수신 ------------------
    private void listenServer() {
        try {
            while (true) {
                Object obj = in.readObject();
                if (!(obj instanceof GamePacket p)) continue;
                SwingUtilities.invokeLater(() -> handlePacket(p));
            }
        } catch (Exception e) {
            appendStatus("[시스템] 서버 연결이 끊어졌습니다.\n");
        }
    }

    // ------------------ 패킷 처리 ------------------
    private void handlePacket(GamePacket p) {
        switch (p.getType()) {
            case ROUND_START -> {
                appendStatus("[시스템] " + p.getMessage() + "\n");
                startTimer(120);
            }
            case RESULT -> {
                board.addMark(p.getX(), p.getY(), p.isCorrect());
                appendStatus(p.getSender() + ": " + p.getMessage() + "\n");
            }
            case SCORE -> scoreArea.setText(p.getMessage());
            case MESSAGE -> {
                if ("SERVER".equals(p.getSender()))
                    appendStatus("SERVER: " + p.getMessage() + "\n");
                else
                    appendChat(p.getSender() + ": " + p.getMessage() + "\n");
            }
            case TIMER_END -> {
                if (swingTimer != null) swingTimer.stop();
                timerLabel.setText("타이머: 0초");
                appendStatus("[시스템] " + p.getMessage() + "\n");
            }
            case PLAYER_COUNT -> { 
                appendStatus("[시스템] " + p.getMessage() + "\n");
            }
            case GAME_OVER -> {
                if (swingTimer != null) swingTimer.stop();
                JOptionPane.showMessageDialog(this, "🎯 게임이 종료되었습니다!");

                try {
                   
                    socket.close();

                    
                    Socket newSocket = new Socket("127.0.0.1", 9999);
                    ObjectOutputStream newOut = new ObjectOutputStream(newSocket.getOutputStream());
                    ObjectInputStream newIn = new ObjectInputStream(newSocket.getInputStream());

                    // 새 연결로 서버에 JOIN 패킷 전송 
                    GamePacket joinPacket = new GamePacket(
                            GamePacket.Type.JOIN,
                            playerName,
                            "쉬움",      
                            "1인",       
                            true         
                    );
                    newOut.writeObject(joinPacket);
                    newOut.flush();

                   
                    new MainMenuFrame(playerName, newSocket, newIn, newOut);
                    dispose();
                } catch (IOException ex) {
                    JOptionPane.showMessageDialog(this, "서버 재연결 실패: " + ex.getMessage());
                }
            }


            case MYPAGE_DATA -> {
                JOptionPane.showMessageDialog(this,
                        "🎯 마이페이지\n\n닉네임: " + playerName +
                                "\n경험치: " + p.getExp() +
                                "\n레벨: " + p.getLevel(),
                        "My Page", JOptionPane.INFORMATION_MESSAGE);
            }
            default -> {}
        }
    }

    // ------------------ 유틸리티 ------------------
    private void startTimer(int seconds) {
        if (swingTimer != null) swingTimer.stop();
        timeLeft = seconds;
        timerLabel.setText("타이머: " + timeLeft + "초");
        swingTimer = new Timer(1000, e -> {
            timeLeft--;
            timerLabel.setText("타이머: " + timeLeft + "초");
            if (timeLeft <= 0) ((Timer) e.getSource()).stop();
        });
        swingTimer.start();
    }

    private void sendPacket(GamePacket p) {
        try {
            out.writeObject(p);
            out.flush();
        } catch (IOException e) {
            appendStatus("[전송 실패] " + e.getMessage() + "\n");
        }
    }

    private void appendStatus(String msg) {
        statusArea.append(msg);
        statusArea.setCaretPosition(statusArea.getDocument().getLength());
    }

    private void appendChat(String msg) {
        chatArea.append(msg);
        chatArea.setCaretPosition(chatArea.getDocument().getLength());
    }

    // ------------------ 게임보드 ------------------
    class GameBoardPanel extends JPanel {
        private final List<ClickMark> marks = new ArrayList<>();
        private final Image bg = new ImageIcon(
                "C:/Users/user/Desktop/projectD/src/images/easy_round1.png"
        ).getImage();

        GameBoardPanel() {
            addMouseListener(new MouseAdapter() {
                public void mouseClicked(MouseEvent e) {
                    sendPacket(new GamePacket(GamePacket.Type.CLICK, playerName, e.getX(), e.getY()));
                }
            });
        }

        void addMark(int x, int y, boolean correct) {
            marks.add(new ClickMark(x, y, correct));
            repaint();
        }

        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            g.drawImage(bg, 0, 0, getWidth(), getHeight(), this);
            Graphics2D g2 = (Graphics2D) g;
            for (ClickMark m : marks) {
                if (m.correct) {
                    g2.setColor(new Color(0, 255, 0, 180));
                    g2.setStroke(new BasicStroke(3));
                    g2.draw(new Ellipse2D.Double(m.x - 20, m.y - 20, 40, 40));
                } else {
                    g2.setColor(Color.RED);
                    g2.setFont(new Font("맑은 고딕", Font.BOLD, 26));
                    g2.drawString("X", m.x - 10, m.y + 10);
                }
            }
        }

        class ClickMark {
            final int x, y;
            final boolean correct;
            ClickMark(int x, int y, boolean correct) {
                this.x = x; this.y = y; this.correct = correct;
            }
        }
    }
}
