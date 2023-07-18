# Versiooni ajalugu

## [4.2]

### Lisatud
- Nupud "Tühista", "Registreeri sisse" ja "Registreeri välja" on keelatud, kui vastav tegevus on seadetega keelatud (registerDuringGracePeriod ja registerSameTypeInRow).
- Registreeringu muutmisel on nüüd näha tiitlis registreerimise aega.

### Muudetud
- Registreeringu muutmisel saab nüüd muuta registreerimsie tüüpi. Tüübi muutmine on nüüd lihtsam.
- Seaded ümber nimetatud: permissions.insertPerson -> general.insertPerson, permissions.updatePerson -> general.updatePerson, permissions.deletePerson -> general.deletePerson.
- general.saveDelay peab nüüd olema positiivne. 0 väärtuse korral on jõudlus kohutav.

### Eemaldatud
- Eemaldatud seaded: permission.insertRegistration, permission.updateRegistation, permission.deleteRegistration. Need seaded tegid programmi seadistamise ebavajalikult keerulisemaks.
- Seade kategooria permissions eemaldatud.

### Parandatud vead
- Parandatud viga, kus valitud rea isiku kustutamine ei salvestunud.
- Parandatud viga, kus aegunud kaarti sai ikka registreerida kui reegel oli DENY.
- Programm sulgeb ohutult oodates kuni kirjutamisega seotud lõimed on lõpetaunud.
- Programmi käivitumisel ei näidatud laadimist salvestufaili lugemisel.
- Parandatud viga, kus muudetava tabelivälja puhul sai määrata tühja või juba kasutuses oleva isikukoodi.
- Parandatud viga, kus tabeli kuupäeva muutmisel teatud juhul kalender enam ei avanenud.
- Combobox form autofill mustriga ei uuenenud, kui väärtust muudeti olemasoleva rea puhul. Kui veerg grupeering registreering, siis ei uuenend üldse.
- Seadete täpsem valideerimine, vigade vältimiseks.


## [4.1]

### Lisatud
- Uus seade 'general.registerSameTypeInRow'. Sama tüüpi registreerimine järjest lubamine. Vaikimisi "ALLOW".
- Uus seade 'excel.exportAutoSizeColumns'.  Exceli eksportimisel veeru suuruse arvutamine vastavalt sisule. Vaikimisi 'true'.
- Uus seade 'general.warnDuplicateRegistrationDate'. Kas näidata hoiatust, kui isikul on kaks erinevat registreerimist samal ajal. Vaikimisi 'true'.
- Uus seade 'general.currentSettingsMenuItem'. Menüü nupu näitamine, millega saab näha seadete hetkeseisu. Vaikimisi 'false'.
- Uus seade 'general.defaultRegistrationType'. Vaikimisi registreerimistüüp, kui registreerimistüüpe on rohkem kui üks. Vaikimisi 'null'.
- Uus seade 'smartCard.enableCardPresentIndicator'. Saab määrata, kas näidata staatusteksti juures ikooni, kui kaart on lugejas. Vaikimisi 'true'.

