package com.lycurg.metavisionfor1c;

import javafx.application.Platform;
import org.fxmisc.richtext.CodeArea;

import java.awt.*;
import java.util.*;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

import javafx.scene.control.TextField;
import javafx.scene.control.Button;
import javafx.scene.control.Label;


//# Контроллер вкладки 2: рабочая среда с инструментами (валидатор кода, конвертер запросов, форматирование)
public class DependencyWorkbench {

    private final DependencyController mainController;

    private CodeSearchHelper workbenchCodeSearch;

    public DependencyWorkbench(DependencyController controller) {
        this.mainController = controller;
    }

    public void initialize() {
        setupSyntaxHighlighting();
        initializeWorkbench();

        setupCodeSearch();
    }



    private void setupCodeSearch() {
        TextField searchField = mainController.getWorkbenchCodeSearchField();
        Button prevBtn = mainController.getWorkbenchCodeSearchPrevBtn();
        Button nextBtn = mainController.getWorkbenchCodeSearchNextBtn();
        Label counter = mainController.getWorkbenchCodeSearchCounter();
        CodeArea codeArea = mainController.getWorkbenchCodeArea();

        if (searchField != null && codeArea != null) {
            workbenchCodeSearch = new CodeSearchHelper(codeArea, searchField, prevBtn, nextBtn, counter);
            System.out.println("✅ Поиск по модулю на вкладке 2 инициализирован");
        }
    }

    public void searchPrevInCode() {
        if (workbenchCodeSearch != null) {
            workbenchCodeSearch.navigatePrev();
        }
    }

    public void searchNextInCode() {
        if (workbenchCodeSearch != null) {
            workbenchCodeSearch.navigateNext();
        }
    }
    //онвертация запросов между форматами с пайпами и без
    public void queryToText() {
        if (mainController.getWorkbenchCodeArea() == null) return;

        String code = mainController.getWorkbenchCodeArea().getText();
        if (code.trim().isEmpty()) {
            logToWorkbench("⚠️ Код пустой");
            return;
        }

        // 🔥 ИСПОЛЬЗУЕМ ТВОЮ ФУНКЦИЮ
        String converted = Query1CConverter.convertCleanToPipes(code);


        mainController.getWorkbenchCodeArea().replaceText(converted);

        // 🔥 ЯВНЫЙ ВЫЗОВ ПОДСВЕТКИ
        OneCHighlighter.apply1CColors(mainController.getWorkbenchCodeArea());

        logToWorkbench("✅ Запросы с | преобразованы в строки");
        updateWorkbenchStats();
    }


    //валидация синтаксиса 1С с детальным отчетом об ошибках
    public void validateCode() {
        if (mainController.getWorkbenchCodeArea() == null) return;

        String code = mainController.getWorkbenchCodeArea().getText();
        if (code.trim().isEmpty()) {
            showValidationResult("⚠️ Код пустой");
            return;
        }

        List<Code1CValidator.ValidationError> errors = Code1CValidator.validate(code);

        StringBuilder result = new StringBuilder();
        result.append("=== РЕЗУЛЬТАТЫ ПРОВЕРКИ КОДА ===\n\n");

        if (errors.isEmpty()) {
            result.append("✅ Код проверен. Ошибок не найдено!\n\n");
        } else {
            result.append("⚠️ Найдено ошибок: ").append(errors.size()).append("\n\n");

            for (Code1CValidator.ValidationError error : errors) {
                result.append("📌 Строка ").append(error.lineNumber).append(": ")
                        .append(error.message).append("\n");

                if (error.line != null && !error.line.trim().isEmpty()) {
                    result.append("   > ").append(error.line.trim()).append("\n");
                }
                result.append("\n");
            }

            // Статистика
            Map<String, Integer> stats = new HashMap<>();
            for (Code1CValidator.ValidationError error : errors) {
                String type = extractErrorType(error.message);
                stats.put(type, stats.getOrDefault(type, 0) + 1);
            }

            result.append("=== СТАТИСТИКА ОШИБОК ===\n");
            stats.forEach((type, count) -> {
                result.append("• ").append(type).append(": ").append(count).append("\n");
            });

            // Рекомендации
            result.append("\n=== РЕКОМЕНДАЦИИ ===\n");
            if (stats.containsKey("Не закрыт блок")) {
                result.append("• Добавьте недостающие КонецЕсли/КонецЦикла/КонецПроцедуры\n");
            }
            if (stats.containsKey("Неправильная структура")) {
                result.append("• Проверьте порядок конструкций (Если-ИначеЕсли-Иначе-КонецЕсли)\n");
            }
            if (stats.containsKey("Незакрытая кавычка")) {
                result.append("• Проверьте незакрытые кавычки в строках\n");
            }
        }

        result.append("\n=== ПРОВЕРКА ЗАВЕРШЕНА ===");
        showValidationResult(result.toString());
    }

