package com.knemerzitski.isikreg.smartcard.records;

import com.knemerzitski.isikreg.settings.Settings;
import com.knemerzitski.isikreg.settings.columns.Column;
import com.knemerzitski.isikreg.smartcard.APDUException;

import javax.smartcardio.CardChannel;
import javax.smartcardio.CardException;
import javax.smartcardio.CommandAPDU;
import javax.smartcardio.ResponseAPDU;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Source TD-ID1-Chip-App: https://installer.id.ee/media/id2019/TD-ID1-Chip-App.pdf
 */
public class EstIdCardRecordsV2018 extends CardRecords {

  //private static final byte[] ATR_PROTOCOL_BYTES = {0x3B, (byte)0x8B, (byte)0x80, 0x01, 0x00, 0x12, 0x23, 0x3F, 0x53, 0x65, 0x49, 0x44, 0x0F, (byte)0x90, 0x00, (byte)0xA0};
  private static final byte[] ATR = {0x3B, (byte) 0xDB, (byte) 0x96, 0x00, (byte) 0x80, (byte) 0xB1, (byte) 0xFE, 0x45, 0x1F, (byte) 0x83, 0x00, 0x12, 0x23, 0x3F, 0x53, 0x65, 0x49, 0x44, 0x0F, (byte) 0x90, 0x00, (byte) 0xF1};
  private static final byte[] ATR_PROTOCOL = Arrays.copyOf(ATR, 10);
  // ATR 3B DB 96 00 80 B1 FE 45 1F 83 00 12 23 3F 53 65 49 44 0F 90 00 F1
  // Historical Bytes 00 12 23 3F 53 65 49 44 0F 90 00

  //Select Main AID
  private static final CommandAPDU SELECT_FILE_AID = new CommandAPDU(new byte[]{0x00, (byte) 0xA4, 0x04, 0x00, 0x10, (byte) 0xA0, 0x00, 0x00, 0x00, 0x77, 0x01, 0x08, 0x00, 0x07, 0x00, 0x00, (byte) 0xFE, 0x00, 0x00, 0x01, 0x00});
  // Personal Data transparent files DF ID 5000hex
  private static final CommandAPDU SELECT_FILE_5000 = new CommandAPDU(new byte[]{0x00, (byte) 0xA4, 0x01, 0x0C, 0x02, 0x50, 0x00});
  private static final CommandAPDU READ_BYTES = new CommandAPDU(new byte[]{0x00, (byte) 0xB0, 0x00, 0x00, 0x00});

  public static byte[] getATR() {
    return ATR_PROTOCOL;
  }

  public static boolean isValidProtocol(byte[] atrBytes) {
    if (atrBytes.length < 10)
      return false;
    return Arrays.equals(Arrays.copyOf(atrBytes, 10), ATR_PROTOCOL);
  }

  // Card response status
  private static final int ResponseOK = 0x9000;

  private static final Charset CARD_ENCODING = StandardCharsets.UTF_8;

  private static final Pattern datePlacePattern = Pattern.compile("(.{2}\\s.{2}\\s.{4})\\s?(.*)");
  //  private static final String dateFormat = "dd MM yyyy";
//  private static final DateFormat onlyDateFormat = new SimpleDateFormat(dateFormat);
//  private static final DateFormat endOfDayDateFormat = new EndOfDayDateFormat(dateFormat);
  private static final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd MM yyyy");

