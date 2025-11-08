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
import java.util.Iterator; // (추가)
import java.util.List;

public class HiddenObjectClientGUI extends JFrame {

    // (기존 코드와 동일)
    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private final String playerName;
    private final String difficulty;
    private JLabel timerLabel;
    private JLabel roundLabel;
    private JTextArea statusArea;
    private JTextArea chatArea;
    private JTextArea scoreArea;
    private JTextField inputField;
    private GameBoardPanel gameBoardPanel;
    private int timeLeft = 120;
    private Timer swingTimer;
    // ...

    public HiddenObjectClientGUI(String host, int port, String playerName, String difficulty) {
        this.playerName = playerName;
        this.difficulty = difficulty;

        setTitle("숨은 그림 찾기");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        buildUI();
        setupKeyBindings();
        connectToServer(host, port);

        pack(); 
        setResizable(false);
        setVisible(true);
    }

    // ================= UI 구성 =================
    // (buildUI, setupKeyBindings, connectToServer, listenFromServer, sendPacket, sendChat)
    // (이전 코드와 100% 동일하므로 생략)
    // ...
    // (이하 동일한 코드)
    private void buildUI() {
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
        
        JPanel centerPanel = new JPanel(new BorderLayout());
        gameBoardPanel = new GameBoardPanel();
        gameBoardPanel.setPreferredSize(new Dimension(600, 450)); // 기본 크기
        centerPanel.add(gameBoardPanel, BorderLayout.CENTER);
        
        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.setPreferredSize(new Dimension(220, 0));
        statusArea = new JTextArea("[상태창]\n");
        statusArea.setEditable(false);
        statusArea.setFont(new Font("맑은 고딕", Font.PLAIN, 12));
        statusArea.setLineWrap(true);
        statusArea.setWrapStyleWord(true);
        
        JScrollPane statusScroll = new JScrollPane(statusArea);
        chatArea = new JTextArea("[채팅창]\n");
        chatArea.setEditable(false);
        chatArea.setFont(new Font("맑은 고딕", Font.PLAIN, 12));
        chatArea.setLineWrap(true);
        chatArea.setWrapStyleWord(true);
        JScrollPane chatScroll = new JScrollPane(chatArea);
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, statusScroll, chatScroll);
        splitPane.setResizeWeight(0.5); 
        rightPanel.add(splitPane, BorderLayout.CENTER);
        scoreArea = new JTextArea();
        scoreArea.setEditable(false);
        scoreArea.setFont(new Font("맑은 고딕", Font.PLAIN, 13));
        scoreArea.setBackground(Color.BLACK);
        scoreArea.setForeground(Color.GREEN);
        scoreArea.setMargin(new Insets(5, 5, 5, 5));
        scoreArea.setText("A 플레이어 : 0점\nB 플레이어 : 0점\n");
        scoreArea.setRows(3); 
        rightPanel.add(scoreArea, BorderLayout.SOUTH);
        centerPanel.add(rightPanel, BorderLayout.EAST);
        add(centerPanel, BorderLayout.CENTER);
        
        JPanel bottomBar = new JPanel(new BorderLayout());
        bottomBar.setBackground(new Color(230, 230, 230));
        bottomBar.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        JLabel hintLabel = new JLabel("Q: 힌트     H: 도움말");
        hintLabel.setFont(new Font("맑은 고딕", Font.PLAIN, 12));
        bottomBar.add(hintLabel, BorderLayout.WEST);
        JPanel inputPanel = new JPanel(new BorderLayout(5, 0));
        inputField = new JTextField();
        JButton sendButton = new JButton("전송");
        inputPanel.add(inputField, BorderLayout.CENTER);
        inputPanel.add(sendButton, BorderLayout.EAST);
        bottomBar.add(inputPanel, BorderLayout.CENTER);
        
