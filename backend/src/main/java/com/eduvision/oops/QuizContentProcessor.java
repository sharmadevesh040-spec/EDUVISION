package com.eduvision.oops;

import org.springframework.stereotype.Component;

/** Content processor for quiz content - overrides the shared behaviour (polymorphism). */
@Component
public class QuizContentProcessor extends AbstractContentProcessor {

    @Override
    public String kind() {
        return "quiz";
    }

    @Override
    public String describe(int parts, int questions) {
        return describeWith("assesses understanding with " + questions + " graded questions", parts, questions);
    }
}
