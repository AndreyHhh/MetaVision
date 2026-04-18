package com.lycurg.metavisionfor1c;

import java.io.File;
import java.sql.*;

//# Загрузчик модулей объектов из BSL файлов
public class ModuleLoader_bsl {

    // Сохраняет все модули объекта в БД.
    // Принимает готовое соединение — не открывает своё.
    public static void saveObjectModules(int objectId, String objectFullName,
                                         File objectDir, String objectType,
                                         Connection conn) throws SQLException {
        if (!objectDir.exists() || !objectDir.isDirectory()) {
            return;
        }

        ModuleInfo[] modules = getRequiredModulesForObjectType(objectType);

        for (ModuleInfo module : modules) {
            try {
                File moduleFile = new File(objectDir, module.relativePath);
                if (moduleFile.exists() && moduleFile.isFile()) {
                    MetadataDbManager.saveModule(conn, objectId, objectFullName,
                            module.type, module.moduleName, moduleFile.getPath());
                }
            } catch (Exception e) {
                System.err.println("⚠️ Ошибка сохранения модуля " + module.type
                        + " для " + objectFullName + ": " + e.getMessage());
            }
        }

        saveFormModules(objectId, objectFullName, objectDir, conn);
        saveCommandModules(objectId, objectFullName, objectDir, conn);
    }

    // Обратная совместимость — для вызовов без готового соединения
    public static void saveObjectModules(int objectId, String objectFullName,
                                         File objectDir, String objectType) throws SQLException {
        try (Connection conn = DriverManager.getConnection(DBPathHelper.getDbUrl())) {
            saveObjectModules(objectId, objectFullName, objectDir, objectType, conn);
        }
    }

    private static ModuleInfo[] getRequiredModulesForObjectType(String objectType) {
        switch (objectType) {

            // Объект + Менеджер (без набора записей)
            case "Documents":
            case "Catalogs":
            case "ExchangePlans":
            case "ChartsOfAccounts":
            case "ChartsOfCharacteristicTypes":
            case "ChartsOfCalculationTypes":
            case "BusinessProcesses":
            case "Tasks":
                return new ModuleInfo[]{
                        new ModuleInfo("Manager", "Ext/ManagerModule.bsl", null),
                        new ModuleInfo("Object",  "Ext/ObjectModule.bsl",  null)
                };

            // Менеджер + Набор записей (без объекта)
            case "InformationRegisters":
            case "AccumulationRegisters":
            case "AccountingRegisters":
            case "CalculationRegisters":
            case "DocumentJournals":
            case "FilterCriteria":
            case "SettingsStorages":
                return new ModuleInfo[]{
                        new ModuleInfo("Manager",   "Ext/ManagerModule.bsl",   null),
                        new ModuleInfo("RecordSet", "Ext/RecordSetModule.bsl", null)
                };

            // Менеджер + Модуль (обработки и отчёты)
            case "Reports":
            case "DataProcessors":
                return new ModuleInfo[]{
                        new ModuleInfo("Manager", "Ext/ManagerModule.bsl", null),
                        new ModuleInfo("Module",  "Ext/Module.bsl",        null)
                };

            // Только модуль
            case "WebServices":
            case "HTTPServices":
            case "CommonModules":
                return new ModuleInfo[]{
                        new ModuleInfo("Module", "Ext/Module.bsl", null)
                };

            case "CommonForms":
                return new ModuleInfo[]{
                        new ModuleInfo("Module", "Ext/Form/Module.bsl", null)
                };

            case "CommonCommands":
                return new ModuleInfo[]{
                        new ModuleInfo("Module", "Ext/CommandModule.bsl", null)
                };

            default:
                return new ModuleInfo[0];
        }
    }

    private static void saveFormModules(int objectId, String objectFullName,
                                        File objectDir, Connection conn) throws SQLException {
        File formsDir = new File(objectDir, "Forms");
        if (!formsDir.exists() || !formsDir.isDirectory()) return;

        File[] formDirs = formsDir.listFiles(File::isDirectory);
        if (formDirs == null) return;

        for (File formDir : formDirs) {
            try {
                File formModule = findFormModule(formDir);
                if (formModule != null) {
                    MetadataDbManager.saveModule(conn, objectId, objectFullName,
                            "Form", formDir.getName(), formModule.getPath());
                }
            } catch (Exception e) {
                System.err.println("⚠️ Ошибка формы " + formDir.getName()
                        + " для " + objectFullName + ": " + e.getMessage());
            }
        }
    }

    private static void saveCommandModules(int objectId, String objectFullName,
                                           File objectDir, Connection conn) throws SQLException {
        File commandsDir = new File(objectDir, "Commands");
        if (!commandsDir.exists() || !commandsDir.isDirectory()) return;

        File[] commandDirs = commandsDir.listFiles(File::isDirectory);
        if (commandDirs == null) return;

        for (File commandDir : commandDirs) {
            try {
                File commandModule = new File(commandDir, "Ext/CommandModule.bsl");
                if (commandModule.exists()) {
                    MetadataDbManager.saveModule(conn, objectId, objectFullName,
                            "Command", commandDir.getName(), commandModule.getPath());
                }
            } catch (Exception e) {
                System.err.println("⚠️ Ошибка команды " + commandDir.getName()
                        + " для " + objectFullName + ": " + e.getMessage());
            }
        }
    }

    private static File findFormModule(File formDir) {
        File[] possiblePaths = {
                new File(formDir, "Ext/Form/Module.bsl"),
                new File(formDir, "Ext/FormModule.bsl"),
                new File(formDir, "Module.bsl"),
                new File(formDir, "Ext/Module.bsl")
        };
        for (File path : possiblePaths) {
            if (path.exists() && path.isFile()) return path;
        }
        return null;
    }

    private static class ModuleInfo {
        String type;
        String relativePath;
        String moduleName;

        ModuleInfo(String type, String relativePath, String moduleName) {
            this.type         = type;
            this.relativePath = relativePath;
            this.moduleName   = moduleName;
        }
    }
}