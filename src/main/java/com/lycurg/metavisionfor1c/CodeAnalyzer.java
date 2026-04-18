package com.lycurg.metavisionfor1c;

import java.util.*;

//# Анализатор структуры функций: парсит код 1С, детектирует циклы, запросы, транзакции, блокировки и вызовы функций
public class CodeAnalyzer {

    public static class CodeStructureDetailed {
        public List<CodeElement> elements = new ArrayList<>();
        public Map<Integer, CodeElement> elementsById = new HashMap<>();
    }

    public static CodeStructureDetailed analyzeFunctionStructure(String functionText, String functionName) {
        CodeStructureDetailed structure = new CodeStructureDetailed();
        if (functionText == null || functionText.isEmpty()) {
            return structure;
        }

        String[] lines = functionText.split("\n");

        Map<String, Integer> counters = new HashMap<>();
        counters.put("Цикл", 0);
        counters.put("Запрос", 0);
        counters.put("Транзакция", 0);
        counters.put("Блокировка", 0);

        // Корневой элемент — сама функция
        CodeElement functionElement = new CodeElement("ОсновнаяФункция", functionName, 1, functionName);
        functionElement.id = 1;
        functionElement.endLine = findFunctionEndLine(lines);
        functionElement.ownerElementId = null;
        functionElement.ownerType = null;
        functionElement.ownerName = null;
        functionElement.ownerFunctionName = functionName;

        structure.elements.add(functionElement);
        structure.elementsById.put(1, functionElement);

        Stack<CodeElement> elementStack = new Stack<>();
        elementStack.push(functionElement);

        int elementId = 2;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmedLine = line.trim();
            int lineNumber = i + 1;

            if (trimmedLine.isEmpty() ||
                    trimmedLine.startsWith("//") ||
                    trimmedLine.startsWith("|") ||
                    trimmedLine.startsWith("#") ||
                    trimmedLine.startsWith("/*") ||
                    trimmedLine.startsWith("* ") ||
                    trimmedLine.equals("*/")) {
                continue;
            }

            // 1. Конец элементов
            if (isEndOfElement(line)) {
                if (elementStack.size() > 1) {
                    CodeElement endedElement = elementStack.pop();
                    endedElement.endLine = lineNumber;
                }
                continue;
            }

            // 2. Транзакция
            CodeElement transactionElement = analyzeTransaction(line, lineNumber, counters);
            if (transactionElement != null) {
                transactionElement.id = elementId++;
                setOwner(transactionElement, elementStack, functionName);
                structure.elements.add(transactionElement);
                structure.elementsById.put(transactionElement.id, transactionElement);
                elementStack.push(transactionElement);
                continue;
            }

            // 3. Циклы
            CodeElement loopElement = analyzeLoop(line, lineNumber, counters);
            if (loopElement != null) {
                loopElement.id = elementId++;
                setOwner(loopElement, elementStack, functionName);
                structure.elements.add(loopElement);
                structure.elementsById.put(loopElement.id, loopElement);
                elementStack.push(loopElement);
                continue;
            }

            // 4. Запрос
            CodeElement queryElement = analyzeQuery(line, lineNumber, counters);
            if (queryElement != null) {
                queryElement.id = elementId++;
                setOwner(queryElement, elementStack, functionName);
                structure.elements.add(queryElement);
                structure.elementsById.put(queryElement.id, queryElement);
                continue;
            }

            // 5. Блокировка
            CodeElement lockElement = analyzeLock(line, lineNumber, counters);
            if (lockElement != null) {
                lockElement.id = elementId++;
                setOwner(lockElement, elementStack, functionName);
                structure.elements.add(lockElement);
                structure.elementsById.put(lockElement.id, lockElement);
                continue;
            }

            // 6. Вызовы функций
            List<CodeElement> methodCalls = analyzeMethodCall(line, lineNumber, elementStack, null);
            for (CodeElement methodCall : methodCalls) {
                methodCall.id = elementId++;
                structure.elements.add(methodCall);
                structure.elementsById.put(methodCall.id, methodCall);
            }
        }

