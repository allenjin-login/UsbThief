package com.superredrock.usbthief.test;

import com.superredrock.usbthief.worker.PriorityRule;
import java.nio.file.Path;
import java.nio.file.Paths;

public class PriorityRuleTest {
    static void main(String[] args) {
        testDefaultExtensionMapping();
        testUnknownExtensionReturnsDefault();
        testClamping();
        testSizeAdjustmentDoesNotCrash();
        System.out.println("All PriorityRule tests passed!");
    }

    private static void testDefaultExtensionMapping() {
        PriorityRule rule = new PriorityRule();

        assert rule.calculatePriority(Paths.get("document.pdf")) == 10 : "PDF should be 10, got " + rule.calculatePriority(Paths.get("document.pdf"));
        assert rule.calculatePriority(Paths.get("report.docx")) == 9 : "DOCX should be 9";
        assert rule.calculatePriority(Paths.get("data.xlsx")) == 9 : "XLSX should be 9";
        assert rule.calculatePriority(Paths.get("slides.pptx")) == 8 : "PPTX should be 8";
        assert rule.calculatePriority(Paths.get("notes.txt")) == 7 : "TXT should be 7";
        assert rule.calculatePriority(Paths.get("image.jpg")) == 6 : "JPG should be 6";
        assert rule.calculatePriority(Paths.get("photo.png")) == 6 : "PNG should be 6";
        assert rule.calculatePriority(Paths.get("temp.tmp")) == 1 : "TMP should be 1";
        assert rule.calculatePriority(Paths.get("debug.log")) == 1 : "LOG should be 1";

        System.out.println("✓ Extension mapping works");
    }

    private static void testUnknownExtensionReturnsDefault() {
        PriorityRule rule = new PriorityRule();
        int priority = rule.calculatePriority(Paths.get("unknown.xyz"));
        assert priority == 5 : "Unknown extension should return default 5, got " + priority;
        System.out.println("✓ Unknown extension returns default");
    }

    private static void testClamping() {
        PriorityRule rule = new PriorityRule();
        // Even with bonuses/penalties, should stay in 0-100
        int priority = rule.calculatePriority(Paths.get("test.pdf"));
        assert priority >= 0 && priority <= 100 : "Should return valid priority in 0-100";
        System.out.println("✓ Clamping works");
    }

    private static void testSizeAdjustmentDoesNotCrash() {
        // This test uses the project's own files which exist
        PriorityRule rule = new PriorityRule();
        // Use existing source files that exist
        int priority = rule.calculatePriority(Paths.get("src/module-info.java"));
        assert priority >= 0 && priority <= 100 : "Should return valid priority";
        System.out.println("✓ Size adjustment doesn't crash");
    }
}
