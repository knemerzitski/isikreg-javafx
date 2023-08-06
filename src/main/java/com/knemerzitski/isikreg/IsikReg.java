package com.knemerzitski.isikreg;

import com.knemerzitski.isikreg.beans.LockableValue;
import com.knemerzitski.isikreg.date.Date;
import com.knemerzitski.isikreg.exception.AppInfoException;
import com.knemerzitski.isikreg.exception.AppQuitException;
import com.knemerzitski.isikreg.gson.GsonBooleanProperty;
import com.knemerzitski.isikreg.gson.GsonDateProperty;
import com.knemerzitski.isikreg.person.*;
import com.knemerzitski.isikreg.settings.ColumnProperties;
import com.knemerzitski.isikreg.settings.Settings;
import com.knemerzitski.isikreg.settings.SettingsValidator;
import com.knemerzitski.isikreg.settings.columns.*;
import com.knemerzitski.isikreg.smartcard.ProcessedReader;
import com.knemerzitski.isikreg.smartcard.TerminalReader;
import com.knemerzitski.isikreg.smartcard.TerminalsManager;
import com.knemerzitski.isikreg.smartcard.records.CardRecords;
import com.knemerzitski.isikreg.table.DateTableCell;
import com.knemerzitski.isikreg.threading.TaskExecutor;
import com.knemerzitski.isikreg.ui.DialogHandler;
import com.knemerzitski.isikreg.ui.RegistrationFormDialog;
import com.knemerzitski.isikreg.ui.StageDialogHandler;
import com.knemerzitski.isikreg.ui.StatisticsLabel;
import com.knemerzitski.isikreg.ui.status.CardStatusPane;
import com.knemerzitski.isikreg.ui.status.CardStatusText;
import com.knemerzitski.isikreg.utils.TreeViewUtils;
import com.sun.javafx.scene.control.skin.TableViewSkin;
import com.sun.javafx.scene.control.skin.VirtualFlow;
import javafx.application.Platform;
import javafx.beans.property.*;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.*;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.control.cell.ComboBoxTableCell;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.WindowEvent;
import javafx.util.converter.DefaultStringConverter;
import org.jetbrains.annotations.NotNull;

import javax.smartcardio.CardTerminal;
import javax.smartcardio.TerminalFactory;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.knemerzitski.isikreg.utils.IOUtils.hasWritePermissions;

public class IsikReg {

  private static final String VERSION = "4.2.1";
  private static final String WINDOW_TITLE = "Isikkoosseisu Registreerimise Rakendus";
  private static final String TITLE_FORMAT = "%1$s %2$s";

  static void logException(FileSystem fileSystem, Throwable e) {
    try {
      if (fileSystem == null) {
        fileSystem = FileSystems.getDefault();
      }

      Path path = fileSystem.getPath("error.log");
      StandardOpenOption op = Files.exists(path) ? StandardOpenOption.APPEND : StandardOpenOption.CREATE_NEW;
      String date = DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(LocalDateTime.now());
      StringWriter sw = new StringWriter();
      PrintWriter pw = new PrintWriter(sw);
      e.printStackTrace(pw);
      String message = sw.toString();
      String content = String.format("%s ERROR - %s", date, message);
      Files.write(path, content.getBytes(), op);
    } catch (IOException e2) {
      e2.printStackTrace();
    }
  }

  static void exit(int status) {
    Platform.exit();
    System.exit(status);
  }


  private FileSystem fileSystem;
  private Settings settings;
  private Thread.UncaughtExceptionHandler exceptionHandler;

  private TaskExecutor taskExecutor;

  private boolean started;
  private boolean stopping;
  private boolean criticalThreadsStopped;
  private PersonListExcelWriter personListExcelWriter;

  private Stage primaryStage;
  private StageDialogHandler dialogHandler;
  private Set<Stage> otherStages;

  private PersonList personList;
  private PersonListHelper personListHelper;
  private TerminalsManager terminalsManager;
  private ColumnProperties cardRecordPropertiesNotRegistered;

  private final BooleanProperty loading = new SimpleBooleanProperty(false);

  private BorderPane mainBorderPane;
  private StackPane progressStackPane;
  private ProgressBar loadingProgressBar;
  private CardStatusPane mainCardStatusPane;
  private CardStatusText mainCardStatusText;

  private Menu cardTerminalsMenu;

  private TextField tableViewFilterTextField;
  private TableView<Registration> registrationTableView;

  public IsikReg() {
  }

  public IsikReg(FileSystem fileSystem, Settings settings, Thread.UncaughtExceptionHandler exceptionHandler) {
    this.fileSystem = fileSystem;
    this.settings = settings;
    this.exceptionHandler = exceptionHandler;
  }

  public void start(Stage primaryStage) {
    if (started) return;
    started = true;
    try {
      dialogHandler = new StageDialogHandler(primaryStage);

      if (exceptionHandler == null)
        exceptionHandler = createUncaughtExceptionHandler();
      Thread.setDefaultUncaughtExceptionHandler(exceptionHandler); // For JavaFX Thread

      // Check if have file write permissions in app directory
      if(!hasWritePermissions()){
        dialogHandler.exception(true, "Kirjutusviga", "Programmi kaustas puudub kirjutusõigus!");
        stop(null);
      }

      if (fileSystem == null)
        fileSystem = createFileSystem();

      if (settings == null)
        settings = createSettings(fileSystem);
      initSettings(settings, dialogHandler);

      taskExecutor = createTaskExecutor(exceptionHandler);

      if (settings.general.smoothFont)
        System.setProperty("prism.lcdtext", "false");

      if (primaryStage.getStyle() == StageStyle.UNDECORATED)
        primaryStage.initStyle(StageStyle.DECORATED);
      primaryStage.setMinWidth(730);
      primaryStage.setMinHeight(467);
      primaryStage.setWidth(Font.getDefault().getSize() * 65);
      primaryStage.setHeight(Font.getDefault().getSize() * 45);

      primaryStage.setTitle(String.format(TITLE_FORMAT, WINDOW_TITLE, VERSION));
      // Prevent default behaviour of closing the window
      primaryStage.setOnCloseRequest(this::stop);

      Scene scene = initScene(primaryStage);

      // Load style
      String stylePath = "style.css";
      URL styleAsset = getClass().getResource(stylePath);
      if (styleAsset == null)
        throw new IOException("Resource '" + stylePath + "' is missing");
      scene.getStylesheets().add(styleAsset.toExternalForm());

      primaryStage.setScene(scene);

      this.primaryStage = primaryStage;
      primaryStage.show();
    } catch (Exception e) {
      showExceptionAndQuit(e);
    }
  }

  public void stop() {
    if (!started) return;
    stop(null);
  }

  private void stop(WindowEvent windowEvent) {
    if (primaryStage == null) { // Start method didn't complete? Exit right away
      exit(0);
    }
    if (criticalThreadsStopped) {
      // Nothing to wait for, close the app
      otherStages.forEach(Stage::close);
      primaryStage.close();
      // Critical threads have stopped, other threads don't matter, exit the app
      if (!taskExecutor.isTerminated()) {
        // Some threads are still running, most likely card terminals waitForChange methods
        exit(0);
      }
      return;
    }

    // Must stop all other threads before stopping the app
    if (windowEvent != null) windowEvent.consume(); // Prevent window close

    if (stopping) {
      // Already trying to stop. Stop forcefully?
      if (dialogHandler.confirm("Programm sulgub hetkel ohutult.\nOled kindel, et tahad programmi sunniviisiliselt sulgeda?", (String) null)) {
        exit(-1);
      }
      return;
    }

    stopping = true;
    startLoading(); // Will show loading forever since stopping = true

    taskExecutor.execute(() -> {
      try {
        personList.waitForWritingFinished(); // Writing might be delayed, wait for it
        if (personListExcelWriter != null)
          personListExcelWriter.waitForWritingFinished();
      } catch (InterruptedException e) {
        if (!stopping)
          throw new AppQuitException(e); // Unexpected interrupt
      }
      criticalThreadsStopped = true;

      taskExecutor.shutdownNow(); // Now can finally shut down

      Platform.runLater(() -> {
        // Close the app again but now all other important threads are done
        primaryStage.fireEvent(
            new WindowEvent(
                primaryStage,
                WindowEvent.WINDOW_CLOSE_REQUEST
            )
        );
      });
    });
  }