### Muudetud
- Optimeerisin programmi. Filtreerimine/otsing ja nimekirja kustutamine kiirem suure tabeli korral.
- Isiku registreerimisel täidetakse alati kõigepealt tühjad read enne kui uut luuakse (tüüp pole oluline).
- Tabeli rea fookusesse viimisel toimub kerimine ainult siis, kui rida pole nähtaval.
- Isiku viimase registreerimise tühistamisel enam ei kustutata ja lisata uus registreerimine vaid resetitakse olemasolev viimane registreerimise rida. Enam ei lähe isiku viimase registeerimise tühistamisel see tabeli lõppu.
- Uus registreerimine listakse koheselt isiku teiste registreerimise juurde, mitte tabeli lõppu.
- Seade 'general.quickRegistrationButtons' muudetud objektiks ja lisaseadistus 'general.quickRegistrationButtons.showSelectedPerson'. Vaikimisi 'true'.
- Programmi aken on nüüd avamisel vertikaalselt natuke suurem.
- Excel import loading ui on täpsem.
- Failide charset on nüüd alati UTF-8: settings.json, isikreg.json.sync.
- statusFormat teise struktuuriga. Näiteks ${FIRST_NAME} => {FIRST_NAME}. $ eemaldatud.
- All vasakul "Registreeritud" arvu näitamine on nüüd eraldi vastavalt registreerimistüübi järgi.
- Veeru statistika näitamine on nüüd tabelina vastavalt registreerimistüübi järgi. Seadetes on nüüd 'statistics' vaid boolean väärtus.
- Väli võib olla nüüd ka null. Kui väli on null ja isikule lisatakse uus registreerimise tüüp vormis, siis null välja väärtuseid saab muuta. Exceli eksportimisel null väljad jäetakse tühjaks.
- Exceli eksportimisel ei muudeta teksti numbriks.
- COMBOBOX autofill=true korral täidetakse see automaatselt olemasolevate väärtustega. Automaatset väärtuse valikut ei ole.
- Salvestusfail eemaldatud on faililaiendus ".sync".

### Eemaldatud
- Seade 'tableFilterAsyncCount' eemaldatud. Pole vaja, sest filtreerimine on nüüd kiire.
- Seade 'general.registrationMode' on eemaldatud. Oli üleliigne ja tekitas segadust, mis registreerimisi sai teha.
- Seade 'general.statisticsLayout' on eemaldatud. Paigutus on nüüd fikseeritud tabelina.

### Parandatud vead
- Kui CHECKBOX veerg oli salvestatud ja seejärel seadetes eemaldatud, siis ei suutunud programm salvestusfaili avada.
- Mäluleke parandatud.
- Vahepeal ei tuvastanud programm, et ID-kaart on juba eemaldatud.
- Exceli importimisel ei pruukinud registreeritute arv, registreerimise tüüp ja registreereimise aeg õigesti loetud olla olenevalt Exceli faili veergude järjestusest.
- Parandatud andmete muutmine otse tabelis vead: kuupäev, raadio, tekst.
- Exceli eksportimisel grupeerides registreerimise tüüp parandatud. Nüüd pannakse kõik veerud grupis registreering eraldi veerule.
- Parandatud viga, kus COMBOBOX väärtust ei täidetud olemasoleva isiku väärtusega.
- Parandatud viga, kus kaardi/lugeja ootamatul eemaldamisel andmete töötlemisel ei olnud staatuse näitamine järjekindel.
- Exceli faili eksportimisel enam ei sulgu programm, kui eksportimine ei õnnestu, sest Exceli fail on juba kasutuses.
- Importimisel, kui kellaaeg oli täpselt 0, siis loeti see ainult kuupäevaks ja aja näitemisel tekkis tõrge.
- Exceli ekspordi ajamuster oli vale.
- Teatud juhul ei suutnud importida exceli faili null lahtritega.


## [4.0]

### Lisatud
- Exceli faili eksportimine on liigitatud kaheks:
  1) "Eksport": uus ekspordi viis, kus tabel eksporditakse täpselt nii nagu see on programmis näha.
  2) "Eksport (grupeeri registreerimise tüüp)": vana "Eksport" ümber nimetatud.
