package server;

import model.GamePacket;
import java.awt.Dimension;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class GameServer {
    private final int port;
    private ServerSocket serverSocket;

    // --- 클라이언트 관리 ---
    private final List<ClientHandler> clients = Collections.synchronizedList(new ArrayList<>());
    private final Map<String, Integer> scores = new ConcurrentHashMap<>();
    private final Map<String, Integer> expMap = new ConcurrentHashMap<>();

    // --- 라운드/정답 관리 ---
    private final GameLogic gameLogic;
    private int currentRound = 1;

    public GameServer(int port) throws IOException {
        this.port = port;
        this.gameLogic = new GameLogic();
        System.out.println("[서버] 정답 데이터 및 로직 초기화 완료");
    }

    // ------------------- 서버 시작 -------------------
    public void start() throws IOException {
        serverSocket = new ServerSocket(port);
        System.out.println("[서버] 숨은 그림 찾기 서버 시작 (port=" + port + ")");
        while (true) {
            Socket socket = serverSocket.accept();
            ClientHandler handler = new ClientHandler(socket);
            clients.add(handler);
            handler.start();
        }
    }

    // ------------------- 브로드캐스트 -------------------
    private void broadcast(GamePacket p) {
        synchronized (clients) {
            for (ClientHandler c : clients) c.sendPacket(p);
        }
    }

    private void broadcastScore() {
        StringBuilder sb = new StringBuilder("=== 점수판 ===\n");
        for (var e : scores.entrySet()) {
            sb.append(e.getKey()).append(" : ").append(e.getValue()).append("점\n");
        }
        broadcast(new GamePacket(GamePacket.Type.SCORE, "SERVER", sb.toString()));
    }

    private void broadcastPlayerCount() {
        int count = clients.size();
        GamePacket packet = new GamePacket(GamePacket.Type.PLAYER_COUNT, "SERVER",
                "현재 접속자 수: " + count + "명");
        broadcast(packet);
    }

    // ------------------- 내부 클래스: 클라이언트 핸들러 -------------------
    private class ClientHandler extends Thread {
        private final Socket socket;
        private ObjectOutputStream out;
        private ObjectInputStream in;
        private String playerName = "";
        private String difficulty = "쉬움";

        ClientHandler(Socket socket) { this.socket = socket; }

        @Override
        public void run() {
            try {
                out = new ObjectOutputStream(socket.getOutputStream());
                in = new ObjectInputStream(socket.getInputStream());

                while (true) {
                    Object obj = in.readObject();
                    if (!(obj instanceof GamePacket p)) continue;

                    switch (p.getType()) {
                        case JOIN -> handleJoin(p);
                        case CLICK -> handleClickPacket(p);
                        case MESSAGE -> handleMessage(p);
                        case MYPAGE_REQUEST -> handleMyPage(p);
                        default -> {}
                    }
                }
            } catch (Exception ignored) {
            } finally {
                clients.remove(this);
                try { socket.close(); } catch (IOException ignored) {}
                if (!playerName.isEmpty()) {
                    System.out.println("[서버] 종료: " + playerName);
                    broadcast(new GamePacket(GamePacket.Type.MESSAGE, "SERVER",
                            playerName + " 님이 퇴장했습니다."));
                    broadcastPlayerCount();
                }
            }
        }

        // --- JOIN ---
        private void handleJoin(GamePacket p) {
            String name = p.getSender();
            if (isDuplicateName(name)) {
                sendPacket(new GamePacket(GamePacket.Type.MESSAGE, "SERVER", "[중복] 닉네임이 이미 존재합니다."));
                return;
            }

            playerName = name;
            difficulty = p.getDifficulty() != null ? p.getDifficulty() : "쉬움";
            scores.put(playerName, 0);
            expMap.putIfAbsent(playerName, 0);

            System.out.println("[서버] 접속: " + name + " (" + difficulty + ")");
            sendPacket(new GamePacket(GamePacket.Type.MESSAGE, "SERVER", "[확인] 닉네임 사용 가능"));
            broadcast(new GamePacket(GamePacket.Type.MESSAGE, "SERVER", name + " 님이 입장했습니다."));
            broadcastPlayerCount();

            startNewRound(difficulty);
        }

        private boolean isDuplicateName(String name) {
            synchronized (clients) {
                for (ClientHandler c : clients)
                    if (name.equals(c.playerName)) return true;
            }
            return false;
        }

        // --- CLICK ---
        private void handleClickPacket(GamePacket p) {
            int answerIndex = p.getAnswerIndex();
            boolean correct = gameLogic.checkAnswer(difficulty, currentRound, answerIndex);

            if (correct) {
                scores.put(playerName, scores.getOrDefault(playerName, 0) + 10);
                expMap.put(playerName, expMap.getOrDefault(playerName, 0) + 10);
            } else {
                scores.put(playerName, scores.getOrDefault(playerName, 0) - 5);
            }

            broadcast(new GamePacket(GamePacket.Type.RESULT, playerName,
                    answerIndex, correct, correct ? "정답!" : "오답!"));
            broadcastScore();

            // 모든 정답 찾음
            if (correct && gameLogic.areAllFound(difficulty, currentRound)) {
                broadcast(new GamePacket(GamePacket.Type.TIMER_END, "SERVER", "모든 정답 완료"));
                broadcast(new GamePacket(GamePacket.Type.GAME_OVER, "SERVER", "🎯 라운드 종료!"));
                startNewRound(difficulty);
            }
        }

        // --- MESSAGE ---
        private void handleMessage(GamePacket p) {
            broadcast(new GamePacket(GamePacket.Type.MESSAGE, p.getSender(), p.getMessage()));
        }

        // --- MYPAGE ---
        private void handleMyPage(GamePacket p) {
            int exp = expMap.getOrDefault(p.getSender(), 0);
            int level = exp / 50 + 1;
            sendPacket(new GamePacket(GamePacket.Type.MYPAGE_DATA, "SERVER", exp, level, true));
        }

        // --- NEW ROUND ---
        private void startNewRound(String difficulty) {
            gameLogic.loadRound(difficulty, currentRound);
            broadcast(new GamePacket(GamePacket.Type.ROUND_START, currentRound,
                    gameLogic.getImagePath(difficulty, currentRound),
                    gameLogic.getOriginalAnswers(difficulty, currentRound),
                    gameLogic.getOriginalDimension(difficulty, currentRound)
            ));
        }

        // --- 유틸 ---
        void sendPacket(GamePacket packet) {
            try {
                if (out != null) {
                    out.writeObject(packet);
                    out.flush();
                }
            } catch (IOException ignored) {}
        }
    }

    // ------------------- main -------------------
    public static void main(String[] args) {
        try {
            new GameServer(9999).start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
