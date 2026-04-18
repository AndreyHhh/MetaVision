package com.lycurg.metavisionfor1c;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
//# Singleton менеджер пула потоков для многопоточного анализа кода
public class ExecutorServiceSingleton {
    private static ExecutorService instance;

    public static synchronized ExecutorService getInstance() {
        if (instance == null || instance.isShutdown()) {
            instance = Executors.newSingleThreadExecutor();
        }
        return instance;
    }

    public static synchronized void shutdown() {
        if (instance != null && !instance.isShutdown()) {
            instance.shutdownNow();
        }
    }
}