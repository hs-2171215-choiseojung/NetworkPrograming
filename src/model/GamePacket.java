package model;
import java.io.Serializable;

public class GamePacket implements Serializable {
    public enum Type {
        JOIN, MESSAGE, CLICK, ROUND_START, RESULT, SCORE, TIMER_END, GAME_OVER
    }

    private Type type;
    private String sender;
    private String message;
    private int x, y;
    private int round;
    private boolean correct;
    private String difficulty;

    // --- 생성자들 ---
    public GamePacket(Type type, String sender, String message) {
        this.type = type;
        this.sender = sender;
        this.message = message;
    }

    public GamePacket(Type type, String sender, int x, int y) {
        this.type = type;
        this.sender = sender;
        this.x = x;
        this.y = y;
    }

    public GamePacket(Type type, String sender, String message, int round) {
        this.type = type;
        this.sender = sender;
        this.message = message;
        this.round = round;
    }

    public GamePacket(Type type, String sender, String difficulty, boolean dummy) {
        this.type = type;
        this.sender = sender;
        this.difficulty = difficulty;
    }

    // ✅ 새 생성자 (결과 전송용)
    public GamePacket(Type type, String sender, String message, boolean correct, int x, int y) {
        this.type = type;
        this.sender = sender;
        this.message = message;
        this.correct = correct;
        this.x = x;
        this.y = y;
    }

    // --- Getter ---
    public Type getType() { return type; }
    public String getSender() { return sender; }
    public String getMessage() { return message; }
    public int getX() { return x; }
    public int getY() { return y; }
    public int getRound() { return round; }
    public boolean isCorrect() { return correct; }
    public String getDifficulty() { return difficulty; }
}
