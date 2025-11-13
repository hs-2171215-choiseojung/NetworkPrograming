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
import java.util.Iterator; 
import java.util.List;

// 실제 '게임 화면'을 담당, GameLauncher에 의해 실행됨.
public class HiddenObjectClientGUI extends JFrame {

    // --- 통신 관련 ---
    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private final String playerName;
    private final String difficulty;

    // --- UI 컴포넌트 ---
    private JLabel timerLabel;
    private JLabel roundLabel;
    private JTextArea statusArea;
    private JTextArea chatArea;
    private JTextArea scoreArea;
    private JTextField inputField;
    private GameBoardPanel gameBoardPanel;

    // --- 게임 상태 ---
    private int timeLeft = 120;
    private Timer swingTimer;
    private boolean isGameActive = false;

    public HiddenObjectClientGUI(Socket socket, ObjectInputStream in, ObjectOutputStream out, 
                                 String playerName, String difficulty, GamePacket roundStartPacket) {
        this.socket = socket;
        this.in = in;
        this.out = out;
        this.playerName = playerName;
        this.difficulty = difficulty;

        setTitle("숨은 그림 찾기 (플레이어: " + playerName + ")");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        buildUI();
        setupKeyBindings();
        
        // 리스너 스레드 즉시 시작
        Thread listenerThread = new Thread(this::listenFromServer);
        listenerThread.setDaemon(true);
        listenerThread.start();
        
        // 전달받은 ROUND_START 패킷으로 즉시 1라운드 시작
        handlePacket(roundStartPacket); 

        pack(); 
        setResizable(false);
        setVisible(true);
    }

    // UI 구성
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
        splitPane.setEnabled(false);
        splitPane.setDividerLocation(0.5);
        
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
    
