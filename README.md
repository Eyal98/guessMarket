# Guess Market — תרגיל 1

מימוש כאפליקציית Console בסביבת Java 25.

מערכת מסחר באירועים בינאריים, בהשראת Polymarket. משתמשים קונים מניות של תוצאה שהם מאמינים בה,
המחיר נקבע בשיטת LMSR, ובסגירת האירוע משולם דולר לכל מניה זוכה.

---

## בונוסים שמומשו

מומש **בונוס שמירה וטעינה של מצב המערכת (5 נקודות)** במלואו.

- פקודה 6 שומרת את מלוא מצב המערכת לקובץ, כולל כל הסטוריית המסחר ויתרות החשבונות.
- פקודה 7 טוענת מצב שנשמר, במקום מה שטעון כרגע. הפקודה זמינה גם לפני שנטען קובץ XML כלשהו.
- המשתמש נוקב נתיב מלא ושם קובץ ללא סיומת; המערכת מוסיפה בעצמה את הסיומת `.gm`.

---

## פרטי המגיש

| פרט | ערך |
|---|---|
| שם מלא | אייל אופק |
| דוא"ל | eyal.offek@gmail.com |
| אופן הגשה | יחיד |
| מאגר קוד | https://github.com/Eyal98/guessMarket |

> תעודת הזהות מופיעה בקובץ ה‑readme המוגש בלבד, ולא כאן — אין סיבה לפרסם מספר תעודת זהות במאגר ציבורי.

---

## הוראות הרצה

### דרישת סביבה

המערכת נכתבה, קומפלה ונבדקה על **Java 25** בלבד (JDK 25.0.4), על מערכת Windows.
יש לוודא שהפקודה `java` ניתנת להרצה משורת הפקודה.

### הרצה

```
run.bat
```

או, מתוך אותה תיקייה:

```
java -jar guess-market.jar
```

**חשוב:** שני ה‑jar חייבים לשבת באותה תיקייה. הקובץ `guess-market.jar` מצביע על `engine.jar`
ב‑`Class-Path` שלו, ולכן הפרדתם תמנע את עליית התוכנית.

`run.bat` בודק מראש אם Java מותקנת ואם שני ה‑jar נמצאים במקום, ומציג הודעה מובנת במקום לקרוס
עם חריגה טכנית.

### בנייה מהמקור

```
build.bat
```

מקמפל את שני המודולים ויוצר את `build\engine.jar` ואת `build\guess-market.jar`.
משתמש ב‑`javac` וב‑`jar` מה‑JDK בלבד — אין תלות ב‑Maven, ב‑Gradle או בכל כלי חיצוני אחר.

### הרצת הבדיקות

```
test.bat
```

דורש את `junit-platform-console-standalone.jar` בתיקיית `tools/` (אינו נכלל במאגר).

---

## תפריט הפקודות

| # | פקודה | הערות |
|---|---|---|
| 1 | Load an events file | זמינה תמיד. מבקשת נתיב מלא לקובץ XML. |
| 2 | Show all events | מציגה את כל האירועים הטעונים. |
| 3 | Show the market state of an event | מצב המסחר, החשבונות והסטוריית אירוע אחד. |
| 4 | Participate in an event | קניית מניות. מוצגים רק אירועים פעילים. |
| 5 | Close an event | הכרעת אירוע ותשלום למנצחים. |
| 6 | Save the system to a file | בונוס. שמירת מצב המערכת. |
| 7 | Load a saved system from a file | בונוס. טעינת מצב שנשמר. זמינה תמיד. |
| 8 | Exit | יציאה מהמערכת. |

פקודות 2 עד 6 דורשות שיהיה קובץ תקין טעון. אם אין — מוצגת הודעה המסבירה זאת ומפנה לפקודה 1,
והמערכת ממשיכה לעבוד כרגיל.

בכל מקום שבו בוחרים מתוך רשימה, הקלדת **0** מבטלת ומחזירה לתפריט ללא שדבר נעשה.
בבקשת נתיב קובץ, לחיצת Enter על שורה ריקה מבטלת.