    // 🔥 НОВЫЙ МЕТОД: показывает результаты в панели
    private void showValidationResult(String message) {
        if (mainController != null && mainController.validationResultsArea != null) {
            Platform.runLater(() -> {
                mainController.validationResultsArea.clear();
                mainController.validationResultsArea.appendText(message);
                mainController.validationResultsArea.setScrollTop(0);
            });
        }
        logToWorkbench("✅ Проверка кода завершена");
    }

    // 🔥 Вспомогательный метод для определения типа ошибки
    private String extractErrorType(String errorMessage) {
        if (errorMessage.contains("Не закрыт блок")) return "Не закрыт блок";
        if (errorMessage.contains("без соответствующего")) return "Неправильная структура";
        if (errorMessage.contains("Незакрытая кавычка")) return "Незакрытая кавычка";
        if (errorMessage.contains("Незакрытая скобка")) return "Незакрытая скобка";
        if (errorMessage.contains("Лишняя закрывающая скобка")) return "Лишняя закрывающая скобка";
        return "Другие ошибки";
    }

    public void textToQuery() {
        if (mainController.getWorkbenchCodeArea() == null) return;

        String code = mainController.getWorkbenchCodeArea().getText();
        if (code.trim().isEmpty()) {
            logToWorkbench("⚠️ Код пустой");
            return;
        }

        // 🔥 ИСПОЛЬЗУЕМ ТВОЮ ФУНКЦИЮ
        String converted = Query1CConverter.convertPipesToClean(code);

        mainController.getWorkbenchCodeArea().replaceText(converted);
        OneCHighlighter.apply1CColors(mainController.getWorkbenchCodeArea());
        logToWorkbench("✅ Строки преобразованы в запросы с |");
        updateWorkbenchStats();
    }


