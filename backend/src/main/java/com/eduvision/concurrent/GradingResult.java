package com.eduvision.concurrent;

/**
 * Immutable result of a single grading task executed on the thread pool.
 *
 * @param taskId the 1-based task number
 * @param score  the awarded score (0..100)
 * @param remark a short human-readable remark
 */
public record GradingResult(int taskId, int score, String remark) {
}
