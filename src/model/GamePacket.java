package model;

import java.io.Serializable;
import java.awt.Rectangle;
import java.awt.Dimension;
import java.util.List;

public class GamePacket implements Serializable {
    private static final long serialVersionUID = 1L;
    
    public enum Type {
        JOIN,
        CLICK,
        MESSAGE,
        ROUND_START,
        RESULT,
        SCORE,
        TIMER_END,
        GAME_OVER,
        MYPAGE_REQUEST,
        MYPAGE_DATA,
        PLAYER_COUNT
    }
    
    // 공통 필드
    private final Type type;
    private final String sender;
    private String message;
    private int round;
    
    // 게임 로직용
    private int answerIndex;
    private boolean correct;
    private List<Rectangle> originalAnswers;
    private Dimension originalDimension;
    
    // 마이페이지용
    private int exp;
    private int level;
    
    // 기타
    private String difficulty;
    private String mode;

    // 생성자 1: JOIN
    public GamePacket(Type type, String sender, String difficulty, String mode, boolean joinMarker) {
        this.type = type;
        this.sender = sender;
        this.difficulty = difficulty;
        this.mode = mode;
    }
    
    // 생성자 2: CLICK
    public GamePacket(Type type, String sender, int answerIndex) {
        this.type = type;
        this.sender = sender;
        this.answerIndex = answerIndex;
    }
    
    // 생성자 3: MESSAGE
    public GamePacket(Type type, String sender, String message) {
        this.type = type;
        this.sender = sender;
        this.message = message;
    }
    
    // 생성자 4: RESULT
    public GamePacket(Type type, String sender, int answerIndex, boolean correct, String message) {
        this.type = type;
        this.sender = sender;
        this.answerIndex = answerIndex;
        this.correct = correct;
        this.message = message;
    }
    
    // 생성자 5: ROUND_START (완전판)
    public GamePacket(Type type, int round, String imagePath, 
                      List<Rectangle> originalAnswers, Dimension originalDimension) {
        this.type = type;
        this.sender = "SERVER";
        this.round = round;
        this.message = imagePath;
        this.originalAnswers = originalAnswers;
        this.originalDimension = originalDimension;
    }
    
    // 생성자 6: ROUND_START (간단판)
    public GamePacket(Type type, String sender, String message, int round) {
        this.type = type;
        this.sender = sender;
        this.message = message;
        this.round = round;
    }
    
    // 생성자 7: MYPAGE_DATA
    public GamePacket(Type type, String sender, int exp, int level, boolean mypageMarker) {
        this.type = type;
        this.sender = sender;
        this.exp = exp;
        this.level = level;
    }
    
    // 생성자 8: 단순 메시지
    public GamePacket(Type type, String message) {
        this.type = type;
        this.sender = "SERVER";
        this.message = message;
    }

    // Getter 메서드
    public Type getType() { return type; }
    public String getSender() { return sender; }
    public String getMessage() { return message; }
    public int getRound() { return round; }
    public int getAnswerIndex() { return answerIndex; }
    public boolean isCorrect() { return correct; }
    public List<Rectangle> getOriginalAnswers() { return originalAnswers; }
    public Dimension getOriginalDimension() { return originalDimension; }
    public int getExp() { return exp; }
    public int getLevel() { return level; }
    public String getDifficulty() { return difficulty; }
    public String getMode() { return mode; }
}