    //автоматическое форматирование отступов по ключевым словам
    public void format1CCode() {
        if (mainController.getWorkbenchCodeArea() == null) {
            // Используем лог вкладки 2 вместо несуществующего addMessage
            logToWorkbench("❌ Ошибка: редактор кода не доступен");
            return;
        }

        String originalCode = mainController.getWorkbenchCodeArea().getText();
        if (originalCode == null || originalCode.trim().isEmpty()) {
            logToWorkbench("⚠️ Нет кода для форматирования");
            return;
        }

        logToWorkbench("🔧 Начало форматирования кода 1С...");

        try {
            CodeFormatter1C formatter = new CodeFormatter1C();

            // Отладочная информация
            formatter.printDebugInfo(originalCode);

            // Форматируем код
            String formattedCode = formatter.formatCode(originalCode);

            // Применяем форматированный код
            mainController.getWorkbenchCodeArea().replaceText(formattedCode);

            // Применяем подсветку синтаксиса
            OneCHighlighter.apply1CColors(mainController.getWorkbenchCodeArea());

            logToWorkbench("✅ Код успешно отформатирован");
            logToWorkbench("📝 Изменения: добавлены отступы, выровнены операторы = и КАК в запросах");

        } catch (Exception e) {
            logToWorkbench("❌ Ошибка форматирования: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void setupSyntaxHighlighting() {
        if (mainController.getWorkbenchCodeArea() != null) {
            mainController.getWorkbenchCodeArea().textProperty().addListener((observable, oldValue, newValue) -> {
                // 🔥 БЫЛО: OneCHighlighter.setupAutoHighlighting(...)
                // 🔥 СТАЛО: обычный слушатель с задержкой

                // Небольшая задержка чтобы не подсвечивать на каждый символ
                javafx.application.Platform.runLater(() -> {
                    OneCHighlighter.apply1CColors(mainController.getWorkbenchCodeArea());
                    updateWorkbenchStats();
                });
            });

            // Применяем подсветку к текущему тексту
            OneCHighlighter.apply1CColors(mainController.getWorkbenchCodeArea());
        }
    }

    private void initializeWorkbench() {
        if (mainController.getWorkbenchCodeArea() != null) {
            String exampleCode = "&НаСервере\n" +
                    "Процедура Пример()\n" +
                    "    // Ваш код 1С здесь\n" +
                    "    Запрос = Новый Запрос;\n" +
                    "    Запрос.Текст = \"ВЫБРАТЬ * ИЗ Документы\";\n" +
                    "    Результат = Запрос.Выполнить();\n" +
                    "    \n" +
                    "    Для Каждого Строка Из Результат.Выгрузить() Цикл\n" +
                    "        Сообщить(Строка.Наименование);\n" +
                    "    КонецЦикла;\n" +
                    "КонецПроцедуры";

            mainController.getWorkbenchCodeArea().replaceText(exampleCode);
            OneCHighlighter.apply1CColors(mainController.getWorkbenchCodeArea());
            updateWorkbenchStats();
            logToWorkbench("🛠️ Готов к работе. Вставьте или отредактируйте код 1С.");
        }
    }


    //очистка редактора и лога
    public void clearWorkbench() {
        if (mainController.getWorkbenchCodeArea() != null) {
            mainController.getWorkbenchCodeArea().clear();
        }
        if (mainController.getWorkbenchLogArea() != null) {
            mainController.getWorkbenchLogArea().clear();
        }
        updateWorkbenchStats();
        logToWorkbench("🧹 Окно редактора очищено");
    }

    public void clearWorkbenchLog() {
        if (mainController.getWorkbenchLogArea() != null) {
            mainController.getWorkbenchLogArea().clear();
            logToWorkbench("🗑️ Лог операций очищен");
        }
    }

    private void logToWorkbench(String message) {
        if (mainController.getWorkbenchLogArea() != null) {
            Platform.runLater(() -> {
                mainController.getWorkbenchLogArea().appendText(message + "\n");
                mainController.getWorkbenchLogArea().setScrollTop(Double.MAX_VALUE);
            });
        }
        if (mainController.getWorkbenchStatusLabel() != null) {
            Platform.runLater(() -> mainController.getWorkbenchStatusLabel().setText(message));
        }
    }

    private void updateWorkbenchStats() {
        if (mainController.getWorkbenchCodeArea() != null && mainController.getWorkbenchStatsLabel() != null) {
            String code = mainController.getWorkbenchCodeArea().getText();
            int lines = code.isEmpty() ? 0 : code.split("\n").length;
            int chars = code.length();
            Platform.runLater(() ->
                    mainController.getWorkbenchStatsLabel().setText(String.format("Строк: %d | Символов: %d", lines, chars))
            );
        }
    }

    public void shutdown() {
        // Очистка ресурсов если нужно
    }


    public class Query1CConverter {

        /**
         * Конвертирует запрос с | и кавычками в чистый текст
         */
        public static String convertPipesToClean(String text) {
            if (text == null || text.isEmpty()) {
                return "";
            }

            StringBuilder result = new StringBuilder();
            String[] lines = text.split("\\r?\\n", -1);

            boolean inQuery = false;

            for (int i = 0; i < lines.length; i++) {
                String line = lines[i];
                String trimmed = line.trim();

                // Начало блока запроса: строка начинается с "
                if (!inQuery && trimmed.startsWith("\"") && isQueryStart(trimmed.substring(1).trim())) {
                    inQuery = true;
                    // Убираем открывающую кавычку и первый |
                    String cleaned = trimmed.substring(1); // убрали "
                    if (cleaned.startsWith("|")) cleaned = cleaned.substring(1);
                    result.append(cleaned).append("\n");
                    continue;
                }

                if (inQuery) {
                    // Конец блока: строка заканчивается на ";
                    if (trimmed.endsWith("\";") || trimmed.equals("\";")) {
                        // Убираем | в начале и "; в конце
                        String cleaned = trimmed;
                        if (cleaned.startsWith("|")) cleaned = cleaned.substring(1);
                        cleaned = cleaned.replaceAll("\";$", "");
                        if (!cleaned.trim().isEmpty()) {
                            result.append(cleaned).append("\n");
                        }
                        inQuery = false;
                        continue;
                    }

                    // Строка внутри запроса
                    String cleaned = trimmed;
                    if (cleaned.startsWith("|")) cleaned = cleaned.substring(1);
                    result.append(cleaned).append("\n");
                } else {
                    result.append(line).append("\n");
                }
            }

            return result.toString();
        }

        /**
         * Конвертирует чистый текст запроса обратно в формат с | и кавычками
         */
        public static String convertCleanToPipes(String text) {
            if (text == null || text.isEmpty()) return "";

            // 🔥 Обрезаем пустые строки сверху и снизу
            text = text.trim();

            StringBuilder result = new StringBuilder();
            String[] lines = text.split("\\r?\\n", -1);

            boolean inQuery = false;
            String baseIndent = "";
            int baseIndentLevel = 0;
            int caseDepth = 0; // 🔥 счётчик вложенности ВЫБОР...КОНЕЦ
            StringBuilder queryBuffer = new StringBuilder();

            for (int i = 0; i < lines.length; i++) {
                String line = lines[i];
                String trimmed = line.trim();
                String upper = trimmed.toUpperCase();

                if (!inQuery && trimmed.isEmpty()) {
                    result.append(line).append("\n");
                    continue;
                }

                // Начало запроса
                if (!inQuery && isQueryStart(trimmed)) {
                    inQuery = true;
                    caseDepth = 0;
                    baseIndent = getIndent(line);
                    baseIndentLevel = getIndentLevel(line);
                    queryBuffer.setLength(0);
                    queryBuffer.append(baseIndent).append("\"").append(trimmed).append("\n");
                    continue;
                }

                if (inQuery) {
                    // Считаем вложенность ВЫБОР / КОНЕЦ внутри SQL
                    if (upper.startsWith("ВЫБОР") || upper.startsWith("CASE")) {
                        caseDepth++;
                    }
                    // КОНЕЦ КАК ... или просто КОНЕЦ
                    if (upper.startsWith("КОНЕЦ") || upper.startsWith("END")) {
                        caseDepth = Math.max(0, caseDepth - 1);
                    }

                    // Разделитель пакетного запроса
                    if (trimmed.equals(";") || trimmed.startsWith("///")) {
                        boolean hasNextQuery = false;
                        for (int j = i + 1; j < lines.length; j++) {
                            String nextTrimmed = lines[j].trim();
                            if (nextTrimmed.isEmpty() || nextTrimmed.startsWith("///")) continue;
                            if (isQueryStart(nextTrimmed)) hasNextQuery = true;
                            break;
                        }

                        if (hasNextQuery) {
                            queryBuffer.append(baseIndent).append("\t|").append(trimmed).append("\n");
                        } else {
                            String query = queryBuffer.toString();
                            if (query.endsWith("\n")) query = query.substring(0, query.length() - 1);
                            result.append(query).append("\";\n");
                            inQuery = false;
                            caseDepth = 0;
                            queryBuffer.setLength(0);
                            result.append(line).append("\n");
                        }
                        continue;
                    }

                    int currentIndentLevel = getIndentLevel(line);

                    // 🔥 Завершаем запрос только если:
                    // 1. Уровень отступа вернулся к базовому
                    // 2. Это точно код 1С
                    // 3. МЫ НЕ ВНУТРИ ВЫБОР...КОНЕЦ (caseDepth == 0)
                    if (caseDepth == 0
                            && currentIndentLevel <= baseIndentLevel
                            && !trimmed.isEmpty()
                            && isDefinitelyCode1C(trimmed)) {

                        String query = queryBuffer.toString();
                        if (query.endsWith("\n")) query = query.substring(0, query.length() - 1);
                        result.append(query).append("\";\n");
                        result.append(line).append("\n");
                        inQuery = false;
                        queryBuffer.setLength(0);
                    } else {
                        queryBuffer.append(baseIndent).append("\t|").append(trimmed).append("\n");
                    }
                } else {
                    result.append(line).append("\n");
                }
            }

            if (inQuery && queryBuffer.length() > 0) {
                String query = queryBuffer.toString();
                if (query.endsWith("\n")) query = query.substring(0, query.length() - 1);
                    result.append(query).append("\";\n");
            }


            // 🔥 Обрезаем пустые строки в результате сверху и снизу
            return result.toString().trim() + "\n";

        }


        /**
         * Проверяет начало запроса
         */
        private static boolean isQueryStart(String line) {
            String upper = line.toUpperCase();
            return upper.startsWith("ВЫБРАТЬ") || upper.startsWith("SELECT");
        }

        /**
         * FIXED for Windows - заменены прямые русские диапазоны [А-ЯA-Z] на Unicode
         * Проверяет, является ли строка ТОЧНО кодом 1С (не SQL)
         */
        private static boolean isDefinitelyCode1C(String line) {
            if (line.isEmpty()) return false;

            String upper = line.toUpperCase();

            // 🔥 Сначала исключаем SQL-паттерны — они НЕ являются кодом 1С

            // SQL КАК алиас: "Документ.ЭтапПроизводства КАК Алиас"
            if (upper.matches(".*\\s+КАК\\s+[А-ЯA-Z\\u0410-\\u042F][А-ЯA-Zа-яa-z\\u0400-\\u04FF0-9]*.*")) {
                return false;
            }

            // SQL соединения: ЛЕВОЕ СОЕДИНЕНИЕ, ВНУТРЕННЕЕ СОЕДИНЕНИЕ и т.д.
            if (upper.startsWith("ЛЕВОЕ") || upper.startsWith("ПРАВОЕ") ||
                    upper.startsWith("ВНУТРЕННЕЕ") || upper.startsWith("ПОЛНОЕ") ||
                    upper.startsWith("СОЕДИНЕНИЕ") ||
                    upper.startsWith("LEFT") || upper.startsWith("RIGHT") ||
                    upper.startsWith("INNER") || upper.startsWith("FULL") ||
                    upper.startsWith("JOIN") || upper.startsWith("CROSS")) {
                return false;
            }

            // SQL ПО (...): условие соединения
            if (upper.startsWith("ПО (") || upper.startsWith("ПО(") || upper.startsWith("ON ")) {
                return false;
            }

            // SQL ГДЕ / WHERE
            if (upper.startsWith("ГДЕ") || upper.startsWith("WHERE")) {
                return false;
            }

            // SQL И / ИЛИ в условии (строка начинается с И или ИЛИ — это SQL-условие)
            if (upper.startsWith("И ") || upper.startsWith("ИЛИ ") || upper.startsWith("AND ") || upper.startsWith("OR ")) {
                return false;
            }

            // SQL СГРУППИРОВАТЬ / УПОРЯДОЧИТЬ / ИМЕЮЩИЕ
            if (upper.startsWith("СГРУППИРОВАТЬ") || upper.startsWith("УПОРЯДОЧИТЬ") ||
                    upper.startsWith("ИМЕЮЩИЕ") || upper.startsWith("GROUP BY") ||
                    upper.startsWith("ORDER BY") || upper.startsWith("HAVING")) {
                return false;
            }

            // SQL ПОМЕСТИТЬ / INTO
            if (upper.startsWith("ПОМЕСТИТЬ") || upper.startsWith("INTO")) {
                return false;
            }

            // SQL ИЗ / FROM
            if (upper.startsWith("ИЗ") || upper.startsWith("FROM")) {
                return false;
            }

            // SQL КОГДА / ТОГДА / ИНАЧЕ / КОНЕЦ
            if (upper.startsWith("КОГДА") || upper.startsWith("ТОГДА") ||
                    upper.startsWith("ИНАЧЕ") || upper.startsWith("КОНЕЦ") ||
                    upper.startsWith("WHEN") || upper.startsWith("THEN") ||
                    upper.startsWith("ELSE") || upper.startsWith("END")) {
                return false;
            }

            // SQL строка с точкой + КАК или просто ссылка на поле таблицы
            // "Документ.Что_то КАК ..." или "Таблица.Поле"
            if (line.matches("^[А-ЯA-Za-z\\u0400-\\u04FF][А-ЯA-Za-z\\u0400-\\u04FF0-9_]*\\.[А-ЯA-Za-z\\u0400-\\u04FF].*")) {
                // Уточняем: если это вызов метода 1С — там будет скобка
                // "Результат.Выгрузить()" — это 1С
                // "Документ.ЭтапПроизводства КАК" — это SQL
                if (!line.contains("(") && !line.contains(")")) {
                    return false; // нет скобок — скорее всего SQL поле/таблица
                }
            }

            // ✅ Теперь позитивные проверки — точно код 1С

            // Ключевые слова встроенного языка 1С
            String[] bslKeywords = {
                    "ЕСЛИ", "IF", "ИНАЧЕЕСЛИ", "ELSEIF",
                    "КОНЕЦЕСЛИ", "ENDIF",
                    "ДЛЯ", "FOR", "КОНЕЦЦИКЛА", "ENDDO", "КОНЕЦДЛЯ",
                    "ПОКА", "WHILE",
                    "ПРОЦЕДУРА", "PROCEDURE", "КОНЕЦПРОЦЕДУРЫ", "ENDPROCEDURE",
                    "ФУНКЦИЯ", "FUNCTION", "КОНЕЦФУНКЦИИ", "ENDFUNCTION",
                    "ВОЗВРАТ", "RETURN", "ПРЕРВАТЬ", "BREAK", "ПРОДОЛЖИТЬ", "CONTINUE",
                    "ПОПЫТКА", "TRY", "ИСКЛЮЧЕНИЕ", "EXCEPT", "КОНЕЦПОПЫТКИ", "ENDTRY",
                    "НОВЫЙ", "NEW"
            };

            for (String keyword : bslKeywords) {
                String k = keyword.toUpperCase();
                if (upper.equals(k) || upper.startsWith(k + " ") ||
                        upper.startsWith(k + "\t") || upper.startsWith(k + "(") ||
                        upper.startsWith(k + ";")) {
                    return true;
                }
            }

            // Присваивание без точки, без &, без скобок — точно 1С переменная
            if (line.matches("^[\\u0410-\\u042FA-Z][\\u0430-\\u044f\\u0410-\\u042FA-Za-z0-9]* = .*")
                    && !line.contains("&")
                    && !line.contains(".")
                    && !line.contains("(")) {
                return true;
            }

            return false;
        }

        /**
         * Получает отступ в начале строки
         */
        private static String getIndent(String line) {
            int i = 0;
            while (i < line.length() && Character.isWhitespace(line.charAt(i))) {
                i++;
            }
            return line.substring(0, i);
        }

        /**
         * Получает уровень отступа (количество символов)
         */
        private static int getIndentLevel(String line) {
            return getIndent(line).length();
        }
    }



    public class Code1CValidator {

        public static class ValidationError {
            public int lineNumber;
            public String message;
            public String line;

            public ValidationError(int lineNumber, String message, String line) {
                this.lineNumber = lineNumber;
                this.message = message;
                this.line = line;
            }

            @Override
            public String toString() {
                return "Строка " + lineNumber + ": " + message + "\n  > " + line.trim();
            }
        }

        private static class BlockInfo {
            String type;
            int lineNumber;
            String line;
            boolean hasReturn; // Для функций - была ли команда Возврат

            BlockInfo(String type, int lineNumber, String line) {
                this.type = type;
                this.lineNumber = lineNumber;
                this.line = line;
                this.hasReturn = false;
            }
        }

        /**
         * Валидирует код 1С и возвращает список ошибок
         */
        public static List<ValidationError> validate(String code) {
            List<ValidationError> errors = new ArrayList<>();

            if (code == null || code.isEmpty()) {
                return errors;
            }

            String[] lines = code.split("\\r?\\n");

            // Стеки для отслеживания открытых блоков
            Stack<BlockInfo> blockStack = new Stack<>();
            Stack<Integer> parenthesesStack = new Stack<>();
            Stack<BlockInfo> loopStack = new Stack<>(); // Отдельный стек для циклов

            // Отслеживание кавычек
            boolean inString = false;
            int stringStartLine = -1;

            for (int i = 0; i < lines.length; i++) {
                String line = lines[i];
                String trimmed = line.trim();
                String upper = trimmed.toUpperCase();
                int lineNum = i + 1;

                // Пропускаем пустые строки и комментарии
                if (trimmed.isEmpty() || trimmed.startsWith("//")) {
                    continue;
                }

                // Проверка парных скобок (вне строк)
                checkParentheses(line, lineNum, parenthesesStack, errors);

                // Проверка парных кавычек
                int quoteCount = countQuotes(line);
                if (quoteCount % 2 != 0) {
                    if (!inString) {
                        inString = true;
                        stringStartLine = lineNum;
                    } else {
                        inString = false;
                    }
                }

                // Проверка блочных конструкций

                // Если
                if (startsWithKeyword(upper, "ЕСЛИ", "IF")) {
                    blockStack.push(new BlockInfo("ЕСЛИ", lineNum, trimmed));
                }
                else if (startsWithKeyword(upper, "ИНАЧЕ", "ELSE")) {
                    if (blockStack.isEmpty() || !blockStack.peek().type.equals("ЕСЛИ")) {
                        errors.add(new ValidationError(lineNum,
                                "Иначе без соответствующего Если", trimmed));
                    }
                }
                else if (startsWithKeyword(upper, "ИНАЧЕЕСЛИ", "ELSEIF")) {
                    if (blockStack.isEmpty() || !blockStack.peek().type.equals("ЕСЛИ")) {
                        errors.add(new ValidationError(lineNum,
                                "ИначеЕсли без соответствующего Если", trimmed));
                    }
                }
                else if (startsWithKeyword(upper, "КОНЕЦЕСЛИ", "ENDIF")) {
                    if (blockStack.isEmpty() || !blockStack.peek().type.equals("ЕСЛИ")) {
                        errors.add(new ValidationError(lineNum,
                                "КонецЕсли без соответствующего Если", trimmed));
                    } else {
                        blockStack.pop();
                    }
                }

                // Циклы Для (проверяем наличие ключевого слова в строке, а не только начало)
                else if (upper.contains("ДЛЯ КАЖДОГО") || upper.contains("FOR EACH") ||
                        startsWithKeyword(upper, "ДЛЯ", "FOR")) {
                    // Проверяем что это именно цикл, а не часть другой конструкции
                    if (upper.contains("ЦИКЛ") || upper.contains("DO")) {
                        BlockInfo loopBlock = new BlockInfo("ЦИКЛ", lineNum, trimmed);
                        blockStack.push(loopBlock);
                        loopStack.push(loopBlock);
                    }
                }

                // Цикл Пока
                else if (startsWithKeyword(upper, "ПОКА", "WHILE")) {
                    BlockInfo loopBlock = new BlockInfo("ЦИКЛ", lineNum, trimmed);
                    blockStack.push(loopBlock);
                    loopStack.push(loopBlock);
                }

                // Конец цикла
                else if (startsWithKeyword(upper, "КОНЕЦЦИКЛА", "ENDDO", "КОНЕЦДЛЯ")) {
                    if (blockStack.isEmpty() || !blockStack.peek().type.equals("ЦИКЛ")) {
                        errors.add(new ValidationError(lineNum,
                                "КонецЦикла без соответствующего Для/Пока", trimmed));
                    } else {
                        blockStack.pop();
                        if (!loopStack.isEmpty()) {
                            loopStack.pop();
                        }
                    }
                }

                // Процедура
                else if (startsWithKeyword(upper, "ПРОЦЕДУРА", "PROCEDURE")) {
                    blockStack.push(new BlockInfo("ПРОЦЕДУРА", lineNum, trimmed));
                }
                else if (startsWithKeyword(upper, "КОНЕЦПРОЦЕДУРЫ", "ENDPROCEDURE")) {
                    if (blockStack.isEmpty() || !blockStack.peek().type.equals("ПРОЦЕДУРА")) {
                        errors.add(new ValidationError(lineNum,
                                "КонецПроцедуры без соответствующей Процедуры", trimmed));
                    } else {
                        blockStack.pop();
                    }
                }

                // Функция
                else if (startsWithKeyword(upper, "ФУНКЦИЯ", "FUNCTION")) {
                    blockStack.push(new BlockInfo("ФУНКЦИЯ", lineNum, trimmed));
                }
                else if (startsWithKeyword(upper, "КОНЕЦФУНКЦИИ", "ENDFUNCTION")) {
                    if (blockStack.isEmpty() || !blockStack.peek().type.equals("ФУНКЦИЯ")) {
                        errors.add(new ValidationError(lineNum,
                                "КонецФункции без соответствующей Функции", trimmed));
                    } else {
                        BlockInfo func = blockStack.pop();
                        // Проверяем наличие Возврат в функции
                        if (!func.hasReturn) {
                            errors.add(new ValidationError(func.lineNumber,
                                    "Функция не содержит оператор Возврат", func.line));
                        }
                    }
                }

                // Возврат
                else if (startsWithKeyword(upper, "ВОЗВРАТ", "RETURN")) {
                    // Проверяем что мы внутри функции
                    boolean inFunction = false;
                    for (BlockInfo block : blockStack) {
                        if (block.type.equals("ФУНКЦИЯ")) {
                            block.hasReturn = true;
                            inFunction = true;
                            break;
                        }
                    }

                    // Возврат в процедуре - ошибка
                    if (!inFunction) {
                        boolean inProcedure = false;
                        for (BlockInfo block : blockStack) {
                            if (block.type.equals("ПРОЦЕДУРА")) {
                                inProcedure = true;
                                break;
                            }
                        }
                        if (inProcedure) {
                            errors.add(new ValidationError(lineNum,
                                    "Возврат не может использоваться в Процедуре (только в Функции)", trimmed));
                        }
                    }
                }

                // Прервать
                else if (startsWithKeyword(upper, "ПРЕРВАТЬ", "BREAK")) {
                    if (loopStack.isEmpty()) {
                        errors.add(new ValidationError(lineNum,
                                "Прервать может использоваться только внутри цикла", trimmed));
                    }
                }

                // Продолжить
                else if (startsWithKeyword(upper, "ПРОДОЛЖИТЬ", "CONTINUE")) {
                    if (loopStack.isEmpty()) {
                        errors.add(new ValidationError(lineNum,
                                "Продолжить может использоваться только внутри цикла", trimmed));
                    }
                }

                // Попытка
                else if (startsWithKeyword(upper, "ПОПЫТКА", "TRY")) {
                    blockStack.push(new BlockInfo("ПОПЫТКА", lineNum, trimmed));
                }
                else if (startsWithKeyword(upper, "ИСКЛЮЧЕНИЕ", "EXCEPT")) {
                    if (blockStack.isEmpty() || !blockStack.peek().type.equals("ПОПЫТКА")) {
                        errors.add(new ValidationError(lineNum,
                                "Исключение без соответствующей Попытки", trimmed));
                    }
                }
                else if (startsWithKeyword(upper, "КОНЕЦПОПЫТКИ", "ENDTRY")) {
                    if (blockStack.isEmpty() || !blockStack.peek().type.equals("ПОПЫТКА")) {
                        errors.add(new ValidationError(lineNum,
                                "КонецПопытки без соответствующей Попытки", trimmed));
                    } else {
                        blockStack.pop();
                    }
                }
            }

            // Проверка незакрытых блоков
            while (!blockStack.isEmpty()) {
                BlockInfo block = blockStack.pop();
                String expected = getExpectedClosing(block.type);
                errors.add(new ValidationError(block.lineNumber,
                        "Не закрыт блок " + block.type + " (ожидается " + expected + ")",
                        block.line));
            }

            // Проверка незакрытых скобок
            while (!parenthesesStack.isEmpty()) {
                int openLine = parenthesesStack.pop();
                errors.add(new ValidationError(openLine,
                        "Незакрытая скобка (", ""));
            }

            // Проверка незакрытых кавычек
            if (inString) {
                errors.add(new ValidationError(stringStartLine,
                        "Незакрытая кавычка", ""));
            }

            // Сортируем ошибки по номеру строки
            errors.sort(Comparator.comparingInt(e -> e.lineNumber));

            return errors;
        }

        /**
         * Проверяет баланс скобок в строке
         */
        private static void checkParentheses(String line, int lineNum,
                                             Stack<Integer> stack,
                                             List<ValidationError> errors) {
            boolean inString = false;

            for (int i = 0; i < line.length(); i++) {
                char c = line.charAt(i);

                // Отслеживаем строки
                if (c == '"') {
                    inString = !inString;
                    continue;
                }

                // Пропускаем символы внутри строк
                if (inString) {
                    continue;
                }

                if (c == '(') {
                    stack.push(lineNum);
                } else if (c == ')') {
                    if (stack.isEmpty()) {
                        errors.add(new ValidationError(lineNum,
                                "Лишняя закрывающая скобка )", line.trim()));
                    } else {
                        stack.pop();
                    }
                }
            }
        }

        /**
         * Подсчитывает количество кавычек в строке (учитывая экранирование)
         */
        private static int countQuotes(String line) {
            int count = 0;
            boolean escaped = false;

            for (int i = 0; i < line.length(); i++) {
                char c = line.charAt(i);

                if (c == '"' && !escaped) {
                    count++;
                }

                // В 1С экранирование через двойные кавычки ""
                escaped = (c == '"' && i + 1 < line.length() && line.charAt(i + 1) == '"');
                if (escaped) {
                    i++; // Пропускаем следующую кавычку
                }
            }

            return count;
        }

        /**
         * Проверяет, начинается ли строка с ключевого слова (регистронезависимо)
         */
        private static boolean startsWithKeyword(String upper, String... keywords) {
            for (String keyword : keywords) {
                String keywordUpper = keyword.toUpperCase();
                if (upper.equals(keywordUpper) ||
                        upper.startsWith(keywordUpper + " ") ||
                        upper.startsWith(keywordUpper + "\t") ||
                        upper.startsWith(keywordUpper + "(") ||
                        upper.startsWith(keywordUpper + ";")) {
                    return true;
                }
            }
            return false;
        }

        /**
         * Возвращает ожидаемое закрывающее ключевое слово
         */
        private static String getExpectedClosing(String blockType) {
            switch (blockType) {
                case "ЕСЛИ": return "КонецЕсли";
                case "ЦИКЛ": return "КонецЦикла";
                case "ПРОЦЕДУРА": return "КонецПроцедуры";
                case "ФУНКЦИЯ": return "КонецФункции";
                case "ПОПЫТКА": return "КонецПопытки";
                default: return "???";
            }
        }

        // Пример использования
        public static void main(String[] args) {
            String code =
                    "Процедура ТестоваяПроцедура()\n" +
                            "    \n" +
                            "    Если Условие Тогда\n" +
                            "        Для Каждого Элемент Из Массив Цикл\n" +
                            "            Сообщить(Элемент);\n" +
                            "            Прервать; // OK - внутри цикла\n" +
                            "        КонецЦикла;\n" +
                            "    КонецЕсли;\n" +
                            "    \n" +
                            "    Возврат 123; // Ошибка - в процедуре\n" +
                            "    \n" +
                            "КонецПроцедуры;\n" +
                            "\n" +
                            "Функция ВычислитьСумму(А, Б)\n" +
                            "    Результат = А + Б;\n" +
                            "    // Забыли Возврат - ошибка\n" +
                            "КонецФункции;\n" +
                            "\n" +
                            "Функция ПравильнаяФункция()\n" +
                            "    Прервать; // Ошибка - вне цикла\n" +
                            "    Возврат 42; // OK\n" +
                            "КонецФункции;\n";

            System.out.println("=== Проверка кода 1С ===\n");
            System.out.println("Код:");
            System.out.println(code);

            System.out.println("\n=== Результаты валидации ===\n");
            List<ValidationError> errors = validate(code);

            if (errors.isEmpty()) {
                System.out.println("✓ Нет ошибок");
            } else {
                System.out.println("✗ Найдено ошибок: " + errors.size() + "\n");
                for (ValidationError error : errors) {
                    System.out.println(error);
                    System.out.println();
                }
            }
        }
    }

}



