package com.eduvision.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * A multiple-choice AR question attached to an {@link ArContent}.
 * {@code options} is a single pipe-separated string, e.g. "a|left atrium|b|right atrium".
 */
@Entity
@Table(name = "quiz_questions")
public class QuizQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "content_id", nullable = false)
    public ArContent content;

    @Column(name = "question_text", nullable = false, length = 1000)
    public String questionText;

    /** Pipe-separated option list: "a|option a text|b|option b text|c|...|d|..." */
    @Column(name = "options", nullable = false, length = 2000)
    public String options;

    /** The correct option key, one of a / b / c / d. */
    @Column(name = "correct_option", nullable = false, length = 1)
    public char correctOption;

    @Column(name = "explanation", length = 2000)
    public String explanation;

    @Column(name = "order_index", nullable = false)
    public int orderIndex;

    @Column(name = "topic", length = 120)
    public String topic;

    public QuizQuestion() {
    }

    public QuizQuestion(ArContent content, String questionText, String options,
                        String correctOption, String explanation, int orderIndex, String topic) {
        this.content = content;
        this.questionText = questionText;
        this.options = options;
        this.correctOption = (correctOption == null || correctOption.isBlank())
                ? 'a' : correctOption.trim().charAt(0);
        this.explanation = explanation;
        this.orderIndex = orderIndex;
        this.topic = topic;
    }

    /**
     * Splits the pipe-separated option blob into key/text pairs.
     */
    public java.util.List<String> optionList() {
        java.util.List<String> out = new java.util.ArrayList<>();
        if (options == null || options.isBlank()) {
            return out;
        }
        for (String s : options.split("\\|")) {
            String t = s.trim();
            if (!t.isEmpty()) {
                out.add(t);
            }
        }
        return out;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public ArContent getContent() {
        return content;
    }

    public void setContent(ArContent content) {
        this.content = content;
    }

    public String getQuestionText() {
        return questionText;
    }

    public void setQuestionText(String questionText) {
        this.questionText = questionText;
    }

    public String getOptions() {
        return options;
    }

    public void setOptions(String options) {
        this.options = options;
    }

    public char getCorrectOption() {
        return correctOption;
    }

    public void setCorrectOption(char correctOption) {
        this.correctOption = correctOption;
    }

    public String getExplanation() {
        return explanation;
    }

    public void setExplanation(String explanation) {
        this.explanation = explanation;
    }

    public int getOrderIndex() {
        return orderIndex;
    }

    public void setOrderIndex(int orderIndex) {
        this.orderIndex = orderIndex;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }
}