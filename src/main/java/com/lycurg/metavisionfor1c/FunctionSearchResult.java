package com.lycurg.metavisionfor1c;

import javafx.beans.property.*;
import java.util.ArrayList;
import java.util.List;

//# Модель результата поиска по функциям с поддержкой подсветки найденных фрагментов
public class FunctionSearchResult {
    public final StringProperty objectName;
    public final StringProperty functionType;
    public final StringProperty functionName;
    public final StringProperty context;
    public final StringProperty fullText;
    public final StringProperty filePath;
    public final List<int[]> highlightPositions;

    // 🔥 ДОБАВЬТЕ ЭТИ ПОЛЯ
    private int functionId;
    private int lineNumber;
    private String moduleType;

    public FunctionSearchResult(String objectName, String functionType, String functionName,
                                String context, String fullText, String filePath) {
        this.objectName = new SimpleStringProperty(objectName);
        this.functionType = new SimpleStringProperty(functionType);
        this.functionName = new SimpleStringProperty(functionName);
        this.context = new SimpleStringProperty(context);
        this.fullText = new SimpleStringProperty(fullText);
        this.filePath = new SimpleStringProperty(filePath);
        this.highlightPositions = new ArrayList<>();
    }

    public StringProperty objectNameProperty() { return objectName; }
    public StringProperty functionTypeProperty() { return functionType; }
    public StringProperty functionNameProperty() { return functionName; }
    public StringProperty contextProperty() { return context; }
    public String getFullText() { return fullText.get(); }
    public List<int[]> getHighlightPositions() { return highlightPositions; }
    public void setHighlightPositions(List<int[]> positions) {
        this.highlightPositions.clear();
        if (positions != null) {
            this.highlightPositions.addAll(positions);
        }
    }

    // 🔥 ДОБАВЬТЕ ЭТИ ГЕТТЕРЫ
    public int getFunctionId() { return functionId; }
    public int getLineNumber() { return lineNumber; }
    public String getModuleType() { return moduleType; }

    // 🔥 ДОБАВЬТЕ ЭТИ СЕТТЕРЫ
    public void setFunctionId(int functionId) { this.functionId = functionId; }
    public void setLineNumber(int lineNumber) { this.lineNumber = lineNumber; }
    public void setModuleType(String moduleType) { this.moduleType = moduleType; }

    // 🔥 ДОБАВЬТЕ МЕТОД ДЛЯ ПОЛУЧЕНИЯ ИМЕНИ ФУНКЦИИ КАК STRING
    public String getFunctionName() { return functionName.get(); }
    public String getObjectName() { return objectName.get(); }
    public String getContextText() { return context.get(); }
}