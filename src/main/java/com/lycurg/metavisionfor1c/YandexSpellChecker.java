package com.lycurg.metavisionfor1c;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Класс для проверки орфографии через Яндекс.Спеллер API
 */
public class YandexSpellChecker {

    // Константы
    private static final String YANDEX_API_URL = "https://speller.yandex.net/services/spellservice.json/checkText";
    private static final int MAX_TEXT_LENGTH = 10000;
    private static final int MAX_REQUESTS_PER_DAY = 900;

    private final HttpClient httpClient;
    private int requestsToday = 0;
    private Date lastRequestDate = new Date();
    private final Map<String, List<SpellError>> textCache = new HashMap<>();

    public static class SpellError {
        public final int pos;
        public final int len;
        public final String word;
        public final List<String> suggestions;

        public SpellError(int pos, int len, String word, List<String> suggestions) {
            this.pos = pos;
            this.len = len;
            this.word = word;
            this.suggestions = suggestions;
        }

        @Override
        public String toString() {
            return String.format("'%s' (позиция: %d, длина: %d) → %s",
                    word, pos, len, suggestions);
        }
    }

    public YandexSpellChecker() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        resetDailyStatsIfNeeded();
    }

    public CompletableFuture<List<SpellError>> checkSpellingAsync(String moduleCode) {
        return CompletableFuture.supplyAsync(() -> checkSpelling(moduleCode));
    }

    public List<SpellError> checkSpelling(String moduleCode) {
        System.out.println("\n=== ПРОВЕРКА ОРФОГРАФИИ (УМНАЯ) ===");

        // Кэш
        String cacheKey = "CHECK_" + moduleCode.hashCode() + "_" + moduleCode.length();
        if (textCache.containsKey(cacheKey)) {
            return textCache.get(cacheKey);
        }

        System.out.println("Исходный код: " + moduleCode.length() + " символов");

        // 1. Собираем только комментарии и строки
        List<SpellCheckTextCollector.TextChunk> chunks =
                SpellCheckTextCollector.collectTextForSpellCheck(moduleCode);

        if (chunks.isEmpty()) {
            System.out.println("Нет текста для проверки");
            return Collections.emptyList();
        }

        // 2. Подготавливаем для Яндекса
        SpellCheckTextCollector.PreparedText prepared =
                SpellCheckTextCollector.prepareForYandex(chunks);

        if (prepared.combinedText.isEmpty()) {
            System.out.println("Текст для Яндекс пуст");
            return Collections.emptyList();
        }

        System.out.println("Текст для Яндекс: " + prepared.combinedText.length() + " символов");

        // 3. Проверяем лимиты
        if (!canMakeRequest()) {
            System.out.println("Лимит запросов исчерпан");
            return Collections.emptyList();
        }

        // 4. Проверяем в Яндекс
        List<SpellError> yandexErrors = sendToYandex(prepared.combinedText);

        if (yandexErrors.isEmpty()) {
            System.out.println("Яндекс не нашел ошибок");
            return Collections.emptyList();
        }

        System.out.println("Яндекс нашел ошибок: " + yandexErrors.size());

        // 5. Конвертируем позиции
        List<SpellError> convertedErrors =
                convertYandexErrorsToOriginalPositions(yandexErrors, prepared, moduleCode);

        System.out.println("После конвертации: " + convertedErrors.size() + " ошибок");

        // Сохраняем в кэш
        textCache.put(cacheKey, convertedErrors);

        return convertedErrors;
    }

    private List<SpellError> sendToYandex(String text) {
        try {
            // Разбиваем если большой
            if (text.length() > MAX_TEXT_LENGTH) {
                return sendLargeTextToYandex(text);
            }

            String encodedText = URLEncoder.encode(text, StandardCharsets.UTF_8);
            String requestBody = "text=" + encodedText + "&lang=ru&options=512";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(YANDEX_API_URL))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .timeout(Duration.ofSeconds(15))
                    .build();

            HttpResponse<String> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofString());

            requestsToday++;
            lastRequestDate = new Date();

            if (response.statusCode() == 200) {
                return parseYandexResponse(text, response.body());
            } else {
                System.out.println("Ошибка Яндекс: " + response.statusCode());
                return Collections.emptyList();
            }

        } catch (Exception e) {
            System.out.println("Ошибка при отправке в Яндекс: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<SpellError> sendLargeTextToYandex(String text) {
        List<SpellError> allErrors = new ArrayList<>();
        List<String> chunks = splitText(text);

        for (int i = 0; i < chunks.size(); i++) {
            if (!canMakeRequest()) break;

            List<SpellError> chunkErrors = sendToYandex(chunks.get(i));

            // Корректируем позиции
            int offset = 0;
            for (int j = 0; j < i; j++) {
                offset += chunks.get(j).length();
            }

            for (SpellError error : chunkErrors) {
                allErrors.add(new SpellError(
                        error.pos + offset,
                        error.len,
                        error.word,
                        error.suggestions
                ));
            }

            if (i < chunks.size() - 1) {
                try { Thread.sleep(500); } catch (InterruptedException e) {}
            }
        }

        return allErrors;
    }

    private List<String> splitText(String text) {
        List<String> chunks = new ArrayList<>();
        int chunkSize = 9000;

        if (text.length() <= chunkSize) {
            chunks.add(text);
            return chunks;
        }

        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + chunkSize, text.length());

            if (end < text.length()) {
                int lastNewLine = text.lastIndexOf('\n', end);
                if (lastNewLine > start && (lastNewLine - start) > chunkSize * 0.7) {
                    end = lastNewLine + 1;
                }
            }

            chunks.add(text.substring(start, end));
            start = end;
        }

        return chunks;
    }

    private List<SpellError> parseYandexResponse(String originalText, String jsonResponse) {
        List<SpellError> errors = new ArrayList<>();

        try {
            JSONArray jsonArray = new JSONArray(jsonResponse);

            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject errorObj = jsonArray.getJSONObject(i);

                int pos = errorObj.getInt("pos");
                int len = errorObj.getInt("len");
                String word = errorObj.getString("word");

                List<String> suggestions = new ArrayList<>();
                JSONArray suggestionsArray = errorObj.getJSONArray("s");
                for (int j = 0; j < suggestionsArray.length(); j++) {
                    suggestions.add(suggestionsArray.getString(j));
                }

                // Получаем слово из текста для проверки
                if (pos >= 0 && pos + len <= originalText.length()) {
                    String errorText = originalText.substring(pos, pos + len);
                    System.out.println("Яндекс ошибка: \"" + errorText + "\" на позиции " + pos + "-" + (pos + len));

                    errors.add(new SpellError(pos, len, word, suggestions));
                }
            }

        } catch (Exception e) {
            System.out.println("Ошибка парсинга Яндекс: " + e.getMessage());
        }

        return errors;
    }

    private List<SpellError> convertYandexErrorsToOriginalPositions(
            List<SpellError> yandexErrors,
            SpellCheckTextCollector.PreparedText prepared,
            String originalCode) {

        List<SpellError> convertedErrors = new ArrayList<>();

        if (yandexErrors == null || yandexErrors.isEmpty()) {
            return convertedErrors;
        }

        // Считаем позиции chunk'ов в combinedText
        Map<SpellCheckTextCollector.TextChunk, Integer> chunkStarts = new HashMap<>();
        int currentPos = 0;

        for (SpellCheckTextCollector.TextChunk chunk : prepared.chunks) {
            String chunkText = getCleanChunkText(chunk);
            if (!chunkText.isEmpty()) {
                chunkStarts.put(chunk, currentPos);
                currentPos += chunkText.length() + 1; // +1 для \n
            }
        }

        // Конвертируем каждую ошибку
        for (SpellError yandexError : yandexErrors) {
            // Ищем chunk содержащий ошибку
            for (Map.Entry<SpellCheckTextCollector.TextChunk, Integer> entry : chunkStarts.entrySet()) {
                SpellCheckTextCollector.TextChunk chunk = entry.getKey();
                int chunkStart = entry.getValue();
                String chunkText = getCleanChunkText(chunk);
                int chunkLength = chunkText.length();

                if (yandexError.pos >= chunkStart && yandexError.pos < chunkStart + chunkLength) {
                    int offsetInChunk = yandexError.pos - chunkStart;
                    int originalPos = getOriginalPosition(chunk, offsetInChunk);

                    if (originalPos >= 0 && originalPos + yandexError.len <= originalCode.length()) {
                        String errorText = originalCode.substring(originalPos, originalPos + yandexError.len);
                        System.out.println("Конвертировано: Яндекс позиция " + yandexError.pos +
                                " → Исходная позиция " + originalPos + " слово: \"" + errorText + "\"");

                        convertedErrors.add(new SpellError(
                                originalPos,
                                yandexError.len,
                                yandexError.word,
                                yandexError.suggestions
                        ));
                    }
                    break;
                }
            }
        }

        return convertedErrors;
    }

    private String getCleanChunkText(SpellCheckTextCollector.TextChunk chunk) {
        String text = chunk.text;

        if (!chunk.isComment && text.startsWith("\"") && text.endsWith("\"")) {
            text = text.substring(1, text.length() - 1);
        }
        if (chunk.isComment) {
            if (text.startsWith("//")) {
                text = text.substring(2);
            } else if (text.startsWith("/*")) {
                text = text.substring(2, text.length() - 2);
            }
        }

        return text;
    }

    private int getOriginalPosition(SpellCheckTextCollector.TextChunk chunk, int offsetInChunk) {
        int pos = chunk.startPos;

        if (chunk.isComment) {
            if (chunk.text.startsWith("//")) {
                pos += 2;
            } else if (chunk.text.startsWith("/*")) {
                pos += 2;
            }
        } else {
            pos += 1; // Пропускаем открывающую кавычку
        }

        return pos + offsetInChunk;
    }

    private boolean canMakeRequest() {
        resetDailyStatsIfNeeded();
        return requestsToday < MAX_REQUESTS_PER_DAY;
    }

    private void resetDailyStatsIfNeeded() {
        Date now = new Date();
        long diffHours = (now.getTime() - lastRequestDate.getTime()) / (1000 * 60 * 60);

        if (diffHours >= 24) {
            requestsToday = 0;
            lastRequestDate = now;
            textCache.clear();
            System.out.println("Статистика Яндекс сброшена");
        }
    }

    public String getStats() {
        resetDailyStatsIfNeeded();
        return String.format("Запросов сегодня: %d/%d", requestsToday, MAX_REQUESTS_PER_DAY);
    }

    public void clearCache() {
        textCache.clear();
    }
}