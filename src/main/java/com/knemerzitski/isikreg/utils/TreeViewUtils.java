package com.knemerzitski.isikreg.utils;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import javafx.scene.control.TreeItem;

public class TreeViewUtils {

  public static TreeItem<String> toTreeItem(String key, JsonElement element) {
    TreeItem<String> item = new TreeItem<>();
    if (element.isJsonObject()) {
      item.setValue(key);
      JsonObject obj = element.getAsJsonObject();
      obj.entrySet().forEach(e -> item.getChildren().add(toTreeItem(e.getKey(), e.getValue())));
    } else if (element.isJsonArray()) {
      item.setValue(key);
      JsonArray arr = element.getAsJsonArray();
      for (int i = 0; i < arr.size(); i++) {
        item.getChildren().add(toTreeItem(String.valueOf(i), arr.get(i)));
      }
    } else if (element.isJsonPrimitive()) {
      item.setValue(key + ": " + element);
    } else if (element.isJsonNull()) {
      item.setValue(key + ": null");
    }
    return item;
  }

  private TreeViewUtils() {
  }

}
