package com.knemerzitski.isikreg.smartcard.applet;

import com.knemerzitski.isikreg.utils.ArrayTestUtils;
import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.ISO7816;
import javacard.framework.ISOException;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;


public class EstIdCardRecordsV2011Applet extends Applet {

  private static final byte[] INSERT_RECORD = new byte[]{0x00, (byte) 0xA4, 0x02, 0x00};
  private static final byte[] SELECT_FILE_MF = new byte[]{0x00, (byte) 0xa4, 0x00, 0x0c};
  private static final byte[] SELECT_FILE_EEEE = new byte[]{0x00, (byte) 0xa4, 0x01, 0x0c, 0x02, (byte) 0xee, (byte) 0xee};
  private static final byte[] SELECT_FILE_5044 = new byte[]{0x00, (byte) 0xa4, 0x02, 0x04, 0x02, 0x50, 0x44};
  private static final byte[] READ_RECORD = new byte[]{0x00, (byte) 0xb2, 0x04, 0x00};

  private static final Charset RECORDS_ENCODING = Charset.forName("Windows-1252");

  /**
   * This method is called once during applet instantiation process.
   *
   * @param bArray  the array containing installation parameters
   * @param bOffset the starting offset in bArray
   * @param bLength the length in bytes of the parameter data in bArray
   * @throws ISOException if the install method failed
   */
  public static void install(byte[] bArray, short bOffset, byte bLength)
      throws ISOException {
    new EstIdCardRecordsV2011Applet();
  }

  private enum State {
    EMPTY,
    INSERT_RECORD,
    READY,
    SELECT_FILE_MF,
    SELECT_FILE_EEEE,
    SELECT_FILE_5044,
  }

  private List<String> records;
  private int insertRecordsRemaining;

  private State state = State.EMPTY;

  protected EstIdCardRecordsV2011Applet() {
    register();
  }

  /**
   * This method is called each time the applet receives APDU.
   */
  public void process(APDU apdu) {
    if (selectingApplet()) return;

    byte[] buffer = apdu.getBuffer();

    switch (state) {
      case EMPTY:
        if (ArrayTestUtils.startsWith(buffer, INSERT_RECORD)) {
          state = State.INSERT_RECORD;
          insertRecordsRemaining = buffer[INSERT_RECORD.length];
          records = new ArrayList<>();
          return;
        }
        break;
      case INSERT_RECORD:
        String record = new String(buffer, 5, buffer.length - 5);
        records.add(record);
        insertRecordsRemaining--;
        if (insertRecordsRemaining <= 0) {
          state = State.READY;
        }
        return;
      case READY:
        if (ArrayTestUtils.startsWith(buffer, SELECT_FILE_MF)) {
          state = State.SELECT_FILE_MF;
          return;
        }
        break;
      case SELECT_FILE_MF:
        if (ArrayTestUtils.startsWith(buffer, SELECT_FILE_EEEE)) {
          state = State.SELECT_FILE_EEEE;
          return;
        }
        break;
      case SELECT_FILE_EEEE:
        if (ArrayTestUtils.startsWith(buffer, SELECT_FILE_5044)) {
          state = State.SELECT_FILE_5044;
          return;
        }
        break;
      case SELECT_FILE_5044:
        if (ArrayTestUtils.equals(READ_RECORD, 0, buffer, 0, 2) &&
            ArrayTestUtils.equals(READ_RECORD, 2, buffer, 3, 2)) {
          int selectedRecordNumber = buffer[2];
          if (selectedRecordNumber > 0 && selectedRecordNumber < records.size()) {
            record = records.get(selectedRecordNumber - 1);
            sayString(apdu, record);
            return;
          }
        } else if (ArrayTestUtils.startsWith(buffer, SELECT_FILE_MF)) {
          // Reading again using same applet
          state = State.SELECT_FILE_MF;
          return;
        }
        break;
    }

    ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
  }

  private void sayString(APDU apdu, String str) {
    byte[] msg = str.getBytes(RECORDS_ENCODING);
    apdu.setOutgoing();
    apdu.setOutgoingLength((short) msg.length);
    apdu.sendBytesLong(msg, (short) 0, (short) msg.length);
  }


}