  // Records
  //| |-- PD1 (Surname)
  //| |-- PD2 (First Name)
  //| |-- PD3 (Sex)
  //| |-- PD4 (Citizenship ISO3166 alpha-3)
  //| |-- PD5 (Date and place of birth)
  //| |-- PD6 (Personal Identification Code)
  //| |-- PD7 (Document Number)
  //| |-- PD8 (Expiry Date)
  //| |-- PD9 (Date and place of Issuance)
  //| |-- PD10 (Type of residence permit)
  //| |-- PD11 (Notes Line 1)
  //| |-- PD12 (Notes Line 2)
  //| |-- PD13 (Notes Line 3)
  //| |-- PD14 (Notes Line 4)
  //| |-- PD15 (Notes Line 5)
  public static Map<Column, Supplier<Record<?>>> createRecordsFactory(Settings settings) {
    Map<Column.Id, Supplier<Record<?>>> records = new HashMap<>();
    records.put(Column.Id.LAST_NAME, () -> new PlainRecord((byte) 1)); // Xn
    records.put(Column.Id.FIRST_NAME, () -> new PlainRecord((byte) 2)); // Xn
    records.put(Column.Id.SEX, () -> new PlainRecord((byte) 3)); // X?
    records.put(Column.Id.CITIZENSHIP, () -> new PlainRecord((byte) 4)); // XXX?
    records.put(Column.Id.DATE_OF_BIRTH, () -> new DatePlaceRecordReader(datePlacePattern, dateFormatter, (byte) 5).getDateRecord()); // DD MM YYYY XXX?
    records.put(Column.Id.PLACE_OF_BIRTH, () -> new DatePlaceRecordReader(datePlacePattern, dateFormatter, (byte) 5).getPlaceRecord()); // DD MM YYYY XXX?
    records.put(Column.Id.PERSONAL_CODE, () -> new PlainRecord((byte) 6)); // 99999999999
    records.put(Column.Id.DOCUMENT_NR, () -> new PlainRecord((byte) 7)); // XX9999999
    records.put(Column.Id.EXPIRY_DATE, () -> new DateRecord(dateFormatter, (byte) 8)); // DD MM YYYY
    records.put(Column.Id.DATE_OF_ISSUANCE, () -> new DatePlaceRecordReader(datePlacePattern, dateFormatter, (byte) 9).getDateRecord()); // DD MM YYYY XXX?
    records.put(Column.Id.PLACE_OF_ISSUANCE, () -> new DatePlaceRecordReader(datePlacePattern, dateFormatter, (byte) 9).getDateRecord()); // DD MM YYYY XXX?
    records.put(Column.Id.TYPE_OF_RESIDENCE_PERMIT, () -> new PlainRecord((byte) 10)); // Xn?
    records.put(Column.Id.NOTES_LINE1, () -> new PlainRecord((byte) 11)); // Xn?
    records.put(Column.Id.NOTES_LINE2, () -> new PlainRecord((byte) 12)); // Xn?
    records.put(Column.Id.NOTES_LINE3, () -> new PlainRecord((byte) 13)); // Xn?
    records.put(Column.Id.NOTES_LINE4, () -> new PlainRecord((byte) 14)); // Xn?
    records.put(Column.Id.NOTES_LINE5, () -> new PlainRecord((byte) 15)); // Xn?

    // Add only records that are defined in settings
    Map<Column, Supplier<Record<?>>> recordsFactory = new HashMap<>();
    for (Map.Entry<Column.Id, Supplier<Record<?>>> record : records.entrySet()) {
      Column column = settings.getColumn(record.getKey());
      if (column != null) {
        recordsFactory.put(column, record.getValue());
      }
    }

    return recordsFactory;
  }


  // Read data from the card using given channel
  public EstIdCardRecordsV2018(Settings settings, Map<Column, Supplier<Record<?>>> recordsFactory, CardChannel channel) throws CardException, APDUException, UnsupportedEncodingException {
    super(settings, recordsFactory, channel);
  }

  // Send a command APDU to the card.
  private byte[] sendCommand(CardChannel channel, CommandAPDU cmd) throws CardException, APDUException {
    ResponseAPDU r = channel.transmit(cmd);
    int status = r.getSW();
    if (status != ResponseOK) {
      // Bad chip or unexpected format
      throw new APDUException(status, "Card response not OK " + status);
    }
    return r.getData();
  }

  @Override
  protected void beforeReadRecords(CardChannel channel) throws CardException, APDUException {
    // Navigate to personal data file
    sendCommand(channel, SELECT_FILE_AID);
    sendCommand(channel, SELECT_FILE_5000);
  }

  @Override
  protected byte[] readRecord(CardChannel channel, byte recordNumber) throws CardException, APDUException {
    // Select Transparent EF 500x
    sendCommand(channel, new CommandAPDU(new byte[]{0x00, (byte) 0xA4, 0x01, 0x0C, 0x02, 0x50, recordNumber}));
    // Read Binary
    return sendCommand(channel, READ_BYTES);
  }

  @Override
  protected Charset getCardEncoding() {
    return CARD_ENCODING;
  }

}
