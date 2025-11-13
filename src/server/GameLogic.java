package server;

import java.awt.Dimension;
import java.awt.Rectangle;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GameLogic {

    private Map<String, List<Rectangle>> roundAnswers = new HashMap<>();
    private Map<String, String> roundImagePaths = new HashMap<>();
    private Map<String, Dimension> originalDimensions = new HashMap<>();
    private Map<String, boolean[]> foundStatus = new HashMap<>();

    public GameLogic() throws IOException {
        System.out.println("[GameLogic] '쉬움' 1라운드 정답 목록 로드 중...");
        loadAnswersFor_Easy_1();
    }

    private void loadAnswersFor_Easy_1() {
        String key = "쉬움_1";
        List<Rectangle> answers = new ArrayList<>();

        answers.add(new Rectangle(350-20, 407-20, 60, 60));
        answers.add(new Rectangle(399-20, 619-20, 60, 60));
        answers.add(new Rectangle(706-20, 215-20, 60, 60));
        answers.add(new Rectangle(957-20, 780-20, 60, 60));
        

        
        roundAnswers.put(key, answers);
        roundImagePaths.put(key, "C:/Users/user/Desktop/projectD/src/images/kh0138-01.jpg");
        foundStatus.put(key, new boolean[answers.size()]);
        originalDimensions.put(key, new Dimension(850,1202));
    }

    public void loadRound(String difficulty, int round) {
        String key = difficulty + "_" + round;
        if (roundAnswers.containsKey(key)) {
            int count = roundAnswers.get(key).size();
            foundStatus.put(key, new boolean[count]);
            System.out.println("[GameLogic] " + key + " 라운드 정답 " + count + "개 상태 초기화.");
        } else {
            System.out.println("[GameLogic] 경고: " + key + " 라운드 정보가 미리 로드되지 않았습니다.");
        }
    }

    public String getImagePath(String difficulty, int round) {
        String key = difficulty + "_" + round;
        return roundImagePaths.getOrDefault(key, "C:/Users/user/Desktop/projectD/src/images/kh0138-01.jpg");
    }

    public List<Rectangle> getOriginalAnswers(String difficulty, int round) {
        String key = difficulty + "_" + round;
        return roundAnswers.get(key);
    }

    public Dimension getOriginalDimension(String difficulty, int round) {
        String key = difficulty + "_" + round;
        return originalDimensions.get(key);
    }

    public synchronized boolean checkAnswer(String difficulty, int round, int answerIndex) {
        String key = difficulty + "_" + round;
        boolean[] found = foundStatus.get(key);

        if (found == null || answerIndex < 0 || answerIndex >= found.length) {
            System.out.println("[GameLogic] 판정 오류: 잘못된 인덱스 " + answerIndex);
            return false;
        }

        if (found[answerIndex]) {
            System.out.println("[GameLogic] " + key + " " + answerIndex + "번은 이미 찾음.");
            return false;
        }

        found[answerIndex] = true;
        System.out.println("[GameLogic] " + key + " 정답 " + answerIndex + "번 찾음!");
        return true;
    }

    public boolean areAllFound(String difficulty, int round) {
        String key = difficulty + "_" + round;
        boolean[] found = foundStatus.get(key);
        if (found == null) return false;
        for (boolean f : found) {
            if (!f) return false;
        }
        return true;
    }
}