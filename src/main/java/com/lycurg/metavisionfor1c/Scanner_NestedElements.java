package com.lycurg.metavisionfor1c;

import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;

import com.lycurg.metavisionfor1c.DBPathHelper;

/**
 * Сканер межфункциональных цепочек вложенности.
 * Логика: строим цепочки от корневых функций вглубь через ВызовФункции.
 * Одна строка = одна цепочка (может проходить через несколько функций).
 */
public class Scanner_NestedElements {

    private static final String DB_URL = DBPathHelper.getDbUrl();
    private static Set<Integer> cachedRecursiveFunctionIds = null;

    // =========================================================================
    // PUBLIC: результат анализа
    // =========================================================================

    public static class NestedElementIssue {
        public String elementType;
        public String severity;
        public String description;
        public String functionName;
        public String objectFullName;
        public String moduleType;
        public String problematicCode;
        public int    lineNumber;
        public String recommendation;
        public String chainPath;
        public int    totalDepth;
        public int    cycleCount;
        public int    transactionCount;
        public int    lockCount;
        public int    rootElementId;

        public List<String> allFunctionNames = new ArrayList<>();
        public List<Integer> allFunctionIds = new ArrayList<>();
        public List<Integer> chainElementIds = new ArrayList<>();
        public Map<Integer, Integer> chainParentMap = new HashMap<>();
        public int functionCount;
        public boolean hasRecursion;
    }
    // =========================================================================
    // Внутренние узлы дерева
    // =========================================================================

    private static class ElementNode {
        int    id;
        String name;
        String type;
        int    lineNumber;
        String text;
        int    ownerId;
        String ownerType;
        int    calledFunctionId;
        List<ElementNode> children  = new ArrayList<>();
        FunctionChain     calledChain; // если ВызовФункции — сюда вставляем дерево
    }


    private static class FunctionChain {
        int    functionId;
        String functionName;
        String objectFullName;
        String moduleType;
        ElementNode root;
        boolean hasStructural;           // есть ли циклы/транзакции/блокировки
        boolean hasTransactionOrLock;    // 🔥 НОВОЕ: есть ли транзакции или блокировки
    }

    // =========================================================================
    // MAIN
    // =========================================================================

    public static List<NestedElementIssue> scanForNestedElements() {
        List<NestedElementIssue> issues = new ArrayList<>();

        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            System.out.println("🔍 Сканирование межфункциональных цепочек...");

            // Загружаем рекурсивные функции один раз
            Set<Integer> recursiveFunctionIds = getRecursiveFunctionIds(conn);

            Set<Integer> recursiveFunctions = loadRecursiveFunctions(conn);
            List<Integer> rootFunctions     = findRootFunctions(conn, recursiveFunctions);

            System.out.println("📊 Корневых функций: " + rootFunctions.size());

            for (int funcId : rootFunctions) {
                Set<Integer> visited = new HashSet<>();
                FunctionChain chain  = buildFunctionChain(conn, funcId, visited, false);
                if (chain == null || !chain.hasStructural) continue;

                NestedElementIssue issue = buildIssue(chain, recursiveFunctionIds);

                if (issue == null || issue.totalDepth < 1) continue;
                if (!hasProblematicChain(chain)) continue;

                issues.add(issue);
            }

            issues.sort((a, b) -> Integer.compare(b.totalDepth, a.totalDepth));
            System.out.println("✅ Найдено цепочек: " + issues.size());

        } catch (SQLException e) {
            System.err.println("❌ Ошибка: " + e.getMessage());
            e.printStackTrace();
        }

