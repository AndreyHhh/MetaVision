package com.lycurg.metavisionfor1c;

import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


//# Подсветка синтаксиса 1С в CodeArea с поддержкой регистронезависимых ключевых слов и позиций выделения
public class OneCHighlighter {

    // 🔥 ВСЕ КЛЮЧЕВЫЕ СЛОВА В ВЕРХНЕМ РЕГИСТРЕ
    private static final Set<String> ALL_KEYWORDS_UPPER = Set.of(
            // Директивы (с &)
            "&НАСЕРВЕРЕ", "&НАКЛИЕНТЕ", "&НАСЕРВЕРЕБЕЗКОНТЕКСТА",
            "&НАКЛИЕНТЕНАСЕРВЕРЕБЕЗКОНТЕКСТА", "&ПЕРЕД", "&ПОСЛЕ",
            "&ВМЕСТО", "&ОБЩИЙМОДУЛЬ", "&ВНЕШНЯЯОБРАБОТКА",
            "&ВНЕШНЯЯОБРАБОТКАСЕРВЕР", "&ЕСЛИ", "&ИНАЧЕЕСЛИ",
            "&КОНЕЦЕСЛИ", "&ИНАЧЕ", "&ОБЛАСТЬ", "&КОНЕЦОБЛАСТИ",


            // Ключевые слова 1С (русский)
            "ЕСЛИ", "ТОГДА", "ИНАЧЕ", "ИНАЧЕЕСЛИ", "КОНЕЦЕСЛИ",
            "ДЛЯ", "КАЖДОГО", "ИЗ", "ПО", "ЦИКЛ", "КОНЕЦЦИКЛА", "ПОКА", "ПРЕРВАТЬ", "ПРОДОЛЖИТЬ",
            "ФУНКЦИЯ", "КОНЕЦФУНКЦИИ", "ПРОЦЕДУРА", "КОНЕЦПРОЦЕДУРЫ", "ВОЗВРАТ",
            "ПЕРЕМ", "ЭКСПОРТ", "ЗНАЧ",
            "НЕ", "И", "ИЛИ",
            "ПОПЫТКА", "ИСКЛЮЧЕНИЕ", "КОНЕЦПОПЫТКИ", "ВЫЗВАТЬИСКЛЮЧЕНИЕ",
            "ПЕРЕЙТИ", "НОВЫЙ", "ВЫЧИСЛИТЬ", "ВЫПОЛНИТЬРАСШИРЕНИЕ",


            // Ключевые слова 1С (английский)
            "IF", "THEN", "ELSE", "ELSEIF", "ENDIF",
            "FOR", "EACH", "IN", "TO", "DO", "ENDDO", "WHILE", "BREAK", "CONTINUE",
            "FUNCTION", "ENDFUNCTION", "PROCEDURE", "ENDPROCEDURE", "RETURN",
            "VAR", "EXPORT", "VAL",
            "NOT", "AND", "OR",
            "TRY", "EXCEPT", "ENDTRY", "RAISE",
            "GOTO", "NEW",

            // Литералы
            "ИСТИНА", "ЛОЖЬ", "НЕОПРЕДЕЛЕНО", "NULL",
            "TRUE", "FALSE", "UNDEFINED"
    );

    // 🔥 ЕДИНЫЙ ПАТТЕРН ДЛЯ ВСЕГО
    private static final Pattern COMBINED_PATTERN;

    static {
        // 🔥 Создаём паттерн для ключевых слов через регистронезависимые группы
        String keywordsPattern = String.join("|",
                ALL_KEYWORDS_UPPER.stream()
                        .map(OneCHighlighter::makeRegexCaseInsensitive)
                        .toArray(String[]::new)
        );

        // 🔥 ОДИН РЕГУЛЯРНЫЙ ВЫРАЖЕНИЕ
        String pattern =
                // 1. Строки запросов (начинаются с |)
                "(?<QUERY>^[ \\t]*\\|[^\\n\\r]*)" + "|" +
                        // 2. Строки в кавычках
                        "(?<STRING>\"[^\"\\\\]*(?:\\\\.[^\"\\\\]*)*\")" + "|" +
                        // 3. Скобки ( )
                        "(?<BRACKET>[()])" + "|" +
                        // 4. Ключевые слова (с границами для кириллицы)
                        "(?<KEYWORD>(?<![А-Яа-яA-Za-z0-9_&])(?:" + keywordsPattern + ")(?![А-Яа-яA-Za-z0-9_]))" + "|" +
                        // 5. Комментарии
                        "(?<COMMENT>//[^\\n\\r]*|/\\*.*?\\*/)" + "|" +
                        // 6. Числа
                        "(?<NUMBER>\\b\\d+(?:\\.\\d+)?\\b)";

        COMBINED_PATTERN = Pattern.compile(pattern, Pattern.MULTILINE | Pattern.DOTALL);
    }

    // 🔥 Преобразует слово в регистронезависимый regex-паттерн
    // "КонецЦикла" -> "[Кк][Оо][Нн][Ее][Цц][Цц][Ии][Кк][Лл][Аа]"
    private static String makeRegexCaseInsensitive(String word) {
        StringBuilder regex = new StringBuilder();
        for (char c : word.toCharArray()) {
            if (Character.isLetter(c)) {
                char lower = Character.toLowerCase(c);
                char upper = Character.toUpperCase(c);
                if (lower != upper) {
                    regex.append('[').append(lower).append(upper).append(']');
                } else {
                    regex.append(Pattern.quote(String.valueOf(c)));
                }
            } else {
                regex.append(Pattern.quote(String.valueOf(c)));
            }
        }
        return regex.toString();
    }