---

## מבנה המערכת — שני מודולים

**`engine`** — מנוע המערכת. מודול פסיבי לגמרי: הוא עונה לפקודות ומחזיר נתונים, ואינו יודע מי פונה
אליו. אין בו אף קריאה ל‑`System.out` ואף קריאת קלט.

**`ui`** — ממשק ה‑console. המודול האקטיבי שמניע את המערכת: מחזיק את לולאת התפריט, אוסף קלט ומדפיס פלט.

כל ההדפסות בתוכנית מרוכזות במחלקה אחת בלבד, `ConsolePrinter`, וכל קריאת הקלט במחלקה אחת בלבד,
`ConsoleReader`.

המנוע נחשף לעולם דרך ממשק אחד, `GuessMarketEngine`, ומחזיר אך ורק אובייקטי נתונים (DTO) בלתי משתנים.
המופע הקונקרטי נוצר במקום אחד יחיד בכל המערכת — `ConsoleApp.main()`. כך המנוע יוכל לשמש בהמשך גם
ממשק JavaFX או שרת, בלי לשנות בו שורה.

כל המספרים העוברים בממשק המנוע ספורים מ‑1, כך שהממשק מעביר בדיוק את מה שהמשתמש בחר, והמרת בסיס
הספירה קורית במקום אחד.

### המרת DTO לעומק

אף מחלקה בחבילת ה‑DTO אינה מפנה למודל. ההמרה יורדת עד הסוף, כך שאף קורא אינו יכול להגיע לאובייקט
של המנוע דרך DTO שקיבל. גם חישוב אין בהם: סכומים ודגלים נישאים כערכים, וכך כל record נשאר מכל
שקוראים ממנו בלבד.

---

## חישוב LMSR

המימוש עוקב במדויק אחר נספח א' של התרגיל:

```
C(q)      = b * ln( sum of e^(qi/b) )
value(i)  = e^(qi/b) / sum of e^(qj/b)
buy cost  = C(q after the purchase) - C(q before it)
subsidy   = C(0,0) = b * ln(2)
```

**יציבות נומרית:** שתי הנוסחאות מחושבות עם חיסור המעריך הגדול ביותר (log-sum-exp). בלי זה, קנייה
גדולה ביחס ל‑`b` מבריחה את `Math.exp` לאינסוף וכל המספרים הופכים ל‑NaN. עם התיקון, גם קנייה של
מיליון מניות על `b=100` עובדת כראוי.

המימוש נבדק מול הדוגמה המספרית שבנספח א':

| נתון | ערך במערכת |
|---|---|
| סבסוד התחלתי, b=100 | 69.31 |
| עלות קניית 100 מניות Yes | 62.01 |
| ערך Yes לאחר הקנייה | 0.73 |
| ערך No לאחר הקנייה | 0.27 |

---

## הנחות והחלטות מימוש

כל מקום שבו התרגיל לא קבע במדויק, מופיעה כאן הבחירה שנעשתה והנימוק לה.

1. **גרסת Java** — במסמך התרגיל קיימת סתירה: בהנחיות הכלליות לכתיבת התרגיל נאמר Java 21, ובהנחיות
   למימוש Guess Market (סעיף 7) נאמר Java 25. נבחרה גרסה 25, שהיא ההנחייה הספציפית לפרויקט הזה.

2. **תשלום בסגירת אירוע** — כל מניה של האפשרות הזוכה שווה 1.00, ומניה מפסידה שווה 0, כפי שנקבע
   בנספח א'.

3. **עמלת on-purchase** — מתווספת מעל למחיר המניות: הקונה משלם את עלות המניות ועוד האחוז מעליה,
   ושני הסכומים נכנסים לחשבון האירוע. הפלט מפרט את שני המרכיבים בנפרד.

