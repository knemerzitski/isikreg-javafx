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
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Code was written using the following website as a guide: https://eid.eesti.ee/index.php/Creating_new_eID_applications#Direct_communication_with_the_card
 * Source: https://www.id.ee/public/EstEID_kaardi_kasutusjuhend.pdf
 */
public class EstIdCardRecordsV2011 extends CardRecords {

  // ATR 3B FA 18 00 00 80 31 FE 45 FE 65 49 44 20 2F 20 50 4B 49 03
  // Historical ATR FE 65 49 44 20 2F 20 50 4B 49

  private static final byte[] ATR = {
      0x3B, (byte) 0xFA, (byte) 0x18, 0x00, (byte)
      0x00, (byte) 0x80, (byte) 0x31, (byte) 0xFE,
      0x45, (byte) 0xFE, 0x65, 0x49,
      0x44, 0x20, 0x2F, 0x20,
      0x50, 0x4B, 0x49, (byte) 0x03};
  private static final byte[] ATR_PROTOCOL = Arrays.copyOf(ATR, 10);

  public static byte[] getATR() {
    return ATR_PROTOCOL;
  }

  // The command to choose root folder
  private static final CommandAPDU SELECT_FILE_MF = new CommandAPDU(new byte[]{0x00, (byte) 0xa4, 0x00, 0x0c});
  // The command to choose folder EEEE which contains personal data
  private static final CommandAPDU SELECT_FILE_EEEE = new CommandAPDU(new byte[]{0x00, (byte) 0xa4, 0x01, 0x0c, 0x02, (byte) 0xee, (byte) 0xee});
  // The command to choose file 5044.
  private static final CommandAPDU SELECT_FILE_5044 = new CommandAPDU(new byte[]{0x00, (byte) 0xa4, 0x02, 0x04, 0x02, 0x50, 0x44});

  // Card response status
  private static final int ResponseOK = 0x9000;

  private static final Charset CARD_ENCODING = Charset.forName("Windows-1252");

  //  private static final String dateFormat = "dd.MM.yyyy";
  private static final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");
//  private static final DateFormat onlyDateFormat = new SimpleDateFormat(dateFormat);

  // Records
  //    1 Perenimi 28 Xn
  //    2 Eesnimede rida 1 15 Xn
  //    3 Eesnimede rida 2 15 Xn
  //    4 Sugu 1 X?
  //    5 Kodakondsus (3 tähte, alati EST) 3 XXX?
  //    6 Sünnikuupäev (pp.kk.aaaa) 10 DD.MM.YYYY
  //    7 Isikukood 11 99999999999
  //    8 Dokumendi number 8 XX999999
  //    9 Kehtivuse viimane päev (pp.kk.aaaa) 10 DD.MM.YYYY
  //    10 Sünnikoht 35 XXX
  //    11 Väljaandmise kuupäev (pp.kk.aaaa) 10 DD.MM.YYYY
  //    12 Elamisloa tüüp 50 Xn?
  //    13 Märkuste rida 1 50 Xn?
  //    14 Märkuste rida 2 50 Xn?
  //    15 Märkuste rida 3 50 Xn?
  //    16 Märkuste rida 4 50 Xn?
  public static Map<Column, Supplier<Record<?>>> createRecordsFactory(Settings settings) {
    Map<Column.Id, Supplier<Record<?>>> records = new HashMap<>();
    records.put(Column.Id.LAST_NAME, () -> new PlainRecord((byte) 1)); // Xn
    records.put(Column.Id.FIRST_NAME, () -> new SplitFirstNameRecord(new Byte[]{(byte) 2, (byte) 3})); // Xn
    records.put(Column.Id.SEX, () -> new PlainRecord((byte) 4)); // X?
    records.put(Column.Id.CITIZENSHIP, () -> new PlainRecord((byte) 5)); // XXX?
    records.put(Column.Id.DATE_OF_BIRTH, () -> new DateRecord(dateFormatter, (byte) 6)); // DD MM YYYY XXX?
    records.put(Column.Id.PERSONAL_CODE, () -> new PlainRecord((byte) 7)); // 99999999999
    records.put(Column.Id.DOCUMENT_NR, () -> new PlainRecord((byte) 8)); // XX9999999
    records.put(Column.Id.EXPIRY_DATE, () -> new DateRecord(dateFormatter, (byte) 9)); // DD MM YYYY
    records.put(Column.Id.PLACE_OF_BIRTH, () -> new PlainRecord((byte) 10)); // DD MM YYYY XXX?
    records.put(Column.Id.DATE_OF_ISSUANCE, () -> new DateRecord(dateFormatter, (byte) 11)); // DD MM YYYY XXX?
    records.put(Column.Id.TYPE_OF_RESIDENCE_PERMIT, () -> new PlainRecord((byte) 12)); // Xn?
    records.put(Column.Id.NOTES_LINE1, () -> new PlainRecord((byte) 13)); // Xn?
    records.put(Column.Id.NOTES_LINE2, () -> new PlainRecord((byte) 14)); // Xn?
    records.put(Column.Id.NOTES_LINE3, () -> new PlainRecord((byte) 15)); // Xn?
    records.put(Column.Id.NOTES_LINE4, () -> new PlainRecord((byte) 16)); // Xn?

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
  public EstIdCardRecordsV2011(Settings settings, Map<Column, Supplier<Record<?>>> recordsFactory, CardChannel channel) throws CardException, APDUException, UnsupportedEncodingException {
    super(settings, recordsFactory, channel);
  }

  // Send a command APDU to the card.
  private byte[] sendCommand(CardChannel channel, CommandAPDU cmd) throws CardException, APDUException {
    ResponseAPDU r = channel.transmit(cmd);
    int status = r.getSW();
    if (status != ResponseOK) {
      throw new APDUException(status, "Card response not OK " + status);
    }
    return r.getData();
  }

  @Override
  protected void beforeReadRecords(CardChannel channel) throws APDUException, CardException {
    // Navigate to personal data file
    sendCommand(channel, SELECT_FILE_MF);
    sendCommand(channel, SELECT_FILE_EEEE);
    sendCommand(channel, SELECT_FILE_5044);
  }

  @Override
  protected byte[] readRecord(CardChannel channel, byte recordNumber) throws CardException, APDUException {
    return sendCommand(channel, new CommandAPDU(new byte[]{0x00, (byte) 0xb2, recordNumber, 0x04, 0x00}));
  }

  @Override
  public Charset getCardEncoding() {
    return CARD_ENCODING;
  }


}
