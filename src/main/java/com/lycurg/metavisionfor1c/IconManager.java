package com.lycurg.metavisionfor1c;

import javafx.scene.image.Image;
import java.util.HashMap;
import java.util.Map;


//# Менеджер загрузки и кэширования иконок для дерева объектов конфигурации
public class IconManager {
    private static IconManager instance;
    private final Map<String, Image> iconCache = new HashMap<>();

    private IconManager() {}

    public static IconManager getInstance() {
        if (instance == null) {
            instance = new IconManager();
        }
        return instance;
    }

    public Image getIcon(String iconKey) {
        if (iconKey == null || iconKey.isEmpty()) {
            iconKey = "conf";
        }

        // Проверяем кэш
        if (iconCache.containsKey(iconKey)) {
            return iconCache.get(iconKey);
        }

        // Загружаем иконку
        String[] paths = {
                "/icons/" + iconKey + ".png",
                "/icons/" + iconKey + ".jpg",
                "/icons/" + iconKey + ".gif",
                "/icons/conf.png"  // fallback
        };

        Image icon = null;
        for (String path : paths) {
            try {
                icon = new Image(getClass().getResourceAsStream(path));
                if (icon != null && !icon.isError()) {
                    break;
                }
            } catch (Exception e) {
                // Пробуем следующий путь
            }
        }

        // Если иконка не найдена, пробуем найти по базовому имени
        if (icon == null || icon.isError()) {
            String baseName = extractBaseName(iconKey);
            if (!baseName.equals(iconKey)) {
                icon = getIcon(baseName);
            }
        }

        // Если всё ещё null, создаем пустую иконку
        if (icon == null || icon.isError()) {
            icon = createEmptyIcon();
        }

        // Кэшируем
        iconCache.put(iconKey, icon);
        return icon;
    }

    private String extractBaseName(String iconKey) {
        // Убираем суффиксы типа _menedzer, _osn и т.д.
        if (iconKey.endsWith("_menedzer")) {
            return iconKey.substring(0, iconKey.length() - "_menedzer".length());
        }
        if (iconKey.endsWith("_osn")) {
            return iconKey.substring(0, iconKey.length() - "_osn".length());
        }
        if (iconKey.endsWith("_modyl")) {
            return iconKey.substring(0, iconKey.length() - "_modyl".length());
        }
        return iconKey;
    }

    private Image createEmptyIcon() {
        // Создаем пустую иконку 16x16
        return new Image(getClass().getResourceAsStream("/icons/conf.png"));
    }

    public void preloadIcons(String... iconKeys) {
        for (String key : iconKeys) {
            getIcon(key);
        }
    }

    public void clearCache() {
        iconCache.clear();
    }
}