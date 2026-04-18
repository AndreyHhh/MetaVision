package com.lycurg.metavisionfor1c;

import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * Собирает только комментарии и строки из кода 1С для проверки орфографии
 * Исключает: запросы, имена переменных, ключевые слова
 */
public class SpellCheckTextCollector {

    /**
     * Часть текста для проверки
     */
    public static class TextChunk {
        public final String text;        // Текст для проверки
        public final int lineNumber;     // Номер строки (0-based)
        public final int startPos;       // Позиция начала в исходном коде
        public final int endPos;         // Позиция конца
        public final boolean isComment;  // true=комментарий, false=строка

        public TextChunk(String text, int lineNumber, int startPos, int endPos, boolean isComment) {
            this.text = text;
            this.lineNumber = lineNumber;
            this.startPos = startPos;
            this.endPos = endPos;
            this.isComment = isComment;
        }

        @Override
        public String toString() {
            return String.format("Line %d [%d-%d]: %s \"%s\"",
                    lineNumber + 1, startPos, endPos,
                    isComment ? "Comment" : "String",
                    text.length() > 30 ? text.substring(0, 30) + "..." : text);
        }
    }


    // Ключевые слова которые запускают/завершают запросы
    private static final Set<String> QUERY_START_KEYWORDS = Set.of(
            "Запрос.Текст", "Запрос =", "Новый Запрос", "ТекстЗапроса"
    );

    private static final Set<String> QUERY_END_KEYWORDS = Set.of(
            "\";", ";", "КонецЗапроса"
    );

    /**
     * Собирает все комментарии и строки из кода 1С для проверки орфографии
     */
    public static List<TextChunk> collectTextForSpellCheck(String moduleCode) {
        List<TextChunk> chunks = new ArrayList<>();

        if (moduleCode == null || moduleCode.isEmpty()) {
            return chunks;
        }

        System.out.println("\n=== СОБИРАЕМ ТЕКСТ ДЛЯ ПРОВЕРКИ ОРФОГРАФИИ ===");
        System.out.println("Длина исходного кода: " + moduleCode.length() + " символов");

        String[] lines = moduleCode.split("\n", -1); // -1 чтобы сохранить пустые строки

        boolean insideQuery = false; // Находимся внутри запроса?
        boolean insideBlockComment = false; // Находимся внутри /* */ комментария?
        int currentPos = 0; // Текущая позиция в исходном коде

        for (int lineNum = 0; lineNum < lines.length; lineNum++) {
            String line = lines[lineNum];
            int lineStartPos = currentPos;

            // 1. Проверяем не находимся ли мы внутри запроса
            if (!insideQuery) {
                // Проверяем начало запроса
                for (String keyword : QUERY_START_KEYWORDS) {
                    if (line.contains(keyword)) {
                        insideQuery = true;
                        System.out.println("Line " + (lineNum + 1) + ": НАЧАЛО ЗАПРОСА - пропускаем строку");
                        break;
                    }
                }
            } else {
                // Проверяем конец запроса
                for (String keyword : QUERY_END_KEYWORDS) {
                    if (line.contains(keyword)) {
                        insideQuery = false;
                        System.out.println("Line " + (lineNum + 1) + ": КОНЕЦ ЗАПРОСА");
                        break;
                    }
                }
            }

            // 2. Если не внутри запроса - собираем текст для проверки
            if (!insideQuery) {
                // Ищем комментарии и строки в этой строке
                List<TextChunk> lineChunks = findChunksInLine(line, lineNum, lineStartPos, insideBlockComment);

                // Обновляем состояние блочного комментария
                for (TextChunk chunk : lineChunks) {
                    if (chunk.isComment && chunk.text.startsWith("/*")) {
                        insideBlockComment = !insideBlockComment;
                    }
                }

                chunks.addAll(lineChunks);
            }

            currentPos += line.length() + 1; // +1 для символа новой строки
        }

        System.out.println("Найдено частей для проверки: " + chunks.size());
        for (TextChunk chunk : chunks) {
            System.out.println("  " + chunk);
        }
        System.out.println("=== ЗАВЕРШЕНО ===\n");

        return chunks;
    }

