package com.knemerzitski.isikreg.utils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class IOUtils {

  private IOUtils(){

  }

  private static Boolean hasWritePermissions;
  public static boolean hasWritePermissions(){
    if(hasWritePermissions == null){
      try{
        Path filePath = Paths.get("./touch");
        Files.createFile(filePath);
        Files.deleteIfExists(filePath);
        hasWritePermissions = true;
      }catch(Exception e){
        hasWritePermissions = false;
      }
    }
    return hasWritePermissions;
  }
}
