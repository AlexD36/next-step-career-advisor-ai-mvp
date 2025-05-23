package com.springboot.chatgpt.service;

import com.springboot.chatgpt.dto.InterviewChatRequest;
import com.springboot.chatgpt.dto.InterviewResponse;
import com.springboot.chatgpt.dto.PromptRequest;
import com.springboot.chatgpt.dto.StartInterviewRequest;
import com.springboot.chatgpt.model.InterviewMessage;
import com.springboot.chatgpt.model.InterviewSession;
import com.springboot.chatgpt.model.User;
import com.springboot.chatgpt.repository.InterviewMessageRepository;
import com.springboot.chatgpt.repository.InterviewSessionRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class InterviewService {
    private final InterviewSessionRepository sessionRepo;
    private final InterviewMessageRepository messageRepo;
    private final ChatGPTService chatGPTService;

    public InterviewService(InterviewSessionRepository sessionRepo, InterviewMessageRepository messageRepo, ChatGPTService chatGPTService) {
        this.sessionRepo = sessionRepo;
        this.messageRepo = messageRepo;
        this.chatGPTService = chatGPTService;
    }

    public InterviewSession startInterview(StartInterviewRequest request, User user) {
        InterviewSession session = new InterviewSession();
        session.setJobTitle(request.jobTitle());
        session.setStartedAt(LocalDateTime.now());
        session.setUser(user);
        session.setScore(0);

        String firstQuestionPrompt = "Simulează un interviu pentru poziția de " + request.jobTitle() +
                ". Ce întrebare ai pune prima candidatului?";
        String firstQuestion = chatGPTService.getChatReponse(new PromptRequest(firstQuestionPrompt));
        session.setFirstQuestion(firstQuestion);

        return sessionRepo.save(session);
    }

    public InterviewResponse sendMessage(InterviewChatRequest request) {
        InterviewSession session = sessionRepo.findById(request.sessionId())
                .orElseThrow(() -> new RuntimeException("Session not found"));

        InterviewMessage userMsg = new InterviewMessage();
        userMsg.setRole("USER");
        userMsg.setContent(request.userMessage());
        userMsg.setSession(session);
        messageRepo.save(userMsg);

        String prompt = String.format("""
        Simuleaza un interviu pentru jobul: %s.
        Candidatului i s-a pus o intrebare și a raspuns:
        
        "%s"
        
        Vreau să imi spui:
        1. Ce parere ai despre raspuns (scurt feedback)
        2. Ce intrebare de interviu ai pune în continuare
        3. Un scor de la 1 la 10 pentru raspunsul de mai sus.
        """, session.getJobTitle(), request.userMessage());

        String gptReply = chatGPTService.getChatReponse(new PromptRequest(prompt));

        int score = extractScoreFromResponse(gptReply);
        String feedback = extractFeedbackFromResponse(gptReply);

        InterviewMessage aiMsg = new InterviewMessage();
        aiMsg.setRole("AI");
        aiMsg.setContent(gptReply);
        aiMsg.setSession(session);
        messageRepo.save(aiMsg);

        session.setScore(score);
        sessionRepo.save(session);

        return new InterviewResponse(gptReply, score, feedback);
    }

    private int extractScoreFromResponse(String response) {
        var matcher = java.util.regex.Pattern.compile("(?i)scor\\D*(\\d{1,2})")
                .matcher(response);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        return 0;
    }
    private String extractFeedbackFromResponse(String response) {
        String[] lines = response.split("\n");
        for (String line : lines) {
            if (line.toLowerCase().contains("parere") || line.toLowerCase().contains("feedback")) {
                return line.trim();
            }
            if (line.trim().startsWith("1.")) {
                return line.trim().substring(2).trim();
            }
        }
        return "Indisponible feedback.";
    }


}