  // ############################### INIT ############################

  private Thread.UncaughtExceptionHandler createUncaughtExceptionHandler() {
    return (thread, throwable) -> showExceptionAndQuit(throwable);
  }

  private FileSystem createFileSystem() {
    return FileSystems.getDefault();
  }

  private TaskExecutor createTaskExecutor(Thread.UncaughtExceptionHandler exceptionHandler) {
    return new TaskExecutor(exceptionHandler);
  }

  private Settings createSettings(FileSystem fileSystem) throws IOException {
    return Settings.readFromJson(fileSystem.getPath("./settings.json"));
  }

  protected TerminalFactory createTerminalFactory() {
    return TerminalFactory.getDefault();
  }

  private void initSettings(Settings settings, DialogHandler dialogHandler) throws IOException, SettingsValidator.SettingsValidationException {
    SettingsValidator.validate(settings);

    settings.saveNew();

    // Don't save REGISTERED column as it can be derived from REGISTER_DATE column
    Column col = settings.getColumn(Column.Id.REGISTERED);
    if (col != null) {
      col.save = false;
    }

    // Add hidden expired date column if checking expire date is needed
    if (settings.smartCard.registerExpiredCards != Settings.Rule.ALLOW && settings.getColumn(Column.Id.EXPIRY_DATE) == null) {
      settings.columns.add(new Column(Column.Group.PERSON, Column.Id.EXPIRY_DATE));
    }

    settings.dialogHandler = dialogHandler;
  }


  private Scene initScene(Stage primaryStage) throws IOException {
    otherStages = new HashSet<>();

    progressStackPane = new StackPane();

    mainBorderPane = new BorderPane();
    progressStackPane.getChildren().add(mainBorderPane);

    loadingProgressBar = initLoadingProgressBar();

    personList = initPersonListController();
    personListHelper = new PersonListHelper(personList);

    VBox registeringPane = new VBox();
    mainBorderPane.setCenter(registeringPane);

    //Prepare
    tableViewFilterTextField = initTableViewFilterTextField();
    HBox.setHgrow(tableViewFilterTextField, Priority.ALWAYS);
    ObservableList<Registration> registrationList = createRegistrationListFromPersonList(personList.getUnmodifiableList(), tableViewFilterTextField);
    registrationTableView = initRegistrationTableView(registrationList);
    VBox.setVgrow(registrationTableView, Priority.ALWAYS);

    if (settings.general.tableContextMenu) {
      ContextMenu tableContextMenu = initTableContextMenu();
      registrationTableView.getSelectionModel().getSelectedItems().addListener((ListChangeListener<? super Registration>) c -> {
        boolean anySelected = !registrationTableView.getSelectionModel().getSelectedItems().isEmpty();
        registrationTableView.setContextMenu(anySelected ? tableContextMenu : null);
      });
    }

    //Center
    mainCardStatusPane = new CardStatusPane(settings.smartCard.statusFormat, 50, settings.smartCard.enableCardPresentIndicator);
    mainCardStatusText = mainCardStatusPane.getCardStatusText();
    registeringPane.getChildren().add(mainCardStatusPane);
    registeringPane.setAlignment(Pos.CENTER);

    //Top
    MenuBar menuBar = initMenuBar(primaryStage);
    menuBar.setUseSystemMenuBar(true);
    mainBorderPane.setTop(menuBar);

    //Bottom
    VBox tableVBox = new VBox();
    Label textFieldLabel = initFilterTextFieldLabel();
    HBox textFieldHBox = new HBox(textFieldLabel, tableViewFilterTextField);
    textFieldHBox.setAlignment(Pos.CENTER);

    BorderPane bottomBorderPane = new BorderPane();
    bottomBorderPane.setPadding(new Insets(0, 5, 0, 5));

    // Registered counts
    if (!personList.registeredCountProperties().isEmpty()) {
      VBox registeredCountVBox = new VBox();
      registeredCountVBox.setPadding(new Insets(15, 5, 15, 5));
      registeredCountVBox.setAlignment(Pos.BOTTOM_LEFT);
      Label registeredLabel = new Label("Registreeritud");
      registeredLabel.getStyleClass().add("underline");
      registeredCountVBox.getChildren().add(registeredLabel);
      bottomBorderPane.setLeft(registeredCountVBox);
      BorderPane.setAlignment(registeredCountVBox, Pos.BOTTOM_CENTER);

      GridPane registeredCountPane = new GridPane();
      registeredCountPane.getStyleClass().add("registered-count-pane");
      registeredCountVBox.getChildren().add(registeredCountPane);
      int counter = 0;
      for (Map.Entry<String, IntegerProperty> entry : personList.registeredCountProperties().entrySet()) {
        String type = entry.getKey();
        IntegerProperty countProperty = entry.getValue();
        Label labelStart = new Label(type + " ");
        Label labelCount = new Label("0/0");
        Label labelPercent = new Label("(0%)");
        ChangeListener<Number> listener = (_l, _o, _n) -> {
          Platform.runLater(() -> {
            int count = countProperty.get();
            int size = personList.size();
            long percent = size == 0 ? 0 : Math.round(((double) count / size * 100));
            labelCount.setText(count + "/" + size);
            labelPercent.setText(" (" + percent + "%)");
          });
        };
        countProperty.addListener(listener);
        personList.sizeProperty().addListener(listener);
        registeredCountPane.add(labelStart, 0, counter);
        registeredCountPane.add(labelCount, 1, counter);
        registeredCountPane.add(labelPercent, 2, counter);
        counter++;
      }
    }

    Pane statisticsContainer = initStatisticsContainer();
    if (statisticsContainer != null) {
      statisticsContainer.setPadding(new Insets(5, 5, 14, 5));
      bottomBorderPane.setCenter(statisticsContainer);
      BorderPane.setAlignment(statisticsContainer, Pos.CENTER_LEFT);
    }

    HBox allButtonsBox = new HBox();
    allButtonsBox.setPadding(new Insets(0, 0, 0, 5));
    allButtonsBox.setSpacing(10);
    allButtonsBox.setAlignment(Pos.BOTTOM_CENTER);
    bottomBorderPane.setRight(allButtonsBox);
    BorderPane.setAlignment(allButtonsBox, Pos.BOTTOM_CENTER);
    Button addRegistrationButton = initAddRegistrationButton();
    HBox addRegistrationButtonBox = new HBox();
    addRegistrationButtonBox.setAlignment(Pos.BOTTOM_CENTER);
    addRegistrationButtonBox.setPadding(new Insets(15, 0, 15, 0));
    addRegistrationButtonBox.getChildren().add(addRegistrationButton);
    allButtonsBox.getChildren().add(0, addRegistrationButtonBox);

    if (settings.general.quickRegistrationButtons != null) {
      HBox quickButtonsBox = new HBox();
      Button delReg = new Button("Tühista");
      delReg.getStyleClass().add("danger-button");
      delReg.setDisable(true);
      ObjectProperty<Registration> currentReg = new SimpleObjectProperty<>();
      ObjectProperty<ChangeListener<Boolean>> currentRegListener = new SimpleObjectProperty<>();
      registrationTableView.getSelectionModel().getSelectedItems().addListener((ListChangeListener<? super Registration>) c -> {
        if (currentReg.get() != null) {
          // Clear listener after selection changes
          currentReg.get().registeredProperty().removeListener(currentRegListener.get());
          currentReg.set(null);
          currentRegListener.set(null);
        }
        if (registrationTableView.getSelectionModel().getSelectedItems().size() == 1) {
          Registration r = registrationTableView.getSelectionModel().getSelectedItems().get(0);
          delReg.setDisable(r.isOnlyRegistration() && r.isReset());
          currentReg.set(r);
          // Listen for any registered changes no matter what
          currentRegListener.set((ob, o, n) -> delReg.setDisable(r.isOnlyRegistration() && r.isReset()));
          r.registeredProperty().addListener(currentRegListener.get());
        } else {
          delReg.setDisable(true);
        }
      });
      delReg.setOnAction(e -> {
        deleteSelectedRegistrations();
      });
      quickButtonsBox.getChildren().add(delReg);

      settings.getRegistrationTypes().forEach(type -> {
        Button regBtn = new Button("Registreeri " + type.toLowerCase());
        regBtn.setDisable(true);
        quickButtonsBox.getChildren().add(regBtn);

        ObjectProperty<ScheduledFuture<Boolean>> clearRegBtnTask = new SimpleObjectProperty<>();
        registrationTableView.getSelectionModel().getSelectedItems().addListener((ListChangeListener<? super Registration>) c -> {
          if (clearRegBtnTask.get() != null) {
            clearRegBtnTask.get().cancel(true);
            clearRegBtnTask.set(null);
          }
          if (registrationTableView.getSelectionModel().getSelectedItems().size() == 1) {
            boolean disable = false;
            if (settings.general.registerSameTypeInRow == Settings.Rule.DENY) {
              Registration r = registrationTableView.getSelectionModel().getSelectedItems().get(0);
              Person p = r.getPerson();
              Registration lr = p.getLatestRegisteredRegistration();
              disable = lr != null && lr.getRegistrationType().equals(type);
            }
            if (!disable && settings.general.registerDuringGracePeriod == Settings.Rule.DENY) {
              Registration r = registrationTableView.getSelectionModel().getSelectedItems().get(0);
              Person p = r.getPerson();
              Registration lr = p.getLatestRegisteredRegistration();
              if (lr != null) {
                Long remainingGracePeriodMillis = lr.remainingGracePeriodMillis();
                if (remainingGracePeriodMillis > 0) {
                  disable = true;
                  ObjectProperty<ScheduledFuture<Boolean>> thisClearRegBtnTask = new SimpleObjectProperty<>();
                  thisClearRegBtnTask.set(taskExecutor.schedule(() -> {
                    Platform.runLater(() -> {
                      if (thisClearRegBtnTask.get() == clearRegBtnTask.get()) {
                        clearRegBtnTask.set(null);
                        regBtn.setDisable(false);
                      }
                    });
                    return true;
                  }, lr.remainingGracePeriodMillis(), TimeUnit.MILLISECONDS));
                  clearRegBtnTask.set(thisClearRegBtnTask.get());
                }
              }
            }
            regBtn.setDisable(disable);
          } else {
            regBtn.setDisable(true);
          }
        });
        regBtn.setOnAction(e -> selectedPersonNewRegistration(type));
      });

      if (!quickButtonsBox.getChildren().isEmpty()) {
        VBox quickButtonsWithLabel = new VBox();
        quickButtonsWithLabel.setAlignment(Pos.BOTTOM_LEFT);
        quickButtonsWithLabel.setSpacing(5);
        quickButtonsWithLabel.getStyleClass().add("quick-buttons");

        quickButtonsBox.setSpacing(5);
        quickButtonsBox.setAlignment(Pos.BOTTOM_CENTER);
        quickButtonsBox.setPadding(new Insets(0, 5, 14, 0));
        quickButtonsWithLabel.getChildren().add(quickButtonsBox);

        if (settings.general.quickRegistrationButtons.showSelectedPerson) {
          quickButtonsWithLabel.setPadding(new Insets(5, 0, 0, 0));
          String noneSelectedText = "-";
          Label currentlySelectedPersonLabel = new Label(noneSelectedText);
          currentlySelectedPersonLabel.maxWidthProperty().bind(quickButtonsBox.widthProperty());
          registrationTableView.getSelectionModel().getSelectedItems().addListener((ListChangeListener<? super Registration>) c -> {
            ObservableList<Registration> items = registrationTableView.getSelectionModel().getSelectedItems();
            if (items.size() == 1) {
              Registration r = items.get(0);
              if (r != null) {
                // show label
                Person p = r.getPerson();
                currentlySelectedPersonLabel.setText(p.getDisplayInfo());
              }
            } else {
              // hide label
              currentlySelectedPersonLabel.setText(noneSelectedText);
            }
          });
          quickButtonsWithLabel.getChildren().add(0, currentlySelectedPersonLabel);
        } else {
          quickButtonsWithLabel.setPadding(new Insets(10, 0, 0, 0));
        }
        allButtonsBox.getChildren().add(0, quickButtonsWithLabel);
      }
    }

    tableVBox.getChildren().addAll(textFieldHBox, registrationTableView, bottomBorderPane);
    VBox.setVgrow(tableVBox, Priority.ALWAYS);
    registeringPane.getChildren().add(tableVBox);

    Scene scene = new Scene(progressStackPane);

    terminalsManager = initCardReader();

    if (personList.read(this::startLoading, this::stopLoading)) {
      terminalsManager.pauseRequest();
    }

    return scene;
  }

