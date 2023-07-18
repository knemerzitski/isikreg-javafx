package com.knemerzitski.isikreg.smartcard;

import com.knemerzitski.isikreg.smartcard.applet.EstIdCardRecordsV2011Applet;
import com.knemerzitski.isikreg.smartcard.applet.EstIdCardRecordsV2018Applet;
import com.knemerzitski.isikreg.smartcard.records.EstIdCardRecordsV2011;
import com.knemerzitski.isikreg.smartcard.records.EstIdCardRecordsV2018;
import com.licel.jcardsim.samples.HelloWorldApplet;

public class FakeCards {

  public static class FakeCardBuilder {

    private String lastName = "Last";
    private String firstName = "First";
    private String sex = "M";
    private String citizenship = "EST";
    private String dateOfBirthYear = "1995";
    private String dateOfBirthMonth = "3";
    private String dateOfBirthDay = "18";
    private String placeOfBirth = "EST";
    private String personalCode = "3641738654";
    private String documentNr = "AA53424234";
    private String expiryDateYear = "2025";
    private String expiryDateMonth = "10";
    private String expiryDateDay = "20";
    private String dateOfIssuanceYear = "2019";
    private String dateOfIssuanceMonth = "5";
    private String dateOfIssuanceDay = "17";
    private String placeOfIssuance = "EST";
    private String typeOfResidencePermit = "Type of residence permit";
    private String notesLine1 = "Notes line 1";
    private String notesLine2 = "Notes line 2";
    private String notesLine3 = "Notes line 3";
    private String notesLine4 = "Notes line 4";
    private String notesLine5 = "Notes line 5";

    private FakeCardBuilder(){}

    public FakeTerminalSimulator.FakeCard build(){
      String[] records = new String[]{
          lastName, firstName, sex, citizenship,
          dateOfBirthDay + " " + dateOfBirthMonth + " " + dateOfBirthYear + " " + placeOfBirth,
          personalCode, documentNr,
          expiryDateDay + " " + expiryDateMonth + " " + expiryDateYear,
          dateOfIssuanceDay + " " + dateOfIssuanceMonth + " " + dateOfIssuanceYear + " " + placeOfIssuance,
          typeOfResidencePermit, notesLine1, notesLine2, notesLine3, notesLine4, notesLine5
      };
      return FakeTerminalSimulator.createCard(EstIdCardRecordsV2018.getATR(), records, EstIdCardRecordsV2018Applet.class);
    }

    public FakeTerminalSimulator.FakeCard buildV2011(){
      String[] firstNameParts = firstName.split("-");
      String[] records = new String[]{
          lastName, firstNameParts[0], firstNameParts.length > 1 ? firstNameParts[1] : "", sex, citizenship,
          dateOfBirthDay + "." + dateOfBirthMonth + "." + dateOfBirthYear,
          personalCode, documentNr,
          expiryDateDay + "." + expiryDateMonth + "." + expiryDateYear,
          placeOfBirth,
          dateOfIssuanceDay + "." + dateOfIssuanceMonth + "." + dateOfIssuanceYear,
          typeOfResidencePermit, notesLine1, notesLine2, notesLine3, notesLine4, notesLine5
      };
      return FakeTerminalSimulator.createCard(EstIdCardRecordsV2018.getATR(), records, EstIdCardRecordsV2018Applet.class);
    }

    public FakeCardBuilder lastName(String lastName) {
      this.lastName = lastName;
      return this;
    }

    public FakeCardBuilder firstName(String firstName) {
      this.firstName = firstName;
      return this;
    }

    public FakeCardBuilder sex(String sex) {
      this.sex = sex;
      return this;
    }

    public FakeCardBuilder citizenship(String citizenship) {
      this.citizenship = citizenship;
      return this;
    }

    public FakeCardBuilder dateOfBirthYear(String dateOfBirthYear) {
      this.dateOfBirthYear = dateOfBirthYear;
      return this;
    }

    public FakeCardBuilder dateOfBirthMonth(String dateOfBirthMonth) {
      this.dateOfBirthMonth = dateOfBirthMonth;
      return this;
    }

    public FakeCardBuilder dateOfBirthDay(String dateOfBirthDay) {
      this.dateOfBirthDay = dateOfBirthDay;
      return this;
    }

    public FakeCardBuilder placeOfBirth(String placeOfBirth) {
      this.placeOfBirth = placeOfBirth;
      return this;
    }

    public FakeCardBuilder personalCode(String personalCode) {
      this.personalCode = personalCode;
      return this;
    }

    public FakeCardBuilder documentNr(String documentNr) {
      this.documentNr = documentNr;
      return this;
    }

    public FakeCardBuilder expiryDateYear(String expiryDateYear) {
      this.expiryDateYear = expiryDateYear;
      return this;
    }

