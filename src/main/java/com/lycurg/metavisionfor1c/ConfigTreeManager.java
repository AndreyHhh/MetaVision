package com.lycurg.metavisionfor1c;

import javafx.scene.control.TreeItem;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import org.w3c.dom.*;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.sql.*;
import java.util.*;

//# Менеджер построения дерева конфигурации из БД с автоматической дозагрузкой модулей объектов
public class ConfigTreeManager {

    private static ConfigTreeManager instance;
    public static ConfigTreeManager getInstance() {
        if (instance == null) instance = new ConfigTreeManager();
        return instance;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ЕДИНСТВЕННЫЙ источник правды: singularEnglish → (русское имя, ключ иконки)
    // ─────────────────────────────────────────────────────────────────────────
    private record TypeInfo(String russianName, String iconKey) {}


    //Это и сортировка Дерева
    private static final Map<String, TypeInfo> TYPES = new LinkedHashMap<>();
    static {
        // Ключ — всегда единственное число на английском (как в XML конфигурации 1С)
        TYPES.put("Subsystem",                   new TypeInfo("Подсистемы",                      "podsist"));
        TYPES.put("CommonModule",                new TypeInfo("Общие модули",                    "obs_modyl"));
        TYPES.put("SessionParameter",            new TypeInfo("Параметры сеанса",                "modyl"));
        TYPES.put("Role",                        new TypeInfo("Роли",                            "roli"));
        TYPES.put("CommonAttribute",             new TypeInfo("Общие реквизиты",                 "obs_rekv"));
        TYPES.put("ExchangePlan",                new TypeInfo("Планы обмена",                    "plan_obmena"));
        TYPES.put("CommonCommand",               new TypeInfo("Общие команды",                   "obs_kom"));
        TYPES.put("FilterCriterion",             new TypeInfo("Критерии отбора",                 "krit_otbora"));
        TYPES.put("ScheduledJob",                new TypeInfo("Регламентные задания",             "reg_z"));
        TYPES.put("FunctionalOption",            new TypeInfo("Функциональные опции",            "f_optsii"));
        TYPES.put("FunctionalOptionsParameter",  new TypeInfo("Параметры функциональных опций",  "f_optsii_param"));
        TYPES.put("DefinedType",                 new TypeInfo("Определяемые типы",               "opred_t"));
        TYPES.put("SettingsStorage",             new TypeInfo("Хранилища настроек",              "hran_nastr"));
        TYPES.put("EventSubscription",           new TypeInfo("Подписки на события",             "podpiski"));
        TYPES.put("CommandGroup",                new TypeInfo("Группы команд",                   "gr_komand"));
        TYPES.put("CommonForm",                  new TypeInfo("Общие формы",                     "obs_formy"));
        TYPES.put("CommonTemplate",              new TypeInfo("Общие макеты",                    "obs_makety"));
        TYPES.put("CommonPicture",               new TypeInfo("Общие картинки",                  "obs_kart"));
        TYPES.put("XDTOPackage",                 new TypeInfo("XDTO пакеты",                     "xdto_p"));
        TYPES.put("WebService",                  new TypeInfo("Веб-сервисы",                     "web_s"));
        TYPES.put("HTTPService",                 new TypeInfo("HTTP-сервисы",                    "http_s"));
        TYPES.put("WSReference",                 new TypeInfo("WS-ссылки",                       "ws_s"));
        TYPES.put("StyleItem",                   new TypeInfo("Элементы стиля",                  "el_stylia"));
        TYPES.put("Style",                       new TypeInfo("Стили",                           "styli"));
        TYPES.put("Language",                    new TypeInfo("Языки",                           "yaz"));

        TYPES.put("Constant",                    new TypeInfo("Константы",                       "konst"));
        TYPES.put("Catalog",                     new TypeInfo("Справочники",                     "sprav"));
        TYPES.put("Document",                    new TypeInfo("Документы",                       "dok"));
        TYPES.put("DocumentJournal",             new TypeInfo("Журналы документов",              "zyrnal"));
        TYPES.put("Enum",                        new TypeInfo("Перечисления",                    "pere4isl"));
        TYPES.put("Report",                      new TypeInfo("Отчёты",                          "ot4et"));
        TYPES.put("DataProcessor",               new TypeInfo("Обработки",                       "obrabotka"));
        TYPES.put("ChartOfCharacteristicTypes",  new TypeInfo("Планы видов характеристик",       "plan_vh"));
        TYPES.put("ChartOfAccounts",             new TypeInfo("Планы счетов",                    "plan"));
        TYPES.put("ChartOfCalculationTypes", new TypeInfo("Планы видов расчета",     "plan_vr"));

        TYPES.put("InformationRegister",         new TypeInfo("Регистры сведений",               "r_sv"));
        TYPES.put("AccumulationRegister",        new TypeInfo("Регистры накопления",             "r_nak"));
        TYPES.put("AccountingRegister",          new TypeInfo("Регистры бухгалтерии",            "r_byh"));
        TYPES.put("CalculationRegister",     new TypeInfo("Регистры расчетов",       "r_ras4"));
        TYPES.put("BusinessProcess",             new TypeInfo("Бизнес-процессы",                 "bizn_pr"));
        TYPES.put("Task",                        new TypeInfo("Задачи",                          "zada4a"));

         }

    // ─────────────────────────────────────────────────────────────────────────
    // МЕТОД 1: Любой вид имени типа → единственное число на английском
    // Принимает: множественное английское ("Documents"), русское ("Документы" / "Документ"),
    //            1С-стиль ("РегистрСведений"), уже правильное ("Document")
    // ─────────────────────────────────────────────────────────────────────────
    public static String toSingularEnglish(String type) {
        if (type == null) return null;
        return switch (type) {
            // Множественное английское (из БД)
            case "Documents"                   -> "Document";
            case "Catalogs"                    -> "Catalog";
            case "DataProcessors"              -> "DataProcessor";
            case "Reports"                     -> "Report";
            case "InformationRegisters"        -> "InformationRegister";
            case "AccumulationRegisters"       -> "AccumulationRegister";
            case "AccountingRegisters"         -> "AccountingRegister";
            case "Enums"                       -> "Enum";
            case "Constants"                   -> "Constant";
            case "ExchangePlans"               -> "ExchangePlan";
            case "DocumentJournals"            -> "DocumentJournal";
            case "ChartsOfAccounts"            -> "ChartOfAccounts";
            case "ChartsOfCharacteristicTypes" -> "ChartOfCharacteristicTypes";
            case "WebServices"                 -> "WebService";
            case "HTTPServices"                -> "HTTPService";
            case "CommonModules"               -> "CommonModule";
            case "CommonForms"                 -> "CommonForm";
            case "CommonCommands"              -> "CommonCommand";
            case "CommonAttributes"            -> "CommonAttribute";
            case "Subsystems"                  -> "Subsystem";
            case "Roles"                       -> "Role";
            case "BusinessProcesses"           -> "BusinessProcess";
            case "Tasks"                       -> "Task";
            case "FilterCriteria"              -> "FilterCriterion";
            case "SettingsStorages"            -> "SettingsStorage";
            case "ScheduledJobs"               -> "ScheduledJob";
            case "SessionParameters"           -> "SessionParameter";
            case "EventSubscriptions"          -> "EventSubscription";
            case "FunctionalOptions"           -> "FunctionalOption";
            case "DefinedTypes"                -> "DefinedType";
            case "CommandGroups"               -> "CommandGroup";
            case "CommonTemplates"             -> "CommonTemplate";
            case "CommonPictures"              -> "CommonPicture";
            case "XDTOPackages"                -> "XDTOPackage";
            case "WSReferences"                -> "WSReference";
            case "StyleItems"                  -> "StyleItem";
            case "Styles"                      -> "Style";
            case "Languages"                   -> "Language";
            // Русские множественные (из дерева UI)
            case "Документы"                   -> "Document";
            case "Справочники"                 -> "Catalog";
            case "Обработки"                   -> "DataProcessor";
            case "Отчёты"                      -> "Report";
            case "Регистры сведений"           -> "InformationRegister";
            case "Регистры накопления"         -> "AccumulationRegister";
            case "Регистры бухгалтерии"        -> "AccountingRegister";
            case "Перечисления"                -> "Enum";
            case "Константы"                   -> "Constant";
            case "Планы обмена"                -> "ExchangePlan";
            case "Журналы документов"          -> "DocumentJournal";
            case "Планы счетов"                -> "ChartOfAccounts";
            case "Планы видов характеристик"   -> "ChartOfCharacteristicTypes";
            case "Веб-сервисы"                 -> "WebService";
            case "HTTP-сервисы"                -> "HTTPService";
            case "Общие модули"                -> "CommonModule";
            case "Общие формы"                 -> "CommonForm";
            case "Общие команды"               -> "CommonCommand";
            case "Общие реквизиты"             -> "CommonAttribute";
            case "Подсистемы"                  -> "Subsystem";
            case "Роли"                        -> "Role";
            case "Бизнес-процессы"             -> "BusinessProcess";
            case "Задачи"                      -> "Task";
            case "Критерии отбора"             -> "FilterCriterion";
            case "Хранилища настроек"          -> "SettingsStorage";
            case "Параметры сеанса"            -> "SessionParameter";
            case "Регламентные задания"        -> "ScheduledJob";
            case "Функциональные опции"        -> "FunctionalOption";
            case "Определяемые типы"           -> "DefinedType";
            case "Подписки на события"         -> "EventSubscription";
            case "Группы команд"               -> "CommandGroup";
            case "Общие макеты"                -> "CommonTemplate";
            case "Общие картинки"              -> "CommonPicture";
            case "XDTO пакеты"                 -> "XDTOPackage";
            case "WS-ссылки"                   -> "WSReference";
            case "Элементы стиля"              -> "StyleItem";
            case "Стили"                       -> "Style";
            case "Языки"                       -> "Language";
            // Русские единственные (стиль 1С)
            case "Документ"                    -> "Document";
            case "Справочник"                  -> "Catalog";
            case "Обработка"                   -> "DataProcessor";
            case "Отчет"                       -> "Report";
            case "РегистрСведений"             -> "InformationRegister";
            case "РегистрНакопления"           -> "AccumulationRegister";
            case "РегистрБухгалтерии"          -> "AccountingRegister";
            case "Перечисление"                -> "Enum";
            case "Константа"                   -> "Constant";
            case "ПланОбмена"                  -> "ExchangePlan";
            case "ЖурналДокументов"            -> "DocumentJournal";
            case "ПланСчетов"                  -> "ChartOfAccounts";
            case "ПланВидовХарактеристик"      -> "ChartOfCharacteristicTypes";
            case "ВебСервис"                   -> "WebService";
            case "HTTPСервис"                  -> "HTTPService";
            case "ОбщийМодуль"                 -> "CommonModule";
            case "ОбщаяФорма"                  -> "CommonForm";
            case "ОбщаяКоманда"               -> "CommonCommand";
            case "Подсистема"                  -> "Subsystem";
            case "Роль"                        -> "Role";
            case "БизнесПроцесс"               -> "BusinessProcess";
            case "Задача"                      -> "Task";
            case "КритерийОтбора"              -> "FilterCriterion";
            case "ХранилищеНастроек"           -> "SettingsStorage";
            case "ПараметрСеанса"              -> "SessionParameter";
            case "РегламентноеЗадание"         -> "ScheduledJob";
            case "ФункциональнаяОпция"         -> "FunctionalOption";
            case "ОпределяемыйТип"             -> "DefinedType";
            case "ПодпискаНаСобытие"           -> "EventSubscription";
            case "ГруппаКоманд"                -> "CommandGroup";
            case "ОбщийМакет"                  -> "CommonTemplate";
            case "ОбщаяКартинка"               -> "CommonPicture";
            case "ПакетXDTO"                   -> "XDTOPackage";
            case "WS-Ссылка"                   -> "WSReference";
            case "ЭлементСтиля"                -> "StyleItem";
            case "Стиль"                       -> "Style";
            case "Язык"                        -> "Language";
            // Уже правильный singular — возвращаем как есть

            // Множественное английское
            case "CalculationRegisters"        -> "CalculationRegister";
            case "ChartsOfCalculationTypes"    -> "ChartOfCalculationTypes";

// Русские множественные
            case "Регистры расчетов"           -> "CalculationRegister";
            case "Планы видов расчета"         -> "ChartOfCalculationTypes";

// 1С-стиль единственное
            case "РегистрРасчетов"             -> "CalculationRegister";
            case "ПланВидовРасчета"            -> "ChartOfCalculationTypes";
            default -> type;
        };
    }

    // ─────────────────────────────────────────────────────────────────────────
    // МЕТОД 2: Любой вид имени типа → русское название группы
    // ─────────────────────────────────────────────────────────────────────────
    public String getRussianName(String type) {
        if (type == null) return "";

        // Поддержка формата "Documents.ИмяОбъекта" или "Документ.ИмяОбъекта"
        int dot = type.indexOf('.');
        if (dot > 0) {
            String prefix = type.substring(0, dot);
            String namePart = type.substring(dot); // включая точку
            TypeInfo info = TYPES.get(toSingularEnglish(prefix));
            return info != null ? info.russianName() + namePart : type;
        }

        TypeInfo info = TYPES.get(toSingularEnglish(type));
        return info != null ? info.russianName() : type;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // МЕТОД 3: Любой вид имени типа → ключ иконки
    // ─────────────────────────────────────────────────────────────────────────
    public String getIconKey(String type) {
        if (type == null) return "conf";
        TypeInfo info = TYPES.get(toSingularEnglish(type));
        return info != null ? info.iconKey() : "conf";
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Старые публичные методы — теперь просто делегируют в три метода выше.
    // Оставлены для обратной совместимости с остальным кодом проекта.
    // ─────────────────────────────────────────────────────────────────────────
    public String getIconKeyByObjectType(String type)    { return getIconKey(type); }
    public String getIconKeyByRussianType(String type)   { return getIconKey(type); }
    public String getIconKeyByDbObjectType(String type)  { return getIconKey(type); }
    public String getRussianNameByObjectType(String type){ return getRussianName(type); }

    // ─────────────────────────────────────────────────────────────────────────
    // Singleton
    // ─────────────────────────────────────────────────────────────────────────


    // ─────────────────────────────────────────────────────────────────────────
    // Построение дерева из XML
    // ─────────────────────────────────────────────────────────────────────────
    public TreeItem<String> buildConfigTree(String configXmlPath) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new File(configXmlPath));
            Element rootEl = doc.getDocumentElement();

            String configName = getTextContent(rootEl, "Name");
            String configSynonym = getSynonym(rootEl);
            String rootLabel = configSynonym.isEmpty() ? configName : configSynonym;

            TreeItem<String> rootItem = new TreeItem<>(rootLabel);
            rootItem.setExpanded(true);
            setIcon(rootItem, "conf");

            NodeList childObjs = rootEl.getElementsByTagNameNS("http://v8.1c.ru/8.3/MDClasses", "ChildObjects");
            if (childObjs.getLength() == 0) return rootItem;

            Element childObjEl = (Element) childObjs.item(0);
            NodeList children = childObjEl.getChildNodes();

            Map<String, List<String>> groups = new HashMap<>();
            for (int i = 0; i < children.getLength(); i++) {
                Node node = children.item(i);
                if (node instanceof Element el && !el.getLocalName().startsWith("xr:")) {
                    String tag = el.getLocalName();
                    String name = el.getTextContent().trim();
                    if (!name.isEmpty()) {
                        groups.computeIfAbsent(tag, k -> new ArrayList<>()).add(name);
                    }
                }
            }

            TreeItem<String> commonGroup = new TreeItem<>("Общие");
            setIcon(commonGroup, "obs");
            rootItem.getChildren().add(commonGroup);

            List<String> commonChildren = List.of(
                    "Subsystem", "CommonModule", "SessionParameter", "Role", "CommonAttribute", "ExchangePlan",
                    "FilterCriterion", "CommonCommand", "ScheduledJob", "FunctionalOption", "FunctionalOptionsParameter",
                    "DefinedType", "SettingsStorage", "EventSubscription", "CommandGroup", "CommonForm",
                    "CommonTemplate", "CommonPicture", "XDTOPackage", "WebService", "HTTPService", "WSReference",
                    "StyleItem", "Style", "Language"
            );

            for (String tag : commonChildren) {
                List<String> items = groups.remove(tag);
                if (items == null || items.isEmpty()) continue;

                TypeInfo info = TYPES.getOrDefault(tag, new TypeInfo(tag, "conf"));
                TreeItem<String> groupItem = new TreeItem<>(info.russianName());
                setIcon(groupItem, info.iconKey());
                items.stream().sorted().forEach(name -> {
                    TreeItem<String> item = new TreeItem<>(name);
                    setIcon(item, info.iconKey());
                    groupItem.getChildren().add(item);
                });
                commonGroup.getChildren().add(groupItem);
            }

            for (Map.Entry<String, TypeInfo> entry : TYPES.entrySet()) {
                String tag = entry.getKey();
                TypeInfo info = entry.getValue();
                if (commonChildren.contains(tag) || !groups.containsKey(tag)) continue;

                List<String> items = groups.get(tag);
                TreeItem<String> groupItem = new TreeItem<>(info.russianName());
                setIcon(groupItem, info.iconKey());
                items.stream().sorted().forEach(name -> {
                    TreeItem<String> item = new TreeItem<>(name);
                    setIcon(item, info.iconKey());
                    groupItem.getChildren().add(item);
                });
                rootItem.getChildren().add(groupItem);
            }

            return rootItem;

        } catch (Exception e) {
            e.printStackTrace();
            return new TreeItem<>("Ошибка: " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Построение дерева из БД
    // ─────────────────────────────────────────────────────────────────────────
    public TreeItem<String> buildConfigTreeFromDb() {
        try (Connection conn = DriverManager.getConnection(DBPathHelper.getDbUrl())) {

            try (Statement checkStmt = conn.createStatement()) {
                ResultSet tables = checkStmt.executeQuery(
                        "SELECT name FROM sqlite_master WHERE type='table' AND name='Konfiguratsia'");
                if (!tables.next()) return new TreeItem<>("Конфигурация не сохранена в БД");
            }

            Map<Integer, TreeItem<String>> nodes = new HashMap<>();
            Map<Integer, Integer> parentMap = new HashMap<>();

            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT id, parent_id, name, icon_key FROM Konfiguratsia ORDER BY id")) {
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    int id = rs.getInt("id");
                    int parentId = rs.getInt("parent_id");
                    boolean parentNull = rs.wasNull();
                    String name = rs.getString("name");
                    String iconKey = rs.getString("icon_key");

                    TreeItem<String> item = new TreeItem<>(name);
                    setIcon(item, iconKey != null ? iconKey : "conf");
                    nodes.put(id, item);
                    if (!parentNull) parentMap.put(id, parentId);
                }
            }

            if (nodes.isEmpty()) return new TreeItem<>("База данных пуста");

            TreeItem<String> root = null;
            for (Map.Entry<Integer, TreeItem<String>> entry : nodes.entrySet()) {
                int id = entry.getKey();
                TreeItem<String> item = entry.getValue();
                Integer parentId = parentMap.get(id);
                if (parentId == null) {
                    root = item;
                } else {
                    TreeItem<String> parent = nodes.get(parentId);
                    if (parent != null) parent.getChildren().add(item);
                }
            }

            if (root != null) {
                addModulesToObjects(nodes, conn);
                root.setExpanded(true);
                return root;
            }
            return new TreeItem<>("Ошибка построения дерева из БД");

        } catch (SQLException e) {
            return new TreeItem<>("Ошибка БД: " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Группы, чьи объекты могут иметь модули
    // ─────────────────────────────────────────────────────────────────────────
    private static final Set<String> GROUPS_WITH_MODULES = Set.of(
            "Документы", "Справочники", "Регистры сведений", "Регистры накопления",
            "Регистры бухгалтерии", "Регистры расчетов",
            "Обработки", "Отчёты", "Планы обмена",
            "Журналы документов", "Планы счетов", "Планы видов характеристик",
            "Планы видов расчета",
            "Веб-сервисы", "HTTP-сервисы", "Критерии отбора", "Хранилища настроек",
            "Бизнес-процессы", "Задачи"   // ← вот этих не было
    );
    private void addModulesToObjects(Map<Integer, TreeItem<String>> nodes, Connection conn) throws SQLException {
        Set<Integer> processed = new HashSet<>();
        for (TreeItem<String> node : nodes.values()) {
            if (!node.getChildren().isEmpty() || node.getParent() == null) continue;
            if (!isObjectNode(node)) continue;

            String objectType = getObjectTypeFromParent(node); // теперь возвращает 1С-стиль
            if (objectType == null) continue;

            Integer objectId = findObjectId(node.getValue(), objectType, conn);
            if (objectId != null && processed.add(objectId)) {
                addModulesForObject(node, objectId, conn);
            }
        }
    }

    private void addModulesForObject(TreeItem<String> objectNode, int objectId, Connection conn) throws SQLException {
        List<ModuleInfo> modules = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT module_type, module_name FROM metadata_modules WHERE object_id = ? ORDER BY module_type")) {
            ps.setInt(1, objectId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                modules.add(new ModuleInfo(rs.getString("module_type"), rs.getString("module_name")));
            }
        }
        if (!modules.isEmpty()) addModulesInCorrectOrder(objectNode, modules);
    }

    private void addModulesInCorrectOrder(TreeItem<String> objectNode, List<ModuleInfo> modules) {
        List<ModuleInfo> recordSets = new ArrayList<>(), objects = new ArrayList<>(),
                managers = new ArrayList<>(), forms = new ArrayList<>(), commands = new ArrayList<>();

        for (ModuleInfo m : modules) {
            switch (m.type) {
                case "RecordSet" -> recordSets.add(m);
                case "Object"    -> objects.add(m);
                case "Manager"   -> managers.add(m);
                case "Form"      -> forms.add(m);
                case "Command"   -> commands.add(m);
            }
        }

        for (ModuleInfo m : recordSets) addSingleModule(objectNode, m.type, m.name);
        for (ModuleInfo m : objects)    addSingleModule(objectNode, m.type, m.name);
        for (ModuleInfo m : managers)   addSingleModule(objectNode, m.type, m.name);
        for (ModuleInfo m : forms)      addSingleModule(objectNode, m.type, m.name);
        for (ModuleInfo m : commands)   addSingleModule(objectNode, m.type, m.name);
    }

    private void addSingleModule(TreeItem<String> objectNode, String moduleType, String moduleName) {
        if ("Form".equals(moduleType)) {
            TreeItem<String> formsGroup = getOrCreateChildGroup(objectNode, "Формы", "forma");
            TreeItem<String> item = new TreeItem<>(moduleName != null ? moduleName : "Форма");
            setIcon(item, "forma");
            formsGroup.getChildren().add(item);
        } else if ("Command".equals(moduleType)) {
            TreeItem<String> commandsGroup = getOrCreateChildGroup(objectNode, "Команды", "obs_kom");
            TreeItem<String> item = new TreeItem<>(moduleName != null ? moduleName : "Команда");
            setIcon(item, "obs_kom");
            commandsGroup.getChildren().add(item);
        } else {
            String displayName = switch (moduleType) {
                case "Object"    -> "МодульОбъекта";
                case "Manager"   -> "МодульМенеджера";
                case "RecordSet" -> "МодульНабораЗаписей";
                case "Module"    -> "Модуль";
                default          -> moduleType;
            };
            String iconKey = switch (moduleType) {
                case "Manager"   -> "modyl_menedzer";
                case "Object", "RecordSet", "Module" -> "modyl_osn";
                default          -> "conf";
            };
            TreeItem<String> item = new TreeItem<>(displayName);
            setIcon(item, iconKey);
            objectNode.getChildren().add(item);
        }
    }

    private TreeItem<String> getOrCreateChildGroup(TreeItem<String> parent, String name, String iconKey) {
        return parent.getChildren().stream()
                .filter(c -> name.equals(c.getValue()))
                .findFirst()
                .orElseGet(() -> {
                    TreeItem<String> group = new TreeItem<>(name);
                    setIcon(group, iconKey);
                    parent.getChildren().add(group);
                    return group;
                });
    }

    private Integer findObjectId(String objectName, String objectType, Connection conn) throws SQLException {
        // objectType здесь в 1С-стиле: "Документ", "РегистрСведений" и т.д.
        String dbType = toSingularEnglish(objectType); // → "Document", "InformationRegister"
        // В БД хранится множественное число, получаем его обратно через отдельный метод
        String dbTypePlural = toDbPluralEnglish(dbType);

        String simpleName = stripObjectPrefix(objectName);

        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id FROM metadata_objects WHERE object_name = ? AND object_type = ?")) {
            ps.setString(1, simpleName);
            ps.setString(2, dbTypePlural);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt("id");
        }

        // Fallback: full_name
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id FROM metadata_objects WHERE full_name = ?")) {
            ps.setString(1, objectType + "." + simpleName);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt("id");
        } catch (SQLException ignored) {}

        return null;
    }

    /** Единственное английское → множественное для хранения в БД */
    private String toDbPluralEnglish(String singular) {
        return switch (singular) {
            case "Document"                 -> "Documents";
            case "Catalog"                  -> "Catalogs";
            case "DataProcessor"            -> "DataProcessors";
            case "Report"                   -> "Reports";
            case "InformationRegister"      -> "InformationRegisters";
            case "AccumulationRegister"     -> "AccumulationRegisters";
            case "AccountingRegister"       -> "AccountingRegisters";
            case "Enum"                     -> "Enums";
            case "Constant"                 -> "Constants";
            case "ExchangePlan"             -> "ExchangePlans";
            case "DocumentJournal"          -> "DocumentJournals";
            case "ChartOfAccounts"          -> "ChartsOfAccounts";
            case "ChartOfCharacteristicTypes" -> "ChartsOfCharacteristicTypes";
            case "WebService"               -> "WebServices";
            case "HTTPService"              -> "HTTPServices";
            case "CommonModule"             -> "CommonModules";
            case "CommonForm"               -> "CommonForms";
            case "CommonCommand"            -> "CommonCommands";
            case "Subsystem"                -> "Subsystems";
            case "Role"                     -> "Roles";
            case "BusinessProcess"          -> "BusinessProcesses";
            case "Task"                     -> "Tasks";
            case "FilterCriterion"          -> "FilterCriteria";
            case "SettingsStorage"          -> "SettingsStorages";
            case "ScheduledJob"             -> "ScheduledJobs";
            case "SessionParameter"         -> "SessionParameters";
            case "EventSubscription"        -> "EventSubscriptions";
            case "FunctionalOption"         -> "FunctionalOptions";
            case "DefinedType"              -> "DefinedTypes";
            case "CommandGroup"             -> "CommandGroups";
            case "CommonTemplate"           -> "CommonTemplates";
            case "CommonPicture"            -> "CommonPictures";
            case "XDTOPackage"              -> "XDTOPackages";
            case "WSReference"              -> "WSReferences";

            case "CalculationRegister"         -> "CalculationRegisters";
            case "ChartOfCalculationTypes"     -> "ChartsOfCalculationTypes";


            default                         -> singular;
        };
    }

    private String stripObjectPrefix(String fullName) {
        int dot = fullName.indexOf('.');
        return dot > 0 ? fullName.substring(dot + 1) : fullName;
    }

    private boolean isObjectNode(TreeItem<String> node) {
        if (node.getParent() == null) return false;
        String parentName = node.getParent().getValue();
        return GROUPS_WITH_MODULES.contains(parentName);
    }

    private String getObjectTypeFromParent(TreeItem<String> node) {
        if (node.getParent() == null) return null;
        // Возвращаем 1С-стиль (единственное число на русском) — для формирования full_name
        return switch (node.getParent().getValue()) {
            case "Документы"                 -> "Документ";
            case "Справочники"               -> "Справочник";
            case "Регистры сведений"         -> "РегистрСведений";
            case "Регистры накопления"       -> "РегистрНакопления";
            case "Регистры бухгалтерии"      -> "РегистрБухгалтерии";
            case "Обработки"                 -> "Обработка";
            case "Отчёты"                    -> "Отчет";
            case "Перечисления"              -> "Перечисление";
            case "Константы"                 -> "Константа";
            case "Планы обмена"              -> "ПланОбмена";
            case "Журналы документов"        -> "ЖурналДокументов";
            case "Планы счетов"              -> "ПланСчетов";
            case "Планы видов характеристик" -> "ПланВидовХарактеристик";
            case "Веб-сервисы"               -> "ВебСервис";
            case "HTTP-сервисы"              -> "HTTPСервис";
            case "Критерии отбора"           -> "КритерийОтбора";
            case "Хранилища настроек"        -> "ХранилищеНастроек";
            case "Бизнес-процессы"           -> "БизнесПроцесс";
            case "Задачи"                    -> "Задача";

            case "Регистры расчетов"           -> "РегистрРасчетов";
            case "Планы видов расчета"         -> "ПланВидовРасчета";
            default -> null;
        };
    }

    private void setIcon(TreeItem<String> item, String iconKey) {
        Image icon = IconManager.getInstance().getIcon(iconKey);
        if (icon != null) {
            ImageView iv = new ImageView(icon);
            iv.setFitWidth(16);
            iv.setFitHeight(16);
            item.setGraphic(iv);
        }
    }

    private String getTextContent(Element parent, String tagName) {
        NodeList nodes = parent.getElementsByTagName(tagName);
        return nodes.getLength() > 0 ? nodes.item(0).getTextContent().trim() : "";
    }

    private String getSynonym(Element root) {
        NodeList synonymNodes = root.getElementsByTagName("Synonym");
        if (synonymNodes.getLength() == 0) return "";
        Element synonymEl = (Element) synonymNodes.item(0);
        NodeList items = synonymEl.getElementsByTagName("v8:item");
        for (int i = 0; i < items.getLength(); i++) {
            Element item = (Element) items.item(i);
            if ("ru".equals(getTextContent(item, "v8:lang")))
                return getTextContent(item, "v8:content");
        }
        return "";
    }

    private record ModuleInfo(String type, String name) {}
}