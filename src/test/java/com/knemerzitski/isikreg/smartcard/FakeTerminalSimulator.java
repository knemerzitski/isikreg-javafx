package com.knemerzitski.isikreg.smartcard;

import com.licel.jcardsim.samples.HelloWorldApplet;
import com.licel.jcardsim.smartcardio.CardSimulator;
import com.licel.jcardsim.smartcardio.CardTerminalSimulator;
import com.licel.jcardsim.utils.AIDUtil;
import javacard.framework.AID;
import javacard.framework.Applet;

import javax.smartcardio.*;
import java.io.OutputStream;
import java.io.PrintStream;
import java.security.NoSuchAlgorithmException;
import java.security.Security;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class FakeTerminalSimulator {

  static {
    if (Security.getProvider("CardTerminalSimulator") == null) {
      CardTerminalSimulator.SecurityProvider provider = new CardTerminalSimulator.SecurityProvider();
      Security.addProvider(provider);



      // Ignore the static init errors
      PrintStream voidStream = new PrintStream(new OutputStream() {
        @Override
        public void write(int b) {
        }
      });
      PrintStream err = System.err;
      PrintStream out = System.out;
      System.setErr(voidStream);
      System.setOut(voidStream);
      try {
        CardSimulator simulator = new CardSimulator();
        AID appletAID = AIDUtil.create("F000000001");
        simulator.installApplet(appletAID, HelloWorldApplet.class);
        CardTerminal terminal = CardTerminalSimulator.terminal(simulator);
        Card card = terminal.connect("T=1");
        CardChannel channel = card.getBasicChannel();
        CommandAPDU selectCommand = new CommandAPDU(AIDUtil.select(appletAID));
        channel.transmit(selectCommand);
        CommandAPDU commandAPDU = new CommandAPDU(0x00, 0x01, 0x00, 0x00);
        simulator.transmitCommand(commandAPDU);
      } catch (CardException e) {
        e.printStackTrace();
      } finally {
        System.setErr(err);
        System.setOut(out);
      }
    }
  }

  public static class FakeCard {

    private final CardSimulator cardSimulator;
    private final AID appletAID;
    private final String[] records;

    FakeCard(CardSimulator cardSimulator, String[] records, AID appletAID) {
      this.cardSimulator = cardSimulator;
      this.appletAID = appletAID;
      this.records = records;
    }

    public String[] getRecords() {
      return records;
    }

    public boolean isAssigned() {
      return cardSimulator.getAssignedCardTerminal() != null;
    }

    public void insert(CardTerminal terminal) {
      cardSimulator.assignToTerminal(terminal);
      cardSimulator.selectApplet(appletAID);
    }

    public void eject() {
      cardSimulator.assignToTerminal(null);
    }
  }

  private static class CardSimulatorCustomATR extends CardSimulator {

    private final byte[] atr;


    public CardSimulatorCustomATR(byte[] atr) {
      this.atr = atr;
    }

    @Override
    public byte[] getATR() {
      return atr;
    }
  }

  public static FakeCard createCard(byte[] atr, String[] records, Class<? extends Applet> applet) {
    CardSimulator cardSimulator = new CardSimulatorCustomATR(atr);

    AID appletAID = AIDUtil.create("F000000001");
    cardSimulator.installApplet(appletAID, applet);

    cardSimulator.selectApplet(appletAID);

    cardSimulator.transmitCommand(new CommandAPDU(new byte[]{0x00, (byte) 0xA4, 0x02, 0x00, (byte) records.length}));
    for (String record : records) {
      byte[] bytes = record.getBytes();
      cardSimulator.transmitCommand(new CommandAPDU(0x00, (byte) 0xA4, 0x02, 0x00, bytes, 0, bytes.length));
    }

    return new FakeCard(cardSimulator, records, appletAID);
  }

  public static TerminalFactory createTerminalFactory() throws NoSuchAlgorithmException {
    return TerminalFactory.getInstance("CardTerminalSimulator", null);
  }

  public static TerminalFactory createTerminalFactory(String... terminalNames) throws NoSuchAlgorithmException {
    return TerminalFactory.getInstance("CardTerminalSimulator", terminalNames);
  }

  private final TerminalFactory terminalFactory;

  public FakeTerminalSimulator(String... terminalNames) throws NoSuchAlgorithmException {
    System.out.println("Creating fake terminals: " + Arrays.stream(terminalNames).map(t -> "'" + t + "'").collect(Collectors.toList()));
    terminalFactory = createTerminalFactory(terminalNames);
  }

  public FakeTerminalSimulator(List<String> terminalNames) throws NoSuchAlgorithmException {
    this(terminalNames.toArray(new String[0]));
  }

  public FakeTerminalSimulator(int terminalCount) throws NoSuchAlgorithmException {
    this(IntStream.range(0, terminalCount).mapToObj(val -> ("Terminal " + val)).collect(Collectors.toList()));
  }

  public TerminalFactory getTerminalFactory() {
    return terminalFactory;
  }

  public CardTerminal getTerminal(String name) {
    return terminalFactory.terminals().getTerminal(name);
  }

  public CardTerminal getTerminal() throws CardException {
    return getTerminal(0);
  }

  public CardTerminal getTerminal(int index) throws CardException {
    List<CardTerminal> terminals = terminalFactory.terminals().list();
    if (terminals.size() <= index)
      throw new NullPointerException("Terminal at index " + index + " doesn't exist");
    return terminals.get(index);
  }

  public int getTerminalIndex(CardTerminal terminal) throws CardException {
    return terminalFactory.terminals().list().indexOf(terminal);
  }

}
