package client;

import model.GamePacket;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Ellipse2D;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class HiddenObjectClientGUI extends JFrame {

    // 네트워크
    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private final String playerName;
    private final String difficulty;

    // UI 컴포넌트
    private JLabel timerLabel;
    private JLabel roundLabel;

    // 오른쪽 영역
    private JTextArea statusArea;   // 위: 정답/오답, 시스템 메시지
    private JTextArea chatArea;     // 아래: 채팅
    private JTextArea scoreArea;    // 맨 아래: 점수판

    // 아래쪽 입력창
    private JTextField inputField;

    // 게임 보드
    private GameBoardPanel gameBoardPanel;

    // 타이머
    private int timeLeft = 120;
    private Timer swingTimer;

    public HiddenObjectClientGUI(String host, int port, String playerName, String difficulty) {
        this.playerName = playerName;
        this.difficulty = difficulty;

        setTitle("숨은 그림 찾기");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(900, 600);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        buildUI();
        setupKeyBindings();
        connectToServer(host, port);

        setVisible(true);
    }

    // ================= UI 구성 =================
    private void buildUI() {
        // 상단 바
        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setBackground(new Color(220, 220, 220));
        topBar.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));

        JLabel titleLabel = new JLabel("숨은 그림 찾기");
        titleLabel.setFont(new Font("맑은 고딕", Font.BOLD, 24));

        timerLabel = new JLabel("타이머: 120초", SwingConstants.CENTER);
        timerLabel.setFont(new Font("맑은 고딕", Font.BOLD, 20));

        roundLabel = new JLabel("라운드 1", SwingConstants.RIGHT);
        roundLabel.setFont(new Font("맑은 고딕", Font.BOLD, 20));

        topBar.add(titleLabel, BorderLayout.WEST);
        topBar.add(timerLabel, BorderLayout.CENTER);
        topBar.add(roundLabel, BorderLayout.EAST);

        add(topBar, BorderLayout.NORTH);

        // 중앙: 게임보드 + 오른쪽 패널
        JPanel centerPanel = new JPanel(new BorderLayout());

        gameBoardPanel = new GameBoardPanel();
        gameBoardPanel.setPreferredSize(new Dimension(600, 450));
        centerPanel.add(gameBoardPanel, BorderLayout.CENTER);

        // 오른쪽 전체 패널
        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.setPreferredSize(new Dimension(220, 0));

        // ---- 위/아래로 상태창 + 채팅창 나누기 ----
        JPanel rightCenter = new JPanel(new GridLayout(2, 1));

        // 상태창 (위)
        statusArea = new JTextArea();
        statusArea.setEditable(false);
        statusArea.setFont(new Font("맑은 고딕", Font.PLAIN, 12));
        statusArea.setLineWrap(true);
        statusArea.setWrapStyleWord(true);
        statusArea.setText("[상태창]\n");
        JScrollPane statusScroll = new JScrollPane(statusArea);

        // 채팅창 (아래)
        chatArea = new JTextArea();
        chatArea.setEditable(false);
        chatArea.setFont(new Font("맑은 고딕", Font.PLAIN, 12));
        chatArea.setLineWrap(true);
        chatArea.setWrapStyleWord(true);
        chatArea.setText("[채팅창]\n");
        JScrollPane chatScroll = new JScrollPane(chatArea);

        rightCenter.add(statusScroll);
        rightCenter.add(chatScroll);
        rightPanel.add(rightCenter, BorderLayout.CENTER);

        // 점수판 (오른쪽 맨 아래 검은 박스)
        scoreArea = new JTextArea();
        scoreArea.setEditable(false);
        scoreArea.setFont(new Font("맑은 고딕", Font.PLAIN, 13));
        scoreArea.setBackground(Color.BLACK);
        scoreArea.setForeground(Color.GREEN);
        scoreArea.setMargin(new Insets(5, 5, 5, 5));
        scoreArea.setText("A 플레이어 : 0점\nB 플레이어 : 0점\n");
        rightPanel.add(scoreArea, BorderLayout.SOUTH);

        centerPanel.add(rightPanel, BorderLayout.EAST);
        add(centerPanel, BorderLayout.CENTER);

        // ---- 하단 바: 힌트/도움말 + 입력창 + 전송 버튼 ----
        JPanel bottomBar = new JPanel(new BorderLayout());
        bottomBar.setBackground(new Color(230, 230, 230));
        bottomBar.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));

        JLabel hintLabel = new JLabel("Q: 힌트    H: 도움말");
        hintLabel.setFont(new Font("맑은 고딕", Font.PLAIN, 12));
        bottomBar.add(hintLabel, BorderLayout.WEST);

        // 입력창 + 버튼 넣을 패널
        JPanel inputPanel = new JPanel(new BorderLayout(5, 0));
        inputField = new JTextField();
        JButton sendButton = new JButton("전송");

        inputPanel.add(inputField, BorderLayout.CENTER);
        inputPanel.add(sendButton, BorderLayout.EAST);
        bottomBar.add(inputPanel, BorderLayout.CENTER);

        // Enter / 버튼 클릭 시 채팅 전송
        inputField.addActionListener(e -> sendChat());
        sendButton.addActionListener(e -> sendChat());

        add(bottomBar, BorderLayout.SOUTH);
    }

    // ================= 키 바인딩 =================
    private void setupKeyBindings() {
        JRootPane root = getRootPane();

        // Q: 힌트 요청 (MESSAGE 패킷으로 서버에 보냄)
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke('Q'), "HINT");
        root.getActionMap().put("HINT", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                GamePacket p = new GamePacket(
                        GamePacket.Type.MESSAGE,
                        playerName,
                        "[힌트 요청]"
                );
                sendPacket(p);
            }
        });

        // H: 로컬 도움말
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke('H'), "HELP");
        root.getActionMap().put("HELP", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                JOptionPane.showMessageDialog(
                        HiddenObjectClientGUI.this,
                        "Q: 힌트 요청\nH: 도움말 보기\n\n" +
                        "메시지 입력창에 채팅을 입력하면 아래 채팅창에 표시됩니다."
                );
            }
        });
    }

    // ================= 네트워크 =================
    private void connectToServer(String host, int port) {
        try {
            socket = new Socket(host, port);
            out = new ObjectOutputStream(socket.getOutputStream());
            in  = new ObjectInputStream(socket.getInputStream());

            // JOIN 패킷 (난이도)
            GamePacket join = new GamePacket(
                    GamePacket.Type.JOIN,
                    playerName,
                    difficulty,
                    true
            );
            sendPacket(join);

            appendStatus("[시스템] 서버에 접속했습니다.\n");

            Thread listener = new Thread(this::listenFromServer);
            listener.setDaemon(true);
            listener.start();

        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "서버 연결 실패: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void listenFromServer() {
        try {
            while (true) {
                Object obj = in.readObject();
                if (!(obj instanceof GamePacket)) continue;
                GamePacket p = (GamePacket) obj;

                SwingUtilities.invokeLater(() -> handlePacket(p));
            }
        } catch (Exception e) {
            appendStatus("[시스템] 서버 연결이 끊어졌습니다.\n");
        }
    }

    private void sendPacket(GamePacket packet) {
        try {
            if (out != null) {
                out.writeObject(packet);
                out.flush();
            }
        } catch (IOException e) {
            appendStatus("[에러] 패킷 전송 실패: " + e.getMessage() + "\n");
        }
    }

    // 채팅 전송
    private void sendChat() {
        String text = inputField.getText().trim();
        if (text.isEmpty()) return;

        GamePacket chatPacket = new GamePacket(
                GamePacket.Type.MESSAGE,
                playerName,
                text
        );
        sendPacket(chatPacket);

        // 입력창 비우기 (표시는 서버에서 브로드캐스트된 MESSAGE를 받아 처리)
        inputField.setText("");
    }

    // ================= 패킷 처리 =================
    private void handlePacket(GamePacket p) {
        switch (p.getType()) {
            case ROUND_START:
                roundLabel.setText("라운드 " + p.getRound());
                appendStatus("[시스템] " + p.getMessage() + "\n");
                gameBoardPanel.clearMarks();
                startCountdownTimer(120);
                break;

            case RESULT:
                // 보드에 결과 표시 + 상태창에 출력
                gameBoardPanel.addMark(p.getX(), p.getY(), p.isCorrect());
                if (p.getMessage() != null) {
                    appendStatus(p.getSender() + ": " + p.getMessage() + "\n");
                }
                break;

            case SCORE:
                scoreArea.setText(p.getMessage());
                break;

            case MESSAGE:
                // SERVER가 보낸 메시지는 상태창, 다른 플레이어는 채팅창
                if ("SERVER".equals(p.getSender())) {
                    appendStatus(p.getSender() + ": " + p.getMessage() + "\n");
                } else {
                    appendChat(p.getSender() + ": " + p.getMessage() + "\n");
                }
                break;

            case TIMER_END:
                if (swingTimer != null) swingTimer.stop();
                timerLabel.setText("타이머: 0초");
                appendStatus("[시스템] " + p.getMessage() + "\n");
                break;

            case GAME_OVER:
                if (swingTimer != null) swingTimer.stop();
                appendStatus("[시스템] 게임이 종료되었습니다.\n");
                if (p.getMessage() != null) {
                    appendStatus(p.getMessage() + "\n");
                }
                break;

            case JOIN:
            default:
                break;
        }
    }

    // ================= 로그 출력 =================
    private void appendStatus(String msg) {
        statusArea.append(msg);
        statusArea.setCaretPosition(statusArea.getDocument().getLength());
    }

    private void appendChat(String msg) {
        chatArea.append(msg);
        chatArea.setCaretPosition(chatArea.getDocument().getLength());
    }

    // ================= 타이머 =================
    private void startCountdownTimer(int seconds) {
        if (swingTimer != null) swingTimer.stop();

        timeLeft = seconds;
        timerLabel.setText("타이머: " + timeLeft + "초");

        swingTimer = new Timer(1000, e -> {
            timeLeft--;
            timerLabel.setText("타이머: " + timeLeft + "초");
            if (timeLeft <= 0) {
                ((Timer) e.getSource()).stop();
            }
        });
        swingTimer.start();
    }

    // ================= 게임 보드 패널 =================
    class GameBoardPanel extends JPanel {
        private Image backgroundImage;
        private final List<ClickMark> marks = new ArrayList<>();
        private static final int RADIUS = 20;

        public GameBoardPanel() {
            backgroundImage = new ImageIcon(
                    "C:/Users/user/Desktop/projectD/src/images/easy_round1.png"
            ).getImage();

            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    int x = e.getX();
                    int y = e.getY();
                    System.out.println("클릭 좌표: (" + x + ", " + y + ")");

                    GamePacket clickPacket =
                            new GamePacket(GamePacket.Type.CLICK, playerName, x, y);
                    sendPacket(clickPacket);
                }
            });
        }

        public void setBackgroundImage(String path) {
            backgroundImage = new ImageIcon(path).getImage();
            clearMarks();
        }

        public void clearMarks() {
            marks.clear();
            repaint();
        }

        public void addMark(int x, int y, boolean correct) {
            marks.add(new ClickMark(x, y, correct));
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;

            if (backgroundImage != null) {
                g2.drawImage(backgroundImage, 0, 0, getWidth(), getHeight(), this);
            } else {
                g2.setColor(Color.LIGHT_GRAY);
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.setColor(Color.BLACK);
                g2.setFont(new Font("맑은 고딕", Font.BOLD, 20));
                g2.drawString("숨은 그림 도안", getWidth() / 2 - 70, getHeight() / 2);
            }

            for (ClickMark m : marks) {
                if (m.correct) {
                    g2.setColor(new Color(0, 255, 0, 180));
                    g2.setStroke(new BasicStroke(3));
                    g2.draw(new Ellipse2D.Double(
                            m.x - RADIUS, m.y - RADIUS,
                            RADIUS * 2, RADIUS * 2
                    ));
                } else {
                    g2.setColor(Color.RED);
                    g2.setFont(new Font("맑은 고딕", Font.BOLD, 28));
                    g2.drawString("X", m.x - 10, m.y + 10);
                }
            }
        }

        class ClickMark {
            int x, y;
            boolean correct;

            ClickMark(int x, int y, boolean correct) {
                this.x = x;
                this.y = y;
                this.correct = correct;
            }
        }
    }

    // ================= main =================
    public static void main(String[] args) {

    	
        SwingUtilities.invokeLater(() -> {
            String name = JOptionPane.showInputDialog("플레이어 이름을 입력하세요:");
            if (name == null || name.isBlank()) name = "Player";

            String[] options = {"쉬움", "보통", "어려움"};
            int sel = JOptionPane.showOptionDialog(
                    null,
                    "난이도를 선택하세요",
                    "난이도",
                    JOptionPane.DEFAULT_OPTION,
                    JOptionPane.QUESTION_MESSAGE,
                    null,
                    options,
                    options[0]
            );
            String diff = (sel >= 0) ? options[sel] : "쉬움";

            new HiddenObjectClientGUI("127.0.0.1", 9999, name, diff);
        });
    }
}
