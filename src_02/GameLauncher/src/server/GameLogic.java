package server;

import javax.imageio.ImageIO;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

// 정답(Rectangle)을 서버에 '수동'으로 입력, 
public class GameLogic {
    private Map<String, List<Rectangle>> roundAnswers = new HashMap<>();
    private Map<String, String> roundImagePaths = new HashMap<>();
    private Map<String, Dimension> originalDimensions = new HashMap<>();
    private Map<String, boolean[]> foundStatus = new HashMap<>(); 

    public GameLogic() throws IOException {
        // 생성자에서 '정답 목록 생성' 호출
        System.out.println("[GameLogic] '쉬움' 1라운드 정답 목록 로드 중...");
        loadAnswersFor_Easy_1(); 
    }

    private void loadAnswersFor_Easy_1() {
        String key = "쉬움_1";
        List<Rectangle> answers = new ArrayList<>();

        answers.add(new Rectangle(114-20, 197-20, 35, 35));   // 1. 나무 위 노란 장식 ㅇ
        answers.add(new Rectangle(161-20, 347-30, 40, 40));   // 2. 남자아이 손 빨간 장식
        answers.add(new Rectangle(232-35, 622-35, 60, 60));   // 3. 파란 상자 위 네모 장식 ㅇ
        answers.add(new Rectangle(451-40, 510-30, 60, 60));   // 4. 자동차 바퀴 빨간 장식
        answers.add(new Rectangle(240-35, 439-40, 60, 60));   // 5. 초록 상자 중앙 장식 ㅇ
        answers.add(new Rectangle(299-20, 503-30, 50, 50));   // 6. 여자아이 모자 사탕 장식ㅇ
        answers.add(new Rectangle(145-20, 621-25, 30, 30));    // 7. 강아지 목줄 장식
        answers.add(new Rectangle(325-20, 360-35, 25, 65));   // 8. 사다리 긴 장식
        answers.add(new Rectangle(420-30, 247-25, 45, 45));   // 9. 창문 속 둥근 장식
        answers.add(new Rectangle(494-30, 378-25, 45, 45));   // 10. 여자아이 옷 장식
        answers.add(new Rectangle(429-50, 567-25, 90, 40));   // 11. 오른쪽 통 위 장식
        answers.add(new Rectangle(223-45, 211-20, 80, 40));   // 12. 나무위 초록 모자 장식
        
        roundAnswers.put(key, answers);
        roundImagePaths.put(key, "images/image4.png"); // 클라이언트가 볼 이미지
        foundStatus.put(key, new boolean[answers.size()]);
        originalDimensions.put(key, new Dimension(924, 1146)); // image4.png의 '원본' 크기
    }

    public void loadRound(String difficulty, int round) {
        String key = difficulty + "_" + round;
        
        // 라운드 1 외에 다른 라운드/난이도 요청 시 동적 로드
        if (!roundAnswers.containsKey(key)) {
            System.out.println("[GameLogic] 경고: " + key + " 정보가 미리 로드되지 않아 동적 로드 시도...");
            try {
                 loadAnswersFromFile(difficulty, round);
            } catch (IOException e) {
                 System.out.println("[GameLogic] 동적 로드 실패: " + e.getMessage());
                 key = "쉬움_1"; 
            }
        }
        
        int count = roundAnswers.get(key).size();
        foundStatus.put(key, new boolean[count]);
        System.out.println("[GameLogic] " + key + " 라운드 정답 " + count + "개 상태 초기화.");
    }
    
    // 텍스트 파일에서 정답 로드
    private void loadAnswersFromFile(String difficulty, int round) throws IOException {
        String key = difficulty + "_" + round;
        String fileName = "answers/" + difficulty + "_" + round + ".txt";
        
        List<Rectangle> answers = new ArrayList<>();
        Dimension dim = new Dimension(800, 600); // 기본값
        File file = new File(fileName);
        
        if (!file.exists()) throw new IOException(fileName + " 파일이 없습니다.");

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            
            // 1. 첫 줄: 원본 크기
            if ((line = br.readLine()) != null) {
                String[] parts = line.split(",");
                dim = new Dimension(Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()));
            }
            originalDimensions.put(key, dim);

            // 2. 나머지: 정답 좌표
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] parts = line.split(",");
                answers.add(new Rectangle(
                    Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()),
                    Integer.parseInt(parts[2].trim()), Integer.parseInt(parts[3].trim())
                ));
            }
        } catch (Exception e) {
            throw new IOException(fileName + " 파일 형식 오류", e);
        }

        roundAnswers.put(key, answers);
        roundImagePaths.put(key, "images/" + difficulty + round + ".png"); // 예: "images/보통1.png"
        foundStatus.put(key, new boolean[answers.size()]);
        System.out.println("[GameLogic] " + key + " 정답 " + answers.size() + "개 (파일) 로드 완료.");
    }
    
    public String getImagePath(String difficulty, int round) {
        String key = difficulty + "_" + round;
        return roundImagePaths.getOrDefault(key, "images/image4.png");
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