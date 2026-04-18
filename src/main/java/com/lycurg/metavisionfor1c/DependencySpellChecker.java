package com.lycurg.metavisionfor1c;

import javafx.application.Platform;
import org.fxmisc.richtext.CodeArea;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

//# Контроллер проверки орфографии через Yandex.Speller API с подсветкой ошибок в редакторе кода
public class DependencySpellChecker {

    private final DependencyController mainController;
    private final YandexSpellChecker spellChecker;

    public DependencySpellChecker(DependencyController controller) {
        this.mainController = controller;
        this.spellChecker = new YandexSpellChecker();
    }



    /*
     * Основной метод проверки орфографии (теперь УМНАЯ проверка)
     */
    public void checkSpelling() {
        System.out.println("\n=== ПРОВЕРКА ОРФОГРАФИИ ===");

        CodeArea codeArea = mainController.getWorkbenchCodeArea();
        if (codeArea == null) {
            showMessage("Ошибка: не найден редактор кода");
            return;
        }

        String moduleCode = codeArea.getText();
        if (moduleCode == null || moduleCode.trim().isEmpty()) {
            showMessage("Нет текста для проверки");
            return;
        }

        updateStatus("Проверка орфографии...");

        // ИСПОЛЬЗУЕМ checkSpellingAsync (он теперь умный)
        CompletableFuture<List<YandexSpellChecker.SpellError>> future =
                spellChecker.checkSpellingAsync(moduleCode);

        future.thenAcceptAsync(errors -> {
            Platform.runLater(() -> {
                if (errors == null || errors.isEmpty()) {
                    updateStatus("Орфографических ошибок не найдено ✓");
                    // 🔥 Один блок вместо двух отдельных вызовов
                    mainController.validationResultsArea.clear();
                    showMessage("Проверка завершена. Ошибок не найдено.\n" + spellChecker.getStats());
                } else {
                    updateStatus("Найдено ошибок: " + errors.size());
                    mainController.validationResultsArea.clear();
                    showMessage("Найдено орфографических ошибок: " + errors.size() +
                            "\n" + spellChecker.getStats());
                    applySpellingHighlights(codeArea, errors);
                }
            });
        }).exceptionally(e -> {
            Platform.runLater(() -> {
                updateStatus("Ошибка проверки");
                mainController.validationResultsArea.clear();
                showMessage("Ошибка при проверке орфографии: " + e.getMessage());
            });
            return null;
        });
    }
    /**
     * Применяет подсветку ошибок в CodeArea
     */
    private void applySpellingHighlights(CodeArea codeArea, List<YandexSpellChecker.SpellError> errors) {
        System.out.println("\n=== ПОДСВЕТКА ОШИБОК ОРФОГРАФИИ ===");

        // 1. Восстанавливаем обычную подсветку синтаксиса
        System.out.println("Восстанавливаем синтаксическую подсветку...");
        OneCHighlighter.apply1CColors(codeArea);

        // 2. Добавляем красные линии для ошибок
        System.out.println("Добавляем " + errors.size() + " ошибок орфографии:");

        for (YandexSpellChecker.SpellError error : errors) {
            try {
                // Получаем ошибочное слово из текста
                String errorText = codeArea.getText(error.pos, error.pos + error.len);

                System.out.println("  ✅ Подчеркиваем: \"" + errorText +
                        "\" (позиция: " + error.pos + "-" + (error.pos + error.len) + ")");

                // Добавляем подсветку ошибки
                codeArea.setStyle(error.pos, error.pos + error.len,
                        Collections.singleton("spelling-error"));

            } catch (Exception e) {
                System.out.println("  ❌ Ошибка подсветки слова на позиции " + error.pos +
                        "-" + (error.pos + error.len) + ": " + e.getMessage());
            }
        }

        System.out.println("=== ПОДСВЕТКА ЗАВЕРШЕНА ===\n");

        // Обновляем статистику
        updateStatsLabel(codeArea, errors.size());
    }

    /**
     * Обновляет статистику
     */
    private void updateStatsLabel(CodeArea codeArea, int errorCount) {
        // Простой подсчет строк без использования getParagraphs()
        String text = codeArea.getText();

        int lineCount;
        if (text == null || text.isEmpty()) {
            lineCount = 0;
        } else {
            // Считаем количество переносов строк
            lineCount = 1; // Первая строка
            for (int i = 0; i < text.length(); i++) {
                if (text.charAt(i) == '\n') {
                    lineCount++;
                }
            }
        }

        int charCount = text != null ? text.length() : 0;

        String stats = String.format("Строк: %d | Символов: %d | Ошибок: %d",
                lineCount, charCount, errorCount);

        Platform.runLater(() -> {
            if (mainController.getWorkbenchStatsLabel() != null) {
                mainController.getWorkbenchStatsLabel().setText(stats);
            }
        });
    }
    /**
     * Обновляет статус
     */
    private void updateStatus(String status) {
        Platform.runLater(() -> {
            if (mainController.getWorkbenchStatusLabel() != null) {
                mainController.getWorkbenchStatusLabel().setText(status);
            }
        });
    }

    /**
     * Показывает сообщение в основном логе
     */
    private void showMessage(String message) {
        Platform.runLater(() -> {
            if (mainController.validationResultsArea != null) {
                mainController.validationResultsArea.appendText("[Орфография] " + message + "\n");
            }
        });
    }
    /**
     * Завершение работы
     */
    public void shutdown() {
        spellChecker.clearCache();
        System.out.println("DependencySpellChecker завершен");
    }



}