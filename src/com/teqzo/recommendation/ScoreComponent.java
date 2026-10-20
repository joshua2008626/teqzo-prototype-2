package com.teqzo.recommendation;

/**
 * One line of the transparent score breakdown.
 *
 * @param score  0..1, or null when the dataset has nothing to score this dimension with
 * @param weight share of the total (0..1) after use-case weighting and ignored dimensions
 * @param points score x weight x 100
 * @param detail the listed values the score was computed from
 */
public record ScoreComponent(Dimension dimension, Double score, double weight, double points, String detail) {
}