- Nimekirja saab lisada/muuta/kustutada igal viisil. Selleks on üleval menüünupud, tabeli kontekstimenüü ja all paremal nupud.
- All paremal nupud hetkel valitud registreerimise kiirtoimiminguteks: "Tühista", "Registreeri sisse", "Registreeri välja".
- Menüü "Nimekiri" => "Uus registreerimine" on sama, mis all paremal nupp "Uus registreerimine".
- Menüü "Valitud read" on sama, mis tabeli peal parema klõpsuga avades konteksti menüü. Sellega saab teha toimingud valitud rea või ridadega.
- Sujuv font, mis on seadetes reguleeritav.
- Uued muudetavad seaded. Täpsem seadete kirjeldus on seadete dokumentatsioonis.
  general: { savePath, saveDelay, saveCompressedZip, errorLogging, smoothFont, registrationMode,
    registerDuringGracePeriod, registerGracePeriod, tableFilterAsyncCount, tableContextMenu,
    quickRegistrationButtons, columnResizePolicy, statisticsLayout },
  permissions: { insertRegistration, updateRegistration, deleteRegistration, insertPerson, updatePerson, deletePerson }
  excel: { sheetName, exportDateTimeFormat, exportDateFormat },
  smartCard: { statusFormat, showSuccessStatusDuration, externalTerminalFontSize, registerExpiredCards,
    registerPersonNotInList, quickNewPersonRegistration, quickExistingPersonRegistration, waitBeforeReadingCard
    cardReadingFailedRetryInterval, cardReadingAttemptsUntilGiveUp, noReadersCheckInterval,
    readerMissingCheckInterval, readersPresentCheckInterval }
  columns: { id, group, type, label, form, table, merge, statistics }
- Dünaamilised veerud, mida saab defineerida seadetes. Toetatud veergude tüübid: 'text', 'checkbox', 'date', 'combobox', 'radio'.
  Combobox korral on võimalus valikuid lisada dünaamiliselt (mõeldud grupeerimise jaoks).
- Veeru olulised eeldefineeritud id: REGISTERED, REGISTRATION_TYPE, REGISTER_DATE, PERSONAL_CODE
- Veeru eeldefineeritud id, mida programm loeb ID-kaardit ja täidab automaatselt: LAST_NAME, FIRST_NAME, SEX
  CITIZENSHIP, DATE_OF_BIRTH, PLACE_OF_BIRTH, PERSONAL_CODE, DOCUMENT_NR, EXPIRY_DATE, DATE_OF_ISSUANCE, PLACE_OF_ISSUANCE,
  TYPE_OF_RESIDENCE_PERMIT, NOTES_LINE1, NOTES_LINE2, NOTES_LINE3, NOTES_LINE4 ja NOTES_LINE5.
- Iga veeru puhul saab programm kuvada statistikat. Statistika loeb kokku registreeritud isikud, kellel on veerus vastav väärtus.
- Registreerimisel on 3 erinevat režiimi: sisse, välja või mõlemad. Vastavalt režiimile on lubatud vaid määratud tüüpi registreerimine.
  Mõlema puhul on lubatud mitmekordne sisse/välja registreerimine.
- Registreerimise režiimi "Mõlemad" korral on puhkeaeg, mil programm saab hoiatada uuesti registreerimisest. Mõeldud vältimaks kogemata topeltregistreerimine.
- Kui sama registreerimine on topelt, siis ülearune eemaldatakse. Põhiliselt käib see ridade kohta, millel pole linnuke "Registreeritud" sees. Kui on
  kaks tühja "Sisse" registreerimise rida, siis ülearune kustutatakse.

### Muudetud
- Menüü "Import" ja "Eksport" on tõstetud eraldi Menüü alla "Fail" => "Import", "Eksport", "Eksport (grupeeri registreerimise tüüp)".
- Exceli faili saab importida igal kujul. Oluline on, et veeru silt oleks sama exceli veeru päisega.
- ID-kaardi lugeja ehk terminali staatust saab nüüd näha iga lugeja puhul eraldi avades akna menüüst "Terminali aknad".
- All paremal nupp "Registreeri uus isik" on ümber nimetatud "Uus registreerimine".
- Kõik seaded on nüüd JSON formaadis failis 'settings.json'.
- Vanad seaded asendatud "DEREGISTER" => general.registrationMode ja "INSERT_ENABLED" => smartCard.registerPersonNotInList.
- Aegunud ID-kaartide aktsepteerimine on nüüd reguleeritav seadetes.
- Uute isikute registreerimist saab muuta nüüd ainult seadete failis.
- Tabeli tekst 'No content in table' asendatud tekstiga 'Tabel on tühi'.
- Menüü "Aknad" => "ID-Kaardi tagasiside aken" on ümber nimetatud "Terminali aknad" => "Kõik terminalid".
- Menüü "Nimekiri" => "Tühista registreerimised" on ümber nimetatud "Nimekiri" => "Tühista kõik registreerimised".
- Menüü "Nimekiri" => "Kustuta" on ümber nimetatud "Nimekiri" => "Kustuta kõik isikud ja registreerimised".

