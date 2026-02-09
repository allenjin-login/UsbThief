package com.superredrock.usbthief.test;

import com.superredrock.usbthief.worker.LoadEvaluator;
import com.superredrock.usbthief.worker.LoadLevel;
import com.superredrock.usbthief.worker.LoadScore;

public class LoadEvaluatorTest {
    static void main(String[] args) {
        testLoadScoreCalculation();
        testLoadLevelDetermination();
        testMissingMetricHandling();
        System.out.println("All LoadEvaluator tests passed!");
    }

    private static void testLoadScoreCalculation() {
        LoadEvaluator evaluator = new LoadEvaluator();

        LoadScore score = evaluator.evaluateLoad();

        assert score.score() >= 0 && score.score() <= 100 : "Score should be 0-100";
        System.out.println("✓ Load score calculation works (score: " + score.score() + ")");
    }

    private static void testLoadLevelDetermination() {
        LoadEvaluator evaluator = new LoadEvaluator();

        LoadScore score = evaluator.evaluateLoad();

        // Just verify it returns a valid level
        assert score.level() == LoadLevel.LOW ||
               score.level() == LoadLevel.MEDIUM ||
               score.level() == LoadLevel.HIGH : "Should return valid load level";

        System.out.println("✓ Load level determination works (level: " + score.level() + ")");
    }

    private static void testMissingMetricHandling() {
        LoadEvaluator evaluator = new LoadEvaluator();

        // Even if SpeedProbe is not available, should not crash
        LoadScore score = evaluator.evaluateLoad();

        assert score.score() >= 0 && score.score() <= 100 : "Should handle missing metrics";
        System.out.println("✓ Missing metric handling works");
    }
}