4. **עמלת on-close — פרשנות שנבחרה** — התרגיל מבקש "לנקות מסכום ההשקעה הכולל באפשרות הזוכה את אחוז
   העמלה". ניתן לקרוא זאת בשתי דרכים: הכסף ששולם בפועל על המניות הזוכות, או הסכום שמגיע למנצחים.
   נבחרה הקריאה השנייה: סך הזכייה הוא 1.00 לכל מניה זוכה, ממנו נגרע אחוז העמלה לטובת חשבון האירוע,
   והיתרה מתחלקת למנצחים לפי כמות המניות שרכשו. הנימוק: נספח א' קובע מפורשות שב‑LMSR משולם דולר
   לכל מניה זוכה.

5. **יתרת חשבון האירוע בסגירה** — לאחר תשלום למנצחים, מה שנותר בחשבון האירוע חוזר לחשבון מנהל
   האירועים (MM) וחשבון האירוע מתאפס. זהו בדיוק מה שמתואר בנספח א': הכסף העודף חוזר לכיסו של מגדיר
   האירוע ומקטין בפועל את הסבסוד ששילם.

6. **חשבון ה‑MM במינוס** — בטעינת קובץ תקין חשבון מנהל האירועים מתאפס, וממנו משולם הסבסוד של כל
   אירוע. לכן היתרה שלו שלילית, ומשמעותה "כמה השקיע ה‑MM עד כה". זה מצב תקין ולא שגיאה.

7. **משתמש יחיד ללא יתרה** — בתרגיל זה קיים משתמש אחד בלבד, ולסכמה אין עדיין אלמנט `initial-cash`.
   לכן למשתמש אין יתרה ואין מסגרת אשראי, וקנייה לא נדחית אי פעם מחוסר כסף. מחלקת `Account` נבנתה
   כבר כך שתשמש גם לחשבונות משתמשים בתרגיל הבא.

8. **מספור האירועים** — לכל אירוע מספר יציב לפי מקומו בקובץ, החל מ‑1, והוא זהה בכל הפקודות. לכן
   רשימת האירועים הפעילים (פקודות 4 ו‑5) מציגה רק את המספרים של האירועים שנותרו פתוחים, והשאלה
   מציגה במפורש את המספרים החוקיים. הבחירה נעשתה כדי שאותו אירוע לא יקבל מספרים שונים בתפריטים
   שונים. אפשרויות האירוע ממוספרות תמיד 1 ו‑2.

9. **שמות אלמנטים ב‑XML** — חיפוש שמות אלמנטים ומאפיינים מתבצע בלי להבדיל בין אות גדולה לקטנה. זו
   החמרה מכוונת שלא עולה דבר ומונעת כשל טעינה שכל סיבתו היא רישום אותיות. ערכי האלמנטים עצמם
   נשמרים כמו שהם, פרט למאפיין `type` של העמלה, שהוא case insensitive לפי הגדרת התרגיל.

10. **רווחים בערכים** — רווחים בתחילת ערך ובסופו מוסרים (`trim`), ורווחים בתוך מחרוזת נשמרים.

11. **מספר האפשרויות** — נדרשות בדיוק שתי אפשרויות לכל אירוע, והן חייבות להיות שונות זו מזו. שתי
    אפשרויות זהות בשמן היו הופכות את הבחירה לחסרת משמעות.

12. **שיטת מסחר שאינה נתמכת** — אירוע שמוגדר עם `GM-order-book` נדחה עם הודעה מפורשת שהגרסה הנוכחית
    תומכת רק ב‑LMSR, ולא בכשל סתום.

13. **הצגת מספרים** — כל מספר עשרוני מוצג עם שתי ספרות אחרי הנקודה, והעיגול מתבצע לתצוגה בלבד —
    החישובים עצמם נשמרים בדיוק מלא כדי ששגיאות עיגול לא ייצברו. הפורמט מוגדר מפורשות עם
    `Locale.US`, כדי שעל מערכת בעלת locale עברי לא יודפס פסיק במקום נקודה עשרונית. סכום הקטן מחצי
    אגורה מוצג כ‑`0.00` ולא עם סימן מינוס.

