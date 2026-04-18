package com.lycurg.metavisionfor1c;

import org.w3c.dom.*;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.util.*;


//# Чтение версий объектов конфигурации из ConfigDumpInfo.xml с кэшированием
public class ModuleLoader_Version {
    private String configDir;
    private Map<String, String> versionCache;

    public ModuleLoader_Version(String configDir) {
        this.configDir = configDir;
        this.versionCache = new HashMap<>();
        loadVersionCache();
    }


    //загрузка XML в память с фильтрацией служебных модулей
    private void loadVersionCache() {
        try {
            File configDumpFile = new File(configDir + "/ConfigDumpInfo.xml");
            if (!configDumpFile.exists()) {
                System.err.println("ConfigDumpInfo.xml не найден: " + configDumpFile.getPath());
                return;
            }

            System.out.println("=== ЗАГРУЗКА КЭША ВЕРСИЙ ИЗ ConfigDumpInfo.xml ===");

            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(configDumpFile);

            NodeList metadataNodes = doc.getElementsByTagNameNS("http://v8.1c.ru/8.3/xcf/dumpinfo", "Metadata");

            System.out.println("Всего элементов в XML: " + metadataNodes.getLength());

            int loadedCount = 0;
            int skippedCount = 0;

            for (int i = 0; i < metadataNodes.getLength(); i++) {
                Element metadataEl = (Element) metadataNodes.item(i);
                String name = metadataEl.getAttribute("name");
                String configVersion = metadataEl.getAttribute("configVersion");

                // Берем ВСЕ объекты с версиями, кроме служебных модулей
                if (!configVersion.isEmpty() && isMainObject(name)) {
                    versionCache.put(name, configVersion);
                    loadedCount++;

                    if (loadedCount % 1000 == 0) {
                        System.out.println("Загружено объектов в кэш: " + loadedCount);
                    }
                } else {
                    skippedCount++;
                }
            }

            System.out.println("=== КЭШ ВЕРСИЙ ЗАГРУЖЕН ===");
            System.out.println("Основных объектов: " + loadedCount);
            System.out.println("Пропущено служебных: " + skippedCount);

            printVersionCacheStats();

        } catch (Exception e) {
            System.out.println("ОШИБКА загрузки кэша версий: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Определяем, является ли имя основным объектом (а не модулем)
    private boolean isMainObject(String name) {
        // ОСНОВНЫЕ ТИПЫ ОБЪЕКТОВ (без модулей)
        String[] mainTypes = {
                "Document.", "Документ.",
                "Catalog.", "Справочник.",
                "InformationRegister.", "РегистрСведений.",
                "AccumulationRegister.", "РегистрНакопления.",
                "AccountingRegister.", "РегистрБухгалтерии.",
                "ChartOfAccounts.", "ПланСчетов.",
                "ChartOfCharacteristicTypes.", "ПланВидовХарактеристик.",
                "Report.", "Отчет.",
                "DataProcessor.", "Обработка.",
                "CommonModule.", "ОбщийМодуль.",
                "CommonForm.", "ОбщаяФорма.",
                "CommonCommand.", "ОбщаяКоманда.",
                "WebService.", "ВебСервис.",
                "HTTPService.", "HTTPСервис.",
                "ExchangePlan.", "ПланОбмена.",
                "SettingsStorage.", "ХранилищеНастроек.",
                "DocumentJournal.", "ЖурналДокументов.",
                "Enum.", "Перечисление.",
                "Constant.", "Константа."
        };

        for (String type : mainTypes) {
            if (name.startsWith(type) && !name.contains(".Module") &&
                    !name.contains(".ManagerModule") && !name.contains(".ObjectModule") &&
                    !name.contains(".FormModule") && !name.contains(".CommandModule") &&
                    !name.contains(".RecordSetModule")) {
                return true;
            }
        }

        return false;
    }


    // МАССОВАЯ ЗАГРУЗКА ВЕРСИЙ ДЛЯ СПИСКА ОБЪЕКТОВ
    public Map<String, String> getVersionsForObjects(List<String> objectNames) {
        Map<String, String> result = new HashMap<>();

        if (versionCache == null || objectNames == null) {
            return result;
        }

        System.out.println("🔍 Массовая загрузка версий для " + objectNames.size() + " объектов...");
        int foundCount = 0;

        for (String objectName : objectNames) {
            // Прямой поиск
            String version = versionCache.get(objectName);

            // Если не нашли, пробуем конвертировать имя
            if (version == null) {
                String convertedName = convertObjectName(objectName);
                if (convertedName != null) {
                    version = versionCache.get(convertedName);
                }
            }

            if (version != null) {
                result.put(objectName, version);
                foundCount++;
            }
        }

        System.out.println("✅ Найдено версий: " + foundCount + "/" + objectNames.size());
        return result;
    }

    // Конвертация имен между русскими и английскими
    private String convertObjectName(String fullName) {
        if (fullName == null) return null;

        String[] parts = fullName.split("\\.", 2);
        if (parts.length != 2) return null;

        String type = parts[0];
        String name = parts[1];

        // Русские -> английские
        switch (type) {
            case "Документ": return "Document." + name;
            case "Справочник": return "Catalog." + name;
            case "РегистрСведений": return "InformationRegister." + name;
            case "РегистрНакопления": return "AccumulationRegister." + name;
            case "РегистрБухгалтерии": return "AccountingRegister." + name;
            case "Отчет": return "Report." + name;
            case "Обработка": return "DataProcessor." + name;
            case "ОбщийМодуль": return "CommonModule." + name;
            case "ОбщаяФорма": return "CommonForm." + name;
            case "ОбщаяКоманда": return "CommonCommand." + name;
            case "ПланОбмена": return "ExchangePlan." + name;
            case "ХранилищеНастроек": return "SettingsStorage." + name;
            case "ЖурналДокументов": return "DocumentJournal." + name;
            case "Перечисление": return "Enum." + name;
            case "Константа": return "Constant." + name;
        }

        // Английские -> русские
        switch (type) {
            case "Document": return "Документ." + name;
            case "Catalog": return "Справочник." + name;
            case "InformationRegister": return "РегистрСведений." + name;
            case "AccumulationRegister": return "РегистрНакопления." + name;
            case "AccountingRegister": return "РегистрБухгалтерии." + name;
            case "Report": return "Отчет." + name;
            case "DataProcessor": return "Обработка." + name;
            case "CommonModule": return "ОбщийМодуль." + name;
            case "CommonForm": return "ОбщаяФорма." + name;
            case "CommonCommand": return "ОбщаяКоманда." + name;
            case "ExchangePlan": return "ПланОбмена." + name;
            case "SettingsStorage": return "ХранилищеНастроек." + name;
            case "DocumentJournal": return "ЖурналДокументов." + name;
            case "Enum": return "Перечисление." + name;
            case "Constant": return "Константа." + name;
        }

        return null;
    }

    private void printVersionCacheStats() {
        Map<String, Integer> typeStats = new HashMap<>();
        for (String key : versionCache.keySet()) {
            String type = key.split("\\.")[0];
            typeStats.put(type, typeStats.getOrDefault(type, 0) + 1);
        }

        System.out.println("=== СТАТИСТИКА ВЕРСИЙ ПО ТИПАМ ===");
        for (Map.Entry<String, Integer> entry : typeStats.entrySet()) {
            System.out.println("  " + entry.getKey() + ": " + entry.getValue() + " объектов");
        }
    }

    public void dispose() {
        if (versionCache != null) {
            System.out.println("Очистка кэша версий из памяти...");
            versionCache.clear();
            versionCache = null;
        }
    }
}