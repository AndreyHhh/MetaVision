package com.lycurg.metavisionfor1c;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.model.StyleSpan;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

import java.util.*;

public class SearchableCodeArea extends VBox {

    private final CodeArea codeArea;
    private final HBox searchBar;
    private final TextField searchField;
    private final Button prevButton;
    private final Button nextButton;
    private final Label counterLabel;
    private final Button closeButton;

    private List<Integer> searchMatches = new ArrayList<>();
    private int currentMatchIndex = -1;
    private String lastSearchText = "";

    public SearchableCodeArea() {
        super(5);
        this.codeArea = new CodeArea();

        // Создаем панель поиска
        searchBar = new HBox(5);
        searchBar.setAlignment(Pos.CENTER_LEFT);
        searchBar.setPadding(new Insets(5));
        searchBar.setStyle("-fx-background-color: #f5f5f5; -fx-border-color: #ddd; -fx-border-width: 0 0 1 0;");
        searchBar.setVisible(false);

        Label searchLabel = new Label("🔍");
        searchField = new TextField();
        searchField.setPromptText("Поиск...");
        searchField.setPrefWidth(200);

        prevButton = new Button("←");
        prevButton.setStyle("-fx-font-weight: bold;");
        prevButton.setDisable(true);

        nextButton = new Button("→");
        nextButton.setStyle("-fx-font-weight: bold;");
        nextButton.setDisable(true);

        counterLabel = new Label("0/0");
        counterLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #666;");

        closeButton = new Button("✖");
        closeButton.setStyle("-fx-background-color: transparent; -fx-text-fill: #999; -fx-cursor: hand;");

        searchBar.getChildren().addAll(searchLabel, searchField, prevButton, nextButton, counterLabel, closeButton);

        // Добавляем компоненты
        getChildren().addAll(searchBar, codeArea);

        // Настройка CodeArea
        VBox.setVgrow(codeArea, javafx.scene.layout.Priority.ALWAYS);

        // Обработчики событий
        setupEventHandlers();
    }

    private void setupEventHandlers() {
        // Поиск при вводе
        searchField.textProperty().addListener((obs, old, newText) -> {
            if (newText == null || newText.trim().isEmpty()) {
                clearSearch();
                return;
            }
            performSearch(newText.trim());
        });

        // Кнопка "Назад"
        prevButton.setOnAction(e -> {
            if (!searchMatches.isEmpty()) {
                currentMatchIndex--;
                if (currentMatchIndex < 0) {
                    currentMatchIndex = searchMatches.size() - 1;
                }
                navigateToCurrentMatch();
                updateCounterLabel();
            }
        });

        // Кнопка "Вперед"
        nextButton.setOnAction(e -> {
            if (!searchMatches.isEmpty()) {
                currentMatchIndex++;
                if (currentMatchIndex >= searchMatches.size()) {
                    currentMatchIndex = 0;
                }
                navigateToCurrentMatch();
                updateCounterLabel();
            }
        });

        // Закрыть поиск
        closeButton.setOnAction(e -> hideSearch());

        // Ctrl+F для показа поиска
        codeArea.setOnKeyPressed(event -> {
            if (event.isControlDown() && event.getCode() == javafx.scene.input.KeyCode.F) {
                showSearch();
                event.consume();
            }
            if (event.getCode() == javafx.scene.input.KeyCode.ESCAPE) {
                hideSearch();
                event.consume();
            }
        });
    }

    private void performSearch(String searchText) {
        String text = codeArea.getText();
        if (text == null || text.isEmpty()) {
            clearSearch();
            return;
        }

        lastSearchText = searchText;
        String lowerText = text.toLowerCase();
        String lowerSearch = searchText.toLowerCase();

        searchMatches.clear();
        int index = 0;
        while (index < lowerText.length()) {
            int found = lowerText.indexOf(lowerSearch, index);
            if (found == -1) break;
            searchMatches.add(found);
            index = found + lowerSearch.length();
        }

        if (searchMatches.isEmpty()) {
            prevButton.setDisable(true);
            nextButton.setDisable(true);
            counterLabel.setText("0/0");
            clearHighlights();
            return;
        }

        prevButton.setDisable(false);
        nextButton.setDisable(false);
        currentMatchIndex = 0;
        updateCounterLabel();

        // Подсветка всех вхождений
        highlightAllMatches(searchText);

        // Переход к первому
        navigateToCurrentMatch();
    }