14. **סיומת קובץ השמירה** — המערכת מוסיפה בעצמה את הסיומת `.gm` לנתיב שהמשתמש נוקב, כפי שהבונוס
    מבקש. נתיב שכבר מכיל את הסיומת מתקבל כמו שהוא ולא מקבל סיומת שנייה.

15. **מנגנון השמירה** — ממומש ב‑Java Serialization. המשמעות היא שקובץ שנשמר בגרסה אחת של המערכת לא
    ייקרא בגרסה אחרת; במקרה כזה מוצגת הודעה מסבירה ולא חריגה.

16. **צבעים וניקוי מסך** — לפי דרישת התרגיל: אין שימוש בצבעים ואין ניקוי מסך בין פקודה לפקודה. כל
    הפלט ב‑ASCII פשוט ובאנגלית בלבד. לא נעשה שימוש באף ספריית צד שלישי — המערכת נשענת על ה‑JDK
    בלבד, ובפרט קריאת ה‑XML ממומשת עם DOM המובנה ב‑JDK.

17. **סיום קלט** — אם הקלט נגמר באמצע (למשל בהרצה מתוך קובץ פקודות), המערכת מודיעה על כך ונסגרת
    בסדר במקום לקרוס.

---

## בדיקת תקינות קלט

המערכת לא קורסת ולא מדפיסה חריגות בשום מצב. ברמת התפריט, קלט שאינו מספר או שאינו בטווח מוביל
להודעה ספציפית ולבקשה חוזרת, ולא ליציאה מהפקודה.

בבדיקת קובץ ה‑XML, תקלות שמונעות מקריאה בכלל — קובץ שאינו קיים, סיומת שגויה, תוכן שאינו XML תקין —
עוצרות מיד. כל שאר הבדיקות נאספות: כל אירוע נבדק במלואו, וכל התקלות מדווחות בדוח אחד ממוספר. כך
ניסיון טעינה אחד מגלה את כל מה שפגום בקובץ, ולא תקלה אחת בכל ניסיון.

הבדיקות המתבצעות: קיום הקובץ וניתנותו לקריאה; סיומת `.xml`; תקינות ה‑XML; אלמנט שורש נכון; קיום כל
האלמנטים והמאפיינים החובה; `id` שלם וייחודי לכל אירוע; עמלה שלמה בטווח 0 עד 90; סוג עמלה חוקי;
בדיוק שתי אפשרויות שונות ולא ריקות; שיטת מסחר נתמכת; ו‑`b` שלם וחיובי.

הודעות השגיאה מצביעות על האירוע לפי מספרו ושמו, ואומרות מה בדיוק פגום ומה היה אמור להיות. לדוגמה:

```
The file was not loaded. 5 problems were found:
  1. Event #1 ("Commission Out Of Range"): its commission is 150, but a
     commission must be between 0 and 90.
  2. Event #2 ("Too Many Options"): it has 3 options, but every event must
     have exactly 2.
  3. Event #3: it has no name attribute.
  4. Event #3: its <GM-LMSR> element has no <b> value. The liquidity index
     must be a positive whole number.
  5. Event #4 ("Id Already Taken"): its id is 2, which is already used by
     Event #2 ("Too Many Options"). Every event must have an id of its own.
  Nothing has changed: the system is still using the 3 events loaded earlier.
```

טעינה של קובץ תקול אינה פוגעת במידע שכבר טעון. מבנית, המצב החדש נבנה עד סופו לצד הקיים, ומחליף
אותו רק אם לא נמצאה בו אף תקלה.

---

## קבצי הבדיקה

בתיקיית `test-files` מצורפים קבצי XML שנועדו להקל על הבדיקה. כל קובץ תקול מכיל תקלה אחת בלבד, כדי
שניתן לראות את ההודעה המתאימה לה בנפרד. בראש כל קובץ הערה המסבירה מה התקלה בו.

