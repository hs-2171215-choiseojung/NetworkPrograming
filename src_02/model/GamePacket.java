package model;

import java.io.Serializable;
import java.awt.Rectangle; // (추가)
import java.awt.Dimension; // (추가)
import java.util.List; // (추가)

/**
 * (수정) HiddenObjectGame 로직 (클라이언트 판정)에 맞게 패킷 구조 변경
 */
public class GamePacket implements Serializable {

    private static final long serialVersionUID = 1L;

    public enum Type {
        JOIN,         // [C->S] 접속
        CLICK,        // [C->S] 정답 클릭 (인덱스)
        MESSAGE,      // [C<->S] 채팅
        ROUND_START,  // [S->C] 라운드 시작 (이미지, 정답 목록, 원본 크기)
        RESULT,       // [S->C] 클릭 결과 (인덱스, 정답/오답)
        SCORE,        // [S->C] 점수판
        TIMER_END,    // [S->C] 타이머 종료
        GAME_OVER     // [S->C] 게임 종료
    }

    // --- 데이터 필드 ---
    private final Type type;
    private final String sender;
    private String message;
    private int round;
    
    // (수정) CLICK / RESULT 용
    private int answerIndex; 
    private boolean correct;
    
    // (수정) ROUND_START 용
    private List<Rectangle> originalAnswers; // 원본 정답 목록
    private Dimension originalDimension; // 원본 이미지 크기

    // (수정) 좌표(x,y)와 패널 크기(w,h) 필드 제거됨

    // --- 생성자 ---

    // 1. JOIN (접속)
    public GamePacket(Type type, String sender, String difficulty, boolean isJoin) {
        this.type = type;
        this.sender = sender;
        this.message = difficulty; 
    }

    // 2. CLICK (수정) -> answerIndex
    public GamePacket(Type type, String sender, int answerIndex) {
        this.type = type;
        this.sender = sender;
        this.answerIndex = answerIndex;
    }

    // 3. MESSAGE (채팅, 힌트)
    public GamePacket(Type type, String sender, String message) {
        this.type = type;
        this.sender = sender;
        this.message = message;
    }
    
    // 4. (서버용) RESULT (클릭 결과) (수정) -> x, y 제거
    public GamePacket(Type type, String sender, int answerIndex, boolean correct, String message) {
        this.type = type;
        this.sender = sender;
        this.answerIndex = answerIndex;
        this.correct = correct;
        this.message = message; 
    }

    // 5. (서버용) ROUND_START (라운드 시작) (수정) -> List<Rectangle>, Dimension 추가
    public GamePacket(Type type, int round, String imagePath, List<Rectangle> originalAnswers, Dimension originalDimension) {
        this.type = type;
        this.sender = "SERVER";
        this.round = round;
        this.message = imagePath; // message 필드에 이미지 경로 저장
        this.originalAnswers = originalAnswers;
        this.originalDimension = originalDimension;
    }
    
    // 6. (서버용) SCORE, TIMER_END, GAME_OVER 등
    public GamePacket(Type type, String message) {
        this.type = type;
        this.sender = "SERVER";
        this.message = message;
    }

    // --- Getter 메소드 ---
    public Type getType() { return type; }
    public String getSender() { return sender; }
    public String getMessage() { return message; }
    public int getRound() { return round; }
    public int getAnswerIndex() { return answerIndex; }
    public boolean isCorrect() { return correct; }
    public List<Rectangle> getOriginalAnswers() { return originalAnswers; }
    public Dimension getOriginalDimension() { return originalDimension; }
}