        return structure;
    }

    private static void setOwner(CodeElement element, Stack<CodeElement> stack, String functionName) {
        element.ownerFunctionName = functionName;

        if (element.type.equals("ОсновнаяФункция")) {
            element.ownerElementId = null;
            element.ownerType = null;
            element.ownerName = null;
            return;
        }

        for (int i = stack.size() - 1; i >= 0; i--) {
            CodeElement owner = stack.get(i);
            if (owner.type.equals("ОсновнаяФункция") ||
                    owner.type.equals("ЦиклНезависимый") ||
                    owner.type.equals("ЦиклЗапроса") ||
                    owner.type.equals("Транзакция")) {
                element.ownerElementId = String.valueOf(owner.id);
                element.ownerType = owner.type;
                element.ownerName = owner.subtype;
                return;
            }
        }

        element.ownerElementId = null;
        element.ownerType = null;
        element.ownerName = null;
    }

    private static void setOwnerFromStack(CodeElement element, Stack<CodeElement> stack, String functionName) {
        element.ownerFunctionName = functionName;

        for (int i = stack.size() - 1; i >= 0; i--) {
            CodeElement owner = stack.get(i);
            if (owner.type.equals("ОсновнаяФункция") ||
                    owner.type.equals("ЦиклНезависимый") ||
                    owner.type.equals("ЦиклЗапроса") ||
                    owner.type.equals("Транзакция")) {
                element.ownerElementId = String.valueOf(owner.id);
                element.ownerType = owner.type;
                element.ownerName = owner.subtype;
                return;
            }
        }

        element.ownerElementId = null;
        element.ownerType = null;
        element.ownerName = null;
    }

    private static CodeElement analyzeLoop(String line, int lineNumber, Map<String, Integer> counters) {
        String lower = line.toLowerCase().trim();

        if (lower.contains("циклов") && lower.contains("=")) {
            return null;
        }

        if (lower.contains("пока") && lower.contains(".следующий(") && lower.contains("цикл")) {
            int num = counters.merge("Цикл", 1, Integer::sum);
            return new CodeElement("ЦиклЗапроса", "Цикл" + num, lineNumber, line);
        }

        if (lower.contains("цикл") &&
                !lower.contains("циклов ") &&
                (lower.contains("для ") || lower.contains("пока ") || lower.contains("каждого"))) {
            int num = counters.merge("Цикл", 1, Integer::sum);
            return new CodeElement("ЦиклНезависимый", "Цикл" + num, lineNumber, line);
        }

        return null;
    }

    private static CodeElement analyzeQuery(String line, int lineNumber, Map<String, Integer> counters) {
        String lower = line.toLowerCase().trim();

        if (lower.matches(".*=\\s*\u043D\u043E\u0432\u044B\u0439\\s+\u0437\u0430\u043F\u0440\u043E\u0441[;\\s]*.*") ||
                lower.matches(".*\u043D\u043E\u0432\u044B\u0439\\s+\u0437\u0430\u043F\u0440\u043E\u0441[;\\s]*.*")) {
            int num = counters.merge("Запрос", 1, Integer::sum);
            return new CodeElement("Запрос", "Запрос" + num, lineNumber, line);
        }

        if (lower.matches(".*\u043D\u043E\u0432\u044B\u0439\\s+\u0441\u0445\u0435\u043C\u0430\u0437\u0430\u043F\u0440\u043E\u0441\u0430.*")) {
            int num = counters.merge("Запрос", 1, Integer::sum);
            return new CodeElement("Запрос", "Запрос" + num, lineNumber, line);
        }

        return null;
    }

    private static CodeElement analyzeLock(String line, int lineNumber, Map<String, Integer> counters) {
        String lower = line.toLowerCase();

        if (lower.matches(".*\u043D\u043E\u0432\u044B\u0439\\s+\u0431\u043B\u043E\u043A\u0438\u0440\u043E\u0432\u043A\u0430\u0434\u0430\u043D\u043D\u044B\u0445.*")) {
            int num = counters.merge("Блокировка", 1, Integer::sum);
            return new CodeElement("Блокировка", "Блокировка" + num, lineNumber, line);
        }

        return null;
    }

    private static CodeElement analyzeTransaction(String line, int lineNumber, Map<String, Integer> counters) {
        String lower = line.toLowerCase();

        if (lower.matches(".*\u043D\u0430\u0447\u0430\u0442\u044C\u0442\u0440\u0430\u043D\u0437\u0430\u043A\u0446\u0438\u044E\\s*\\(\\s*\\).*")) {
            int num = counters.merge("Транзакция", 1, Integer::sum);
            return new CodeElement("Транзакция", "Транзакция" + num, lineNumber, line);
        }

        return null;
    }

    private static List<CodeElement> analyzeMethodCall(String line, int lineNumber,
                                                       Stack<CodeElement> stack, CodeElement parentCall) {
        List<CodeElement> calls = new ArrayList<>();

        try {
            String trimmed = line.trim();
            String lower = trimmed.toLowerCase();

            if (trimmed.startsWith("//") || trimmed.startsWith("/*") ||
                    trimmed.startsWith("*") || trimmed.startsWith("#") ||
                    trimmed.startsWith("|") || trimmed.isEmpty()) {
                return calls;
            }

            if (lower.startsWith("если ") || lower.startsWith("иначеесли ") ||
                    lower.startsWith("иначе") || lower.startsWith("для ") ||
                    lower.startsWith("пока ") || lower.startsWith("попытка") ||
                    lower.startsWith("исключение") || lower.startsWith("процедура ") ||
                    lower.startsWith("функция ") || lower.startsWith("&") ||
                    lower.contains("конеццикла") || lower.contains("конецесли") ||
                    lower.contains("конецпроцедуры") || lower.contains("конецфункции") ||
                    lower.contains("конецпопытки")) {
                return calls;
            }

            if (lower.contains("новый ") || lower.contains("new ")) {
                return calls;
            }

            if (lower.contains("начатьтранзакцию") ||
                    lower.contains("зафиксироватьтранзакцию") ||
                    lower.contains("отменитьтранзакцию")) {
                return calls;
            }

            String ownerFunctionName = null;
            for (int i = stack.size() - 1; i >= 0; i--) {
                CodeElement elem = stack.get(i);
                if (elem.ownerFunctionName != null) {
                    ownerFunctionName = elem.ownerFunctionName;
                    break;
                }
            }
            if (ownerFunctionName == null && !stack.isEmpty()) {
                ownerFunctionName = stack.get(0).subtype;
            }

            findFunctionCallsRecursive(trimmed, lineNumber, calls, 0, parentCall, stack, ownerFunctionName);

        } catch (Exception e) {
            System.err.println("⚠️ Ошибка разбора строки " + lineNumber + ": " + e.getMessage());
        }

        return calls;
    }

    private static void findFunctionCallsRecursive(String text, int lineNumber,
                                                   List<CodeElement> calls, int depth,
                                                   CodeElement parentCall, Stack<CodeElement> stack,
                                                   String ownerFunctionName) {
        if (text == null || text.isEmpty() || depth > 10) return;

        int bracketIndex = text.indexOf('(');
        if (bracketIndex == -1) return;

        String beforeBracket = text.substring(0, bracketIndex).trim();

        if (beforeBracket.contains("=")) {
            beforeBracket = beforeBracket.substring(beforeBracket.lastIndexOf("=") + 1).trim();
        }

        beforeBracket = beforeBracket.replaceAll("\\s*\\.\\s*", ".");

        String candidateName = beforeBracket.contains(".")
                ? beforeBracket.substring(beforeBracket.lastIndexOf(".") + 1).trim()
                : beforeBracket.trim();

        boolean isValid = candidateName.length() >= 2
                && candidateName.matches("^[А-ЯЁа-яёA-Za-z][А-ЯЁа-яёA-Za-z0-9_]*$");

        String afterBracket = text.substring(bracketIndex + 1);

        if (isValid && !BuiltinFunctionsExclude.isExcluded(candidateName)) {
            CodeElement call = new CodeElement("ВызовФункции", candidateName, lineNumber, text);
            call.ownerFunctionName = ownerFunctionName;

            if (parentCall != null) {
                call.ownerName      = parentCall.subtype;
                call.ownerType      = parentCall.type;
                call.ownerElementId = String.valueOf(parentCall.id);
            } else {
                setOwnerFromStack(call, stack, ownerFunctionName);
            }

            calls.add(call);

            findFunctionCallsRecursive(afterBracket, lineNumber, calls, depth + 1,
                    call, stack, ownerFunctionName);
        } else {
            findFunctionCallsRecursive(afterBracket, lineNumber, calls, depth + 1,
                    parentCall, stack, ownerFunctionName);
        }
    }

    private static boolean isEndOfElement(String line) {
        String lower = line.toLowerCase();
        return lower.contains("конеццикла") ||
                lower.contains("зафиксироватьтранзакцию") ||
                lower.contains("отменитьтранзакцию") ||
                lower.contains("конецпопытки");
    }

    private static int findFunctionEndLine(String[] lines) {
        for (int i = lines.length - 1; i >= 0; i--) {
            String lower = lines[i].toLowerCase().trim();
            if (lower.startsWith("конецпроцедуры") || lower.startsWith("конецфункции")) {
                return i + 1;
            }
        }
        return lines.length;
    }

    public static void clearCache() {
        // no-op, кэша нет
    }
}