| קובץ | מה הוא בודק |
|---|---|
| `events-basic.xml` | שלושה אירועים תקינים, שתי שיטות גבייה, שלושה ערכי b, מזהים לא עוקבים |
| `events-single.xml` | אירוע בודד, עמלה 0, ורווחים מיותרים שצריכים להיעלם |
| `events-large-b.xml` | שוק נזיל מאוד (b=10000) לצד שוק תזזיתי (b=1) |
| `with spaces/` | קובץ תקין בתיקייה ובשם קובץ שמכילים רווחים |
| `bad-duplicate-ids.xml` | שני אירועים עם אותו id |
| `bad-commission-too-high.xml` | עמלה 95 |
| `bad-commission-negative.xml` | עמלה שלילית |
| `bad-commission-type.xml` | סוג עמלה שאינו קיים |
| `bad-three-options.xml` | שלוש אפשרויות |
| `bad-one-option.xml` | אפשרות אחת |
| `bad-liquidity-zero.xml` | b שווה 0 |
| `bad-liquidity-text.xml` | b שאינו מספר |
| `bad-missing-description.xml` | אלמנט חובה חסר |
| `bad-order-book.xml` | שיטת מסחר שאינה נתמכת בתרגיל זה |
| `bad-malformed.xml` | תוכן שאינו XML תקין |
| `bad-wrong-root.xml` | XML תקין שאינו מתאר Guess Market |
| `bad-many-problems.xml` | חמש תקלות בבת אחת, לבדיקת הדוח המצטבר |
| `not-an-xml-file.txt` | סיומת שאינה xml |

---

## בדיקות

במקביל לפיתוח נכתבו 68 בדיקות JUnit 5 על מנוע המערכת, וכולן עוברות. הבדיקות אינן חלק מההגשה
ואינן משפיעות על ההרצה. נבדקו בין השאר: נוסחאות LMSR מול המספרים שבנספח א', שתי שיטות העמלה, סגירת
אירוע ותשלום, כל קבצי הבדיקה התקולים, ומעגל שמירה־טעינה מלא.

בנוסף, ההגשה נבדקה בתיקייה נקייה מחוץ לפרויקט, בדיוק באותו אופן שבו הבודק מריץ: שני ה‑jar,
`run.bat` ותיקיית הבדיקה בלבד.

---

# Appendix — class documentation

באנגלית, כדי ששמות המחלקות והחבילות יופיעו בדיוק כפי שהם בקוד.

## Module: engine

The system engine. Passive by design: it answers requests and returns data, and knows nothing about
who is calling it. It contains no printing and no input reading at all.

### `gm.engine.api` — the surface a user interface talks to

| Class | Responsibility |
|---|---|
| `GuessMarketEngine` | The single interface exposing every command. All numbers crossing it are counted from 1. |
| `GuessMarketException` | Base failure type. Its message is already written for a person to read. |
| `FileLoadException` | An events file was rejected. Carries the full list of problems found. |
| `NoFileLoadedException` | A command was asked for that needs a loaded file. |
| `InvalidSelectionException` | An event or option number that does not exist, or a non positive quantity. |
| `EventClosedException` | Trading on, or closing, an event that is already decided. |
| `PersistenceException` | Saving or restoring a system state failed. |

### `gm.engine.api.dto` — immutable records returned to callers

Nothing in this package refers to the model. The conversion goes all the way down, so no caller can
reach an engine object through a DTO it was handed. Nothing here computes anything either: totals and
flags are carried as values, which keeps every record a container that is only read from.

| Record | Contents |
|---|---|
| `EventInfoDto` | Number, id, name, description, option names, trading method, plus the commission and the status already broken down into plain values. |
| `OptionStateDto` | One option: its number, name, current value and shares bought. |
| `TradeDto` | One purchase: option, quantity, cost, commission and total. |
| `MarketStateDto` | The full picture of an event: options, accounts, trades newest first, and whether it is closed. |
| `PurchaseResultDto` | The receipt for a purchase, whether the commission falls due at closing time, and the state of the event afterwards. |
| `LoadResultDto` | The file read, how many events it held, and the total subsidy paid. |