    private void highlightAllMatches(String searchText) {
        String text = codeArea.getText();
        if (text == null || text.isEmpty()) return;

        StyleSpans<Collection<String>> currentSpans = codeArea.getStyleSpans(0, text.length());
        StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();

        String lowerSearch = searchText.toLowerCase();
        int pos = 0;

        for (StyleSpan<Collection<String>> span : currentSpans) {
            int spanStart = pos;
            int spanEnd = pos + span.getLength();
            String spanText = text.substring(spanStart, spanEnd);
            String lowerSpanText = spanText.toLowerCase();

            int searchPos = 0;
            int lastPos = 0;

            while (searchPos < spanText.length()) {
                int found = lowerSpanText.indexOf(lowerSearch, searchPos);
                if (found == -1) break;

                // Текст до найденного
                if (found > lastPos) {
                    spansBuilder.add(span.getStyle(), found - lastPos);
                }

                // Подсвеченное вхождение
                Collection<String> highlightStyles = new ArrayList<>(span.getStyle());
                highlightStyles.add("search-match");
                spansBuilder.add(highlightStyles, lowerSearch.length());

                lastPos = found + lowerSearch.length();
                searchPos = lastPos;
            }

            // Остаток спанa
            if (lastPos < spanText.length()) {
                spansBuilder.add(span.getStyle(), spanText.length() - lastPos);
            }

            pos = spanEnd;
        }

        codeArea.setStyleSpans(0, spansBuilder.create());
    }

    private void clearHighlights() {
        // Восстанавливаем стили (синтаксис)
        OneCHighlighter.apply1CColors(codeArea);
    }

    private void navigateToCurrentMatch() {
        if (searchMatches.isEmpty() || currentMatchIndex < 0 || currentMatchIndex >= searchMatches.size()) return;

        int position = searchMatches.get(currentMatchIndex);

        Platform.runLater(() -> {
            codeArea.moveTo(position, 0);
            codeArea.requestFocus();
            codeArea.showParagraphAtTop(codeArea.getCurrentParagraph());
        });
    }

    private void updateCounterLabel() {
        if (searchMatches.isEmpty()) {
            counterLabel.setText("0/0");
        } else {
            counterLabel.setText((currentMatchIndex + 1) + "/" + searchMatches.size());
        }
    }

    public void showSearch() {
        searchBar.setVisible(true);
        searchField.requestFocus();
        searchField.selectAll();
    }

    public void hideSearch() {
        searchBar.setVisible(false);
        clearSearch();
    }

    public void clearSearch() {
        searchField.clear();
        lastSearchText = "";
        searchMatches.clear();
        currentMatchIndex = -1;
        prevButton.setDisable(true);
        nextButton.setDisable(true);
        counterLabel.setText("0/0");
        clearHighlights();
    }

    // Прокси-методы для доступа к CodeArea
    public void replaceText(String text) {
        codeArea.replaceText(text);
        clearSearch();
    }

    public String getText() {
        return codeArea.getText();
    }

    public void setStyleSpans(int from, StyleSpans<Collection<String>> spans) {
        codeArea.setStyleSpans(from, spans);
    }

    public void moveTo(int position, int column) {
        codeArea.moveTo(position, column);
    }

    public void requestFocus() {
        codeArea.requestFocus();
    }

    public int getCurrentParagraph() {
        return codeArea.getCurrentParagraph();
    }

    public void showParagraphAtTop(int paragraph) {
        codeArea.showParagraphAtTop(paragraph);
    }

    public int getCaretPosition() {
        return codeArea.getCaretPosition();
    }

    public StyleSpans<Collection<String>> getStyleSpans(int start, int end) {
        return codeArea.getStyleSpans(start, end);
    }

    public CodeArea getCodeArea() {
        return codeArea;
    }
}