### Eemaldatud
- Aruanded programmis ja exceli ekspordis.
- Seadeid otse programmi töötamisel enam muuta ei saa. Tuleb muuta seadeid failis 'settings.json'.

### Parandatud vead
- Katkine kaart jäi andmeid lugema. Loodetavasti nüüd katkise ID-kaardi puhul programm teavitab, et ei suutnud andmeid lugeda.
- Programm ei käivitunud, kui lugeja polnud kunagi arvutisse sisestatud. Kui lugejat pole kunagi arvutisse sisestatud, siis annab programm teada, et sisesta lugeja ja restardi programm.
- Kõik uued aknad avanevad programmi keskel, mitte peamonitori keskel.


## [3.0]

### Lisatud
- Töötab uue eID kiipkaardiga. Vastab dokumentatsioonile https://installer.id.ee/media/id2019/TD-ID1-Chip-App.pdf


## [2.0]

### Muudetud
- Uus seadistus: sisse või välja registreerimine (vaikimisi sisse).
- ID Kaardi teksti aknas on eristatav sisse ja välja registreerimine (nt Sisse registreeritud; On juba välja registreeritud).
- Isiku andmemudelis on registreerimise kuupäeva väli asendatud kahe uue väljaga: sisse ja välja registreerimine. Vana registreerimise väli on nüüd vaid visuaalne tabelis näitamiseks.
- Isiku anmemudelis on uus väli: Kutse.
- Isiku andmemudelis registreerimise märkimisel uuendatakse visuaalset registreerimise aeganing vastavalt seadistusele uuendatakse sisse või välja registreerimise aega.
- Isiku anmemudelis on sisse ja välja registreerimise oleku muutmiseks meetod.
- Nimekirja vaates tabeli rea valimisel kopeeritakse vastava rea isikukood lõikepuhvrisse (clipboard) ning isikukoodi saab kleepida lühendiga Ctrl+V.
- Tabelis veerg Registreerimise Aeg näitab nüüd kas tegemist on sisse või välja registreerimisega.
- Tabelisse on lisatud uus veerg: Kutse.
- Impordil ja ekspordil on uus formaat: "Jrk.nr", "Auaste", "Eesnimi", "Perekonnanimi", "Isikukood",  "Sisse registreerimise aeg", "Välja registreerimise aeg", "RA Teenistuskoht", "Struktuurüksus", "Allüksus", "Kategooria", "Kutse", "Märkused".
- Tabelite ühendamisel sisse ja registreerimise väljad ühendatakse eraldi vastavalt varasemale väärtusele.
- Aruannetes näitab nüüd grupeeritult Kutse järgi.
- Exceli ekspordil on kõik aruanded kajastatud.


## [1.1]

### Muudetud
- Näitab isiku üksust sulgudes peale nime kui isik registreeritakse või on juba registreeritud.
- Aruannetes on uus tabel kus grupeeritakse isikud kategooria järgi.
- Käistsi registreerimise üksuste nimekirja saab muuta. Programmi käivitamisel tekib fail "divisions.txt", kus on olemas vaikeväärtused. Kui seda faili muuta ja programm uuesti käivitada, siis kajastuvad muutused programmis.


## [1.0]
- Esialgne versioon