package server;

import model.GamePacket;
import java.awt.Point;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class GameServer {
    private final int port;
    private ServerSocket serverSocket;

    private final List<ClientHandler> clients = Collections.synchronizedList(new ArrayList<>());
    private final Map<String, Integer> scores = new ConcurrentHashMap<>();
    private final Map<String, Integer> expMap = new ConcurrentHashMap<>();

    private final List<Point> answers = new ArrayList<>();
    private final Set<Point> foundAnswers = Collections.synchronizedSet(new HashSet<>());
    private static final int RANGE = 40;
    private int currentRound = 1;

    public GameServer(int port) {
        this.port = port;
        initAnswers();
    }

    // 정답 초기화
    private void initAnswers() {
        answers.clear();
        foundAnswers.clear();
        answers.add(new Point(177, 281));
        answers.add(new Point(156, 191));
        answers.add(new Point(423, 355));
        answers.add(new Point(313, 100));
        System.out.println("[서버] 정답 좌표 초기화 완료");
    }

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

    private boolean isDuplicateName(String name) {
        synchronized (clients) {
            for (ClientHandler c : clients)
                if (name.equals(c.playerName)) return true;
        }
        return false;
    }

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

    private boolean handleClick(String player, int x, int y) {
        Point click = new Point(x, y);
        for (Point ans : answers) {
            if (ans.distance(click) < RANGE) {
                synchronized (foundAnswers) {
                    if (foundAnswers.contains(ans)) return false;
                    foundAnswers.add(ans);
                }
                scores.put(player, scores.getOrDefault(player, 0) + 1);
                expMap.put(player, expMap.getOrDefault(player, 0) + 10);
                broadcastScore();

                if (foundAnswers.size() == answers.size()) {
                    broadcast(new GamePacket(GamePacket.Type.TIMER_END, "SERVER", "모든 정답 완료"));
                    broadcast(new GamePacket(GamePacket.Type.GAME_OVER, "SERVER", "게임이 종료되었습니다."));

                    // 새 게임 준비 (정답/점수 초기화)
                    resetGameState();
                }
                return true;
            }
        }
        return false;
    }

    // 게임 상태 초기화 함수
    private void resetGameState() {
        foundAnswers.clear();
        initAnswers();
        scores.clear();
        System.out.println("[서버] 새 게임 준비 완료 (모든 점수, 정답 초기화)");
    }

    private class ClientHandler extends Thread {
        private final Socket socket;
        private ObjectOutputStream out;
        private ObjectInputStream in;
        private String playerName = "";

        ClientHandler(Socket socket) { this.socket = socket; }

        @Override
        public void run() {
            try {
                out = new ObjectOutputStream(socket.getOutputStream());
                in  = new ObjectInputStream(socket.getInputStream());

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
                    broadcast(new GamePacket(GamePacket.Type.MESSAGE, "SERVER", playerName + " 님이 퇴장했습니다."));
                    broadcastPlayerCount();
                }
            }
        }

        private void handleJoin(GamePacket p) {
            String name = p.getSender();
            if (isDuplicateName(name)) {
                sendPacket(new GamePacket(GamePacket.Type.MESSAGE, "SERVER", "[중복] 닉네임이 이미 존재합니다."));
                return;
            }

            playerName = name;
           
            scores.put(playerName, 0);
            expMap.putIfAbsent(playerName, 0);

          
            initAnswers();

            sendPacket(new GamePacket(GamePacket.Type.MESSAGE, "SERVER", "[확인] 닉네임 사용 가능"));
            System.out.println("[서버] 접속: " + name + " (" + p.getMode() + "/" + p.getDifficulty() + ")");
            broadcast(new GamePacket(GamePacket.Type.MESSAGE, "SERVER", name + " 님이 입장했습니다."));

            sendPacket(new GamePacket(GamePacket.Type.ROUND_START, "SERVER", "라운드 시작!", currentRound));
            broadcastScore();
            broadcastPlayerCount();
        }

        private void handleClickPacket(GamePacket p) {
            boolean ok = handleClick(p.getSender(), p.getX(), p.getY());
            broadcast(new GamePacket(GamePacket.Type.RESULT, p.getSender(),
                    ok ? "정답!" : "오답", ok, p.getX(), p.getY()));
        }

        private void handleMessage(GamePacket p) {
            broadcast(new GamePacket(GamePacket.Type.MESSAGE, p.getSender(), p.getMessage()));
        }

        private void handleMyPage(GamePacket p) {
            int exp = expMap.getOrDefault(p.getSender(), 0);
            int level = exp / 50 + 1;
            sendPacket(new GamePacket(GamePacket.Type.MYPAGE_DATA, "SERVER", exp, level, true));
        }

        void sendPacket(GamePacket packet) {
            try {
                if (out != null) {
                    out.writeObject(packet);
                    out.flush();
                }
            } catch (IOException ignored) {}
        }
    }

    private void broadcastPlayerCount() {
        int count = clients.size();
        GamePacket packet = new GamePacket(GamePacket.Type.PLAYER_COUNT, "SERVER", "현재 접속자 수: " + count + "명");
        broadcast(packet);
    }

    public static void main(String[] args) {
        try { new GameServer(9999).start(); }
        catch (IOException e) { e.printStackTrace(); }
    }
}