    public FakeCardBuilder expiryDateMonth(String expiryDateMonth) {
      this.expiryDateMonth = expiryDateMonth;
      return this;
    }

    public FakeCardBuilder expiryDateDay(String expiryDateDay) {
      this.expiryDateDay = expiryDateDay;
      return this;
    }

    public FakeCardBuilder dateOfIssuanceYear(String dateOfIssuanceYear) {
      this.dateOfIssuanceYear = dateOfIssuanceYear;
      return this;
    }

    public FakeCardBuilder dateOfIssuanceMonth(String dateOfIssuanceMonth) {
      this.dateOfIssuanceMonth = dateOfIssuanceMonth;
      return this;
    }

    public FakeCardBuilder dateOfIssuanceDay(String dateOfIssuanceDay) {
      this.dateOfIssuanceDay = dateOfIssuanceDay;
      return this;
    }

    public FakeCardBuilder placeOfIssuance(String placeOfIssuance) {
      this.placeOfIssuance = placeOfIssuance;
      return this;
    }

    public FakeCardBuilder typeOfResidencePermit(String typeOfResidencePermit) {
      this.typeOfResidencePermit = typeOfResidencePermit;
      return this;
    }

    public FakeCardBuilder notesLine1(String notesLine1) {
      this.notesLine1 = notesLine1;
      return this;
    }

    public FakeCardBuilder notesLine2(String notesLine2) {
      this.notesLine2 = notesLine2;
      return this;
    }

    public FakeCardBuilder notesLine3(String notesLine3) {
      this.notesLine3 = notesLine3;
      return this;
    }

    public FakeCardBuilder notesLine4(String notesLine4) {
      this.notesLine4 = notesLine4;
      return this;
    }

    public FakeCardBuilder notesLine5(String notesLine5) {
      this.notesLine5 = notesLine5;
      return this;
    }
  }

  public static FakeCardBuilder builder(){
    return new FakeCardBuilder();
  }

  public static FakeTerminalSimulator.FakeCard createEstIdCardV2011(String... records) {
    return FakeTerminalSimulator.createCard(EstIdCardRecordsV2011.getATR(), records, EstIdCardRecordsV2011Applet.class);
  }

  public static FakeTerminalSimulator.FakeCard createEstIdCardV2011Basic(String personalCode, String lastName, String firstName) {
    String[] firstNameParts = firstName.split("-");
    String[] records = new String[]{
        lastName, firstNameParts[0], firstNameParts.length > 1 ? firstNameParts[1] : "", "M", "EST", "1.3.1995",
        personalCode,
        "AA53424234", "20.10.2025", "EST", "17.5.2019", "Type of residence permit",
        "Notes Line 1", "Notes Line 2", "Notes Line 3", "Notes Line 4"
    };
    return FakeTerminalSimulator.createCard(EstIdCardRecordsV2018.getATR(), records, EstIdCardRecordsV2018Applet.class);
  }

  public static FakeTerminalSimulator.FakeCard createEstIdCard(String... records) {
    return FakeTerminalSimulator.createCard(EstIdCardRecordsV2011.getATR(), records, EstIdCardRecordsV2011Applet.class);
  }

  public static FakeTerminalSimulator.FakeCard createEstIdCardBasic(String personalCode, String lastName, String firstName) {
    String[] records = new String[]{
        lastName, firstName, "M", "EST", "1 3 1995 EST",
        personalCode,
        "AA53424234", "20 10 2025", "17 5 2019 EST", "Type of residence permit",
        "Notes Line 1", "Notes Line 2", "Notes Line 3", "Notes Line 4", "Notes Line 5"
    };
    return FakeTerminalSimulator.createCard(EstIdCardRecordsV2018.getATR(), records, EstIdCardRecordsV2018Applet.class);
  }

  public static FakeTerminalSimulator.FakeCard createCardWithBadATR(String personalCode, String lastName, String firstName) {
    String[] records = new String[]{
        lastName, firstName, "M", "EST", "1 3 1995 EST",
        personalCode,
        "AA53424234", "20 10 2025", "17 5 2019 EST", "Type of residence permit",
        "Notes Line 1", "Notes Line 2", "Notes Line 3", "Notes Line 4", "Notes Line 5"
    };
    return FakeTerminalSimulator.createCard(new byte[]{
        0x05, (byte) 0x0A, (byte) 0x18, 0x49, (byte)
        0x32, (byte) 0x07, (byte) 0x11, (byte) 0xFE,
    }, records, EstIdCardRecordsV2011Applet.class);
  }

  public static FakeTerminalSimulator.FakeCard createUnknownCard() {
    return FakeTerminalSimulator.createCard(new byte[]{
        0x05, (byte) 0x0A, (byte) 0x18, 0x49, (byte)
        0x32, (byte) 0x07, (byte) 0x11, (byte) 0xFE,
    }, new String[0], HelloWorldApplet.class);
  }

  private FakeCards() {
  }
}