        inputField.addActionListener(e -> sendChat());
        sendButton.addActionListener(e -> sendChat());
        add(bottomBar, BorderLayout.SOUTH);
    }
    private void setupKeyBindings() {
        JRootPane root = getRootPane();
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
    private void connectToServer(String host, int port) {
        try {
            socket = new Socket(host, port);
            out = new ObjectOutputStream(socket.getOutputStream());
            in  = new ObjectInputStream(socket.getInputStream());
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
    private void sendChat() {
        String text = inputField.getText().trim();
        if (text.isEmpty()) return;
        GamePacket chatPacket = new GamePacket(
                GamePacket.Type.MESSAGE,
                playerName,
                text
        );
        sendPacket(chatPacket);
        inputField.setText("");
    }

    // ================= 패킷 처리 =================
    private void handlePacket(GamePacket p) {
        switch (p.getType()) {
            case ROUND_START:
                roundLabel.setText("라운드 " + p.getRound());
                String imagePath = p.getMessage(); 
                
                // (수정) 서버로부터 '원본 정답 목록'을 받음
                List<Rectangle> originalAnswers = p.getOriginalAnswers();
                
                if (imagePath != null && !imagePath.isEmpty() && originalAnswers != null) {
                    // (수정) 게임 보드에 이미지 경로와 '원본 정답'을 함께 전달
                    gameBoardPanel.setRoundData(imagePath, originalAnswers);
                    appendStatus("[시스템] 라운드 " + p.getRound() + " 시작!\n");
                } else {
                    appendStatus("[시스템] " + p.getMessage() + "\n");
                }
                
                gameBoardPanel.clearMarks();
                startCountdownTimer(120);
                break;

            case RESULT:
                // (수정) 서버가 보낸 '인덱스'와 '정답/오답' 여부로 마크를 추가
                gameBoardPanel.addMark(p.getAnswerIndex(), p.isCorrect());
                if (p.getMessage() != null) {
                    appendStatus(p.getSender() + ": " + p.getMessage() + "\n");
                }
                break;

            case SCORE:
                scoreArea.setText(p.getMessage());
                break;
            // (이하 동일)
            case MESSAGE:
                if ("SERVER".equals(p.getSender())) {
                    appendStatus(p.getSender() + ": " + p.getMessage() + "\n");
                } else {
                    appendChat(p.getSender() + ": " + p.getMessage() + "\n");
                }
                break;
            case TIMER_END:
                if (swingTimer != null) swingTimer.stop();
                timerLabel.setText("타이머: 0초");
                timerLabel.setForeground(Color.RED); 
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
    // (이전 코드와 100% 동일)
    private void appendStatus(String msg) {
        statusArea.append(msg);
        statusArea.setCaretPosition(statusArea.getDocument().getLength());
    }
    private void appendChat(String msg) {
        chatArea.append(msg);
        chatArea.setCaretPosition(chatArea.getDocument().getLength());
    }
    // ...

    // ================= 타이머 (점점 빨간색으로) =================
    private void startCountdownTimer(int seconds) {
        if (swingTimer != null) swingTimer.stop();

        timeLeft = seconds;
        timerLabel.setText("타이머: " + timeLeft + "초");
        timerLabel.setForeground(Color.BLACK); 

        swingTimer = new Timer(1000, e -> {
            if (timeLeft > 0) {
                timeLeft--;
                timerLabel.setText("타이머: " + timeLeft + "초");

                if (timeLeft <= 30) {
                    int red = 255;
                    int green = Math.max(0, 200 - (30 - timeLeft) * 7); 
                    timerLabel.setForeground(new Color(red, green, 0));
                }

                gameBoardPanel.removeExpiredMarks();

                if (timeLeft <= 0) {
                    ((Timer) e.getSource()).stop();
                }
            }
        });
        swingTimer.start();
    }

    // ================= 게임 보드 패널 (대폭 수정) =================
    class GameBoardPanel extends JPanel {
        private Image backgroundImage;
        
        // (수정) HiddenObjectGame의 로직을 가져옴
        private List<Rectangle> originalAnswers; // 서버가 준 원본 정답
        private List<Rectangle> scaledAnswers;   // 클라이언트가 계산한 스케일링된 정답
        private boolean[] foundStatus;           // 클라이언트가 추적하는 찾은 상태
        
        // (수정) 마크 리스트 (ClickMark -> GameMark로 변경)
        private final List<GameMark> marks = new ArrayList<>();
        private static final int RADIUS = 20;

        public GameBoardPanel() {
            backgroundImage = null; 
            scaledAnswers = new ArrayList<>();
            foundStatus = new boolean[0];

            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (timeLeft <= 0 || backgroundImage == null) return;

                    int panelW = getWidth();
                    int panelH = getHeight();
                    int imgW = backgroundImage.getWidth(null);
                    int imgH = backgroundImage.getHeight(null);

                    // 동일한 비율과 offset 계산
                    double scale = Math.min((double) panelW / imgW, (double) panelH / imgH);
                    int drawW = (int) (imgW * scale);
                    int drawH = (int) (imgH * scale);
                    int offsetX = (panelW - drawW) / 2;
                    int offsetY = (panelH - drawH) / 2;

                    // 클릭 좌표를 이미지 좌표로 변환
                    double imgX = (e.getX() - offsetX) / scale;
                    double imgY = (e.getY() - offsetY) / scale;

                    Point clickPoint = new Point((int) imgX, (int) imgY);

                    int foundIndex = -1;
                    for (int i = 0; i < scaledAnswers.size(); i++) {
                        if (scaledAnswers.get(i).contains(clickPoint) && !foundStatus[i]) {
                            foundIndex = i;
                            break;
                        }
                    }

                    if (foundIndex != -1) {
                        System.out.println("클라이언트: 정답 " + foundIndex + "번 클릭");
                        foundStatus[foundIndex] = true;
                        sendPacket(new GamePacket(GamePacket.Type.CLICK, playerName, foundIndex));
                    } else {
                        System.out.println("클라이언트: 오답 클릭 (" + e.getX() + ", " + e.getY() + ")");
                        marks.add(new GameMark(new Point((int) imgX, (int) imgY), false));
                        repaint();
                    }
                }
            });

        }
        
        // (수정) 라운드 데이터 설정 (이미지 + 원본 정답)
        public void setRoundData(String path, List<Rectangle> originalAnswers) {
            this.originalAnswers = originalAnswers;
            this.foundStatus = new boolean[originalAnswers.size()];
            this.scaledAnswers.clear();
            
            try {
                backgroundImage = new ImageIcon(path).getImage();
                if (backgroundImage.getWidth(null) == -1) {
                    throw new IOException("이미지 파일 로드 실패: " + path);
                }
                
                // --- (핵심) HiddenObjectGame의 스케일링 로직 ---
                int imgWidth = backgroundImage.getWidth(null);
                int imgHeight = backgroundImage.getHeight(null);
                
                int baseWidth = 600; 
                double ratio = (double) imgHeight / imgWidth;
                int newHeight = (int) (baseWidth * ratio);
                
                setPreferredSize(new Dimension(baseWidth, newHeight));
                
                double scaleFactor = (double) baseWidth / imgWidth;
                recalculateScaledAnswers(scaleFactor);
                
                SwingUtilities.getWindowAncestor(this).pack();
                // ------------------

            } catch (Exception e) {
                e.printStackTrace();
                backgroundImage = null; 
                appendStatus("[에러] 이미지 로드 실패: " + path + "\n");
            }
            clearMarks();
        }
        
        // (신규) HiddenObjectGame의 createScaledRectangle 로직
        private void recalculateScaledAnswers(double scaleFactor) {
            scaledAnswers.clear();
            for (Rectangle r : originalAnswers) {
                int scaledX = (int) (r.x * scaleFactor);
                int scaledY = (int) (r.y * scaleFactor);
                int scaledW = (int) (r.width * scaleFactor);
                int scaledH = (int) (r.height * scaleFactor);
                
                // (클릭 편의를 위한 padding)
                int padding = (int) (10 * scaleFactor); 
                scaledX = Math.max(0, scaledX - padding);
                scaledY = Math.max(0, scaledY - padding);
                scaledW += (padding * 2);
                scaledH += (padding * 2);
                if (scaledW <= 0) scaledW = 1;
                if (scaledH <= 0) scaledH = 1;
                
                scaledAnswers.add(new Rectangle(scaledX, scaledY, scaledW, scaledH));
            }
            System.out.println("클라이언트: 스케일링된 정답 " + scaledAnswers.size() + "개 계산 완료.");
        }

        public void clearMarks() {
            marks.clear();
            repaint();
        }

        // (수정) 서버가 RESULT(index, correct)를 보내면 호출됨
        public void addMark(int answerIndex, boolean correct) {
            if (answerIndex < 0 || answerIndex >= scaledAnswers.size()) {
                // (인덱스가 없는 오답 클릭 - 현재 로직에선 사용 안 함)
                return;
            }
            
            // (수정) 정답 인덱스(answerIndex)에 해당하는 '스케일링된' 사각형의 중심점을 찾음
            Rectangle rect = scaledAnswers.get(answerIndex);
            Point center = new Point(rect.x + rect.width / 2, rect.y + rect.height / 2);
            
            marks.add(new GameMark(center, correct));
            
            repaint();
        }
        
        public void removeExpiredMarks() {
            long currentTime = System.currentTimeMillis();
            if (marks.removeIf(m -> !m.correct && currentTime > m.expiryTime)) {
                repaint();
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;

            if (backgroundImage != null) {
                int panelW = getWidth();
                int panelH = getHeight();
                int imgW = backgroundImage.getWidth(null);
                int imgH = backgroundImage.getHeight(null);

                // 비율 계산
                double scale = Math.min((double) panelW / imgW, (double) panelH / imgH);

                int drawW = (int) (imgW * scale);
                int drawH = (int) (imgH * scale);

                // 이미지 중앙 정렬을 위한 offset
                int offsetX = (panelW - drawW) / 2;
                int offsetY = (panelH - drawH) / 2;

                // 실제 이미지 그리기
                g2.drawImage(backgroundImage, offsetX, offsetY, drawW, drawH, this);

                // 마크 그릴 때도 같은 기준(offset+scale) 적용
                for (GameMark m : marks) {
                    int drawX = (int) (offsetX + m.p.x * scale);
                    int drawY = (int) (offsetY + m.p.y * scale);

                    if (m.correct) {
                        g2.setColor(new Color(0, 255, 0, 180));
                        g2.setStroke(new BasicStroke(3));
                        g2.draw(new Ellipse2D.Double(
                                drawX - RADIUS, drawY - RADIUS,
                                RADIUS * 2, RADIUS * 2
                        ));
                    } else {
                        g2.setColor(Color.RED);
                        g2.setFont(new Font("맑은 고딕", Font.BOLD, 28));
                        g2.drawString("X", drawX - 10, drawY + 10);
                    }
                }
            } else {
                g2.setColor(Color.LIGHT_GRAY);
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.setColor(Color.BLACK);
                g2.setFont(new Font("맑은 고딕", Font.BOLD, 20));
                g2.drawString("서버에서 라운드 시작 대기 중...", getWidth() / 2 - 120, getHeight() / 2);
            }
        }


        /** (수정) GameMark (ClickMark -> GameMark) */
        class GameMark {
            Point p; // (수정) x,y 대신 중심점 Point
            boolean correct;
            long expiryTime; 

            // (수정) 생성자가 Point를 받음
            GameMark(Point centerPoint, boolean correct) {
                this.p = centerPoint;
                this.correct = correct;
                
                if (correct) {
                    this.expiryTime = -1;
                } else {
                    this.expiryTime = System.currentTimeMillis() + 5000;
                }
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