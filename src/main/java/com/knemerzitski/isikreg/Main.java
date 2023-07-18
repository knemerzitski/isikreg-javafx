package com.knemerzitski.isikreg;


import javafx.application.Application;
import javafx.stage.Stage;

import java.nio.file.FileSystems;

public class Main extends Application {

  public static void main(String[] args) {
    Thread.setDefaultUncaughtExceptionHandler((thread, exception) -> {
      try {
        exception.printStackTrace();
        IsikReg.logException(FileSystems.getDefault(), exception);
      } catch (Exception e) {
        e.printStackTrace();
        IsikReg.exit(-1);
      }
    });
    Application.launch(args);
  }

  @Override
  public void start(Stage primaryStage) {
    new IsikReg().start(primaryStage);
  }

}
