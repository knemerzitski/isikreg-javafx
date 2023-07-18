package com.knemerzitski.isikreg.file;

import com.knemerzitski.isikreg.threading.TaskExecutor;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

public abstract class SafeSynchronizedStringFile<C, I> extends SafeSynchronizedFile<C, I> {


  private final Charset charset;

  public SafeSynchronizedStringFile(Path namePath, TaskExecutor taskExecutor, Charset charset) throws IOException {
    super(namePath, taskExecutor);
    this.charset = charset;
  }

  public SafeSynchronizedStringFile(Path namePath, TaskExecutor taskExecutor) throws IOException {
    super(namePath, taskExecutor);
    this.charset = StandardCharsets.UTF_8;
  }

  protected <T> T readInReader(IOFunction<InputStreamReader, T> callback) throws IOException {
    return super.readIn((is) -> {
      try (InputStreamReader isr = new InputStreamReader(is, charset)) {
        return callback.run(isr);
      }
    });
  }


  protected abstract boolean read(InputStreamReader reader, String name) throws IOException;

  @Override
  protected boolean read(InputStream inputStream, String name) throws IOException {
    try (InputStreamReader isr = new InputStreamReader(inputStream, charset)) {
      return read(isr, name);
    }
  }

  protected abstract boolean write(OutputStreamWriter writer, String name) throws IOException;

  @Override
  protected boolean write(OutputStream outputStream, String name) throws IOException {
    try (OutputStreamWriter osw = new OutputStreamWriter(outputStream, charset)) {
      return write(osw, name);
    }
  }

  protected abstract C startWriting(InputStreamReader reader, String name) throws IOException;

  @Override
  protected C startWriting(InputStream inputStream, String name) throws IOException {
    try (InputStreamReader isr = new InputStreamReader(inputStream, charset)) {
      return startWriting(isr, name);
    }
  }

  protected abstract boolean endWriting(OutputStreamWriter writer, String name, C container) throws IOException;

  @Override
  protected boolean endWriting(OutputStream outputStream, String name, C container) throws IOException {
    try (OutputStreamWriter osw = new OutputStreamWriter(outputStream, charset)) {
      return endWriting(osw, name, container);
    }
  }
}
