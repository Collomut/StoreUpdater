package com.stockmanager;

/**
 * Launcher class (non-JavaFX main) required so the fat JAR can start
 * without needing --module-path arguments. This delegates to Main which
 * extends javafx.application.Application.
 */
public class Launcher {
    public static void main(String[] args) {
        Main.main(args);
    }
}