  private PersonList initPersonListController() throws IOException {
    return new PersonList(settings, fileSystem.getPath(settings.general.savePath), taskExecutor);
  }

  private ProgressBar initLoadingProgressBar() {
    ProgressBar loadingProgressBar = new ProgressBar(0.5d);
    loadingProgressBar.setPrefWidth(600);
    loadingProgressBar.setPrefHeight(75);
    return loadingProgressBar;
  }

  private Label initFilterTextFieldLabel() {
    Label label = new Label("OTSI:");
    label.setFont(Font.font("Times New Roman", 40));
    return label;
  }

  private TextField initTableViewFilterTextField() {
    TextField textField = new TextField();
    textField.textProperty().addListener((l, o, n) -> {
      textField.setText(n.toUpperCase());
    });
    textField.setPromptText("ISIKUKOOD; PEREKONNANIMI; EESNIMI");
    textField.setFont(Font.font("Times New Roman", FontWeight.BOLD, 40));
    return textField;
  }

  private TableView<Registration> initRegistrationTableView(ObservableList<Registration> registrationList) {
    TableView<Registration> table = new TableView<>();
    table.setPlaceholder(new Label("Tabel on tühi"));
    table.setEditable(true);
    table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

    if (settings.general.columnResizePolicy == Settings.ColumnResizePolicy.CONSTRAINED) {
      table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
    } else if (settings.general.columnResizePolicy == Settings.ColumnResizePolicy.UNCONSTRAINED) {
      table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
    }

    //Copy row personalCode to clipboard on selection
    table.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
      if (observable.getValue() == null || !Platform.isFxApplicationThread())
        return;
      String code = observable.getValue().getPerson().getPersonalCode();
      ClipboardContent content = new ClipboardContent();
      content.putString(code);
      Clipboard.getSystemClipboard().setContent(content);
    });

    SortedList<Registration> sortedData = new SortedList<>(registrationList);
    sortedData.comparatorProperty().bind(table.comparatorProperty());
    table.setItems(sortedData);

    // Create columns from settings
    for (Column column : settings.columns) {
      if (column.table == null || !column.hasLabel())
        continue;
      switch (column.type) {
        case DATE:
          DateColumn dateColumn = (DateColumn) column;
          TableColumn<Registration, Date> dateCol = new TableColumn<>(dateColumn.label);
          dateCol.getStyleClass().add("column-center");

          dateCol.setCellValueFactory(c -> {
            Property<?> property = c.getValue().getWithPersonProperties().get(dateColumn);
            if (property instanceof GsonDateProperty) {
              return (GsonDateProperty) property;
            }
            return null;
          });

          if (column.table.editable) {
            dateCol.setCellFactory(c -> new DateTableCell<>(settings, dateColumn.dateFormat.getFormatter()));
          } else {
            dateCol.setCellFactory(c -> new TableCell<Registration, Date>() {
              @Override
              protected void updateItem(Date item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                  setText(null);
                } else {
                  setText(dateColumn.dateFormat.format(item.getLocalDateTime()));
                }
              }
            });
          }

          table.getColumns().add(dateCol);
          break;
        case RADIO:
          TableColumn<Registration, String> radioCol = new TableColumn<>(column.label);
          radioCol.getStyleClass().add("column-center");
          radioCol.setCellValueFactory(c -> {
            Property<?> property = c.getValue().getWithPersonProperties().get(column);
            if (property instanceof StringProperty) {
              return (StringProperty) property;
            }
            return null;
          });
          if (column.table.editable && column instanceof RadioColumn) {
            RadioColumn radioColumn = (RadioColumn) column;
            ObservableList<String> values = FXCollections.observableList(radioColumn.getOptionValues());
            radioCol.setCellFactory(tc -> new ComboBoxTableCell<>(values));
          }
          table.getColumns().add(radioCol);
          break;
        case COMBOBOX:
          TableColumn<Registration, String> comboBoxCol = new TableColumn<>(column.label);
          comboBoxCol.getStyleClass().add("column-center");
          comboBoxCol.setCellValueFactory(c -> {
            Property<?> property = c.getValue().getWithPersonProperties().get(column);
            if (property instanceof StringProperty) {
              return (StringProperty) property;
            }
            return null;
          });
          if (column.table.editable && column instanceof OptionsColumn) {
            ComboBoxColumn comboBoxColumn = (ComboBoxColumn) column;
            ObservableList<String> values = FXCollections.observableList(comboBoxColumn.getOptionValues());
            if (comboBoxColumn.hasForm() && (comboBoxColumn.form.autofillPattern != null || comboBoxColumn.form.isSimpleAutofill())) {
              values.addAll(comboBoxColumn.form.autofillValues);
              comboBoxColumn.form.autofillValues.addListener((ListChangeListener<? super String>) c -> {
                while (c.next()) {
                  values.addAll(c.getAddedSubList());
                  values.removeAll(c.getRemoved());
                }
              });
            }
            comboBoxCol.setCellFactory(tc -> new ComboBoxTableCell<>(values));
          }
          table.getColumns().add(comboBoxCol);
          break;
        case TEXT:
          TableColumn<Registration, String> textCol = new TableColumn<>(column.label);
          textCol.getStyleClass().add("column-center");

          textCol.setCellValueFactory(c -> {
            Property<?> property = c.getValue().getWithPersonProperties().get(column);
            if (property instanceof StringProperty) {
              return (StringProperty) property;
            }
            return null;
          });
          if (column.table.editable) {
            textCol.setCellFactory(tc -> new TextFieldTableCell<>(new DefaultStringConverter()));
          }
          table.getColumns().add(textCol);
          break;
        case CHECKBOX:
          TableColumn<Registration, Boolean> checkboxCol = new TableColumn<>(column.label);

          checkboxCol.getStyleClass().add("column-center");
          checkboxCol.setCellValueFactory(c -> {
            Property<?> property = c.getValue().getWithPersonProperties().get(column);
            if (property instanceof BooleanProperty) {
              return (BooleanProperty) property;
            } else if (property instanceof GsonBooleanProperty) {
              return ((GsonBooleanProperty) property).getBooleanPropertyBinding();
            } else {
              return null;
            }
          });

          checkboxCol.setCellFactory(tc -> {
            CheckBoxTableCell<Registration, Boolean> cell = new CheckBoxTableCell<Registration, Boolean>();

            if (!column.table.editable) {
              cell.getStyleClass().add("check-box-readonly");
              cell.setEditable(false);
            }
            return cell;
          });

          table.getColumns().add(checkboxCol);
          break;
      }
    }

    return table;
  }

  private void initNewCardTerminalMenuItem(Stage primaryStage, String name, CardStatusPane cardStatusPane) {
    CheckMenuItem menuItem = new CheckMenuItem(name);

    Stage stage = new Stage();
    otherStages.add(stage);
    stage.setTitle(name);
    stage.setScene(new Scene(cardStatusPane, primaryStage.getWidth(), primaryStage.getHeight()));

    stage.maximizedProperty().addListener((l, o, n) -> {
      if (n)
        stage.setFullScreen(true);
    });
    stage.fullScreenProperty().addListener((l, o, n) -> {
      if (!n)
        stage.setMaximized(false);
    });
    stage.setOnHidden((e2) -> {
      menuItem.setSelected(false);
    });

    menuItem.setOnAction(e -> {
      if (menuItem.isSelected()) {
        stage.show();
      } else {
        stage.hide();
      }
    });

    cardTerminalsMenu.getItems().add(menuItem);
  }

  private TerminalsManager initCardReader() {
    Map<TerminalReader, CardStatusText> terminalStatusTexts = new HashMap<>();

    TerminalsManager terminalsManager = new TerminalsManager(settings, createTerminalFactory(), taskExecutor) {
      @Override
      public void successReader(ProcessedReader processedReader) {
        CardRecords records = processedReader.getRecords();
        ColumnProperties recordProperties = records.getColumnProperties();
        Platform.runLater(() -> {
          mainCardStatusText.add(terminalStatusTexts.get(processedReader.getReader()));
          try {
            mainCardStatusText.waitUserInput(recordProperties);

            cardRecordPropertiesNotRegistered = null;
//            String personDisplayText = records.getPersonalCode() + " " + records.getLastName() + " " + records.getFirstName();
            String personDisplayText = recordProperties.getPersonDisplayInfo();

            // Check Expiry Date
            if (settings.smartCard.registerExpiredCards == Settings.Rule.CONFIRM ||
                settings.smartCard.registerExpiredCards == Settings.Rule.DENY) {
              LocalDate expireDate = records.getExpiryDate();
              if (expireDate == null) {
                if (!dialogHandler.confirm(
                    "Ei suutnud lugeda millal ID-kaart aegub! Kas jätkan registreerimisega?", personDisplayText)) {
                  mainCardStatusText.notRegistered(recordProperties);
                  return;
                }
              } else {
                if (expireDate.compareTo(LocalDate.now()) < 0) {
                  if (settings.smartCard.registerExpiredCards == Settings.Rule.DENY ||
                      (settings.smartCard.registerExpiredCards == Settings.Rule.CONFIRM && !dialogHandler.confirm(
                          "ID-kaart on aegunud! Kas jätkan registreerimisega?", personDisplayText))) {
                    mainCardStatusText.expired(recordProperties);
                    return;
                  }
                }
              }
            }

            // Find existing person
            Person existingPerson = personList.get(records.getPersonalCode());
            if (existingPerson != null) {
              existingPerson.getProperties().merge(recordProperties);

              if (settings.smartCard.quickExistingPersonRegistration) {
                Registration latestRegisteredRegistration = existingPerson.getLatestRegisteredRegistration();
                if (latestRegisteredRegistration != null) {
                  String nextType = existingPerson.getNextRegistrationType();
                  if (latestRegisteredRegistration.getRegistrationType().equals(nextType)) {
                    // Already same type of registration
                    mainCardStatusText.alreadyRegistered(latestRegisteredRegistration.getWithPersonProperties(),
                        existingPerson.getLatestRegisteredRegistration().getRegistrationType());
                  } else {
                    // Different new registration
                    if (existingPerson.checkRegistrationGracePeriod(nextType)) {
                      existingPerson.getOrNewNextRegistration().setRegisteredNoConfirm(true);
                      mainCardStatusText.registered(existingPerson.getLatestRegisteredRegistration().getWithPersonProperties(),
                          existingPerson.getLatestRegisteredRegistration().getRegistrationType());
                    } else {
                      mainCardStatusText.alreadyRegistered(existingPerson.getLatestRegisteredRegistration().getWithPersonProperties(),
                          existingPerson.getLatestRegisteredRegistration().getRegistrationType());
                    }
                  }
                } else { // not registered at all
                  Registration r = existingPerson.getOrNewRegistration();
                  r.setRegisteredNoConfirm(true);
                  mainCardStatusText.registered(r.getWithPersonProperties(), r.getRegistrationType());
                }
              } else {
                // Show registration form
                Registration reg = personListHelper.insertRegistrationShowForm(existingPerson, existingPerson.getProperties());
                if (reg != null) {
                  mainCardStatusText.registered(reg.getWithPersonProperties(), reg.getRegistrationType());
                } else if (existingPerson.getLatestRegisteredRegistration() != null) {
                  mainCardStatusText.alreadyRegistered(existingPerson.getLatestRegisteredRegistration().getWithPersonProperties(),
                      existingPerson.getLatestRegisteredRegistration().getRegistrationType());
                } else {
                  mainCardStatusText.notRegistered(existingPerson.getProperties());
                }
              }
              focusPersonRegistration(existingPerson);
            } else {
              // New person
              if (!settings.general.insertPerson) {
                mainCardStatusText.notOnTheList(recordProperties);
              } else {
                if ((settings.smartCard.registerPersonNotInList == Settings.Rule.ALLOW || (
                    settings.smartCard.registerPersonNotInList == Settings.Rule.CONFIRM &&
                        dialogHandler.confirm("ID-kaart pole nimekirjas. Kas jätkan registreerimisega?", personDisplayText))
                )) {
                  Person newPerson = settings.smartCard.quickNewPersonRegistration ?
                      addNewPerson(recordProperties) :
                      showNewRegistrationForm(recordProperties);
                  if (newPerson != null) {
                    ColumnProperties props = newPerson.getLatestRegistration() != null ?
                        newPerson.getLatestRegistration().getWithPersonProperties() :
                        newPerson.getProperties();
                    mainCardStatusText.registered(props, newPerson.getLatestRegisteredRegistration().getRegistrationType());
                  } else {
                    mainCardStatusText.notRegistered(recordProperties);
                    cardRecordPropertiesNotRegistered = recordProperties;
                  }
                } else {
                  mainCardStatusText.notOnTheList(recordProperties);
                  cardRecordPropertiesNotRegistered = recordProperties;
                }
              }
            }
          } finally {
            mainCardStatusText.remove(terminalStatusTexts.get(processedReader.getReader()));

            // If card is being read then refresh status by triggering change listener
            if (processedReader.getReader().getStatus() == Status.READING_CARD) {
              synchronized (processedReader.getReader().statusProperty()) {
                LockableValue<Status> stat = processedReader.getReader().statusProperty();
                Status original = stat.get(true);
                stat.set(Status.NULL);
                stat.set(original);
              }
            }
            successReaderSignal();
          }
        });
      }

      @Override
      protected void failedReader(ProcessedReader reader) {
      }

      @Override
      protected TerminalReader newReader(CardTerminal newTerminal) {
        TerminalReader reader = super.newReader(newTerminal);

        CardStatusPane statusPane = new CardStatusPane(settings.smartCard.statusFormat, settings.smartCard.externalTerminalFontSize, settings.smartCard.enableCardPresentIndicator);
        statusPane.cardPresentProperty().bind(reader.cardPresentProperty());

        Platform.runLater(() -> {
          terminalStatusTexts.put(reader, statusPane.getCardStatusText());
          initNewCardTerminalMenuItem(primaryStage, reader.getCardTerminalName(), statusPane);
        });

        reader.statusProperty().addListener((observable, oldValue, status) -> {
          Platform.runLater(() -> statusPane.getCardStatusText().setStatus(status));
        });
        return reader;
      }
    };

    terminalsManager.cardPresentProperty().addListener((_obs, _old, newValue) ->
        Platform.runLater(() -> mainCardStatusPane.cardPresentProperty().set(newValue)));

    terminalsManager.statusProperty().addListener((l, o, status) -> {
      if (status != null) {
        Platform.runLater(() -> mainCardStatusText.setStatus(status));
        switch (status) {
          case WAITING_CARD_READER:
          case WAITING_CARD:
          case READING_CARD:
          case FAILED:
          case UNRESPONSIVE_CARD:
          case PROTOCOL_MISMATCH:
          case APDU_EXCEPTION:
          case DRIVER_MISSING:
          case INIT:
            cardRecordPropertiesNotRegistered = null;
        }
      }
    });

    taskExecutor.execute(terminalsManager);

    return terminalsManager;
  }

  private Button initAddRegistrationButton() {
    Button addButton = new Button("Uus registreerimine");
    addButton.setOnAction(e -> {
      showNewRegistrationForm();
    });
    return addButton;
  }

  private MenuBar initMenuBar(Stage stage) {
    MenuBar menuBar = new MenuBar();

    Menu fileMenu = new Menu("Fail");
    menuBar.getMenus().add(fileMenu);

    // Import
    MenuItem importExcel = new MenuItem("Import");
    importExcel.setOnAction(e -> showImportExcelDialog(stage));
    fileMenu.getItems().add(importExcel);

    // Eksport
    MenuItem exportExcel = new MenuItem("Eksport");
    exportExcel.setOnAction(e -> showExportExcelDialog(stage, false));
    fileMenu.getItems().add(exportExcel);

    // Eksport Group by Registration Type
    MenuItem exportExcelGroupByRegistrationType = new MenuItem("Eksport (Grupeeri registreerimise tüüp)");
    exportExcelGroupByRegistrationType.setOnAction(e -> showExportExcelDialog(stage, true));
    fileMenu.getItems().add(exportExcelGroupByRegistrationType);

    Menu personMenu = new Menu("Nimekiri");
    // Registreerimine
    MenuItem newRegistration = new MenuItem("Uus registreerimine");
    newRegistration.setOnAction(e -> {
      showNewRegistrationForm();
    });
    personMenu.getItems().add(newRegistration);

    MenuItem clearAllRegistrations = new MenuItem("Tühista kõik registreerimised");
    clearAllRegistrations.setOnAction(e -> personListHelper.deleteAllRegistrationsConfirm(() -> {
      registrationTableView.getSelectionModel().clearSelection();
    }));
    personMenu.getItems().add(clearAllRegistrations);

    if (settings.general.deletePerson) {
      // Nimekiri
      MenuItem clearPersonList = new MenuItem("Kustuta kõik isikud ja registreerimised");
//      clearPersonList.setOnAction(e -> clearPeopleAndRegistrations());
      clearPersonList.setOnAction(e -> personListHelper.deletePeopleAndRegistrationsConfirm(() -> {
        registrationTableView.getSelectionModel().clearSelection();
      }));
      personMenu.getItems().addAll(clearPersonList);
    }
    if (!personMenu.getItems().isEmpty())
      menuBar.getMenus().add(personMenu);


    // Valitud read
    Menu selectedRowsMenu = new Menu("Valitud read");

    //&& SETTINGS.general.registrationMode == Settings.Mode.MULTIPLE
    MenuItem newRegistrationType = new MenuItem("Uut tüüpi registreerimine");
    newRegistrationType.setOnAction(e -> selectedPersonNewRegistration());
    selectedRowsMenu.getItems().add(newRegistrationType);

    List<String> labelList = new ArrayList<>();
    if (settings.general.updatePerson) {
      labelList.add("isikut");
    }
    labelList.add("registreeringut");

    MenuItem updatePerson = new MenuItem("Muuda " + String.join("/", labelList));
    updatePerson.setOnAction(e -> updateSelectedPersonOrRegistration());
    selectedRowsMenu.getItems().add(updatePerson);

    if (settings.general.updatePerson) {
      MenuItem updatePersonalCode = new MenuItem("Muuda isikukoodi");
      updatePersonalCode.setOnAction(e -> updateSelectedPersonalCode());
      selectedRowsMenu.getItems().add(updatePersonalCode);
    }

    MenuItem removeSelectedRegistrations = new MenuItem("Tühista registreerimised");
    removeSelectedRegistrations.setOnAction(e -> deleteSelectedRegistrations());
    selectedRowsMenu.getItems().add(removeSelectedRegistrations);

    if (settings.general.deletePerson) {
      MenuItem deleteSelectedPeople = new MenuItem("Kustuta isikud");
      deleteSelectedPeople.setOnAction(e -> deleteSelectedPeople());
      selectedRowsMenu.getItems().add(deleteSelectedPeople);
    }

    selectedRowsMenu.setDisable(true);
    registrationTableView.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
      selectedRowsMenu.setDisable(newValue == null);
    });
    menuBar.getMenus().add(selectedRowsMenu);

    cardTerminalsMenu = new Menu("Terminali aknad");
    initNewCardTerminalMenuItem(stage, "Kõik terminalid", mainCardStatusPane.getCopy(settings.smartCard.externalTerminalFontSize));
    menuBar.getMenus().add(cardTerminalsMenu);


    // Praegused seaded
    if (settings.general.currentSettingsMenuItem) {
      Menu settingsMenu = new Menu();
      Label settingsMenuLabel = new Label("Seaded");
      settingsMenu.setGraphic(settingsMenuLabel);
      ObjectProperty<Stage> currentSettingsStage = new SimpleObjectProperty<>();
      settingsMenuLabel.setOnMouseClicked(e -> {
        if (currentSettingsStage.get() != null) {
          Stage curStage = currentSettingsStage.get();
          if (!curStage.isShowing())
            curStage.show();
          else
            curStage.hide();
        } else {
          TreeView<String> treeView = new TreeView<>();
          treeView.setRoot(TreeViewUtils.toTreeItem("settings.json", settings.toJsonTree()));
          treeView.setShowRoot(false);

          Stage settingsStage = new Stage();
          otherStages.add(settingsStage);
          settingsStage.setOnShown(e2 -> {
            double centerX = stage.getX() + stage.getWidth() / 2d;
            double centerY = stage.getY() + stage.getHeight() / 2d;
            settingsStage.setX(centerX - settingsStage.getWidth() / 2d);
            settingsStage.setY(centerY - settingsStage.getHeight() / 2d);
          });
          Scene settingsScene = new Scene(treeView);

          settingsStage.setTitle("Seaded");
          settingsStage.setScene(settingsScene);

          settingsStage.show();
          currentSettingsStage.set(settingsStage);
        }
      });
      menuBar.getMenus().add(settingsMenu);
    }

    return menuBar;
  }

  private ContextMenu initTableContextMenu() {
    ContextMenu contextMenu = new ContextMenu();

    // && SETTINGS.general.registrationMode == Settings.Mode.MULTIPLE
    MenuItem newRegistrationType = new MenuItem("Uut tüüpi registreerimine");
    newRegistrationType.setOnAction(e -> selectedPersonNewRegistration());
    contextMenu.getItems().add(newRegistrationType);

    List<String> labelList = new ArrayList<>();
    if (settings.general.updatePerson) {
      labelList.add("isikut");
    }
    labelList.add("registreeringut");

    MenuItem updatePerson = new MenuItem("Muuda " + String.join("/", labelList));
    updatePerson.setOnAction(e -> updateSelectedPersonOrRegistration());
    contextMenu.getItems().add(updatePerson);

    if (settings.general.updatePerson) {
      MenuItem updatePersonalCode = new MenuItem("Muuda isikukoodi");
      updatePersonalCode.setOnAction(e -> updateSelectedPersonalCode());
      contextMenu.getItems().add(updatePersonalCode);
    }

    MenuItem removeSelectedRegistrations = new MenuItem("Tühista registreerimised");
    removeSelectedRegistrations.setOnAction(e -> deleteSelectedRegistrations());
    contextMenu.getItems().add(removeSelectedRegistrations);

    if (settings.general.deletePerson) {
      MenuItem deleteSelectedPeople = new MenuItem("Kustuta isikud");
      deleteSelectedPeople.setOnAction(e -> deleteSelectedPeople());
      contextMenu.getItems().add(deleteSelectedPeople);
    }


    return contextMenu;
  }

  private Pane initStatisticsContainer() {
    if (settings.columns.stream().noneMatch(c -> c.statistics))
      return null;
    Map<Column, ObservableMap<String, StatisticsLabel>> statisticsMap = new HashMap<>();
    Map<String, Integer> statisticsTypeColumnIndices = new HashMap<>();
    int counter = 0;
    for (String type : settings.getRegistrationTypes()) {
      statisticsTypeColumnIndices.put(type, counter++);
    }

    FlowPane statisticsBox = new FlowPane();
    statisticsBox.setAlignment(Pos.BOTTOM_LEFT);
    statisticsBox.setHgap(6);
    statisticsBox.setVgap(6);
    statisticsBox.getStyleClass().add("statistics-pane");

    // Create initial labels based on option values, and listeners do dynamically update visuals
    settings.columns.forEach(column -> {
      if (column.statistics) {
        ObservableMap<String, StatisticsLabel> labelMap = FXCollections.observableHashMap();
        statisticsMap.put(column, labelMap);

        GridPane gridPane = new GridPane();
        gridPane.setAlignment(Pos.TOP_CENTER);
        gridPane.setHgap(3);
        Label titleLabel = new Label(column.label);
        titleLabel.getStyleClass().add("underline");
        gridPane.add(titleLabel, 0, 0);
        statisticsTypeColumnIndices.forEach((type, index) -> {
          gridPane.add(new Label(type), 0, 1 + index);
        });
        IntegerProperty paneItemCount = new SimpleIntegerProperty();

        labelMap.addListener((MapChangeListener<? super Object, ? super StatisticsLabel>) change -> {
          if (change.wasRemoved()) {
            StatisticsLabel label = change.getValueRemoved();
            Platform.runLater(() -> {
              gridPane.getChildren().remove(label.getTextLabel());
              gridPane.getChildren().removeAll(label.getCountLabels());
              paneItemCount.set(paneItemCount.get() - 1);
              label.removed();
              if (paneItemCount.get() <= 0) { // title + all types
                statisticsBox.getChildren().remove(gridPane);
              }
            });
          }
          if (change.wasAdded()) {
            StatisticsLabel label = change.getValueAdded();
            Platform.runLater(() -> {
              if (!statisticsBox.getChildren().contains(gridPane))
                statisticsBox.getChildren().add(gridPane);
              gridPane.add(label.getTextLabel(), paneItemCount.get() + 1, 0);
              for (Map.Entry<String, StatisticsLabel.LabelProperty> entry : label.getMap().entrySet()) {
                String type = entry.getKey();
                StatisticsLabel.LabelProperty labelProperty = entry.getValue();
                gridPane.add(labelProperty.getLabel(), paneItemCount.get() + 1, 1 + statisticsTypeColumnIndices.get(type));
              }
              paneItemCount.set(paneItemCount.get() + 1);
            });
          }
        });

        if (column instanceof OptionsColumn) {
          OptionsColumn options = (OptionsColumn) column;
          options.getOptionValues().forEach(labelText -> labelMap.put(labelText, new StatisticsLabel(settings.getRegistrationTypes(), labelText)));
        }
      }
    });

    // Add listeners when person is added or removed
    Map<Person, ChangeListener<String>> personRegisteredTypeListeners = new IdentityHashMap<>();
    Map<Person, Map<Property<?>, ChangeListener<Object>>> personPropertyListenersMap = new IdentityHashMap<>();
    personList.getUnmodifiableList().addListener((ListChangeListener<? super Person>) change -> {
      while (change.next()) {
        change.getAddedSubList().forEach(person -> {
          ChangeListener<String> registeredTypeListener = (_observable, oldValue, newValue) -> {
            person.getProperties().forEach((column, property) -> {
              if (!column.statistics)
                return;
              Object objValue = property.getValue();
              if (objValue == null)
                return;
              String value = objValue.toString().trim();
              if (value.isEmpty())
                return;

              ObservableMap<String, StatisticsLabel> map = statisticsMap.get(column);
              if (map != null) {
                StatisticsLabel label = map.get(value);
                if (label == null) {
                  label = new StatisticsLabel(settings.getRegistrationTypes(), value);
                  map.put(value, label);
                }
                IntegerProperty newCount = label.countProperty(newValue);
                if (newCount != null) {
                  newCount.set(newCount.get() + 1);
                }
                IntegerProperty oldCount = label.countProperty(oldValue);
                if (oldCount != null) {
                  oldCount.set(oldCount.get() - 1);
                }
              }
            });
          };
          personRegisteredTypeListeners.put(person, registeredTypeListener);
          person.registeredTypeProperty().addListener(registeredTypeListener);

          Map<Property<?>, ChangeListener<Object>> propertyListeners = new IdentityHashMap<>();
          personPropertyListenersMap.put(person, propertyListeners);
          person.getProperties().forEach((column, property) -> {
            if (column.statistics) {
              ChangeListener<Object> propertyListener = (observable, oldValue, newValue) -> {
                String registeredType = person.getRegisteredType();
                if (registeredType == null)
                  return;
                ObservableMap<String, StatisticsLabel> map = statisticsMap.get(column);
                if (map != null) {
                  String oldVal = oldValue != null ? oldValue.toString().trim() : null;
                  if (oldVal != null && !oldVal.isEmpty()) {
                    StatisticsLabel label = map.get(oldVal);
                    if (label == null) {
                      label = new StatisticsLabel(settings.getRegistrationTypes(), oldVal);
                      map.put(oldVal, label);
                    }
                    IntegerProperty count = label.countProperty(registeredType);
                    if (count != null) {
                      count.set(count.get() - 1);
                    }
                  }

                  String newVal = newValue != null ? newValue.toString().trim() : null;
                  if (newVal != null && !newVal.isEmpty()) {
                    StatisticsLabel label = map.get(newVal);
                    if (label == null) {
                      label = new StatisticsLabel(settings.getRegistrationTypes(), newVal);
                      map.put(newVal, label);
                    }
                    IntegerProperty count = label.countProperty(registeredType);
                    if (count != null) {
                      count.set(count.get() + 1);
                    }
                  }
                }
              };

              propertyListeners.put(property, propertyListener);
              property.addListener(propertyListener);
            }
          });

          // Trigger all listeners, update statistics
          propertyListeners.forEach((property, changeListener) -> {
            changeListener.changed(property, null, property.getValue());
          });
        });

        change.getRemoved().forEach(person -> {
          Map<Property<?>, ChangeListener<Object>> listeners = personPropertyListenersMap.get(person);
          listeners.forEach(ObservableValue::removeListener);
          listeners.clear();
          personPropertyListenersMap.remove(person);

          ChangeListener<String> registeredTypeListener = personRegisteredTypeListeners.get(person);
          person.registeredTypeProperty().removeListener(registeredTypeListener);
          personRegisteredTypeListeners.remove(person);
        });
      }
    });

    return statisticsBox;
  }

  // ############################### HELPERS #############################

  private void selectedPersonNewRegistration() {
    selectedPersonNewRegistration(null);
  }

  private void selectedPersonNewRegistration(String registrationType) {
    Registration reg = registrationTableView.getSelectionModel().getSelectedItem();
    if (reg == null)
      return;

    Registration newReg;
    if (registrationType != null) {
      newReg = personListHelper.insertRegistrationConfirm(reg.getPerson(), registrationType);
    } else {
      newReg = personListHelper.insertRegistrationShowForm(reg.getPerson(), reg.getWithPersonProperties());
    }
    if (newReg != null)
      focusRegistration(newReg);
  }

  private void updateSelectedPersonOrRegistration() {
    Registration reg = registrationTableView.getSelectionModel().getSelectedItem();
    if (reg == null)
      return;

    personListHelper.updatePersonOrRegistrationShowForm(reg);
  }

  private void updateSelectedPersonalCode() {
    Registration reg = registrationTableView.getSelectionModel().getSelectedItem();
    if (reg == null)
      return;

    personListHelper.updatePersonalCodeShowForm(reg);
  }

  private void deleteSelectedRegistrations() {
    List<Registration> regList = new ArrayList<>(registrationTableView.getSelectionModel().getSelectedItems());
    if (regList.isEmpty())
      return;

    Person p = null;
    if (regList.size() == 1)
      p = regList.get(0).getPerson();

    if (personListHelper.deleteRegistrationsConfirm(regList, () -> {
      // Clear selection before delete so that clipboard isn't updated after every delete
      registrationTableView.getSelectionModel().clearSelection();
    })) {
      if (p != null) {
        List<Registration> regs = p.getRegistrations();
        if (regs.size() == 1) {
          focusRegistration(regs.get(0));
        }
      }
    }
  }

  private void deleteSelectedPeople() {
    List<Person> people = registrationTableView.getSelectionModel()
        .getSelectedItems().stream()
        .flatMap(r -> Stream.of(r.getPerson())).distinct().collect(Collectors.toList());
    if (people.isEmpty())
      return;

    personListHelper.deletePeopleConfirm(people, () -> {
      // Clear selection before delete so that clipboard isn't updated after every delete
      registrationTableView.getSelectionModel().clearSelection();
    });
  }

  private ObservableList<Registration> createRegistrationListFromPersonList(ObservableList<Person> personList, TextField filterTextField) {
    ObservableList<Registration> registrationList = FXCollections.observableArrayList();
    ListChangeListener<Registration> registrationListener = c -> {
      while (c.next()) {
        // Add registration after existing one, grouping person registrations together
        c.getAddedSubList().forEach(r -> {
          Person p = r.getPerson();
          Registration r2 = p.getLatestRegistration(r);

          int index = registrationList.indexOf(r2);
          if (index != -1) {
            registrationList.add(index + 1, r);
          } else {
            registrationList.add(r);
          }
        });
//        registrationList.addAll(c.getAddedSubList());
        registrationList.removeAll(c.getRemoved());
      }
    };
    ListChangeListener<? super Person> filteredPersonListListener = c -> {
      while (c.next()) {
        c.getRemoved().forEach(p -> {
          registrationList.removeAll(p.getRegistrations());
          p.getRegistrations().removeListener(registrationListener);
        });

        c.getAddedSubList().forEach(p -> {
          registrationList.addAll(p.getRegistrations());
          p.getRegistrations().addListener(registrationListener);
        });
      }
    };

    FilteredList<Person> filteredPersonList = new FilteredList<>(personList, p -> true);
    filterTextField.textProperty().addListener((l, o, rawText) -> {
      final String text = rawText != null ? rawText.trim().toLowerCase() : "";
      Predicate<Person> filterPredicate = text.isEmpty() ? p -> true : p ->
          p.getPersonalCode().contains(text) ||
              p.getLastName().toLowerCase().contains(text) ||
              p.getFirstName().toLowerCase().contains(text);

      registrationTableView.getSelectionModel().clearSelection();
      filteredPersonList.removeListener(filteredPersonListListener);
      filteredPersonList.setPredicate(filterPredicate);
      filteredPersonList.addListener(filteredPersonListListener);
      registrationList.setAll(filteredPersonList.stream().flatMap(p -> p.getRegistrations().stream()).collect(Collectors.toList()));
    });

    filteredPersonList.addListener(filteredPersonListListener);
    registrationList.setAll(personList.stream().flatMap(person -> person.getRegistrations().stream()).collect(Collectors.toList()));
    this.personList.clearingProperty().addListener((observable, oldValue, newValue) -> {
      if (newValue) {
        // Started clearing
        filterTextField.setDisable(true);
        filteredPersonList.removeListener(filteredPersonListListener);
        personList.forEach(p -> p.getRegistrations().removeListener(registrationListener));
        registrationList.clear();
      } else {
        // Done clearing
        filterTextField.setDisable(false);
        filteredPersonList.addListener(filteredPersonListListener);
        registrationList.setAll(personList.stream().flatMap(person -> person.getRegistrations().stream()).collect(Collectors.toList()));
      }
    });

    return registrationList;
  }

  public boolean focusPersonRegistration(@NotNull Person p) {
    if (p.getLatestRegisteredRegistration() != null)
      return focusRegistration(p.getLatestRegisteredRegistration());
    else
      return focusRegistration(p.getLatestRegistration());
  }

  public boolean focusRegistration(@NotNull Registration r) {
    // Make sure registration is in table and not filtered
    if (!registrationTableView.getItems().contains(r)) {
      tableViewFilterTextField.setText("");
    }

    // Select the registration
    registrationTableView.getSelectionModel().clearSelection();
    registrationTableView.getSelectionModel().select(r);

    // Scroll only if not in view
    TableViewSkin<?> skin = (TableViewSkin<?>) registrationTableView.getSkin();
    if (skin == null || skin.getChildren().size() < 1) return false;
    VirtualFlow<?> flow = (VirtualFlow<?>) skin.getChildren().get(1);
    if (flow == null) return false;
    if (flow.getFirstVisibleCellWithinViewPort() == null ||
        flow.getLastVisibleCellWithinViewPort() == null)
      return false;
    int first = flow.getFirstVisibleCellWithinViewPort().getIndex();
    int last = flow.getLastVisibleCellWithinViewPort().getIndex();
    int idx = registrationTableView.getSelectionModel().getSelectedIndex();

    if (first > idx || idx > last) {
      registrationTableView.requestFocus();
      registrationTableView.scrollTo(r);
    }
    return true;
  }

  private Person addNewPerson(@NotNull ColumnProperties properties) {
    Person p = personListHelper.insertPerson(properties);
    if (p != null)
      focusPersonRegistration(p);
    return p;
  }

  private Person showNewRegistrationForm() {
    return showNewRegistrationForm(null);
  }

  private Person showNewRegistrationForm(ColumnProperties defaultValues) {
    RegistrationFormDialog regForm = new RegistrationFormDialog(settings);

    Platform.runLater(() -> {
      // Set first available registration type
      StringProperty registrationTypeProperty = regForm.registrationTypeProperty();
      if (registrationTypeProperty != null) {
        registrationTypeProperty.set(settings.getDefaultRegistrationType());
      }
    });

    // If card is present but person was not on the list then remember values when opening form dialog
    if (cardRecordPropertiesNotRegistered != null) {
      regForm.setValues(cardRecordPropertiesNotRegistered);
      ColumnProperties props = regForm.showAndWait().orElse(null);
      if (props == null) {
        mainCardStatusText.notRegistered(cardRecordPropertiesNotRegistered);
        return null;
      }
      Person newPerson = addNewPerson(props);
      if (newPerson != null) {
        mainCardStatusText.registered(newPerson.getProperties(), newPerson.getLatestRegisteredRegistration().getRegistrationType());
      } else {
        mainCardStatusText.notRegistered(cardRecordPropertiesNotRegistered);
      }
      return newPerson;
    } else {
      if (defaultValues != null)
        regForm.setValues(defaultValues);

      Optional<ColumnProperties> props = regForm.showAndWait();
      return props.map(this::addNewPerson).orElse(null);
    }
  }

  private void startLoading() {
    synchronized (loading) {
      if (loading.get())
        return;
      loading.set(true);
    }
    terminalsManager.pauseRequest();
    Platform.runLater(() -> {
      mainBorderPane.setDisable(true);
      progressStackPane.getChildren().add(loadingProgressBar);
      loadingProgressBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
    });
  }

  private void loadingProgress(double progress) {
    if (!loading.get())
      return;
    if (progress < 0) {
      Platform.runLater(() -> loadingProgressBar.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS));
    } else {
      Platform.runLater(() -> loadingProgressBar.setProgress(progress));
    }
  }

  private void stopLoading() {
    if (stopping) {
      // Loading can't stop once App is being stopped
      loadingProgressBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
      return;
    }
    if (!loading.get())
      return;
    Platform.runLater(() -> {
      mainBorderPane.setDisable(false);
      progressStackPane.getChildren().remove(loadingProgressBar);
    });
    terminalsManager.resumeRequest();
    loading.set(false);
  }

  private void showImportExcelDialog(Stage stage) {
    if (stopping) return;
    FileChooser fileChooser = new FileChooser();
    fileChooser.setInitialDirectory(new File("."));
    fileChooser.setTitle("Exceli faili importimine");
    FileChooser.ExtensionFilter extFilter = new FileChooser.ExtensionFilter("Microsoft Excel 2007-2013 XML (.xlsx)", "*.xlsx");
    fileChooser.getExtensionFilters().add(extFilter);
    List<File> files = fileChooser.showOpenMultipleDialog(stage);

    if (files != null) {
      List<Path> paths = files.stream().map(File::toPath).collect(Collectors.toList());
      new PersonListExcelReader(settings, taskExecutor) {

        @Override
        protected void process(Person person) {
          personList.add(person, true, true);
        }

      }.readAsync(paths, new ProgressListener() {
        @Override
        public void start() {
          startLoading();
        }

        @Override
        public void progress(double percent) {
          loadingProgress(percent);
        }

        @Override
        public void stop() {
          stopLoading();
        }
      });
    }
  }

  private void showExportExcelDialog(Stage stage, boolean groupByRegistrationType) {
    if (stopping) return;
    FileChooser fileChooser = new FileChooser();
    fileChooser.setInitialDirectory(new File("."));
    fileChooser.setTitle("Exceli failiks eksportimine" + (groupByRegistrationType ? " (Grupeeri registreerimise tüübi järgi)" : ""));
    FileChooser.ExtensionFilter extFilter = new FileChooser.ExtensionFilter("Microsoft Excel 2007-2013 XML (.xlsx)", "*.xlsx");
    fileChooser.getExtensionFilters().add(extFilter);
    File file = fileChooser.showSaveDialog(stage);

    if (file != null) {
      personListExcelWriter = new PersonListExcelWriter(settings, taskExecutor);
      personListExcelWriter.writeAsync(file.toPath(), personList.getUnmodifiableList(), groupByRegistrationType, new ProgressListener() {
        @Override
        public void start() {
          startLoading();
        }

        @Override
        public void progress(double percent) {
          loadingProgress(percent);
        }

        @Override
        public void stop() {
          stopLoading();
          personListExcelWriter = null;
        }
      });
    }
  }

  public TaskExecutor getTaskExecutor() {
    return taskExecutor;
  }

  public Settings getSettings() {
    return settings;
  }

  public FileSystem getFileSystem() {
    return fileSystem;
  }

  public PersonList getPersonList() {
    return personList;
  }

  public TableView<Registration> getRegistrationTableView() {
    return registrationTableView;
  }

  public void showExceptionAndQuit(String message) {
    showExceptionAndQuit(new Exception(message));
  }

  public void showExceptionAndQuit(Throwable exception) {
    showException(exception, true);
  }

  public void showException(String message) {
    showException(new Exception(message), false);
  }

  public void showException(Throwable exception) {
    showException(exception, false);
  }

  public void showException(Throwable exception, boolean quit) {
    runInFxThread(() -> showExceptionInAppThread(exception, quit));
  }

  public void showExceptionInAppThread(Throwable exception, boolean quit) {
    try {
      if (exception instanceof AppInfoException) {
        quit = false;
      }
      if ((exception instanceof AppInfoException ||
          exception instanceof AppQuitException) && exception.getCause() != null) {
        exception = exception.getCause();
      }

      exception.printStackTrace();
      if (settings == null || settings.general.errorLogging) {
        logException(fileSystem, exception);
      }

      if (dialogHandler != null) {
        dialogHandler.exception(quit, exception.getClass().getSimpleName(), exception.getMessage());
      }
      if (quit) {
        stop(null);
      }
    } catch (Exception e) {
      e.printStackTrace();
      exit(-1);
    }
  }

  private void runInFxThread(Runnable runnable) {
    if (Platform.isFxApplicationThread()) {
      runnable.run();
    } else {
      Platform.runLater(runnable);
    }
  }

}