### `gm.engine.model` — the domain

| Class | Responsibility |
|---|---|
| `Event` | One event: its details, options, method, account and history. Owns buying and closing, and is the only place trading changes anything. |
| `EventOption` | One outcome and the shares bought of it. Its share count can only be changed from inside the package. |
| `Account` | A balance, with deposit, withdraw and drainInto. Used for event accounts and the market maker, and ready for user accounts. |
| `Trade` | One completed purchase: option, quantity, cost and commission. |
| `Commission` | How much commission an event charges and when. Validates the 0 to 90 range. |
| `CommissionType` | `ON_PURCHASE` and `ON_CLOSE`. Each constant implements both charging points, so the two policies sit side by side instead of spreading through the code as conditionals. |
| `EventStatus` | `OPEN` or `CLOSED`, with the wording used on screen. |
| `SystemState` | The loaded events plus the market maker account. Building a state also funds it, so a state whose events were never paid for cannot exist. |

### `gm.engine.method` — pricing

| Class | Responsibility |
|---|---|
| `TradingMethod` | Sealed interface for the pricing mechanism of an event. Adding the order book later means adding one permitted implementation and nothing else. |
| `LmsrMethod` | The LMSR cost function and option values, evaluated with a log-sum-exp shift so a large purchase cannot overflow to infinity. |

### `gm.engine.xml` — reading events files

| Class | Responsibility |
|---|---|
| `EventsFileLoader` | Reads a file with the DOM parser built into the JDK and validates it in two passes: faults that make the file unreadable stop at once, everything else is gathered and reported together. |
| `EventNodeReader` | Turns one `GM-event` element into an `Event`, examining every part even after something has gone wrong so all its faults are reported at once. |
| `XmlNode` | A forgiving view over one DOM element: names matched without regard to case, text returned trimmed, anything missing returned as an empty optional. |

### `gm.engine.impl` and `gm.engine.persistence`

| Class | Responsibility |
|---|---|
| `GuessMarketEngineImpl` | Holds the loaded state, checks every selection, and builds the data objects returned. Replaces the loaded state only once a new one has been built without complaint. |
| `StateSerializer` | Writes and reads a saved system with Java serialization, adding the `.gm` extension itself. |

## Module: ui

The console front end, and the active side of the program: it drives the whole system by asking the
engine to do things. Every print in the program happens in `ConsolePrinter` and every read in
`ConsoleReader`.

| Class | Responsibility |
|---|---|
| `ConsoleApp` | The starting point. Shows the menu, hands each choice to its command, and turns every failure into a message. An unanticipated fault ends the command in hand, never the program. |
| `MenuOption` | The commands and their order. A command's number is its position in this list, so the printed menu and the accepted numbers cannot drift apart. |
| `MenuCommand` | Abstract base of a command, holding the engine, reader and printer, and the shared step of choosing an event from a list. |
| `LoadEventsFileCommand` | Command 1. Reports a rejected file together with what is still loaded. |
| `ShowEventsCommand` | Command 2. |
| `ShowMarketStateCommand` | Command 3. |
| `ParticipateCommand` | Command 4. Offers only open events and shows the standing before asking for any commitment. |
| `CloseEventCommand` | Command 5. Shows the whole event before the outcome is chosen, since closing cannot be undone. |
| `SaveSystemCommand` | Command 6, the bonus. |
| `LoadSystemCommand` | Command 7, the bonus. |
| `ConsoleReader` | Collects input and refuses to pass anything on until it makes sense. Reads whole lines, which is what lets a path contain spaces. |
| `ConsolePrinter` | Every layout in the program. Formats all numbers with `Locale.US` so a decimal point is never printed as a comma. |
| `InputEndedException` | There is no more input. Lets every reader keep a plain return type and gives the main loop one place to notice. |
