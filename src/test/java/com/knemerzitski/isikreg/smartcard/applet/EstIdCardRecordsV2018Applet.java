package com.knemerzitski.isikreg.smartcard.applet;

import com.knemerzitski.isikreg.utils.ArrayTestUtils;
import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.ISO7816;
import javacard.framework.ISOException;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;


public class EstIdCardRecordsV2018Applet extends Applet {

  private static final byte[] INSERT_RECORD = new byte[]{0x00, (byte) 0xA4, 0x02, 0x00};
  private static final byte[] SELECT_FILE_AID = new byte[]{0x00, (byte) 0xA4, 0x04, 0x00, 0x10, (byte) 0xA0, 0x00, 0x00, 0x00, 0x77, 0x01, 0x08, 0x00, 0x07, 0x00, 0x00, (byte) 0xFE, 0x00, 0x00, 0x01, 0x00};
  private static final byte[] SELECT_FILE_5000 = new byte[]{0x00, (byte) 0xA4, 0x01, 0x0C, 0x02, 0x50, 0x00};
  private static final byte[] READ_BYTES = new byte[]{0x00, (byte) 0xB0, 0x00, 0x00, 0x00};
  private static final byte[] SELECT_RECORD = new byte[]{0x00, (byte) 0xA4, 0x01, 0x0C, 0x02, 0x50};

  private static final Charset RECORDS_ENCODING = StandardCharsets.UTF_8;

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
    new EstIdCardRecordsV2018Applet();
  }

  private enum State {
    EMPTY,
    INSERT_RECORD,
    READY,
    SELECT_FILE_AID,
    SELECT_FILE_5000,
    SELECT_RECORD,
  }

  private List<String> records;
  private int insertRecordsRemaining;

  private byte selectedRecordNumber;
  private State state = State.EMPTY;

  protected EstIdCardRecordsV2018Applet() {
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
        if (ArrayTestUtils.startsWith(buffer, SELECT_FILE_AID)) {
          state = State.SELECT_FILE_AID;
          return;
        }
        break;
      case SELECT_FILE_AID:
        if (ArrayTestUtils.startsWith(buffer, SELECT_FILE_5000)) {
          state = State.SELECT_FILE_5000;
          return;
        }
        break;
      case SELECT_FILE_5000:
        if (ArrayTestUtils.startsWith(buffer, SELECT_RECORD) &&
            buffer.length > SELECT_RECORD.length) {
          state = State.SELECT_RECORD;
          selectedRecordNumber = buffer[SELECT_RECORD.length];
          return;
        } else if (ArrayTestUtils.startsWith(buffer, SELECT_FILE_AID)) {
          // Reading again using same applet
          state = State.SELECT_FILE_AID;
          return;
        }
        break;
      case SELECT_RECORD:
        if (ArrayTestUtils.startsWith(buffer, READ_BYTES) &&
            selectedRecordNumber > 0 &&
            selectedRecordNumber < records.size()) {
          state = State.SELECT_FILE_5000;
          record = records.get(selectedRecordNumber - 1);
          sayString(apdu, record);
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