    public static void apply1CColors(CodeArea codeArea) {
        apply1CColors(codeArea, Collections.emptyList());
    }

    public static void apply1CColors(CodeArea codeArea, List<String> highlightWords) {
        String text = codeArea.getText();
        if (text == null || text.isEmpty()) return;

        StyleSpans<Collection<String>> highlighting = computeHighlighting(text, highlightWords);
        codeArea.setStyleSpans(0, highlighting);
    }

    public static void apply1CColorsWithPositions(CodeArea codeArea, List<int[]> highlightPositions) {
        String text = codeArea.getText();
        if (text == null || text.isEmpty()) return;

        StyleSpans<Collection<String>> highlighting = computeHighlightingWithPositions(text, highlightPositions);
        codeArea.setStyleSpans(0, highlighting);
    }

    private static StyleSpans<Collection<String>> computeHighlightingWithPositions(String text, List<int[]> highlightPositions) {
        List<Match> matches = new ArrayList<>();

        // 🔥 ОДИН ПРОХОД ПО ТЕКСТУ
        Matcher matcher = COMBINED_PATTERN.matcher(text);
        while (matcher.find()) {
            if (matcher.group("QUERY") != null) {
                matches.add(new Match(matcher.start(), matcher.end(), "query-string"));
            } else if (matcher.group("STRING") != null) {
                matches.add(new Match(matcher.start(), matcher.end(), "string"));
            } else if (matcher.group("BRACKET") != null) {
                matches.add(new Match(matcher.start(), matcher.end(), "keyword"));
            } else if (matcher.group("KEYWORD") != null) {
                matches.add(new Match(matcher.start(), matcher.end(), "keyword"));
            } else if (matcher.group("COMMENT") != null) {
                String comment = matcher.group();
                matches.add(new Match(matcher.start(), matcher.end(),
                        comment.startsWith("/*") ? "block-comment" : "line-comment"));
            } else if (matcher.group("NUMBER") != null) {
                matches.add(new Match(matcher.start(), matcher.end(), "number"));
            }
        }

        // Выделение позиций
        if (highlightPositions != null) {
            for (int[] pos : highlightPositions) {
                matches.add(new Match(pos[0], pos[1], "highlight"));
            }
        }

        return buildStyleSpans(text, matches);
    }

    private static StyleSpans<Collection<String>> computeHighlighting(String text, List<String> highlightWords) {
        List<Match> matches = new ArrayList<>();

        // 🔥 ОДИН ПРОХОД ПО ТЕКСТУ
        Matcher matcher = COMBINED_PATTERN.matcher(text);
        while (matcher.find()) {
            if (matcher.group("QUERY") != null) {
                matches.add(new Match(matcher.start(), matcher.end(), "query-string"));
            } else if (matcher.group("STRING") != null) {
                matches.add(new Match(matcher.start(), matcher.end(), "string"));
            } else if (matcher.group("BRACKET") != null) {
                matches.add(new Match(matcher.start(), matcher.end(), "keyword"));
            } else if (matcher.group("KEYWORD") != null) {
                matches.add(new Match(matcher.start(), matcher.end(), "keyword"));
            } else if (matcher.group("COMMENT") != null) {
                String comment = matcher.group();
                matches.add(new Match(matcher.start(), matcher.end(),
                        comment.startsWith("/*") ? "block-comment" : "line-comment"));
            } else if (matcher.group("NUMBER") != null) {
                matches.add(new Match(matcher.start(), matcher.end(), "number"));
            }
        }

        // 🔥 Выделение слов поиска (оптимизированное)
        if (highlightWords != null && !highlightWords.isEmpty()) {
            for (String word : highlightWords) {
                if (word != null && !word.trim().isEmpty()) {
                    // Создаём регистронезависимый паттерн для каждого слова
                    String wordPattern = makeRegexCaseInsensitive(word.trim());
                    Pattern highlightPattern = Pattern.compile(
                            "(?<![А-Яа-яA-Za-z0-9_])" + wordPattern + "(?![А-Яа-яA-Za-z0-9_])"
                    );
                    Matcher hlMatcher = highlightPattern.matcher(text);
                    while (hlMatcher.find()) {
                        matches.add(new Match(hlMatcher.start(), hlMatcher.end(), "highlight"));
                    }
                }
            }
        }

        return buildStyleSpans(text, matches);
    }

    private static StyleSpans<Collection<String>> buildStyleSpans(String text, List<Match> matches) {
        StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();

        matches.sort(Comparator.comparingInt(m -> m.start));

        int lastEnd = 0;
        for (Match match : matches) {
            if (match.start > lastEnd) {
                spansBuilder.add(Collections.singleton("identifier"), match.start - lastEnd);
            }
            spansBuilder.add(Collections.singleton(match.styleClass), match.end - match.start);
            lastEnd = match.end;
        }

        if (lastEnd < text.length()) {
            spansBuilder.add(Collections.singleton("identifier"), text.length() - lastEnd);
        }

        return spansBuilder.create();
    }

    private static class Match {
        final int start;
        final int end;
        final String styleClass;

        Match(int start, int end, String styleClass) {
            this.start = start;
            this.end = end;
            this.styleClass = styleClass;
        }
    }
}