package client;

import model.GamePacket;
import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.Map;

// CardLayout을 사용하여 HomePanel(접속)과 WatingRoom(대기방)을 관리
public class GameLauncher extends JFrame {

    // --- 통신 관련 ---
    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private String playerName;
    private String selectedDifficulty; 

    // --- UI 관련 ---
    private CardLayout cardLayout;
    private JPanel mainPanel; 
    private HomePanel homePanel;
    private WaitingRoom waitingRoom;

    // --- 카드 이름 (식별자) ---
    private static final String CARD_HOME = "HOME";
    private static final String CARD_LOBBY = "LOBBY";

    public GameLauncher() {
        setTitle("시작 화면");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(300, 400);

        cardLayout = new CardLayout();
        mainPanel = new JPanel(cardLayout);

        // 1. 홈 패널 (포트/닉네임 입력) 생성
        homePanel = new HomePanel(this);
        mainPanel.add(homePanel, CARD_HOME);

        // 2. 대기방 패널 생성
        waitingRoom = new WaitingRoom(this);
        mainPanel.add(waitingRoom, CARD_LOBBY);

        add(mainPanel);
        cardLayout.show(mainPanel, CARD_HOME); // 첫 화면

        setLocationRelativeTo(null);
        setVisible(true);
    }

    // 대기방(Lobby) 카드로 전환
    public void switchToLobby(Socket socket, ObjectOutputStream out, ObjectInputStream in, String playerName) {
        this.socket = socket;
        this.out = out;
        this.in = in;
        this.playerName = playerName; 
        
        waitingRoom.setConnection(out, playerName);
        
        // 서버 리스너 스레드 시작
        Thread listenerThread = new Thread(this::listenFromServer);
        listenerThread.setDaemon(true);
        listenerThread.start();

        this.setSize(600, 500);
        cardLayout.show(mainPanel, CARD_LOBBY);
        setTitle("대기방 (유저: " + playerName + ")");
    }
    
    // 서버로부터 패킷 수신 (대기방 전용)
    private void listenFromServer() {
        try {
            while (true) {
                Object obj = in.readObject();
                if (!(obj instanceof GamePacket)) continue;
                GamePacket p = (GamePacket) obj;
                
                // 패킷을 처리하도록 EDT에 넘김
                SwingUtilities.invokeLater(() -> handlePacket(p));
                
                if (p.getType() == GamePacket.Type.ROUND_START) {
                    System.out.println("[GameLauncher] ROUND_START 감지. 대기방 리스너를 종료합니다.");
                    break; // while 루프를 빠져나가 스레드를 정상 종료합니다.
                }
            }
        } catch (Exception e) {
            if (this.isVisible()) {
                JOptionPane.showMessageDialog(this, "서버 연결이 끊어졌습니다: " + e.getMessage());
                cardLayout.show(mainPanel, CARD_HOME);
                homePanel.resetUI();
            } else {
                System.out.println("[GameLauncher] 리스너가 게임 시작으로 인해 정상 종료되었습니다.");
            }
        }
    }
    
    // 대기방 상태에서의 패킷 처리
    private void handlePacket(GamePacket p) {
        switch (p.getType()) {
            case MESSAGE:
                // 일반 채팅 또는 서버 공지
            	waitingRoom.appendChat(p.getSender() + ": " + p.getMessage() + "\n");
                break;
            case LOBBY_UPDATE:
                // "대기방 인원" 목록 갱신
            	waitingRoom.updateLobbyInfo(
                    p.getHostName(), 
                    p.getPlayerReadyStatus(), // Map<String, Boolean>
                    p.getDifficulty(),
                    p.getGameMode()
                );
                break;
            case ROUND_START:
                // 게임 시작 신호
                System.out.println("GameLauncher: ROUND_START 수신! 게임 창을 엽니다.");
                
                this.dispose(); // 1. 현재 대기방 창 닫기
                
                // 2. HiddenObjectClientGUI에 '새로운 생성자'로 연결 정보 전달
                new HiddenObjectClientGUI(
                    socket, in, out, 
                    playerName, 
                    selectedDifficulty, // 대기방에서 선택한 난이도
                    p // 서버가 보낸 ROUND_START 패킷 (정답 목록 포함)
                );
                break;
            default:
                // 다른 패킷 (CLICK, RESULT 등)은 대기방에서 무시
                break;
        }
    }

    // 게임 시작 요청
    public void requestStartGame(String difficulty, String gameMode) {
        this.selectedDifficulty = difficulty;
        
        GamePacket requestPacket = new GamePacket(
            GamePacket.Type.START_GAME_REQUEST,
            playerName,
            difficulty,
            gameMode
        );
        sendPacket(requestPacket);
    }
    
    // 채팅/메시지 전송
    public void sendPacket(GamePacket packet) {
        if (out != null) {
            try {
                out.writeObject(packet);
                out.flush();
            } catch (IOException e) {
                e.printStackTrace();
                waitingRoom.appendChat("[오류] 메시지 전송 실패: " + e.getMessage() + "\n");
            }
        }
    }
    
    // 현재 플레이어 이름 반환
    public String getPlayerName() {
        return playerName;
    }


    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new GameLauncher());
    }
}