    /**
     * Ищет комментарии и строки в одной строке кода
     */
    private static List<TextChunk> findChunksInLine(String line, int lineNum, int lineStartPos,
                                                    boolean currentlyInBlockComment) {
        List<TextChunk> chunks = new ArrayList<>();

        if (line == null || line.isEmpty()) {
            return chunks;
        }

        int posInLine = 0;

        // Если находимся внутри блочного комментария, ищем его конец
        if (currentlyInBlockComment) {
            int commentEnd = line.indexOf("*/");
            if (commentEnd != -1) {
                String commentText = line.substring(0, commentEnd + 2);
                chunks.add(new TextChunk(
                        commentText,
                        lineNum,
                        lineStartPos,
                        lineStartPos + commentEnd + 2,
                        true
                ));
                posInLine = commentEnd + 2;
            } else {
                // Весь строкa - часть блочного комментария
                chunks.add(new TextChunk(
                        line,
                        lineNum,
                        lineStartPos,
                        lineStartPos + line.length(),
                        true
                ));
                return chunks; // Вся строка обработана
            }
        }

        // Ищем комментарии и строки в оставшейся части строки
        while (posInLine < line.length()) {
            // Сначала проверяем однострочный комментарий
            int lineCommentStart = line.indexOf("//", posInLine);
            if (lineCommentStart != -1) {
                // Найден комментарий // - берем все до конца строки
                String commentText = line.substring(lineCommentStart);
                chunks.add(new TextChunk(
                        commentText,
                        lineNum,
                        lineStartPos + lineCommentStart,
                        lineStartPos + line.length(),
                        true
                ));
                break; // После // больше ничего не проверяем в этой строке
            }

            // Проверяем начало блочного комментария /*
            int blockCommentStart = line.indexOf("/*", posInLine);
            if (blockCommentStart != -1) {
                int blockCommentEnd = line.indexOf("*/", blockCommentStart + 2);
                if (blockCommentEnd != -1) {
                    // Блочный комментарий полностью в этой строке
                    String commentText = line.substring(blockCommentStart, blockCommentEnd + 2);
                    chunks.add(new TextChunk(
                            commentText,
                            lineNum,
                            lineStartPos + blockCommentStart,
                            lineStartPos + blockCommentEnd + 2,
                            true
                    ));
                    posInLine = blockCommentEnd + 2;
                    continue;
                } else {
                    // Блочный комментарий начинается здесь и продолжается дальше
                    String commentText = line.substring(blockCommentStart);
                    chunks.add(new TextChunk(
                            commentText,
                            lineNum,
                            lineStartPos + blockCommentStart,
                            lineStartPos + line.length(),
                            true
                    ));
                    break; // Вся остальная строка - часть комментария
                }
            }

            // Ищем строковые литералы
            int stringStart = line.indexOf('"', posInLine);
            if (stringStart != -1) {
                // Ищем закрывающую кавычку
                int stringEnd = findClosingQuote(line, stringStart);
                if (stringEnd != -1) {
                    String stringText = line.substring(stringStart, stringEnd + 1);
                    chunks.add(new TextChunk(
                            stringText,
                            lineNum,
                            lineStartPos + stringStart,
                            lineStartPos + stringEnd + 1,
                            false
                    ));
                    posInLine = stringEnd + 1;
                    continue;
                } else {
                    // Нет закрывающей кавычки - строка продолжается дальше
                    break;
                }
            }

            // Ничего не найдено - выходим
            break;
        }

        return chunks;
    }

    /**
     * Находит закрывающую кавычку строкового литерала
     */
    private static int findClosingQuote(String line, int startPos) {
        boolean escaped = false;

        for (int i = startPos + 1; i < line.length(); i++) {
            char c = line.charAt(i);

            if (escaped) {
                escaped = false;
                continue;
            }

            if (c == '\\') {
                escaped = true;
            } else if (c == '"') {
                return i;
            }
        }

        return -1; // Закрывающая кавычка не найдена
    }

    /**
     * Объединяет все тексты для проверки в одну строку для Яндекса
     * Возвращает текст и маппинг позиций
     */
    public static class PreparedText {
        public final String combinedText; // Текст для отправки в Яндекс
        public final List<TextChunk> chunks; // Исходные chunks
        public final Map<Integer, TextChunk> positionMapping; // Позиция в combinedText → TextChunk

        public PreparedText(String combinedText, List<TextChunk> chunks, Map<Integer, TextChunk> positionMapping) {
            this.combinedText = combinedText;
            this.chunks = chunks;
            this.positionMapping = positionMapping;
        }
    }

    /**
     * Подготавливает текст для отправки в Яндекс.Спеллер
     */
    public static PreparedText prepareForYandex(List<TextChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return new PreparedText("", Collections.emptyList(), Collections.emptyMap());
        }

        StringBuilder combinedText = new StringBuilder();
        Map<Integer, TextChunk> positionMapping = new HashMap<>();

        int currentPos = 0;

        for (TextChunk chunk : chunks) {
            // Добавляем текст chunk'а
            String textToAdd = chunk.text;

            // Для строковых литералов убираем кавычки
            if (!chunk.isComment && textToAdd.startsWith("\"") && textToAdd.endsWith("\"")) {
                textToAdd = textToAdd.substring(1, textToAdd.length() - 1);
            }

            // Для комментариев убираем // или /*
            if (chunk.isComment) {
                if (textToAdd.startsWith("//")) {
                    textToAdd = textToAdd.substring(2);
                } else if (textToAdd.startsWith("/*")) {
                    textToAdd = textToAdd.substring(2, textToAdd.length() - 2);
                }
            }

            if (!textToAdd.isEmpty()) {
                // Запоминаем маппинг
                positionMapping.put(currentPos, chunk);

                // Добавляем текст
                combinedText.append(textToAdd).append("\n");
                currentPos += textToAdd.length() + 1; // +1 для \n
            }
        }

        System.out.println("Подготовлено для Яндекс: " + combinedText.length() + " символов");

        return new PreparedText(combinedText.toString(), chunks, positionMapping);
    }
}