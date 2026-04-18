package com.lycurg.metavisionfor1c;

import java.util.*;

public class CodeFormatter1C {

    private static final Set<String> OPENING_KEYWORDS = Set.of(
            "Если", "Для", "Пока", "Попытка", "Процедура", "Функция"
    );

    private static final Set<String> CLOSING_KEYWORDS = Set.of(
            "КонецЕсли", "КонецЦикла", "КонецПопытки", "КонецПроцедуры", "КонецФункции"
    );

    private static final Set<String> MIDDLE_KEYWORDS = Set.of(
            "Иначе", "ИначеЕсли", "Исключение"
    );

    public String formatCode(String code) {
        if (code == null || code.trim().isEmpty()) {
            return code;
        }

        String[] lines = code.split("\n", -1);
        StringBuilder result = new StringBuilder();

        int indentLevel = 0;
        boolean inMultilineComment = false;

        for (String line : lines) {
            String trimmed = line.trim();

            // Пустые строки
            if (trimmed.isEmpty()) {
                result.append("\n");
                continue;
            }

            // Многострочные комментарии
            if (inMultilineComment) {
                result.append(line).append("\n");
                if (trimmed.contains("*/")) {
                    inMultilineComment = false;
                }
                continue;
            }

            if (trimmed.startsWith("/*")) {
                inMultilineComment = true;
                result.append(makeIndent(indentLevel)).append(trimmed).append("\n");
                if (trimmed.contains("*/")) {
                    inMultilineComment = false;
                }
                continue;
            }

            // Однострочные комментарии
            if (trimmed.startsWith("//")) {
                result.append(makeIndent(indentLevel)).append(trimmed).append("\n");
                continue;
            }

            // 🔥 ЕСЛИ ЭТО ЗАПРОС (начинается с | или " и содержит ВЫБРАТЬ)
            if (trimmed.startsWith("|") ||
                    (trimmed.startsWith("\"") && (trimmed.contains("ВЫБРАТЬ") || trimmed.contains("SELECT")))) {
                result.append(formatQueryLine(line, indentLevel)).append("\n");
                continue;
            }

            // 🔥 ОБЫЧНЫЙ КОД (не запрос)
            String firstWord = extractFirstWord(trimmed);
            boolean isClosing = CLOSING_KEYWORDS.contains(firstWord);
            boolean isMiddle = MIDDLE_KEYWORDS.contains(firstWord);

            int currentIndent = indentLevel;
            if (isClosing) {
                currentIndent = Math.max(0, indentLevel - 1);
                indentLevel = currentIndent;
            } else if (isMiddle) {
                currentIndent = Math.max(0, indentLevel - 1);
            }

            result.append(makeIndent(currentIndent)).append(trimmed).append("\n");

            // Увеличиваем отступ для следующих строк
            if (OPENING_KEYWORDS.contains(firstWord) ||
                    trimmed.endsWith("Тогда") ||
                    trimmed.endsWith("Цикл")) {
                indentLevel++;
            }

            if (isMiddle && !firstWord.equals("Иначе")) {
                indentLevel = currentIndent + 1;
            }
        }

        return result.toString();
    }

    /**
     * Форматирует строку запроса — сохраняет | и нормализует отступ
     */
    private String formatQueryLine(String line, int indentLevel) {
        String trimmed = line.trim();

        // Если строка начинается с | — просто ставим правильный отступ
        if (trimmed.startsWith("|")) {
            return makeIndent(indentLevel) + trimmed;
        }

        // Если это начало запроса с кавычкой
        if (trimmed.startsWith("\"")) {
            return makeIndent(indentLevel) + trimmed;
        }

        // Остальное — просто возвращаем с отступом
        return makeIndent(indentLevel) + trimmed;
    }

    private String extractFirstWord(String line) {
        if (line.isEmpty()) return "";

        int end = 0;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == ' ' || c == '\t' || c == '(') break;
            end++;
        }

        if (end == 0) return line;

        String word = line.substring(0, end);
        if (word.endsWith(";")) {
            word = word.substring(0, word.length() - 1);
        }
        return word;
    }

    private String makeIndent(int level) {
        if (level <= 0) return "";
        return "\t".repeat(level);
    }

    public void printDebugInfo(String code) {
        System.out.println("=== DEBUG CodeFormatter1C ===");
        System.out.println("Input length: " + code.length());
        System.out.println("Lines: " + code.split("\n").length);
        System.out.println("============================");
    }
}