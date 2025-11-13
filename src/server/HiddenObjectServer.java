package server;

import model.GamePacket;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class HiddenObjectServer {

    private static final int PORT = 9999;
    private ServerSocket listener = null;

    private final Map<String, ClientHandler> clients = new ConcurrentHashMap<>();
    private final Map<String, Integer> scores = new ConcurrentHashMap<>();
    private final Map<String, Integer> expMap = new ConcurrentHashMap<>();

    private GameLogic gameLogic;
    private int currentRound = 0;

    public HiddenObjectServer() {
        try {
            this.gameLogic = new GameLogic();
            System.out.println("[서버] 게임 로직 초기화 완료.");
        } catch (IOException e) {
            System.out.println("[서버] 치명적 오류: 게임 정답 이미지 로드 실패!");
            e.printStackTrace();
            System.exit(1);
        }
    }

    public void run() {
        try {
            listener = new ServerSocket(PORT);
            System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            System.out.println("✅ [서버] 숨은 그림 찾기 서버 시작!");
            System.out.println("✅ [서버] 포트: " + PORT);
            System.out.println("✅ [서버] 클라이언트 연결 대기 중...");
            System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

            while (true) {
                Socket socket = listener.accept();
                System.out.println("🔗 [서버] 새 클라이언트 접속! IP: " + socket.getInetAddress());

                ClientHandler clientHandler = new ClientHandler(socket);
                clientHandler.start();
            }

        } catch (IOException e) {
            System.out.println("❌ [서버] 오류: " + e.getMessage());
            e.printStackTrace();
        } finally {
            try {
                if (listener != null) listener.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    // ================= 클라이언트 핸들러 =================
    private class ClientHandler extends Thread {
        private final Socket socket;
        private ObjectOutputStream out;
        private ObjectInputStream in;
        private String playerName;
        private String difficulty;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        public void run() {
            try {
                out = new ObjectOutputStream(socket.getOutputStream());
                in = new ObjectInputStream(socket.getInputStream());

                GamePacket firstPacket = (GamePacket) in.readObject();
                
                // MYPAGE_REQUEST는 JOIN 없이 즉시 처리
                if (firstPacket.getType() == GamePacket.Type.MYPAGE_REQUEST) {
                    handleMyPageQuick(firstPacket);
                    return; // 바로 종료
                }
                
                // JOIN 패킷 처리
                if (firstPacket.getType() == GamePacket.Type.JOIN) {
                    this.playerName = firstPacket.getSender();
                    this.difficulty = firstPacket.getDifficulty();
                    handleJoin(this, this.difficulty);
                } else {
                    socket.close();
                    return;
                }

                while (true) {
                    GamePacket packet = (GamePacket) in.readObject();
                    handlePacket(this, packet);
                }

            } catch (Exception e) {
                System.out.println("[서버] " + playerName + " 연결 끊김.");
            } finally {
                handleDisconnect();
            }
        }
        
        // 빠른 마이페이지 조회 (JOIN 불필요)
        private void handleMyPageQuick(GamePacket p) {
            String name = p.getSender();
            int exp = expMap.getOrDefault(name, 0);
            int level = exp / 50 + 1;
            
            System.out.println("[서버] 마이페이지 조회: " + name + " (exp=" + exp + ", lv=" + level + ")");
            
            sendPacket(new GamePacket(GamePacket.Type.MYPAGE_DATA, "SERVER", exp, level, true));
            
            try {
                socket.close();
            } catch (IOException ignored) {}
        }

        public void sendPacket(GamePacket packet) {
            try {
                out.writeObject(packet);
                out.flush();
            } catch (IOException e) {
                System.out.println("[서버] " + playerName + "에게 패킷 전송 실패: " + e.getMessage());
            }
        }

        private void handleDisconnect() {
            if (playerName != null) {
                clients.remove(playerName);
                scores.remove(playerName);
                broadcast(new GamePacket(GamePacket.Type.MESSAGE, "SERVER",
                        playerName + " 님이 퇴장했습니다."));
                broadcast(new GamePacket(GamePacket.Type.SCORE, getScoreboardString()));
                broadcastPlayerCount();
            }
            try {
                if (socket != null) socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    // ================= 서버 메인 로직 =================
    private synchronized void handleJoin(ClientHandler handler, String difficulty) {
        // 중복 닉네임 체크
        if (clients.containsKey(handler.playerName)) {
            System.out.println("[서버] 중복 닉네임 거부: " + handler.playerName);
            handler.sendPacket(new GamePacket(GamePacket.Type.MESSAGE, "SERVER",
                    "[중복] 이미 사용 중인 닉네임입니다."));
            try {
                handler.socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            return;
        }
        
        clients.put(handler.playerName, handler);
        scores.put(handler.playerName, 0);
        expMap.putIfAbsent(handler.playerName, 0);

        System.out.println("[서버] " + handler.playerName + " (난이도: " + difficulty + ") 입장.");

        handler.sendPacket(new GamePacket(GamePacket.Type.MESSAGE, "SERVER",
                handler.playerName + " 님 환영합니다!"));

        broadcast(new GamePacket(GamePacket.Type.MESSAGE, "SERVER",
                handler.playerName + " 님이 입장했습니다."));

        broadcast(new GamePacket(GamePacket.Type.SCORE, getScoreboardString()));
        broadcastPlayerCount();

        if (currentRound == 0) {
            startNewRound(difficulty);
        } else {
            handler.sendPacket(new GamePacket(GamePacket.Type.ROUND_START,
                    currentRound,
                    gameLogic.getImagePath(difficulty, currentRound),
                    gameLogic.getOriginalAnswers(difficulty, currentRound),
                    gameLogic.getOriginalDimension(difficulty, currentRound)));
        }
    }

    private synchronized void handlePacket(ClientHandler handler, GamePacket packet) {
        String difficulty = handler.difficulty;

        switch (packet.getType()) {
            case CLICK:
                int answerIndex = packet.getAnswerIndex();
                System.out.println("[서버] " + handler.playerName + " 클릭: " + answerIndex + "번");

                boolean isCorrect = gameLogic.checkAnswer(difficulty, currentRound, answerIndex);

                String resultMsg;
                if (isCorrect) {
                    resultMsg = "정답!";
                    scores.put(handler.playerName, scores.get(handler.playerName) + 10);
                    expMap.put(handler.playerName, expMap.getOrDefault(handler.playerName, 0) + 10);
                } else {
                    resultMsg = "오답 (또는 이미 찾음)!";
                    scores.put(handler.playerName, scores.get(handler.playerName) - 5);
                }

                broadcast(new GamePacket(GamePacket.Type.RESULT,
                        handler.playerName, answerIndex, isCorrect, resultMsg));

                broadcast(new GamePacket(GamePacket.Type.SCORE, getScoreboardString()));

                if (isCorrect && gameLogic.areAllFound(difficulty, currentRound)) {
                    System.out.println("[서버] " + currentRound + "라운드 클리어!");
                    broadcast(new GamePacket(GamePacket.Type.TIMER_END, "모든 정답 완료"));
                    broadcast(new GamePacket(GamePacket.Type.GAME_OVER, "🎯 라운드 클리어!"));
                    // 연결은 유지하고 클라이언트가 메뉴로 돌아감
                }
                break;

            case MESSAGE:
                System.out.println("[서버] " + packet.getSender() + " 메시지: " + packet.getMessage());
                broadcast(packet);
                break;

            case MYPAGE_REQUEST:
                int exp = expMap.getOrDefault(handler.playerName, 0);
                int level = exp / 50 + 1;
                handler.sendPacket(new GamePacket(GamePacket.Type.MYPAGE_DATA,
                        "SERVER", exp, level, true));
                break;

            default:
                System.out.println("[서버] 알 수 없는 패킷 타입: " + packet.getType());
                break;
        }
    }

    private synchronized void startNewRound(String difficulty) {
        currentRound++;
        System.out.println("[서버] " + currentRound + " 라운드 시작 (" + difficulty + ")");

        gameLogic.loadRound(difficulty, currentRound);

        broadcast(new GamePacket(GamePacket.Type.ROUND_START,
                currentRound,
                gameLogic.getImagePath(difficulty, currentRound),
                gameLogic.getOriginalAnswers(difficulty, currentRound),
                gameLogic.getOriginalDimension(difficulty, currentRound)));
    }

    private synchronized void broadcast(GamePacket packet) {
        for (ClientHandler handler : clients.values()) {
            handler.sendPacket(packet);
        }
    }

    private synchronized String getScoreboardString() {
        StringBuilder sb = new StringBuilder();
        sb.append("--- 점수판 ---\n");
        for (Map.Entry<String, Integer> entry : scores.entrySet()) {
            sb.append(entry.getKey()).append(" : ").append(entry.getValue()).append("점\n");
        }
        return sb.toString();
    }

    private void broadcastPlayerCount() {
        int count = clients.size();
        broadcast(new GamePacket(GamePacket.Type.PLAYER_COUNT, "현재 접속자 수: " + count + "명"));
    }

    public static void main(String[] args) {
        new HiddenObjectServer().run();
    }
}