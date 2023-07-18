package com.knemerzitski.isikreg.ui;

import javafx.scene.Node;
import javafx.scene.control.Dialog;

public class DialogHandler {

  public boolean confirm(String header, Node content) {
    System.out.println(header);
    return true;
  }

  public boolean confirm(String header, String content) {
    System.out.println(header + ": " + content);
    return true;
  }

  public void warning(String header, String content) {
    System.out.println(header + ": " + content);
  }

  public void exception(boolean err, String header, String content) {
    System.out.println(header + ": " + content);
  }

  public <T> void centerOnShown(Dialog<T> dialog) {

  }

}
