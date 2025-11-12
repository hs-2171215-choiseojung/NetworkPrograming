package model;
import java.io.Serializable;

public class GamePacket implements Serializable {
    public enum Type {
        JOIN, MESSAGE, CLICK,
        ROUND_START, RESULT, SCORE,
        TIMER_END, GAME_OVER,
        MYPAGE_REQUEST, MYPAGE_DATA,
        PLAYER_COUNT
        
    }
    

    private Type type;
    private String sender;
    private String message;
    private int x, y;
    private boolean correct;
    private int round;
    private String difficulty;
    private String mode;
    private int exp;
    private int level;

   

    public GamePacket(Type type, String sender, String message) {
        this.type = type; this.sender = sender; this.message = message;
    }
    public GamePacket(Type type, String sender, int x, int y) {
        this.type = type; this.sender = sender; this.x = x; this.y = y;
    }
    public GamePacket(Type type, String sender, String message, int round) {
        this.type = type; this.sender = sender; this.message = message; this.round = round;
    }
    public GamePacket(Type type, String sender, String difficulty, String mode, boolean joinMarker) {
        this.type = type; this.sender = sender; this.difficulty = difficulty; this.mode = mode;
    }
    public GamePacket(Type type, String sender, String message, boolean correct, int x, int y) {
        this.type = type; this.sender = sender; this.message = message; this.correct = correct;
        this.x = x; this.y = y;
    }
    public GamePacket(Type type, String sender, int exp, int level, boolean mypageMarker) {
        this.type = type; this.sender = sender; this.exp = exp; this.level = level;
    }

    public Type getType() { return type; }
    public String getSender() { return sender; }
    public String getMessage() { return message; }
    public int getX() { return x; }
    public int getY() { return y; }
    public boolean isCorrect() { return correct; }
    public int getRound() { return round; }
    public String getDifficulty() { return difficulty; }
    public String getMode() { return mode; }
    public int getExp() { return exp; }
    public int getLevel() { return level; }
}
