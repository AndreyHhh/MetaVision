package com.lycurg.metavisionfor1c;

import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.model.StyleSpan;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CodeSearchHelper {

    private final CodeArea codeArea;
    private final TextField searchField;
    private final Button prevButton;
    private final Button nextButton;
    private final Label counterLabel;

    private String lastSearchQuery = "";
    private List<Integer> matchPositions = new ArrayList<>();
    private int currentMatchIndex = -1;

    public CodeSearchHelper(CodeArea codeArea, TextField searchField,
                            Button prevButton, Button nextButton, Label counterLabel) {
        this.codeArea = codeArea;
        this.searchField = searchField;
        this.prevButton = prevButton;
        this.nextButton = nextButton;
        this.counterLabel = counterLabel;

        setupEventHandlers();
        updateButtonsState();
    }

    private void setupEventHandlers() {
        // Только поиск по Enter
        searchField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                String query = searchField.getText().trim();
                if (!query.isEmpty()) {
                    performSearch(query);
                } else {
                    clearSearch();
                }
                event.consume();
            }
        });

        // Кнопки Prev/Next
        if (prevButton != null) {
            prevButton.setOnAction(e -> navigatePrev());
        }
        if (nextButton != null) {
            nextButton.setOnAction(e -> navigateNext());
        }

        // Кнопка очистки (если есть отдельная) - опционально
        // Если хочешь, чтобы при очистке поля поиск сбрасывался
        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null || newVal.trim().isEmpty()) {
                clearSearch();
            }
        });
    }

    /**
     * Применить подсветку поиска поверх существующей подсветки
     */
    private void applySearchHighlight(Map<Integer, Boolean> searchMatches, int currentMatchPos) {
        String text = codeArea.getText();
        if (text == null || text.isEmpty()) {
            return;
        }

        int searchLength = lastSearchQuery.length();

        // Получаем текущие стили (синтаксическая подсветка уже применена)
        StyleSpans<Collection<String>> currentSpans = codeArea.getStyleSpans(0, text.length());

        StyleSpansBuilder<Collection<String>> builder = new StyleSpansBuilder<>();
        int pos = 0;

        for (StyleSpan<Collection<String>> span : currentSpans) {
            int spanStart = pos;
            int spanEnd = pos + span.getLength();
            Collection<String> baseStyles = span.getStyle();

            // Ищем совпадения поиска в этом span
            List<Integer> matchesInSpan = new ArrayList<>();
            for (int matchPos : searchMatches.keySet()) {
                if (matchPos >= spanStart && matchPos < spanEnd) {
                    matchesInSpan.add(matchPos);
                }
            }

            if (matchesInSpan.isEmpty()) {
                // Нет совпадений - добавляем как есть
                builder.add(baseStyles, span.getLength());
            } else {
                // Есть совпадения - разбиваем
                Collections.sort(matchesInSpan);
                int lastPos = spanStart;

                for (int matchPos : matchesInSpan) {
                    // Текст до совпадения
                    if (matchPos > lastPos) {
                        builder.add(baseStyles, matchPos - lastPos);
                    }

                    // Текст совпадения с подсветкой поиска
                    Collection<String> highlightStyles = new ArrayList<>(baseStyles);
                    if (matchPos == currentMatchPos) {
                        highlightStyles.add("search-highlight-current");
                    } else {
                        highlightStyles.add("search-highlight");
                    }
                    builder.add(highlightStyles, searchLength);

                    lastPos = matchPos + searchLength;
                }

                // Текст после последнего совпадения
                if (lastPos < spanEnd) {
                    builder.add(baseStyles, spanEnd - lastPos);
                }
            }

            pos = spanEnd;
        }

        codeArea.setStyleSpans(0, builder.create());
    }

    /**
     * Выполнить поиск
     */
    public void performSearch(String query) {
        if (query == null || query.trim().isEmpty()) {
            OneCHighlighter.apply1CColors(codeArea);
            lastSearchQuery = "";
            matchPositions.clear();
            currentMatchIndex = -1;
            updateCounterDisplay();
            updateButtonsState();
            return;
        }

        lastSearchQuery = query;
        String text = codeArea.getText();

        // 🔥 ПРОВЕРКА: если текста нет — откладываем поиск
        if (text == null || text.isEmpty()) {
            matchPositions.clear();
            currentMatchIndex = -1;
            updateCounterDisplay();
            updateButtonsState();
            // Пробуем ещё раз через 100 мс
            new Thread(() -> {
                try { Thread.sleep(100); } catch (InterruptedException e) {}
                Platform.runLater(() -> performSearch(query));
            }).start();
            return;
        }
        // Сначала обновляем синтаксическую подсветку
        OneCHighlighter.apply1CColors(codeArea);

        // Поиск всех вхождений
        Pattern pattern = Pattern.compile(Pattern.quote(query), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        Matcher matcher = pattern.matcher(text);

        matchPositions.clear();
        Map<Integer, Boolean> matchMap = new HashMap<>();
        while (matcher.find()) {
            matchPositions.add(matcher.start());
            matchMap.put(matcher.start(), true);
        }

        if (!matchPositions.isEmpty()) {
            currentMatchIndex = 0;
            int currentMatchPos = matchPositions.get(currentMatchIndex);
            // Применяем подсветку поиска поверх синтаксической
            Platform.runLater(() -> {
                applySearchHighlight(matchMap, currentMatchPos);
                navigateToMatch(currentMatchIndex);
            });
        } else {
            currentMatchIndex = -1;
        }

        updateCounterDisplay();
        updateButtonsState();
    }

    private void navigateToMatch(int index) {
        if (matchPositions.isEmpty() || index < 0 || index >= matchPositions.size()) {
            return;
        }

        String text = codeArea.getText();
        if (text == null || text.isEmpty()) {
            return;
        }

        int matchStart = matchPositions.get(index);
        int searchLength = lastSearchQuery.length();

        // Защита от выхода за границы
        if (matchStart + searchLength > text.length()) {
            // Если координаты устарели — просто выходим, не выделяем
            return;
        }

        Platform.runLater(() -> {
            try {
                codeArea.requestFocus();
                codeArea.selectRange(matchStart, matchStart + searchLength);
                codeArea.showParagraphAtTop(codeArea.offsetToPosition(matchStart,
                        org.fxmisc.richtext.model.TwoDimensional.Bias.Forward).getMajor());
            } catch (IndexOutOfBoundsException e) {
                // Молча игнорируем — не вылетаем
                System.err.println("⚠️ Ошибка навигации: " + e.getMessage());
            }
        });
    }



    public void navigatePrev() {
        if (matchPositions.isEmpty()) return;
        int newIndex = currentMatchIndex - 1;
        if (newIndex < 0) newIndex = matchPositions.size() - 1;
        currentMatchIndex = newIndex;
        navigateToMatch(currentMatchIndex);
        updateCounterDisplay();
    }

    public void navigateNext() {
        if (matchPositions.isEmpty()) return;
        int newIndex = currentMatchIndex + 1;
        if (newIndex >= matchPositions.size()) newIndex = 0;
        currentMatchIndex = newIndex;
        navigateToMatch(currentMatchIndex);
        updateCounterDisplay();
    }


    private void updateCounterDisplay() {
        if (counterLabel == null) return;
        if (matchPositions.isEmpty() || lastSearchQuery.isEmpty()) {
            counterLabel.setText("0/0");
        } else {
            counterLabel.setText((currentMatchIndex + 1) + "/" + matchPositions.size());
        }
    }

    private void updateButtonsState() {
        boolean hasMatches = !matchPositions.isEmpty() && !lastSearchQuery.isEmpty();
        if (prevButton != null) prevButton.setDisable(!hasMatches);
        if (nextButton != null) nextButton.setDisable(!hasMatches);
    }

    public void clearSearch() {
        if (searchField != null) searchField.clear();
        lastSearchQuery = "";
        matchPositions.clear();
        currentMatchIndex = -1;
        // Восстанавливаем только синтаксическую подсветку
        OneCHighlighter.apply1CColors(codeArea);
        updateCounterDisplay();
        updateButtonsState();
    }

    public void clearHighlights() {
        OneCHighlighter.apply1CColors(codeArea);
    }

    public String getCurrentQuery() {
        return lastSearchQuery;
    }

    public int getMatchCount() {
        return matchPositions.size();
    }
}