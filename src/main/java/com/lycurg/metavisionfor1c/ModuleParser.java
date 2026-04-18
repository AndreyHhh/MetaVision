package com.lycurg.metavisionfor1c;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class ModuleParser {

    public static List<FunctionInfo> parseModuleFunctions(File moduleFile) {
        List<FunctionInfo> functions = new ArrayList<>();

        if (!moduleFile.exists()) {
            System.err.println("❌ ModuleParser: Файл не существует: " + moduleFile.getPath());
            return functions;
        }

        if (!moduleFile.getName().toLowerCase().endsWith(".bsl") &&
                !moduleFile.getName().toLowerCase().endsWith(".os")) {
            return functions;
        }

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(moduleFile), StandardCharsets.UTF_8))) {

            List<String> lines = new ArrayList<>();
            String line;
            boolean firstLine = true;

            while ((line = reader.readLine()) != null) {
                if (firstLine) {
                    if (line.startsWith("\uFEFF")) line = line.substring(1);
                    firstLine = false;
                }
                lines.add(line);
            }

            for (int i = 0; i < lines.size(); i++) {
                String currentLine = lines.get(i);
                String trimmed = currentLine.trim();

                if (trimmed.isEmpty() || trimmed.startsWith("//") || trimmed.startsWith("#")) {
                    continue;
                }

                String lowerLine = trimmed.toLowerCase();

                boolean isProcedure = lowerLine.startsWith("процедура ") || lowerLine.startsWith("procedure ");
                boolean isFunction = lowerLine.startsWith("функция ") || lowerLine.startsWith("function ");

                if (isProcedure || isFunction) {
                    String type = isProcedure ? "Процедура" : "Функция";
                    FunctionInfo func = extractFunction(lines, i, type);
                    if (func != null) {
                        functions.add(func);
                        i = func.endLine - 1;
                    }
                }
            }

        } catch (IOException e) {
            System.err.println("❌ ModuleParser: Ошибка чтения файла: " + moduleFile.getPath() + " - " + e.getMessage());
        }

        return functions;
    }

    private static FunctionInfo extractFunction(List<String> lines, int startLine, String type) {
        String firstLine = lines.get(startLine);
        String functionName = extractFunctionName(firstLine, type);

        if (functionName == null || functionName.isEmpty()) {
            System.err.println("❌ ModuleParser: Не удалось извлечь имя функции из строки: " + firstLine);
            return null;
        }

        int endLine = startLine;
        boolean foundEnd = false;

        for (int i = startLine + 1; i < lines.size(); i++) {
            String line = lines.get(i);

            int commentPos = line.indexOf("//");
            String lineBeforeComment = (commentPos >= 0) ? line.substring(0, commentPos) : line;

            String lowerLine = lineBeforeComment.toLowerCase().trim();

            if (type.equals("Процедура")) {
                if (lowerLine.startsWith("конецпроцедуры") || lowerLine.startsWith("endprocedure")) {
                    endLine = i;
                    foundEnd = true;
                    break;
                }
            } else {
                if (lowerLine.startsWith("конецфункции") || lowerLine.startsWith("endfunction")) {
                    endLine = i;
                    foundEnd = true;
                    break;
                }
            }
        }

        if (!foundEnd) {
            System.err.println("❌ ModuleParser: Не найден конец " + type + " " + functionName +
                    " (от строки " + (startLine + 1) + ")");
            return null;
        }

        StringBuilder functionText = new StringBuilder();
        for (int i = startLine; i <= endLine; i++) {
            functionText.append(lines.get(i)).append("\n");
        }

        return new FunctionInfo(type, functionName, functionText.toString().trim(), startLine + 1, endLine + 1);
    }

    private static String extractFunctionName(String line, String type) {
        String trimmed = line.trim();
        String lowerTrimmed = trimmed.toLowerCase();

        int keywordLen = -1;
        int startPos = -1;

        // Поиск ключевого слова (русского или английского)
        if (type.equals("Процедура")) {
            startPos = lowerTrimmed.indexOf("процедура ");
            if (startPos == -1) startPos = lowerTrimmed.indexOf("procedure ");
            keywordLen = 9; // "процедура".length()
            if (startPos != -1 && lowerTrimmed.startsWith("procedure ", startPos)) keywordLen = 9;
        } else {
            startPos = lowerTrimmed.indexOf("функция ");
            if (startPos == -1) startPos = lowerTrimmed.indexOf("function ");
            keywordLen = 7; // "функция".length()
            if (startPos != -1 && lowerTrimmed.startsWith("function ", startPos)) keywordLen = 8;
        }

        if (startPos == -1) return null;

        String afterKeyword = trimmed.substring(startPos + keywordLen).trim();

        // Ищем имя до скобки, пробела или точки с запятой
        int endIndex = afterKeyword.length();
        for (int i = 0; i < afterKeyword.length(); i++) {
            char c = afterKeyword.charAt(i);
            if (c == '(' || c == ' ' || c == '\t' || c == ';') {
                endIndex = i;
                break;
            }
        }

        String name = afterKeyword.substring(0, endIndex).trim();
        return name.isEmpty() ? null : name;
    }

    public static class FunctionInfo {
        public final String type;
        public final String name;
        public final String text;
        public final int startLine;
        public final int endLine;

        public FunctionInfo(String type, String name, String text, int startLine, int endLine) {
            this.type = type;
            this.name = name;
            this.text = text;
            this.startLine = startLine;
            this.endLine = endLine;
        }
    }
}