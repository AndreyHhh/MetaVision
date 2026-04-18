package com.lycurg.metavisionfor1c;

//# Базовая модель элемента кода для хранения типа, строк, владельцев и связей с функциями
public class CodeElement {
    public String type;
    public String subtype;
    public int startLine;
    public int endLine;
    public String text;
    public String ownerElementId;
    public String ownerType;      // ← ТЕПЕРЬ ИНИЦИАЛИЗИРУЕТСЯ В КОНСТРУКТОРЕ
    public String ownerName;
    public String ownerFunctionName;
    public Integer id;
    public Integer calledFunctionId;
    public Integer function_id;

    public CodeElement(String type, String subtype, int startLine, String text) {
        this.type = type;
        this.subtype = subtype;
        this.startLine = startLine;
        this.text = text;
        this.endLine = startLine;
        this.ownerElementId = null;
        this.ownerType = null;     // ← ВАЖНО: инициализируем
        this.ownerName = null;
        this.ownerFunctionName = null;
        this.id = null;
        this.calledFunctionId = null;
        this.function_id = null;
    }

    public CodeElement() {
        this.ownerElementId = null;
        this.ownerType = null;     // ← ВАЖНО: инициализируем
        this.ownerName = null;
        this.ownerFunctionName = null;
        this.id = null;
        this.calledFunctionId = null;
        this.function_id = null;
    }
}