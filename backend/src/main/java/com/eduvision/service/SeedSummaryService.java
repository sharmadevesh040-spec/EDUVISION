package com.eduvision.service;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.eduvision.repository.ArContentRepository;
import com.eduvision.repository.AssignmentRepository;
import com.eduvision.repository.ClassMemberRepository;
import com.eduvision.repository.ContentPartRepository;
import com.eduvision.repository.EduClassRepository;
import com.eduvision.repository.QuizQuestionRepository;
import com.eduvision.repository.UserRepository;

/**
 * Read-only row counts across the whole domain, used to prove the seed landed.
 */
@Service
public class SeedSummaryService {

    private final UserRepository userRepository;
    private final EduClassRepository eduClassRepository;
    private final ClassMemberRepository classMemberRepository;
    private final ArContentRepository arContentRepository;
    private final ContentPartRepository contentPartRepository;
    private final QuizQuestionRepository quizQuestionRepository;
    private final AssignmentRepository assignmentRepository;

    public SeedSummaryService(UserRepository userRepository,
                              EduClassRepository eduClassRepository,
                              ClassMemberRepository classMemberRepository,
                              ArContentRepository arContentRepository,
                              ContentPartRepository contentPartRepository,
                              QuizQuestionRepository quizQuestionRepository,
                              AssignmentRepository assignmentRepository) {
        this.userRepository = userRepository;
        this.eduClassRepository = eduClassRepository;
        this.classMemberRepository = classMemberRepository;
        this.arContentRepository = arContentRepository;
        this.contentPartRepository = contentPartRepository;
        this.quizQuestionRepository = quizQuestionRepository;
        this.assignmentRepository = assignmentRepository;
    }

    /**
     * Counts, with the six headline keys first in a stable order.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> summary() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("users", userRepository.count());
        out.put("classes", eduClassRepository.count());
        out.put("contents", arContentRepository.count());
        out.put("parts", contentPartRepository.count());
        out.put("questions", quizQuestionRepository.count());
        out.put("assignments", assignmentRepository.count());
        // extra detail
        out.put("classMembers", classMemberRepository.count());
        return out;
    }
}