package server;

import model.GamePacket;

import java.awt.Point;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 숨은 그림 찾기 서버
 * - 여러 클라이언트 접속
 * - 클릭 좌표 판정
 * - 점수 관리, 라운드 시작/종료 브로드캐스트
 */
public class GameServer {

    private final int port;
    private ServerSocket serverSocket;

    // 접속한 클라이언트들
    private final List<ClientHandler> clients =
            Collections.synchronizedList(new ArrayList<>());

    // 플레이어별 점수
    private final Map<String, Integer> scores = new ConcurrentHashMap<>();

    // 정답 좌표 & 이미 찾은 좌표
    private final List<Point> answers = new ArrayList<>();
    private final Set<Point> foundAnswers = new HashSet<>();

    private static final int RANGE = 40; // 정답 판정 거리
    private int currentRound = 1;
    private String difficulty = "쉬움";

    // 라운드 시간 (초)
    private static final int ROUND_TIME = 120;

    public GameServer(int port) {
        this.port = port;
        initAnswers(); // 일단 쉬움 1라운드 좌표
    }

    // 예시 정답 좌표 (쉬움 1라운드)
    private void initAnswers() {
        answers.clear();
        foundAnswers.clear();

        // ✅ 여기 좌표를 “네가 클릭해서 얻은 값”으로 다시 정확히 넣기
        answers.add(new Point(177, 281)); // 예시: 모자
        answers.add(new Point(156, 191)); // 예시: 사과
        answers.add(new Point(423, 355)); // 예시: 열쇠
        answers.add(new Point(313, 100)); // 예시: 시계

        // 🔍 디버깅용 출력
        System.out.println("[서버] 정답 좌표 리스트:");
        for (Point p : answers) {
            System.out.println("  정답: (" + p.x + ", " + p.y + ")");
        }
    }

    public void start() throws IOException {
        serverSocket = new ServerSocket(port);
        System.out.println("[서버] 숨은 그림 찾기 서버 시작, port=" + port);

        // 첫 라운드 시작
        startRound();

        while (true) {
            Socket socket = serverSocket.accept();
            System.out.println("[서버] 클라이언트 접속: " + socket);

            ClientHandler handler = new ClientHandler(socket);
            clients.add(handler);
            handler.start();
        }
    }

    // 라운드 시작 브로드캐스트 + 타이머 스레드
    private void startRound() {
        initAnswers(); // 라운드 시작 시 정답 초기화 (라운드별로 다르게 만들고 싶으면 여기서 분기)

        GamePacket startPacket = new GamePacket(
                GamePacket.Type.ROUND_START,
                "SERVER",
                difficulty + " " + currentRound + "라운드 시작!",
                currentRound
        );
        broadcast(startPacket);

        // 타이머 스레드
        new Thread(() -> {
            try {
                Thread.sleep(ROUND_TIME * 1000L);
                // 라운드 시간 종료 알림
                GamePacket timerEnd = new GamePacket(
                        GamePacket.Type.TIMER_END,
                        "SERVER",
                        "시간 종료!"
                );
                broadcast(timerEnd);

                // 여기서 바로 다음 라운드로 넘어가거나,
                // 한 라운드만 하고 게임 종료할 수도 있음
                // 예시는 한 라운드 끝나면 게임 종료
                GamePacket gameOver = new GamePacket(
                        GamePacket.Type.GAME_OVER,
                        "SERVER",
                        "게임이 종료되었습니다."
                );
                broadcast(gameOver);

            } catch (InterruptedException e) {
                // 필요하면 로그
            }
        }).start();
    }

    // 모든 클라이언트에게 패킷 전송
    private void broadcast(GamePacket packet) {
        synchronized (clients) {
            for (ClientHandler c : clients) {
                c.sendPacket(packet);
            }
        }
    }

    // 점수판 문자열 만들어서 SCORE 패킷으로 브로드캐스트
    private void broadcastScore() {
        StringBuilder sb = new StringBuilder();
        // A/B 두 명만 있다고 가정할 수 있지만, 그냥 전체 플레이어 출력
        for (Map.Entry<String, Integer> e : scores.entrySet()) {
            sb.append(e.getKey())
              .append(" : ")
              .append(e.getValue())
              .append("점\n");
        }
        GamePacket scorePacket = new GamePacket(
                GamePacket.Type.SCORE,
                "SERVER",
                sb.toString()
        );
        broadcast(scorePacket);
    }