    // 키 바인딩
    private void setupKeyBindings() {
        JRootPane root = getRootPane();
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke('Q'), "HINT");
        root.getActionMap().put("HINT", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (!isGameActive) return;
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

    // 인게임 리스너
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
            JOptionPane.showMessageDialog(this, "서버 연결이 끊겼습니다.");
            this.dispose();
            // 런처를 다시 실행
            SwingUtilities.invokeLater(() -> new GameLauncher());
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
    
    // 인게임 패킷 처리
    private void handlePacket(GamePacket p) {
        switch (p.getType()) {
            case ROUND_START:
                roundLabel.setText("라운드 " + p.getRound());
                String imagePath = p.getMessage(); 
                List<Rectangle> originalAnswers = p.getOriginalAnswers();
                Dimension originalDimension = p.getOriginalDimension();
                
                if (imagePath != null && !imagePath.isEmpty() && originalAnswers != null && originalDimension != null) {
                    gameBoardPanel.setRoundData(imagePath, originalAnswers, originalDimension);
                    appendStatus("[시스템] 라운드 " + p.getRound() + " 시작!\n");
                    isGameActive = true; 
                } else {
                    appendStatus("[시스템] " + p.getMessage() + "\n");
                }
                
                gameBoardPanel.clearMarks();
                startCountdownTimer(120);
                break;

            case RESULT:
                gameBoardPanel.addMark(p.getAnswerIndex(), p.isCorrect());
                if (p.getMessage() != null) {
                    appendStatus(p.getSender() + ": " + p.getMessage() + "\n");
                }
                break;
            case SCORE:
                scoreArea.setText(p.getMessage());
                break;
            case MESSAGE:
                // 인게임에서는 채팅창에만 표시
                appendChat(p.getSender() + ": " + p.getMessage() + "\n");
                break;
            case TIMER_END:
                isGameActive = false; 
                if (swingTimer != null) swingTimer.stop();
                timerLabel.setText("타이머: 0초");
                timerLabel.setForeground(Color.RED); 
                appendStatus("[시스템] " + p.getMessage() + "\n");
                break;
            case GAME_OVER:
                isGameActive = false; 
                if (swingTimer != null) swingTimer.stop();
                appendStatus("[시스템] 게임이 종료되었습니다.\n");
                if (p.getMessage() != null) {
                    appendStatus(p.getMessage() + "\n");
                }
                // 게임 종료 후 3초 뒤 런처로 복귀
                Timer exitTimer = new Timer(3000, e -> {
                    this.dispose();
                    SwingUtilities.invokeLater(() -> new GameLauncher());
                });
                exitTimer.setRepeats(false);
                exitTimer.start();
                break;
            case LOBBY_UPDATE:
                break;
            case JOIN:
            default:
                break;
        }
    }
    
    // 로그 출력
    private void appendStatus(String msg) {
        statusArea.append(msg);
        statusArea.setCaretPosition(statusArea.getDocument().getLength());
    }
    private void appendChat(String msg) {
        chatArea.append(msg);
        chatArea.setCaretPosition(chatArea.getDocument().getLength());
    }

    // 타이머
    private void startCountdownTimer(int seconds) {
        if (swingTimer != null) swingTimer.stop();
        
        timeLeft = seconds;
        timerLabel.setText("타이머: " + timeLeft + "초");
        timerLabel.setForeground(Color.BLACK);
        
        swingTimer = new Timer(1000, e -> {
            if (isGameActive && timeLeft > 0) { 
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
            } else if (!isGameActive) {
                 ((Timer) e.getSource()).stop();
            }
        });
        swingTimer.start();
    }
    
    // 게임 보드 패널
    class GameBoardPanel extends JPanel {
        private Image backgroundImage;
        private List<Rectangle> originalAnswers;
        private List<Rectangle> scaledAnswers;
        private boolean[] foundStatus;
        private Dimension originalDimension;
        private final List<GameMark> marks = new ArrayList<>();
        private static final int RADIUS = 20; 
        
        public GameBoardPanel() {
            backgroundImage = null; 
            scaledAnswers = new ArrayList<>();
            foundStatus = new boolean[0];
            
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (!isGameActive || timeLeft <= 0 || backgroundImage == null || originalDimension == null) {
                        return;
                    }
                    
                    // 1. 현재 패널 크기 및 이미지 원본 크기
                    int panelW = getWidth();
                    int panelH = getHeight();
                    int imgW = originalDimension.width;
                    int imgH = originalDimension.height;
                    
                    // 2. 스케일링 비율 및 오프셋 계산 (paintComponent와 동일)
                    double scale = Math.min((double) panelW / imgW, (double) panelH / imgH);
                    int drawW = (int) (imgW * scale);
                    int drawH = (int) (imgH * scale);
                    int offsetX = (panelW - drawW) / 2;
                    int offsetY = (panelH - drawH) / 2;
                    
                    // 3. 클릭 좌표를 원본 이미지 좌표계로 변환
                    // (클릭 좌표 - 오프셋) / 스케일
                    double originalX = (e.getX() - offsetX) / scale;
                    double originalY = (e.getY() - offsetY) / scale;

                    // 4. 원본 정답 목록과 비교하여 인덱스 찾기
                    int foundIndex = -1;
                    for (int i = 0; i < originalAnswers.size(); i++) {
                        // 원본 정답 Rectangle과 변환된 원본 좌표 (originalX, originalY)를 비교
                        if (originalAnswers.get(i).contains(originalX, originalY) && !foundStatus[i]) {
                            foundIndex = i;
                            break;
                        }
                    }
                    
                    if (foundIndex != -1) {
                        System.out.println("클라이언트: 정답 " + foundIndex + "번 클릭");
                        sendPacket(new GamePacket(GamePacket.Type.CLICK, playerName, foundIndex));
                    } else {
                        System.out.println("클라이언트: 오답 클릭 (" + e.getX() + ", " + e.getY() + ")");
                        marks.add(new GameMark(new Point((int) originalX, (int) originalY), false));
                        repaint();
                    }
                }
            });
        }
        public void setRoundData(String path, List<Rectangle> originalAnswers, Dimension originalDimension) {
            this.originalAnswers = originalAnswers;
            this.originalDimension = originalDimension;
            this.foundStatus = new boolean[originalAnswers.size()];
            this.scaledAnswers.clear(); // scaledAnswers는 더 이상 사용하지 않지만, 초기화 유지
            try {
                backgroundImage = new ImageIcon(path).getImage();
                if (backgroundImage.getWidth(null) == -1) {
                    throw new IOException("이미지 파일 로드 실패: " + path);
                }
                
                // BaseWidth 기준으로 Panel 크기 설정
                int imgWidth = originalDimension.width;
                int imgHeight = originalDimension.height;
                int baseWidth = 600; 
                double ratio = (double) imgHeight / imgWidth;
                int newHeight = (int) (baseWidth * ratio);
                setPreferredSize(new Dimension(baseWidth, newHeight));
                
            } catch (Exception e) {
                e.printStackTrace();
                backgroundImage = null; 
                HiddenObjectClientGUI.this.appendStatus("[에러] 이미지 로드 실패: " + path + "\n");
            }
            clearMarks();
        }
        
        public void clearMarks() {
            marks.clear();
            repaint();
        }
        public void addMark(int answerIndex, boolean correct) {
            if (answerIndex < 0 || answerIndex >= originalAnswers.size()) {
                System.out.println("클라이언트: 서버로부터 잘못된 RESULT 인덱스 수신: " + answerIndex);
                return;
            }
            
            // 마크 중심점을 원본 정답의 중심점으로 설정
            Rectangle originalRect = originalAnswers.get(answerIndex);
            Point center = new Point(originalRect.x + originalRect.width / 2, originalRect.y + originalRect.height / 2);
            
            marks.add(new GameMark(center, correct));
            
            if (correct) {
                foundStatus[answerIndex] = true;
            }
            repaint();
        }
        public void removeExpiredMarks() {
            long currentTime = System.currentTimeMillis();
            // GameMark의 p(Point): 원본 이미지 좌표계
            if (marks.removeIf(m -> !m.correct && currentTime > m.expiryTime)) {
                repaint();
            }
        }
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON); 
            if (backgroundImage != null) {
                int panelW = getWidth();
                int panelH = getHeight();
                int imgW = originalDimension.width;
                int imgH = originalDimension.height;

                // 비율 계산
                double scale = Math.min((double) panelW / imgW, (double) panelH / imgH);

                int drawW = (int) (imgW * scale);
                int drawH = (int) (imgH * scale);

                // 이미지 중앙 정렬을 위한 offset
                int offsetX = (panelW - drawW) / 2;
                int offsetY = (panelH - drawH) / 2;

                // 실제 이미지 그리기
                g2.drawImage(backgroundImage, offsetX, offsetY, drawW, drawH, this);

                // 마크 그릴 때 (m.p.x, m.p.y는 원본 좌표)
                for (GameMark m : marks) {
                    // [수정] 원본 좌표를 화면 좌표로 변환하여 그립니다.
                    int drawX = (int) (offsetX + m.p.x * scale);
                    int drawY = (int) (offsetY + m.p.y * scale);
                    
                    if (m.correct) {
                        // ... (정답 마크 그리기 로직) ...
                         g2.setColor(new Color(0, 255, 0, 180));
                         g2.setStroke(new BasicStroke(3));
                         g2.draw(new Ellipse2D.Double(
                                 drawX - RADIUS, drawY - RADIUS,
                                 RADIUS * 2, RADIUS * 2
                         ));
                    } else {
                        // ... (오답 마크 그리기 로직) ...
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
        class GameMark {
            Point p; // 원본 이미지 좌표계의 중심점
            boolean correct;
            long expiryTime; 
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
}