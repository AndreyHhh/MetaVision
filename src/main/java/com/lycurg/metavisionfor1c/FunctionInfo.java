package com.lycurg.metavisionfor1c;

import javafx.beans.property.*;


//# Singleton менеджер пула потоков для многопоточного анализа кода
public class FunctionInfo {
    private final StringProperty name;
    private final StringProperty type;
    private final IntegerProperty lineCount;
    private final IntegerProperty elementCount;
    private final IntegerProperty moduleId;
    private final StringProperty objectName;
    private final IntegerProperty functionId;

    public FunctionInfo(String name, String type, int lineCount, int elementCount,
                        int moduleId, String objectName, int functionId) {
        this.name = new SimpleStringProperty(name);
        this.type = new SimpleStringProperty(type);
        this.lineCount = new SimpleIntegerProperty(lineCount);
        this.elementCount = new SimpleIntegerProperty(elementCount);
        this.moduleId = new SimpleIntegerProperty(moduleId);
        this.objectName = new SimpleStringProperty(objectName);
        this.functionId = new SimpleIntegerProperty(functionId);
    }

    public int getFunctionId() { return functionId.get(); }
    public String getName() { return name.get(); }
    public String getType() { return type.get(); }
    public int getModuleId() { return moduleId.get(); }
    public String getObjectName() { return objectName.get(); }
    public StringProperty nameProperty() { return name; }

    public int getLineCount() {
        return lineCount.get();
    }

    public int getElementCount() {
        return elementCount.get();
    }

}