    // 클릭이 정답인지 판정 + 점수 처리
    private boolean handleClick(String player, int x, int y) {
        Point click = new Point(x, y);
        System.out.println("[서버] 클릭 좌표: (" + x + ", " + y + ")");
        System.out.println("  현재 정답 개수: " + answers.size() + ", RANGE = " + RANGE);

        if (answers.isEmpty()) {
            System.out.println("  ⚠ answers 리스트가 비어 있습니다! (항상 오답 처리됨)");
            return false;
        }

        for (Point ans : answers) {
            double dist = ans.distance(click);
            System.out.printf("  -> 정답 (%d, %d) 까지 거리: %.2f%n", ans.x, ans.y, dist);

            if (dist < RANGE) {
                System.out.println("  ✅ 거리 조건 만족! 정답 처리");

                synchronized (foundAnswers) {
                    if (foundAnswers.contains(ans)) {
                        System.out.println("  이미 찾은 정답 좌표입니다. (중복 클릭)");
                        return false; // 이미 맞춘 곳은 더 이상 점수 X
                    }
                    foundAnswers.add(ans);
                }

                // 점수 +1
                scores.putIfAbsent(player, 0);
                scores.put(player, scores.get(player) + 1);
                broadcastScore();

                // 모든 정답 찾았는지 체크
                if (foundAnswers.size() == answers.size()) {
                    GamePacket timerEnd = new GamePacket(
                            GamePacket.Type.TIMER_END,
                            "SERVER",
                            "모든 정답을 찾았습니다! 라운드 종료!"
                    );
                    broadcast(timerEnd);

                    GamePacket gameOver = new GamePacket(
                            GamePacket.Type.GAME_OVER,
                            "SERVER",
                            "게임이 종료되었습니다."
                    );
                    broadcast(gameOver);
                }

                return true;
            }
        }

        System.out.println("  ❌ 어떤 정답과도 RANGE 이내가 아님 → 오답");
        return false;
    }

    // ================= 클라이언트 핸들러 =================
    private class ClientHandler extends Thread {
        private final Socket socket;
        private ObjectOutputStream out;
        private ObjectInputStream in;
        private String playerName = "Unknown";

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                out = new ObjectOutputStream(socket.getOutputStream());
                in  = new ObjectInputStream(socket.getInputStream());

                while (true) {
                    Object obj = in.readObject();
                    if (!(obj instanceof GamePacket)) continue;
                    GamePacket p = (GamePacket) obj;

                    switch (p.getType()) {
                        case JOIN:
                            handleJoin(p);
                            break;
                        case MESSAGE:
                            handleMessage(p);
                            break;
                        case CLICK:
                            handleClickPacket(p);
                            break;
                        default:
                            // 나머지는 서버에서 생성해서 클라로 보내는 타입이므로 여기선 안씀
                            break;
                    }
                }
            } catch (Exception e) {
                System.out.println("[서버] 클라이언트 종료: " + playerName);
            } finally {
                try { socket.close(); } catch (IOException ignored) {}
                clients.remove(this);
            }
        }

        private void handleJoin(GamePacket p) {
            this.playerName = p.getSender();
            scores.putIfAbsent(playerName, 0);
            difficulty = p.getDifficulty() != null ? p.getDifficulty() : "쉬움";

            System.out.println("[서버] JOIN: " + playerName + " (" + difficulty + ")");

            // 환영 메시지
            GamePacket welcome = new GamePacket(
                    GamePacket.Type.MESSAGE,
                    "SERVER",
                    playerName + " 님이 입장했습니다."
            );
            broadcast(welcome);

            // 현재 점수판 한번 보내주기
            broadcastScore();
        }

        private void handleMessage(GamePacket p) {
            // 채팅/힌트 요청 등은 그냥 모두에게 브로드캐스트
            GamePacket msg = new GamePacket(
                    GamePacket.Type.MESSAGE,
                    p.getSender(),
                    p.getMessage()
            );
            broadcast(msg);
        }

        private void handleClickPacket(GamePacket p) {
            int x = p.getX();
            int y = p.getY();
            String name = p.getSender();

            System.out.println("[서버] CLICK 패킷 받음: " + name + " (" + x + ", " + y + ")");

            boolean correct = handleClick(name, x, y);

            String msg = correct ? "정답!" : "오답";
            GamePacket resultPacket = new GamePacket(
                    GamePacket.Type.RESULT,
                    name,
                    msg,
                    correct,
                    x,
                    y
            );
            broadcast(resultPacket);

            System.out.println("[서버] 판정 결과: " + msg);
        }


        public void sendPacket(GamePacket packet) {
            try {
                if (out != null) {
                    out.writeObject(packet);
                    out.flush();
                }
            } catch (IOException e) {
                System.out.println("[서버] sendPacket 실패: " + e.getMessage());
            }
        }
    }

    // ================= main =================
    public static void main(String[] args) {
        try {
            GameServer server = new GameServer(9999);
            server.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