        return issues;
    }

    // =========================================================================
    // Кэш рекурсивных функций
    // =========================================================================

    private static Set<Integer> getRecursiveFunctionIds(Connection conn) throws SQLException {
        if (cachedRecursiveFunctionIds != null) {
            return cachedRecursiveFunctionIds;
        }

        cachedRecursiveFunctionIds = new HashSet<>();
        try (PreparedStatement ps = conn.prepareStatement("SELECT function_id FROM recursive_functions");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                cachedRecursiveFunctionIds.add(rs.getInt("function_id"));
            }
        }
        return cachedRecursiveFunctionIds;
    }

    // =========================================================================
    // Загрузка рекурсивных функций
    // =========================================================================

    private static Set<Integer> loadRecursiveFunctions(Connection conn) throws SQLException {
        Set<Integer> result = new HashSet<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT function_id FROM recursive_functions");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) result.add(rs.getInt("function_id"));
        }
        return result;
    }

    // =========================================================================
    // Найти корневые функции:
    // имеют структурные элементы И не вызываются из структурного контекста
    // другой функции
    // =========================================================================

    private static List<Integer> findRootFunctions(Connection conn,
                                                   Set<Integer> recursive) throws SQLException {
        // Функции которые вызываются из структурного контекста
        Set<Integer> calledFromStructural = new HashSet<>();
        String sql1 = """
                SELECT DISTINCT called_function_id
                FROM code_elements
                WHERE element_type = 'ВызовФункции'
                  AND called_function_id IS NOT NULL
                  AND owner_type IN ('ЦиклНезависимый','ЦиклЗапроса','Транзакция','Блокировка')
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql1);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) calledFromStructural.add(rs.getInt(1));
        }

        // Функции у которых есть структурные элементы — кандидаты в корни
        List<Integer> roots = new ArrayList<>();
        String sql2 = """
                SELECT DISTINCT function_id
                FROM code_elements
                WHERE element_type IN ('ЦиклНезависимый','ЦиклЗапроса','Транзакция','Блокировка')
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql2);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                int fid = rs.getInt(1);
                if (!calledFromStructural.contains(fid)) {
                    roots.add(fid);
                }
            }
        }
        return roots;
    }


    private static boolean isTransactionOrLock(String type) {
        return "Транзакция".equals(type) || "Блокировка".equals(type);
    }

    // =========================================================================
    // Построить дерево функции рекурсивно
    // inheritedContext = true если функция вызвана из структурного контекста
    // =========================================================================

    private static FunctionChain buildFunctionChain(Connection conn, int functionId,
                                                    Set<Integer> visited,
                                                    boolean inheritedContext) throws SQLException {
        if (visited.contains(functionId)) return null;
        visited.add(functionId);

        String sql = """
            SELECT ce.id, ce.element_name, ce.element_type,
                   ce.owner_id, ce.owner_type,
                   ce.start_line, ce.element_text,
                   ce.called_function_id,
                   mf.function_name, mm.object_full_name, mm.module_type
            FROM code_elements ce
            JOIN metadata_functions mf ON ce.function_id = mf.id
            JOIN metadata_modules   mm ON mf.module_id   = mm.id
            WHERE ce.function_id = ?
              AND ce.element_type IN ('ОсновнаяФункция','ЦиклНезависимый','ЦиклЗапроса',
                                      'Транзакция','Блокировка','ВызовФункции')
            ORDER BY ce.start_line ASC
            """;

        FunctionChain chain = new FunctionChain();
        chain.functionId = functionId;
        Map<Integer, ElementNode> nodeMap = new LinkedHashMap<>();

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, functionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    chain.functionName   = rs.getString("function_name");
                    chain.objectFullName = rs.getString("object_full_name");
                    chain.moduleType     = rs.getString("module_type");

                    ElementNode n    = new ElementNode();
                    n.id             = rs.getInt("id");
                    n.name           = rs.getString("element_name");
                    n.type           = rs.getString("element_type");
                    n.lineNumber     = rs.getInt("start_line");
                    n.text           = rs.getString("element_text");
                    n.ownerId        = rs.getInt("owner_id");
                    n.ownerType      = rs.getString("owner_type");
                    n.calledFunctionId = rs.getInt("called_function_id");

                    // Если вызов функции — рекурсивно строим
                    if ("ВызовФункции".equals(n.type) && n.calledFunctionId > 0) {
                        boolean structuralOwner = isStructural(n.ownerType);
                        if (structuralOwner || inheritedContext) {
                            FunctionChain called = buildFunctionChain(
                                    conn, n.calledFunctionId, visited, true);
                            // 🔥 ИСПРАВЛЕНИЕ: добавляем calledChain только если в нём ЕСТЬ транзакция или блокировка
                            if (called != null && called.hasTransactionOrLock) {
                                n.calledChain = called;
                            }
                        }
                    }

                    nodeMap.put(n.id, n);
                }
            }
        }

        if (chain.functionName == null) return null;

        // Строим дерево через owner_id
        for (ElementNode n : nodeMap.values()) {
            if ("ОсновнаяФункция".equals(n.type)) {
                chain.root = n;
            } else if (n.ownerId > 0 && nodeMap.containsKey(n.ownerId)) {
                nodeMap.get(n.ownerId).children.add(n);
            }
        }

        // Сортируем детей по строке
        for (ElementNode n : nodeMap.values()) {
            n.children.sort((a, b) -> Integer.compare(a.lineNumber, b.lineNumber));
        }

        // 🔥 ИСПРАВЛЕНИЕ: hasStructural теперь только для транзакций/блокировок
        chain.hasStructural = nodeMap.values().stream()
                .anyMatch(n -> isTransactionOrLock(n.type) || (n.calledChain != null && n.calledChain.hasTransactionOrLock));

        // 🔥 НОВОЕ ПОЛЕ: hasTransactionOrLock для всей цепочки
        chain.hasTransactionOrLock = chain.hasStructural;

        return chain;
    }



    // =========================================================================
    // Строим issue из цепочки
    // =========================================================================

    private static int calcMaxDepth(FunctionChain chain) {
        if (chain.root == null) return 0;
        return calcMaxDepthNode(chain.root, 0);
    }

    private static int calcMaxDepthNode(ElementNode node, int current) {
        int depth = current + (isStructural(node.type) ? 1 : 0);
        int max = depth;
        if (node.calledChain != null && node.calledChain.root != null)
            max = Math.max(max, calcMaxDepthNode(node.calledChain.root, depth));
        for (ElementNode ch : node.children)
            max = Math.max(max, calcMaxDepthNode(ch, depth));
        return max;
    }

    private static NestedElementIssue buildIssue(FunctionChain chain, Set<Integer> recursiveFunctionIds) {
        int[] counts = {0, 0, 0};
        countStructural(chain, counts);

        int totalDepth = calcMaxDepth(chain);
        if (totalDepth == 0) return null;

        // Считаем количество функций в цепочке
        int[] funcCount = {0};
        countFunctions(chain, funcCount);

        NestedElementIssue issue = new NestedElementIssue();

        issue.elementType         = "Межфункциональная цепочка";
        issue.functionName        = chain.functionName;
        issue.objectFullName      = chain.objectFullName;
        issue.moduleType          = chain.moduleType;
        issue.totalDepth          = totalDepth;
        issue.cycleCount          = counts[0];
        issue.transactionCount    = counts[1];
        issue.lockCount           = counts[2];
        issue.lineNumber          = findFirstStructuralLine(chain);
        issue.severity            = totalDepth >= 5 ? "CRITICAL" :
                totalDepth >= 4 ? "HIGH" :
                        totalDepth >= 3 ? "MEDIUM" : "LOW";
        issue.description         = String.format(
                "Цепочка: %d циклов, %d транзакций, %d блокировок",
                counts[0], counts[1], counts[2]);
        issue.recommendation      = buildRecommendation(counts[0], counts[1]);
        issue.chainPath           = buildChainPath(chain);
        issue.problematicCode     = findFirstStructuralText(chain);

        // Собираем chainElementIds и chainParentMap
        issue.chainElementIds = new ArrayList<>();
        issue.chainParentMap  = new HashMap<>();
        issue.allFunctionNames = new ArrayList<>();
        issue.allFunctionIds = new ArrayList<>();

        collectChainIds(chain, issue.chainElementIds, issue.chainParentMap, 0);
        collectFunctionNames(chain, issue.allFunctionNames);
        collectFunctionIds(chain, issue.allFunctionIds);

        issue.rootElementId = issue.chainElementIds.isEmpty() ? 0 : issue.chainElementIds.get(0);
        issue.functionCount = funcCount[0];

        // Проверка рекурсии по всем функциям в цепочке (используем кэш)
        boolean hasRecursion = false;
        for (int funcId : issue.allFunctionIds) {
            if (recursiveFunctionIds.contains(funcId)) {
                hasRecursion = true;
                break;
            }
        }
        issue.hasRecursion = hasRecursion;

        return issue;
    }

    // =========================================================================
    // Сбор ID функций
    // =========================================================================

    private static void collectFunctionIds(FunctionChain chain, List<Integer> ids) {
        if (chain == null) return;
        ids.add(chain.functionId);
        if (chain.root != null) {
            collectFunctionIdsFromNode(chain.root, ids);
        }
    }

    private static void collectFunctionIdsFromNode(ElementNode node, List<Integer> ids) {
        if (node.calledChain != null) {
            ids.add(node.calledChain.functionId);
            collectFunctionIdsFromNode(node.calledChain.root, ids);
        }
        for (ElementNode child : node.children) {
            collectFunctionIdsFromNode(child, ids);
        }
    }

    private static void collectFunctionNames(FunctionChain chain, List<String> names) {
        if (chain == null) return;
        names.add(chain.functionName);
        if (chain.root != null) {
            collectFunctionNamesFromNode(chain.root, names);
        }
    }

    private static void collectFunctionNamesFromNode(ElementNode node, List<String> names) {
        if (node.calledChain != null) {
            names.add(node.calledChain.functionName);
            collectFunctionNamesFromNode(node.calledChain.root, names);
        }
        for (ElementNode child : node.children) {
            collectFunctionNamesFromNode(child, names);
        }
    }

    private static void collectChainIds(FunctionChain chain, List<Integer> ids,
                                        Map<Integer, Integer> parentMap, int parentId) {
        if (chain.root == null) return;
        int funcMarker = -chain.functionId;
        ids.add(funcMarker);
        parentMap.put(funcMarker, parentId);
        collectChainIdsNode(chain.root, ids, parentMap, funcMarker);
    }

    private static void collectChainIdsNode(ElementNode node, List<Integer> ids,
                                            Map<Integer, Integer> parentMap, int parentId) {
        if (isStructural(node.type)) {
            ids.add(node.id);
            parentMap.put(node.id, parentId);
            for (ElementNode ch : node.children)
                collectChainIdsNode(ch, ids, parentMap, node.id);
        } else {
            for (ElementNode ch : node.children)
                collectChainIdsNode(ch, ids, parentMap, parentId);
        }
        if (node.calledChain != null)
            collectChainIds(node.calledChain, ids, parentMap, parentId);
    }

    private static void countStructural(FunctionChain chain, int[] counts) {
        if (chain.root == null) return;
        countStructuralNode(chain.root, counts);
    }

    private static void countStructuralNode(ElementNode node, int[] counts) {
        if (node.type.contains("Цикл"))          counts[0]++;
        else if ("Транзакция".equals(node.type)) counts[1]++;
        else if ("Блокировка".equals(node.type)) counts[2]++;

        if (node.calledChain != null) countStructural(node.calledChain, counts);
        for (ElementNode ch : node.children) countStructuralNode(ch, counts);
    }

    private static int findFirstStructuralLine(FunctionChain chain) {
        if (chain.root == null) return 1;
        return findFirstStructuralLineNode(chain.root);
    }

    private static int findFirstStructuralLineNode(ElementNode node) {
        if (isStructural(node.type)) return node.lineNumber;
        for (ElementNode ch : node.children) {
            int line = findFirstStructuralLineNode(ch);
            if (line > 0) return line;
        }
        return 0;
    }

    private static String findFirstStructuralText(FunctionChain chain) {
        if (chain.root == null) return "";
        return findFirstStructuralTextNode(chain.root);
    }

    private static String findFirstStructuralTextNode(ElementNode node) {
        if (isStructural(node.type)) return truncate(node.text, 100);
        for (ElementNode ch : node.children) {
            String t = findFirstStructuralTextNode(ch);
            if (!t.isEmpty()) return t;
        }
        return "";
    }

    // =========================================================================
    // Строим читаемое дерево цепочки
    // =========================================================================

    private static String buildChainPath(FunctionChain chain) {
        StringBuilder sb = new StringBuilder();
        sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n");
        int[] funcCount = {0};
        countFunctions(chain, funcCount);
        int[] counts = {0, 0, 0};
        countStructural(chain, counts);
        int depth = calcMaxDepth(chain);
        sb.append(String.format("📊 Цепочка: %d уровней в %d функци%s\n\n",
                depth, funcCount[0], funcCount[0] == 1 ? "и" : "ях"));
        renderFunctionChain(chain, sb, "", true);
        return sb.toString();
    }

    private static void renderFunctionChain(FunctionChain chain, StringBuilder sb,
                                            String prefix, boolean isRoot) {
        sb.append(String.format("%s📄 Функция: \"%s\"\n", prefix, chain.functionName));
        if (chain.root != null) {
            List<ElementNode> visible = getVisibleChildren(chain.root);
            for (int i = 0; i < visible.size(); i++) {
                renderElementNode(visible.get(i), sb, prefix, i == visible.size() - 1);
            }
        }
    }

    private static void countFunctions(FunctionChain chain, int[] count) {
        count[0]++;
        if (chain.root == null) return;
        countFunctionsNode(chain.root, count);
    }

    private static void countFunctionsNode(ElementNode node, int[] count) {
        if (node.calledChain != null) countFunctions(node.calledChain, count);
        for (ElementNode ch : node.children) countFunctionsNode(ch, count);
    }

    private static void renderElementNode(ElementNode node, StringBuilder sb,
                                          String prefix, boolean isLast) {
        if ("ВызовФункции".equals(node.type)) {
            if (node.calledChain == null) return;
            String connector  = isLast ? "└── " : "├── ";
            String childPrefix = prefix + (isLast ? "    " : "│   ");
            sb.append(String.format("%s%s📞 Вызов: \"%s\" (строка: %d)\n",
                    prefix, connector, node.name, node.lineNumber));
            renderFunctionChain(node.calledChain, sb, childPrefix, false);
            return;
        }

        if (!isStructural(node.type)) return;

        String connector   = isLast ? "└── " : "├── ";
        String childPrefix = prefix + (isLast ? "    " : "│   ");
        sb.append(String.format("%s%s%s %s: \"%s\" (строка: %d)\n",
                prefix, connector,
                getIcon(node.type), getTypeName(node.type),
                node.name, node.lineNumber));

        List<ElementNode> visible = getVisibleChildren(node);
        for (int i = 0; i < visible.size(); i++) {
            renderElementNode(visible.get(i), sb, childPrefix, i == visible.size() - 1);
        }
    }

    private static List<ElementNode> getVisibleChildren(ElementNode node) {
        return node.children.stream()
                .filter(ch -> isStructural(ch.type) || ch.calledChain != null)
                .sorted((a, b) -> Integer.compare(a.lineNumber, b.lineNumber))
                .collect(Collectors.toList());
    }

    // =========================================================================
    // Вспомогательные
    // =========================================================================

    private static boolean isStructural(String type) {
        return type != null && (
                "ЦиклНезависимый".equals(type) ||
                        "ЦиклЗапроса".equals(type)     ||
                        "Транзакция".equals(type)       ||
                        "Блокировка".equals(type));
    }

    private static String buildRecommendation(int cycleCount, int transactionCount) {
        StringBuilder sb = new StringBuilder("💡 РЕКОМЕНДАЦИИ:\n");
        if (cycleCount > 0 && transactionCount > 0)
            sb.append("1. Вынести транзакцию за пределы цикла.\n");
        sb.append("2. Рассмотреть рефакторинг межфункциональной цепочки.\n");
        sb.append("3. Уменьшить глубину вложенности.\n");
        return sb.toString();
    }

    private static String getIcon(String type) {
        if (type.contains("Цикл"))     return "🔄";
        if ("Транзакция".equals(type)) return "💎";
        if ("Блокировка".equals(type)) return "🔒";
        return "📄";
    }

    private static String getTypeName(String type) {
        return switch (type) {
            case "ЦиклНезависимый" -> "Цикл";
            case "ЦиклЗапроса"     -> "Цикл запроса";
            case "Транзакция"      -> "Транзакция";
            case "Блокировка"      -> "Блокировка";
            default                -> type;
        };
    }

    private static String truncate(String s, int max) {
        if (s == null || s.isEmpty()) return "";
        return s.length() > max ? s.substring(0, max - 3) + "..." : s;
    }

    private static boolean hasProblematicChain(FunctionChain chain) {
        List<String> types = new ArrayList<>();
        collectStructuralTypes(chain, types);

        boolean hasCycle       = types.stream().anyMatch(t -> t.contains("Цикл"));
        boolean hasTransaction = types.stream().anyMatch(t -> "Транзакция".equals(t));
        boolean hasLock        = types.stream().anyMatch(t -> "Блокировка".equals(t));

        return (hasTransaction || hasLock);
    }

    private static void collectStructuralTypes(FunctionChain chain, List<String> types) {
        if (chain.root == null) return;
        collectStructuralTypesNode(chain.root, types);
    }

    private static void collectStructuralTypesNode(ElementNode node, List<String> types) {
        if (isStructural(node.type)) types.add(node.type);
        if (node.calledChain != null) collectStructuralTypes(node.calledChain, types);
        for (ElementNode ch : node.children) collectStructuralTypesNode(ch, types);
    }
}