# תוכנית אחסון מתמשך — ManyManager

תוכנית לעשרה שלבים, עם ממצאי הבסיס של שלב 1 ומעקב מימוש של שלבים 2–8.
ממצאי הקוד בסעיף הבסיס מתארים את המצב לפני שלב 2; מצב התשתית העדכני מתועד בסוף המסמך.
קטגוריות, מקורות, הקצאות והוצאות מחוברים למסד; שלב 8 מוסיף שמירה ותיקון סכום בלבד.

## מצב העבודה והיקף

- נקודת ההתחלה: ענף `main`, קומיט `1dacfec`.
- נוצר ונבחר ענף חדש `feature/persistent-data`; לא היה ענף מקומי בשם זה.
- לפני יצירתו היו שמונה קבצים ששונו ו־drawable חדש של עיצוב הכפתורים המשניים. כולם נשמרו באותו working tree, ללא stash, commit, reset או push.
- לא נמצאו קובצי AGENTS.md בחיפוש בפרויקט ובתיקיות האב שנבדקו עד שורש הכונן. תיקיית docs לא הייתה קיימת.
- התוצר החדש היחיד בשלב זה הוא מסמך זה. אין שינוי בקוד, ב־Gradle, במסד נתונים או בממשק HTML.

## ממצאי הבסיס מהקוד — לפני שלב 2

כל הנתיבים להלן יחסיים לשורש המאגר. קובצי Kotlin של האפליקציה נמצאים תחת
`androidApp/src/main/kotlin/com/example/manymanager/`.

### מבנה וטכנולוגיה

`settings.gradle.kts` כולל בפועל שני מודולים: `:androidApp` ו־`:shared`.
הדיווח הראשוני של ה־IDE על אפס תתי־פרויקטים אינו תיאור של הגדרות Gradle.

הממשק הפעיל הוא Android Views עם XML: `MainActivity.kt` יורש AppCompatActivity,
קורא ל־setContentView ומחבר את `res/layout/activity_main.xml` ואת page_home,
page_summary, page_budget, page_categories ו־page_settings. דיאלוג ההוצאה הוא Dialog מותאם;
טופסי קטגוריות ומקורות משתמשים גם ב־AlertDialog. אין מעבר ל־Compose בתוכנית זו.

ב־androidApp מופעל Compose ויש תלויות Compose, אבל זה אינו הממשק הפעיל.
`shared/src/commonMain/kotlin/com/example/manymanager/App.kt` הוא מסך הדוגמה של Compose
ואינו מסלול התצוגה של MainActivity. shared מכיל גם דוגמאות Greeting ובדיקות בסיסיות;
אין בו שכבת נתונים עסקית.

### בעלות על הנתונים והתנהגות

| תחום | קוד ומצב נוכחי |
| --- | --- |
| ViewModel | `BudgetViewModel.kt`, נוצר דרך ViewModelProvider של ה־Activity. מחזיק רשימת קטגוריות, MonthlyBudgetAllocations, homeMonth, managementMonth ומצב הרחבה. שורד שינוי תצורה רגיל, אינו אחסון לאחר מות תהליך. |
| קטגוריות | MutableList של String; שמות משמשים גם לזהות הבחירה. addCategory חותך רווחים ודוחה שם ריק בלבד; שמות כפולים מתקבלים. `CategoryManagementUi.kt` מציג ומפעיל callbacks. |
| מקורות | `ExpenseUiData.kt` מגדיר PaymentSource(id: String, name: String). `MonthlyBudgetAllocations.kt` יוצר UUID למקור חדש. זו אותה רשימה שמוצעת כמקור ההוצאה; שינוי שם אינו משנה id. |
| הקצאות | מפה בזיכרון YearMonth -> sourceId -> Long באגורות. חודש חסר או מקור ללא הקצאה מחזירים אפס. המקור עצמו נשאר ברשימה. |
| פעולות תקציב | הוספת מקור עם הקצאה התחלתית, שינוי שם ותיקון +/− פועלים בזיכרון. preview אינו כותב; אישור מעדכן. `BudgetSourceDialogs.kt` קורא ישירות למודל; בעריכה adjust ו־rename הם כרגע שתי קריאות נפרדות. |
| כלל הקצאה | apply דוחה מקור לא מוכר, הקצאה שלילית וגלישת Long בסכום ההקצאות. previewAdjustment יכול להציג תוצאה שלילית, אבל אישורה נדחה. אין כיום חסימת הוצאה לפי יתרה. |
| כסף | `BudgetAmounts.kt`: BigDecimal לצורך parsing מדויק והמרה ל־Long באגורות; פורמט מקומי לתצוגה. אין צורך להמציא טיפוס כסף מבוסס Double. |
| הוצאות | אין רשומות הוצאה, CRUD או מסד. ExpenseUiData.monthlyTotal ו־expenseCount הם קבועי אפס. saveExpense מושבת ב־`ExpenseSheetDialog.kt` וב־dialog_expense.xml. |
| טיוטה | ExpenseDraft באותו קובץ: amount/date/note כמחרוזות, category כשם, sourceId כמזהה. אובייקט הטיוטה שייך לדיאלוג; MainActivity שומר Bundle של הטיוטה הפתוחה ומשחזר אותו. זה אינו מנגנון לשמירת הוצאות או טיוטות בדיסק. |
| ניווט בזמן | תקציב משתמש ב־YearMonth ובחיצי חודש פעילים. כניסה למסך מאפסת managementMonth ל־homeMonth. Home/Summary מציגים currentDate; חיצי הסיכום מושבתים. תקופות 10–9 עדיין אינן קיימות. |

### רענון המסכים כיום

`MainActivity.refreshManagedData()` קורא ידנית ל־BudgetManagementUi.render,
CategoryManagementUi.render, bindEmptyState ו־BudgetHomeUi.refresh לאחר שינוי מנוהל.
selectPage מרענן גם את מסך הבית/תקציב/קטגוריות בעת כניסה.

`BudgetHomeUi.kt` מחשב כיום בתוך בקר התצוגה סכום הקצאות ויתרה באמצעות חיסור קבוע
הוצאות אפס. הוא מציג פירוט כל המקורות, גם עם אפס הקצאה.
`BudgetManagementUi.kt` מבקש סכום וערכים לכל מקור מתוך המודל ומפרמט אותם.
`MainActivity.bindEmptyState()` קובע את סכומי Home/Summary ואת מספר ההוצאות לאפס,
ומחליף הודעת ריק לפי קיום קטגוריות ומקורות. כרטיסיות הסיכום מחליפות פאנל;
אין בהן עדיין נתוני הוצאות או aggregation אמיתי. אין Flow, Repository או DAO.

הוצאות נפתחות עם snapshot של המקורות והקטגוריות. בזמן יצירת קטגוריה מתוך ההוצאה
יש עדכון ממוקד של בחירות הקטגוריה, ולא בנייה מחדש של כל הטופס.

### יצירת קטגוריה מתוך טיוטת הוצאה — חוזה שחייב להישמר

1. MainActivity.showExpense יוצר ExpenseSheetDialog עם ExpenseDraft ומחבר onAddCategory.
2. showAddCategory(fromExpense) משאיר את גיליון ההוצאה פתוח מתחת לדיאלוג הקטגוריה;
   במסלול זה אינו קורא ל־dismissActiveDialogImmediately.
3. אישור תקין מוסיף קטגוריה למודל, מרענן מסכים וקורא ל־selectCategory; הקטגוריה החדשה
   נבחרת ורק רשימת הבחירות נבנית מחדש. הסכום, התאריך, המקור וההערה נשמרים.
4. Cancel/Back סוגרים את דיאלוג הקטגוריה וחוזרים לטיוטה ללא שינוי הבחירה.
5. saveUiState שומר expenseDialog.draft גם כאשר דיאלוג הקטגוריה מעליו.
   לאחר שחזור נפתח גיליון ההוצאה עם הטיוטה; אין שמירה ייעודית של טקסט שם הקטגוריה
   או חובה לפתוח מחדש את דיאלוג הקטגוריה עצמו.
6. בעת מעבר לאחסון אסינכרוני אין לסגור את הדיאלוג או לבחור קטגוריה לפני commit מוצלח.
   יש להעביר id חדש, לשמור טיוטה בזמן loading/error ולמנוע אישור כפול.
7. bindChoices בוחר כרגע את ההדגשה של ילדים לפי text/name לאחר לחיצה. יש להחליף השוואה זו
   בהשוואת id גם במקורות, כדי ששני שמות זהים לא ידגישו שתי בחירות.

### שפה, מראה ו־HTML

`AppearancePreferences.kt` שומר Boolean בשם dark ב־SharedPreferences בשם appearance.
`MainActivity.kt` מפעיל localNightMode; Locale נשמר דרך AppCompat/platform,
עם autoStoreLocales ב־`androidApp/src/main/AndroidManifest.xml`. ברירת המחדל he;
API 30–32 משתמשים במנגנון AndroidX ו־33+ במנגנון המערכת. לא מעבירים הגדרות אלו למסד.
יש לשמר עברית/רוסית/אנגלית, RTL/LTR, את AppearanceTransitionState ואת שחזור הממשק.

`ui-reference/README.md` ו־`UI_MAP.md` מתארים את ההקבלה.
`js/preview-model.js` מחזיק קטגוריות כמחרוזות, מקורות עם source-N, Map של הקצאות
בחודש קלנדרי ו־expenseTotal=0. אין אחסון בדפדפן או שינוי הוצאות.
`js/app.js` מבצע פעולות לפי data-action ומרנדר מחדש.
`js/components/category-dialog.js` שומר categoryReturnOverlay ומחזיר ל־expense;
`js/state.js` משמר את expenseDraft בין הרינדורים. ה־HTML של הגיליון מוחלף, אך ערכי
הטיוטה משוחזרים. `js/components/expense-sheet.js` משבית שמירה.
Home/Summary מציגים סכומי הוצאות אפס. שפה ומראה בדפדפן נשמרים רק בזיכרון,
בניגוד להגדרות Android המתמשכות.

## דרישות מוסכמות

- מנהל הוצאות ותקציב פרטי ל־Android; שומרים על העיצוב הנוכחי.
- מחשבון מקומי אחד בתחילה. בעלות מפורשת לפי calculatorId מאפשרת הרחבה בעתיד;
  אין חשבונות משתמש, סנכרון או ממשק החלפת מחשבון.
- קטגוריות ומקורות הם נתוני משתמש, מתחילים ריקים וזמינים בכל התקופות.
- מקור תקציב ומקור הוצאה הם אותה ישות. לקטגוריות ולמקורות מזהים יציבים,
  שאינם שם, תרגום או מיקום ברשימה.
- כסף הוא Long באגורות. הוצאה מכילה סכום, תאריך, קטגוריה אופציונלית, מקור חובה והערה אופציונלית.
- החלטת מוצר לשלב 2: categoryId=null מייצג הוצאה ללא קטגוריה. „ללא קטגוריה” הוא כיתוב תצוגה,
  לא רשומת קטגוריה שנזרעת במסד. non-null חייב להפנות לקטגוריה באותו מחשבון.
- הקצאות לפי מקור ותקופה והוצאות הן העובדות. סכומים, ספירות ויתרות נגזרים מהן;
  אין טבלת יתרות מתעדכנת או expenseTotal עצמאי.
- שינוי תקציב משנה הקצאה בלבד ואינו יוצר הוצאה.
- מותרת יתרה שלילית; אפס הקצאה אינו מבטל מקור ואינו חוסם הוצאה.
- כלל איסור הקצאה שלילית הקיים מתועד לעיל ונשמר במעבר, בנפרד מיתרה שלילית.
- כל גישה לנתונים עסקיים דרך שכבה מרכזית. המסכים מציגים state ומגישים actions;
  חישובי תקופה, הקצאה, preview, totals ושמירה אינם באחריותם.
- הצפנה נדחית. גבול אחסון ברור יעזור בעתיד, אך הצפנה עשויה לדרוש בחירת driver,
  ניהול מפתחות, גיבוי, פתיחת מסד ומיגרציה; אין הבטחה להחלפה בשורה אחת.

## תקופות תקציב

הגדרת Calculator.periodStartDay מתחילה ב־10. אין מסך הגדרה בשלב זה.
BudgetPeriodCalculator טהור ומרכזי מקבל LocalDate ויום התחלה ומחזיר
BudgetPeriod(start: LocalDate, endExclusive: LocalDate). בחישוב ברירת המחדל:
אם היום בחודש לפני 10, תחילת התקופה היא 10 בחודש הקודם; אחרת 10 בחודש הנוכחי.
endExclusive הוא 10 בחודש הבא יחסית לתחילת התקופה.

לדוגמה, [2026-11-10, 2026-12-10) כולל את 9 בדצמבר ואינו כולל את 10 בדצמבר.
9 בנובמבר שייך לתקופה הקודמת. מעבר שנה ופברואר אינם מוסיפים שעות או timestamps:
אלו תאריכים אזרחיים, ללא המרת חצות ל־UTC. רק בחירת "היום" תלויה בשעון/אזור הזמן המקומי.

מזהה התקופה המוצע הוא startEpochDay: Long בהקשר המחשבון, ולא YearMonth או
מחרוזת תצוגה. אין צורך בישות תקופה נפרדת עבור day=10 קבוע.
תאריך הוצאה נשמר בנפרד; התקופה לצורך סינון נגזרת מתאריך זה.
שאילתות משתמשות ב־dateEpochDay >= start AND dateEpochDay < endExclusive.
רק BudgetPeriodCalculator מפיק גבולות; DAO אינו מכיל SQL שמחשב "יום 10".

הניווט ישמור את החיצים והמבנה. state יכיל selectedPeriodStart במקום YearMonth.
החיצים עוברים לתקופה הקודמת/הבאה באמצעות הרכיב המרכזי. המלצת התווית היא חודש ושנת
תחילת התקופה, במקום החודש של היום; למשל "נובמבר 2026" עבור 10 בנובמבר–9 בדצמבר.
יש לעדכן בהמשך ניסוח/תיאור נגישות או טקסט עזר קיים כדי שהמשמעות 10–9 תהיה ברורה,
בלי עיצוב חדש. Home/Summary ישתמשו בתקופה של היום; אין להפעיל חיצים מושבתים
לפני שיש התנהגות מתוכננת בשלב 9. בשלב 1 לא משתנה UI או תווית.
כניסה לניהול תקציב תמשיך לבחור כברירת מחדל את התקופה הפעילה, כפי שהחודש מאופס כיום.

תקופה חדשה אינה דורשת job או יצירת שורות מראש: אין הקצאה -> אפס.
רשימת המקורות נשלפת מטבלת המקורות עם LEFT JOIN להקצאה של התקופה,
ולא מטבלת הקצאות בלבד. הוצאות יכולות להפנות למקור גם ללא שורת הקצאה.
אין העתקת תקציב או נשיאת יתרה. שינוי עתידי ליום ההתחלה דורש מדיניות היסטורית
לפני הפעלה; אין לשנות את ההגדרה ולסווג בשקט מחדש את העבר.
אין צורך להחליט כעת כיצד לטפל ביום 29–31; אין API פתוח לשינוי יום כזה לפני החלטה.

## כיוון טכני ותאימות

המלצה: Room מעל SQLite בתוך androidApp, ללא מודול חדש וללא העברת היישום ל־KMP.
הקוד העסקי הפעיל וה־java.time כבר ב־Android; shared אינו צורך נתונים כרגע.

מצב הגרסאות בפועל ב־`gradle/libs.versions.toml`: AGP 9.5.0, Kotlin 2.4.21,
compile/target SDK 37, minSdk 30, lifecycle 2.11.0.
Gradle wrapper הוא 9.6.0; יעד bytecode הוא JVM 11 (אין להסיק ממנו את גרסת JDK להפעלת Gradle).
androidApp משתמש ב־Kotlin המובנה של AGP, ללא plugin נפרד של kotlin.android.
במצב הבסיס לא היו Room, KSP או schema export; תוצאות מימוש שלב 2 מפורטות בסוף המסמך.

בשלב 2 יש לבדוק גרסה יציבה של Room ושל KSP שתומכת בפועל בשרשרת הזאת,
לרבות Kotlin המובנה, ולהוסיף runtime/compiler/export ובדיקות בלבד דרך version catalog.
לא ננעלת כאן גרסה שרירותית ולא מתבצע שדרוג AGP/Kotlin כפעולת לוואי.
יש לבדוק גם את מרחב השמות/ה־artifacts של גרסת Room שנבחרה; התיעוד העדכני
כבר מציג Room 3, ולכן אין להעתיק קונפיגורציה ישנה ללא בדיקה.
Flow, viewModelScope ו־repeatOnLifecycle יצריכו תלויות Android מפורשות מתאימות;
אין להסתמך על כך שספריית Compose ב־shared מספקת transitively את כל הדרוש.

מקורות טכניים: [Room והגדרה](https://developer.android.com/training/data-storage/room),
[ForeignKey](https://developer.android.com/reference/androidx/room/ForeignKey),
[מיגרציות וייצוא סכמות](https://developer.android.com/training/data-storage/room/migrating-db-versions).
יש לאמת את ה־API של ForeignKey עבור משפחת Room שתיבחר. מפתחות מורכבים ואינדקסי
הורים/ילדים ייבדקו במסד אמיתי, מעבר לוולידציה באפליקציה.

## סכמה מוצעת מינימלית

שמות וטיפוסים כאן הם הצעת מימוש, לא החלטות מוצר נוספות.
מזהי ישויות String/UUID הנשמרים כ־TEXT; סכומים ותאריכים כ־Long/INTEGER.
אין אחסון של מספרים מעוצבים או של תאריך תלוי locale.
אין צורך בטבלת payment_methods נוספת.

| ישות | שדות | מפתחות, קשרים ואינדקסים |
| --- | --- | --- |
| Calculator | id: String; periodStartDay: Int = 10; currencyCode: String = "ILS" | PK id. מחשבון מקומי בעל id קבוע פנימי בעת האתחול. לא מוסיפים UNIQUE גלובלי שמונע כמה מחשבונים בעתיד. currencyCode מתעד יחידות, אינו UI לריבוי מטבעות. |
| Category | calculatorId: String; id: String; name: String | PK (calculatorId,id); FK calculatorId -> Calculator.id. אינדקס ה־PK מכסה סינון לפי בעלים. אין UNIQUE על name לפני החלטת כפילויות. |
| BudgetSource | calculatorId: String; id: String; name: String | PK (calculatorId,id); FK למחשבון; ה־PK מכסה רשימת מקורות בבעלות מחשבון. אין קשר באמצעות שם. |
| BudgetAllocation | calculatorId: String; sourceId: String; periodStartEpochDay: Long; allocatedMinor: Long | PK (calculatorId,sourceId,periodStartEpochDay), המבטיח הקצאה יחידה. FK (calculatorId,sourceId) -> BudgetSource(calculatorId,id). אינדקס (calculatorId,periodStartEpochDay) לשליפת תקופה. הבעלות מובטחת דרך FK המקור. |
| Expense | calculatorId: String; id: String; amountMinor: Long; dateEpochDay: Long; categoryId: String?; sourceId: String; note: String? | PK (calculatorId,id); FK (calculatorId,categoryId) -> Category(calculatorId,id); FK (calculatorId,sourceId) -> BudgetSource(calculatorId,id). אינדקסים (calculatorId,dateEpochDay), (calculatorId,categoryId), (calculatorId,sourceId). categoryId nullable בהתאם להחלטת המוצר; sourceId חובה. נוסף גם FK ישיר למחשבון. |

מפתחות מורכבים מונעים ברמת SQLite הוצאה עם בעלים A שמפנה לקטגוריה או מקור של B,
גם אם קוד קורא שוגה. FK ל־id בלבד ובדיקת ViewModel לא מספיקים.
PK ההורים הוא בדיוק הזוג אליו מצביעים. כל שאילתה/עדכון כולל calculatorId;
לא מעדכנים רשומה לפי שם או id חסר הקשר.

מומלץ FK עם NO ACTION/RESTRICT, ללא מחיקה מדורגת של הוצאות דרך מקור/קטגוריה/מחשבון.
זו הגנת שלמות ראשונית, לא הכרעת מדיניות מחיקה למשתמש. מחיקות ניהול עדיין אינן נחשפות.
אם יוחלט על ארכוב, יתווסף שדה ארכוב במיגרציה והישויות ההיסטוריות יישארו נגישות.
לא מוסיפים כרגע audit log, חשבונות, טבלאות cache או entity גנרי.

Repository מאמת שם לא ריק לאחר trim, בעלות, כסף מדויק, תקופה תקינה והיעדר overflow.
הקצאה לא שלילית נשמרת בכלל העסקי הקיים; יש לבחון בשלב 2 אכיפת CHECK
בכלים הנתמכים בגרסת Room הנבחרת, בלי ליצור DDL שלא תואם לסכמה המיוצאת.
אין CHECK amount>0 להוצאות לפני החלטת החזרים/אפס.
קטגוריה אופציונלית הוכרעה: categoryId nullable וה־FK חל כאשר הערך אינו null.
אין חסם nullability להקמת טבלת Expense בגרסה 1.

## שכבת נתונים וגבול הבקרים

כיוון זרימה: XML/Dialog -> action ב־BudgetViewModel -> ManyManagerRepository
-> DAO/Room. בכיוון חזרה: observation של מסד -> state מוכן -> render.
אין למסכים DAO, RoomDatabase או מפות הקצאה mutable.

הצעה קומפקטית תחת אותה חבילת בסיס:
- `data/local/ManyManagerDatabase.kt`, `Entities.kt`, `ManyManagerDao.kt` וספריית schemas.
- `data/ManyManagerRepository.kt` כמימוש מרכזי קונקרטי אחד; interface נוסף רק אם בדיקות מצדיקות.
- `domain/BudgetPeriod.kt`, `BudgetPeriodCalculator.kt`, `BudgetCalculations.kt` ו־`Models.kt`.
- `BudgetUiState.kt` ל־prepared state; משאירים BudgetViewModel ומפתחים אותו בהדרגה.
- `ManyManagerApplication.kt` ו־factory פשוט ל־ViewModel; Application מחזיק מופע מסד ו־Repository
  עם applicationContext ומוסר אותם במפורש. אין registry גלובלי/getService, DI library או service locator.

DAO אחד עם קבוצות פעולות מספיק בתחילה: ensure/get calculator; observe categories/sources;
שליפת הקצאות בטווח/תקופה; insert/update scoped; CRUD הוצאות; שאילתות הוצאות בטווח
ופירוקים לפי categoryId/sourceId. כתיבה suspend, תצפית Flow; לא allowMainThreadQueries.
צירופים ושאילתות מרובות שמרכיבים snapshot אחד יהיו תחת transaction;
אין סכום הקצאות וסכום הוצאות שמוצגים מערכי DB משני רגעים שונים.

Repository מציע createCategory המחזיר entity/id, createSourceWithInitialAllocation,
updateSourceAndAdjustAllocation, observeOptions, observePeriodSnapshot,
createExpense, updateExpense, deleteExpense ו־getLocalCalculator.
הוא מרכז ולידציה וחישובים, ומחזיר תוצאות שגיאה מוגדרות.
parse ופורמט סכום ניתנים לשימוש חוזר, אך sign של adjustment וחישוב preview יוצאים מהדיאלוג.
חיבור SQL ו־Kotlin יחזיר עובדות/aggregates ללא עותקים mutable של היתרות.

BudgetViewModel משתמש ב־viewModelScope, מחזיק תקופה נבחרת ו־StateFlow של state
עם loading/ready/error ופעולות בטיפול. State כולל סכומי אגורות, ספירות, שורות, יתרות,
אפשרויות בחירה ו־preview מוכנים. עיצוב מחרוזות לפי locale יכול להישאר ב־formatter תצוגה,
אבל הסכום והתקופה מגיעים מוכנים; אין חיבור/חיסור עסקי במסך.

MainActivity אוסף ב־repeatOnLifecycle(STARTED), עם collector אחד לבעלות המסך שנבנה.
renderContent שמחליף Views בעקבות locale/theme אינו רשאי להשאיר collectors שמפנים
ל־Views ישנים או לרשום collector נוסף בכל החלפה. החלת state תחודש על references חדשים.
גיליון פתוח מקבל אפשרויות מעודכנות לפי id בלי לדרוס שדות טיוטה. טעינה אינה מוצגת כאילו
הרשימה ריקה, ושגיאת אחסון אינה מוצגת כהצלחה.

טיוטה נשארת state זמני; SavedStateHandle או Bundle יכילו categoryId/sourceId ושדות גולמיים,
לצד LocalDate/epochDay לתאריך תקין. טקסט תאריך לא תקין נשאר גלוי לתיקון.
אין לפרש מחדש תאריך ממחרוזת של locale קודם אחרי החלפת שפה.
לא נדרשת שמירת טיוטה כטבלת מסד. שחזור לאחר מות תהליך חייב לוודא שהמזהים עדיין קיימים;
אין להניח שקיום Bundle משחזר רשימת קטגוריות זמנית שאבדה.

### עסקאות, חישובים ואתחול

- יצירת מקור והקצאתו ההתחלתית הן עסקה אחת: שגיאה אינה משאירה מקור חלקי.
- rename + adjustment הם עסקה אחת. קוראים מחדש את ההקצאה בתוך העסקה ומחילים delta
  על הערך העדכני, לא על current שנלכד בעת פתיחת הדיאלוג. אין lost update.
- preview אינו כותב; confirm בודק מחדש בעלות, overflow והקצאה לא שלילית.
- יצירה/עריכה של הוצאה בודקת קשרים וכותבת כיחידה אחת; שינוי תאריך יכול להעביר תקופה.
  אין update נפרד של totals/remaining. מחיקת הוצאה מוחקת עובדה אחת.
- סיכום: allocated=sum(allocation); spent=sum(expenses בטווח); remaining=allocated-spent;
  ספירה וקבוצות נגזרות מאותן עובדות. מקור עם אפס הקצאה עדיין נכלל.
- יש לשמר Math.addExact/בדיקות תחום גם ב־aggregation. SQLite SUM על INTEGER עלול לגלוש;
  אין לעבור ל־TOTAL/Double כדי להסתיר זאת. בדיקות עומס ערכי Long יגדירו שגיאה עקבית,
  או צבירה מדויקת עם BigInteger בשכבת הנתונים ובדיקת המרה ל־Long.
- מסד אחד פרטי לאפליקציה. אתחול async יוצר אם חסר את המחשבון המקומי עם id פנימי קבוע,
  ILS ויום 10; INSERT IGNORE/עסקה ואחריו קריאה מונעים כפילויות. אין עדכון יום ההתחלה
  בכל פתיחה, ואין seed של קטגוריות/מקורות/כסף. בשלב 2 האתחול אינו משנה rendering;
  מצב loading יידרש רק כאשר מסך יתחיל להסתמך על המסד.
- סכמות מיוצאות נשמרות ב־version control מהגרסה הראשונה; כל שינוי עתידי מקבל version
  ומיגרציה מפורשת או auto migration שנבדקה. אין fallbackToDestructiveMigration.
  בדיקות משדרגות קובץ קיים ומוודאות זהויות, קשרים, סכומים ותאריכים נשמרים.
- טרם קיימים נתונים עסקיים בדיסק למיגרציה. לא ממציאים יבוא אוטומטי מזיכרון Activity.
  המעבר במפתח יסומן בבירור: נתוני דוגמה/בדיקה זמניים אינם baseline של נתוני משתמש.
  appearance ו־locale אינם נמחקים, מיובאים או נכתבים מחדש באתחול.

## חלוקה לשלבים ותלויות

הנתיבים החדשים להלן הם תחת חבילת Kotlin של androidApp, אלא אם צוין אחרת.
כל שלב משאיר את היכולות שכבר חוברו עובדות; אסור להוסיף מקור אמת שני לאותו תחום.

### 2 — הגדרת מסד וישויות

**תוצאה:** מסד נפתח, סכמה ו־FK נבדקים ומחשבון יחיד מאותחל; הממשק העסקי עדיין אינו עובר למסד.
**קיימים:** gradle/libs.versions.toml, build.gradle.kts, androidApp/build.gradle.kts,
AndroidManifest.xml.
**חדשים:** data/local/{ManyManagerDatabase,Entities,ManyManagerDao}.kt,
ManyManagerApplication.kt, androidApp/schemas, בדיקות device למסד ואתחול.
**תלות:** מסמך זה ובדיקת תאימות Room/KSP/JDK. קטגוריית הוצאה אופציונלית — הוכרע ומומש.
**בדיקה:** assembleDebug, schema export, פתיחה חוזרת ללא כפילות calculator, FK בין בעלים,
הקצאה כפולה לאותו מקור/תקופה, הישרדות נתונים בסגירה/פתיחה.
**הוכרע:** קטגוריה אופציונלית; אין אילוץ ייחודיות על שמות משתמש בשלב 2.
אין צורך בחישוב יום 10 כדי להקים שדות epochDay; בדיקות סכמה מזינות גבולות מפורשים.

### 3 — חישוב תקופות

**תוצאה:** רכיב טהור יחיד לתקופה ולניווט; עדיין ללא שינוי UI נרחב.
**קיימים:** BudgetViewModel.kt לצורך הכנת מעבר מ־YearMonth בלבד.
**חדשים:** domain/BudgetPeriod.kt, BudgetPeriodCalculator.kt ובדיקות יחידה.
**תלות:** שדות של שלב 2; אין תלות ב־Room בתוך האלגוריתם.
**בדיקה:** 9/10 בכל חודש, הדוגמה נובמבר–דצמבר, דצמבר–ינואר, פברואר מעובר,
previous/next, תאריך קודם/עתידי ושעון מוזרק לצורך today.
**פתוח:** ניסוח תווית תקופה לפני חיבור המסכים בשלב 7. לא מכניסים זמנית day-10 למסכים.

### 4 — שכבת גישה מרכזית

**תוצאה:** Repository ו־ViewModel factory, חוזי actions/state ותצפיות, עסקאות ואירועי שגיאה.
**קיימים:** BudgetViewModel.kt, MainActivity.kt, ManyManagerApplication.kt וה־DAO משלב 2.
**חדשים:** data/ManyManagerRepository.kt, domain/{Models,BudgetCalculations}.kt,
BudgetUiState.kt, בדיקות repository/transaction.
**תלות:** 2–3; queries מקבלים טווחים מרכיב התקופות.
**בדיקה:** isolation לפי מחשבון, rollback, תצפית על commit, overflow, ללא IO ב־main,
ביטול collectors בשינוי תצורה.
**פתוח:** החלטות שטרם נדרשות ל־UI נשארות ללא פעולה ציבורית.
מותרת תשתית API מתוכננת; אין להחזיק DB וגם מפה mutable עבור תחום שכבר הועבר.
היכולות הישנות ממשיכות בזיכרון עד השלב שבו מועברות כל הקריאות והכתיבות שלהן יחד.

### 5 — קטגוריות מתמשכות וחיבור ממשק

**תוצאה:** רשימת הקטגוריות והיצירה במסך ובגיליון משתמשות במסד וב־id יציב.
**קיימים:** BudgetViewModel.kt, CategoryManagementUi.kt, MainActivity.kt,
ExpenseSheetDialog.kt, ExpenseUiData.kt.
**חדשים:** בדיקות זרימת קטגוריה/טיוטה; types הנדרשים יורחבו בקבצים של שלב 4.
**תלות:** 4; החלטת שמות כפולים לפני validation חדשה. ללא שינוי מדיניות ברירת מחדל סמוי.
**בדיקה:** התחלה ריקה, restart עם קטגוריות קיימות; יצירה מתוך טיוטה ובחירת id החדש;
Cancel/Back, שגיאת כתיבה, אישור כפול, סיבוב ושינוי locale/theme עם כל שדות הטיוטה.
**פתוח:** אין delete/archive UI עד החלטת מדיניות.
במקביל מעדכנים preview-model ו־category-dialog/expense-sheet בדפדפן למזהי קטגוריות.

### 6 — מקורות תקציב מתמשכים

**תוצאה:** אותו מקור קבוע בניהול ובגיליון; שינוי שם לא משנה קשרים.
**קיימים:** BudgetManagementUi.kt, BudgetSourceDialogs.kt, BudgetViewModel.kt,
ExpenseUiData.kt, ExpenseSheetDialog.kt, MainActivity.kt, MonthlyBudgetAllocations.kt.
**חדשים:** בדיקות source identity ואינטגרציית אפשרויות.
**תלות:** 4–5, ורכיב תקופות מ־3.
**בדיקה:** יצירה ושינוי שם לאחר restart; מקורות בעלי שמות זהים נבדלים בבחירה;
מקור ללא הקצאה זמין בגיליון; כשל בהקצאה התחלתית מבטל יצירת מקור.
**פתוח:** שמות כפולים/ארכוב לפני חשיפת פעולות חדשות.
**תלות מפורשת ב־7:** טופס ההוספה הקיים כולל סכום התחלתי. לכן כבר בשלב 6 מחברים
createSourceWithInitialAllocation לעסקת DB מהשכבה של 4 ולתקופה מ־3. אין להשבית את השדה
או לשמור את סכומו במפה. קוראים הקצאות אלו מהמסד לצורך תצוגה בסיסית בבית ובתקציב.
שלב 7 משלים את עריכת ההקצאות והניווט; אינו יוצר מימוש אחסון שני.
השלמת שינוי שם בטופס עם adjustment מחייבת לחבר את הפעולה האטומית כבר כאן
אם הטופס נשאר פעיל; שלב 7 מרחיב בדיקות ו־preview ולא משאיר כפתור פעיל שבור.

### 7 — הקצאות ו־+/− לפי תקופה

**תוצאה:** מעבר מלא מ־MonthlyBudgetAllocations למסד, preview מוכן וניווט תקופות 10–9.
**קיימים:** BudgetSourceDialogs.kt, BudgetManagementUi.kt, BudgetHomeUi.kt,
BudgetViewModel.kt, MainActivity.kt; strings לשפות ותוויות התקופה.
**חדשים:** בדיקות עסקאות adjustment, תצוגת תקופה ו־prepared preview.
**תלות:** 3–6; העסקאות הבסיסיות של יצירת מקור קיימות כבר משלב 6.
**בדיקה:** +/−, אפס/ריק, preview שלילי ללא כתיבה, דחיית הקצאה שלילית, overflow,
אישור מול עדכון מקביל, חודשים סמוכים ושנים; תקופה חדשה אפס בלי העתקה ובלי אובדן מקור.
**פתוח:** אישור נוסח התקופה/תיאור נגישות; אין שינוי בכלל התקציב ללא החלטה מפורשת.
כבר כאן Home וניהול מתעדכנים מתצפית עבור הקצאות; לא מחכים לשלב 9.

### 8 — שמירת הוצאה ותיקון סכומה בלבד

**החלטת המוצר המאושרת מחליפה את הצעת CRUD המקורית:** סכום גדול מאפס בלבד;
אין אפס, שליליים או החזרים. categoryId אופציונלי ו־sourceId חובה, גם בהקצאת אפס.
מותרת יתרה שלילית. לחיצה על רשומה ב״כל ההוצאות״ פותחת תיקון סכום בלבד.
אין מחיקה או עריכת מקור/קטגוריה/תאריך/הערה.
**תוצאה:** שמירה ותיקון מתמשכים לפי ID, רשימה לתקופה הפעילה וסיכומים נצפים.
**קיימים:** Repository/DAO, PersistentDataViewModel, ExpenseSheetDialog, MainActivity,
panel_all.xml, BudgetHomeUi והתרגומים.
**חדשים:** ExpenseState.kt, ExpenseCorrectionDialog.kt, AllExpensesUi.kt ובדיקות ממוקדות.
**בדיקה:** אגורות, יחסים בין מחשבונים, תאריכי 9/10, null category, חריגה מותרת,
rollback בכשל/overflow, פתיחה חוזרת, תיקון אותה רשומה ושחזור ללא שליחה חוזרת.
**חיבור מידי:** עם ההוצאה הראשונה שמבטלת את קבועי האפס, Home/Summary מציגים totals/count
והרשימה מ־state של המסד; אין גרסה שבה שמירה מצליחה אך המסך ממשיך להציג אפס.
שלב 9 משלים פירוקים, אטומיות וניסוי רגרסיות בכל המסכים.

### 9 — חישובים נגזרים ורענון אוטומטי מלא

**תוצאה:** Home, ניהול, שלוש כרטיסיות Summary וגיליון פתוח עקביים עם העובדות.
**קיימים:** BudgetHomeUi.kt, BudgetManagementUi.kt, MainActivity.bindEmptyState/
refreshManagedData, BudgetViewModel.kt, ExpenseUiData.kt ופאנלי Summary.
**חדשים:** SummaryUi.kt אם נחוץ כדי להוציא קוד תצוגה מה־Activity; בדיקות חישוב/תצפית.
**תלות:** 4–8; סכומים בסיסיים כבר פעילים מ־7/8.
**בדיקה:** הוספה/עריכה/מחיקה מעדכנות סכום, ספירה, קבוצות ויתרה פעם אחת; rename אינו
שובר קיבוץ; אין flicker של שילוב סכום ישן/חדש; בחירת תקופה עקבית, RTL/LTR ו־locale.
**פתוח:** האם להפעיל ניווט היסטורי ב־Summary במסגרת הפיצ'ר או להשאיר disabled;
אין להפעילו במקרה רק בגלל שקיימת שאילתת טווח.

### 10 — השלמה וניקוי

**תוצאה:** אין מקור אמת זמני בתחום שהועבר; תיעוד, מיגרציות ובדיקות מקצה לקצה הושלמו.
**קיימים:** ExpenseUiData.kt/MonthlyBudgetAllocations.kt יוסרו או יצומצמו לאחר חיפוש usages;
README.md, ui-reference/{README.md,UI_MAP.md}, בדיקות ישנות ו־Gradle אם נדרש לבדיקות.
**חדשים:** בדיקות מיגרציה ו־end-to-end, fixtures לבדיקות בלבד.
**תלות:** 2–9 והכרעות המוצר שנדרשו ליכולות שנחשפו.
**בדיקה:** התקנה נקייה; שמירה/סגירה/פתיחה; מות תהליך לעומת סיבוב; שדרוג מסד שמכיל נתונים;
10–9 ומעבר שנה; עבודה ללא רשת; שגיאות וגלישה; שלוש שפות, שתי ערכות נושא ושני כיוונים;
קטגוריה מתוך טיוטה; פעולה במקור אפס ויתרה שלילית.
אין מחיקת קובץ זמני לפני שכל הקריאות אליו נותקו, ואין מחיקת מסד כפתרון לבדיקת שדרוג.

## שאלות מוצר ונקודות הכרעה

| שאלה | מה ידוע / המלצה בלבד | מועד הכרעה |
| --- | --- | --- |
| קטגוריה חובה בהוצאה? | הוכרע: לא. categoryId=null; „ללא קטגוריה” הוא כיתוב בלבד. מקור נשאר חובה. | נסגר בשלב 2. |
| שמות קטגוריות/מקורות כפולים? | מתקבלים כרגע; בדיקות אף משתמשות בשני מקורות עם אותו שם. מומלץ לא להוסיף UNIQUE ולשמר זאת עד הכרעה. יש להגדיר trim/case/Unicode אם בוחרים ייחודיות. | לפני 2 רק אם רוצים אילוץ UNIQUE; אחרת לפני שינוי התנהגות ב־5/6. |
| מחיקה או ארכוב של ישות עם הוצאות? | לא קיים UI. המלצה: הגנת FK נגד orphan וללא cascade; מחיקה פיזית/ארכוב/העברה דורשים החלטה. | לפני חשיפת delete/archive ב־5/6 או שלב עתידי, לא blocker ליצירה. |
| החזרים, אפס או הוצאה שלילית? | הוכרע בשלב 8: סכום גדול מאפס בלבד. אפס, שליליים והחזרים אינם נתמכים. | נסגר להיקף שלב 8; אין שינוי סכמה. |
| שינוי עתידי של יום תחילת תקופה | אין UI נדרש. יש לבחור effective-from, snapshot/version של מדיניות או חישוב היסטורי מפורש. | לפני כל שינוי הגדרה עתידי; ייתכן שיידרשו טבלת תקופות/גרסאות ומיגרציה. |
| תווית תקופה וניווט Summary | מומלץ חודש תחילת התקופה + פירוט נגיש 10–9; עיצוב נשמר. | תווית לפני 7; חיצי Summary לפני 9. |
| טיוטה לאחר הריגה יזומה/פתיחה חדשה | שמירה זמנית בשחזור Activity קיימת; durable draft אינו דרישה מוסכמת. | לפני הרחבת טיוטות בשלב 8, רק אם מבקשים שרידות נוספת. |
| גיבוי | Manifest הנוכחי allowBackup=true. "פרטי" אינו הבטחה שהמסד מוצפן או מוחרג מגיבוי. | בדיקת scope וגיבוי לפני סיום 10; אין שינוי הגדרה בשלב 1. |

חסם הקטגוריה לשלב 2 הוסר בהחלטת המוצר. הסכמה המלאה כוללת Expense עם categoryId nullable.
אין לשנות מדיניות כפילויות, החזרים או מחיקה באופן סמוי במסגרת התשתית.

## שמירת ההקבלה ל־ui-reference

הדפדפן נשאר companion מבודד בזיכרון: ללא Room, העתק של production repository,
IndexedDB או localStorage כתחליף לארכיטקטורת Android.
בכל שלב התנהגותי מעדכנים את preview-model, state, app.js והרכיבים המתאימים יחד עם Android:
מזהי קטגוריות בשלב 5; מקורות וזהות בשלב 6; helper מרכזי לתקופות וניווט בשלב 7;
רשומות הוצאה ופעולות/ולידציה בשלב 8; נגזרות ופירוקים בשלב 9.
יש להשתמש במספרי אגורות בטווח Number.isSafeInteger ולהצהיר על מגבלת preview לעומת Long;
לא לטעון לשקילות מלאה בקצוות ללא בדיקה.

יצירת קטגוריה חייבת להחזיר id חדש ל־expenseDraft, ולשמר את categoryReturnOverlay.
שומרים את כל השדות לפני רינדור/שינוי overlay, ו־Cancel/Escape לא משנים את הבחירה.
sourceId נשאר אותו id בניהול ובגיליון, גם אחרי rename.
משקפים empty/loading/error הנראים למשתמש כשיש לכך הדמיה מועילה, אך לא יוצרים מערכת
אחסון דפדפן אמיתית. reload ממשיך לאפס את preview; מתעדים שזה שונה מ־Android.
ui-reference/README.md ו־UI_MAP.md יעודכנו לפי מה שבאמת מומש ונבדק.
אין שינוי HTML/CSS/JS בשלב זה ואין טענה לאימות פיקסלים בין הפלטפורמות.

## פקודות ובדיקות

משורש המאגר, ב־PowerShell עם JDK מתאים ל־Gradle ב־JAVA_HOME או דרך Android Studio:

```powershell
.\gradlew.bat :androidApp:assembleDebug
.\gradlew.bat :androidApp:testDebugUnitTest
.\gradlew.bat :shared:testAndroidHostTest
git diff --check
```

ב־shell תואם Unix: ./gradlew עם אותן משימות. ב־README מתועדת גם פקודת assembleDebug.
הבדיקות העסקיות הקיימות:
`androidApp/src/test/kotlin/com/example/manymanager/BudgetAmountsTest.kt`
ו־`MonthlyBudgetAllocationsTest.kt`. הן מכסות parsing, שפות, overflow, זהויות,
אפס, עצמאות חודשים, preview והכלל נגד הקצאה שלילית.
בדיקות shared/commonTest ו־shared/androidHostTest שנבדקו הן דוגמאות 1+2=3,
לא בדיקות persistence או UI. אין כעת תשתית בדיקות Room ב־androidApp.

לאחר הגדרת בדיקות device בשלב 2:
`./gradlew :androidApp:connectedDebugAndroidTest` עם מכשיר/אמולטור.
יש לאמת את שם המשימה לאחר הסנכרון; מוסיפים runner ותלויות נדרשות אז בלבד.
ל־HTML: `python -m http.server 8000 --directory ui-reference`, ואז localhost:8000;
אין כרגע test runner אוטומטי ל־preview.

### בדיקות שבוצעו בשלב 1

בוצעו בדיקת branch/status/log, חיפוש הוראות, קריאת קוד Android/Gradle/בדיקות ומימוש
ודוקומנטציית ui-reference, סקירת מקורות Room רשמיים ובדיקת diff/working tree.
נבדקה יצירת הענף ושימור רשימת השינויים הקודמים.
לא הורצו בדיקות Room שאינו קיים, ולא בוצע אימות חזותי או תפקודי בממשק בשלב זה.
אירוע ה־IDE שסופק בתחילת הבקשה דיווח על build/run מוצלח של androidApp;
זה אינו build חדש שהורץ במסגרת כתיבת המסמך.

## מימוש שלב 2 — תשתית Android

החלטת הקטגוריה האופציונלית נסגרה: Expense.categoryId הוא String?,
המקור String שאינו nullable. „ללא קטגוריה” יתווסף רק לתצוגה בעת חיבור ההוצאות;
אין seed כזה. אין מגבלת סכום הוצאה או ייחודיות שמות חדשות.

### כלי הבנייה שנבחרו

- Room runtime/compiler/testing וה־Gradle plugin: **2.8.5**, הגרסה היציבה הנוכחית
  של משפחת androidx.room לפי [release notes](https://developer.android.com/jetpack/androidx/releases/room).
  נבחרה המשפחה היציבה 2.x עם platform SQLite כדי לשמור על תשתית Android מצומצמת;
  Room 3 קיים אך אינו תנאי למודל הזה. composite PK/FK, nullable child column ו־RESTRICT
  נתמכים ב־Room 2.x; הקומפיילר ובדיקות המכשיר מאמתים את הסכמה בפועל.
- KSP **2.3.13**, KSP2 בלבד, לפי [המהדורה הרשמית](https://github.com/google/ksp/releases/tag/2.3.13).
  נשמר Kotlin המובנה של AGP; לא נוסף Kotlin Android plugin.
- kotlinx-coroutines-android **1.11.0** לאתחול async ולבדיקות suspend;
  AndroidX Test core **1.7.0**, ext JUnit **1.3.0**, runner **1.7.0**.
- Serialization BOM **1.8.1** מיישר core ו־JSON גם ב־app וגם ב־androidTest.
  בדיקת הסכמה הראשונה חשפה AbstractMethodError: consistent resolution בחר core 1.7.3
  מ־runtime האפליקציה, בעוד room-testing דרש JSON 1.8.1. נבחרה גרסת ה־BOM שתואמת
  לתלות המוצהרת של Room, ללא serialization plugin נוסף וללא שינוי קוד האפליקציה.
  [תיעוד גרסת serialization](https://github.com/Kotlin/kotlinx.serialization/releases/tag/v1.8.1).
- AGP 9.5.0, Kotlin 2.4.21, Gradle 9.6.0, minSdk 30 ו־compile/targetSdk 37 נשמרו.
  launcher Java שנמצא הוא JBR 25.0.4; הגדרת daemon הקיימת ב־
  gradle/gradle-daemon-jvm.properties בוחרת Azul JDK 21, וכך פועל ה־daemon בפועל.
  bytecode נשאר JVM 11. אין שינוי בתצורת JDK/SDK הקיימת.

### קבצים ומנגנון

- androidApp/src/main/kotlin/com/example/manymanager/data/local/Entities.kt:
  חמש הישויות, סכומים/תאריכים כ־Long; אין צורך ב־type converters.
  PK מורכבים ו־FK מורכבים מונעים יחסים בין בעלים שונים.
  Expense כולל גם FK ישיר למחשבון. כל FK הוא RESTRICT במחיקה ו־NO ACTION בעדכון.
  אין CASCADE, REPLACE או טבלאות יתרות.
- data/local/ManyManagerDao.kt: primitives של insert/read ועסקת initializeLocalCalculator.
  insert של נתונים הוא ABORT; כפילות הקצאה נדחית ומשאירה את הערך המקורי.
  רק insert של מחשבון באתחול הוא IGNORE.
- data/local/ManyManagerDatabase.kt: version=1, exportSchema=true,
  קובץ ייצור פרטי בשם many-manager.db. אין destructive fallback ואין מיגרציה פיקטיבית.
- ManyManagerApplication.kt: בעלות על מופע lazy אחד, CoroutineScope של תהליך על IO,
  Deferred<Result<CalculatorEntity>> לתוצאת האתחול; כשל נרשם ואינו מוצג כהצלחה.
  אין תלות של מסכים במוכנות המסד ואין loading UI חדש.
- המזהה הפנימי הקבוע **local-calculator** אינו שם תצוגה. אתחול מכניס אותו רק אם חסר
  עם day=10 ו־ILS. עסקה ו־INSERT IGNORE מונעים כפילות ואינם מאפסים הגדרות קיימות.
- AndroidManifest.xml רושם את Application; Gradle root, androidApp ו־version catalog
  מוסיפים tooling ותלויות ממוקדות. schema export מוגדר תחת androidApp/schemas.
- androidApp/src/androidTest/kotlin/com/example/manymanager/DatabaseTestRunner.kt
  מפעיל Application בסיסי לבדיקות, כדי שלא לאתחל/לזרוע את מסד הייצור בזמן fixtures.
- androidApp/src/androidTest/kotlin/com/example/manymanager/data/local/ManyManagerDatabaseTest.kt:
  Room אמיתי עם platform SQLite; בדיקות in-memory ובדיקות קובץ עם שמות UUID
  המתחילים many-manager-test-. הניקוי מוחק רק שמות שנוצרו בידי הבדיקה.
  אין clear-data, uninstall או שינוי בקובץ הייצור.
- הבדיקות מכסות אתחול כפול/מקביל, שימור הגדרות, אפס seed, reopen של כל סוגי הרשומות,
  uniqueness של הקצאה, קטגוריה null, FK תקינים/חסרים/בין בעלים, מקור חובה,
  מניעת מחיקת הורים עם היסטוריה, שמות כפולים, סכומי הוצאה שלא הוכרעו,
  ופתיחת מסד שנוצר מה־JSON המיוצא באמצעות MigrationTestHelper.

בסיום שלב 2 לא היו Repository, חישוב יום 10, חיבור ViewModel למסד או שמירת הוצאות בממשק.
רכיב חישוב התקופות נוסף בשלב 3, כמתועד להלן; יתר החיבורים נשארים לשלבים הבאים.
אין יבוא אוטומטי של רשימות זמניות. מסכי Android, הגדרות שפה/מראה ו־ui-reference
נשארים בהתנהגותם הקיימת. כלל איסור הקצאה שלילית עדיין נאכף רק במודל הזמני;
בשלב 4/7 יועבר ל־business transaction. לא הוכנס CHECK ידני שאינו מיוצג ב־Room Entity.

סכמה 1 תישמר במאגר כתוצר הקומפיילר. בעת שינוי עתידי יוגדל version, תיכתב מיגרציה
ותיבדק מול הסכמה/נתונים הישנים. אין למחוק מסד משתמש כדי לעקוף כשל שדרוג.

### תוצאות אימות שלב 2

**שלב 2 הושלם.** לאחר תיקון יישור serialization הורצה בהצלחה הפקודה:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :androidApp:assembleDebug :androidApp:testDebugUnitTest :shared:testAndroidHostTest :androidApp:connectedDebugAndroidTest
```

- assembleDebug עבר עם KSP ו־Room; אין שינוי בגרסאות AGP/Kotlin/SDK.
- 14 בדיקות היחידה הקיימות ב־androidApp עברו (5 parsing/formatting, 9 allocations).
- שתי בדיקות shared:testAndroidHostTest עברו.
- כל 12 בדיקות Room/SQLite החדשות עברו במכשיר SM-S928B עם Android 17, ללא דילוג.
  זו הרצת SQLite אמיתית, כולל בדיקת file-backed reopen ובדיקת הסכמה המיוצאת.
- סכמה 1 שנוצרה בקומפיילר נמצאת ב־
  androidApp/schemas/com.example.manymanager.data.local.ManyManagerDatabase/1.json.
  נבדקו גם PK, nullability, FK ואינדקסים ב־JSON; identityHash:
  bafcc93374123a999f237711f0f81500.
- בדיקת diff מוודאת שאין שגיאות whitespace. שינויי העיצוב הקודמים נשארו מחוץ לעבודת שלב 2.
- בהרצת הבדיקות לא נוקה מידע של האפליקציה ולא בוצע uninstall כפתרון לבעיה.
  ה־runner מבודד את אתחול הייצור; כל רשומות ה־fixtures הן במסדי בדיקה נפרדים.
- לא בוצע אימות חזותי או end-to-end של המסכים. הם אינם מחוברים למסד;
  דפדפן ה־preview נשאר בזיכרון בלבד. אזהרות deprecation בקוד UI קיים נשארו ללא שינוי.

לשלב 3 אין חסם מוצר שנותר משלב 2. יש לממש את BudgetPeriodCalculator המרכזי
ובדיקות גבולות 10–9; אין להחליף זאת בחישוב מקומי במסכים.
חיבור Repository, תצפיות ופעולות עסקיות למסד נשאר לשלבים הבאים.

## מימוש שלב 3 — חישוב תקופות מרכזי

### קבצים ו־API

תחת androidApp/src/main/kotlin/com/example/manymanager/domain/ נוספו:

- **BudgetPeriod.kt**: data class בלתי משתנה עם start: LocalDate ו־endExclusive: LocalDate.
  הבנאי (וגם copy) מחייב start < endExclusive. ניתן לבדוק שייכות באמצעות
  contains(date), או date in period. השדות startEpochDay ו־endExclusiveEpochDay הם
  getters נגזרים; id הוא startEpochDay בהקשר של המחשבון. נשמרים רק שני התאריכים,
  ללא שדות כסף, שם חודש, locale או נתונים כפולים.
- **BudgetPeriodCalculator.kt**:
  BudgetPeriodCalculator(periodStartDay: Int, clock: Clock).
  שני הארגומנטים מפורשים, ללא קריאה למסד או ברירת מחדל מוסתרת ליום 10.
  periodContaining(date) מוצא תקופה, currentPeriod() משתמש ב־LocalDate.now(clock)
  בכל קריאה, ו־previous(period)/next(period) מנווטים לתקופה סמוכה.
  הניווט דורש תקופה שזהה לתקופה הקנונית של תאריך תחילתה לפי הגדרת המחשבון;
  יום התחלה אחר או סוף לא תואם נדחים עם IllegalArgumentException.

הטווח הנתמך במפורש הוא **1..28**. ערכי 29–31, אפס, ערכים שליליים ומעל 31 נדחים;
אין clamping, גלישה לסוף חודש או שינוי הגדרות והיסטוריה.
החישוב משתמש ב־withDayOfMonth וב־plusMonths/minusMonths של LocalDate,
ללא משכי זמן קבועים, UTC midnight או timestamps. טווח התאריכים כפוף לטווח
java.time.LocalDate; ניסיון לחשב גבול מחוץ לו נכשל ב־DateTimeException.

לדוגמה עבור day=10:

- 2026-11-09 שייך ל־[2026-10-10, 2026-11-10).
- 2026-11-10 ו־2026-12-09 שייכים ל־[2026-11-10, 2026-12-10).
- 2026-12-10 אינו נכלל בתקופה הזאת ומתחיל את הבאה.
- תקופות סמוכות נפגשות בדיוק ב־endExclusive של הראשונה וב־start של השנייה.

זהו רכיב domain טהור, ללא Android, Room או localization.
אין הקצאות, מקורות, יצירת נתוני תקופה, העתקת תקציבים או rollover ברכיב.
CalculatorEntity.periodStartDay והאתחול נשארו ללא שינוי.
YearMonth במסכים, מראה/ניווט, SQL, סכמה 1 ו־ui-reference נשארו ללא שינוי.
בעת חיבור הנתונים בשלב 4, יועבר אליו יום ההתחלה של CalculatorEntity ושעון מתאים;
המסכים וה־DAO לא יכילו חישוב מקביל של יום 10.

### בדיקות ואימות שלב 3

נוספו בדיקות JVM ב־androidApp/src/test/kotlin/com/example/manymanager/domain/:
BudgetPeriodTest.kt ו־BudgetPeriodCalculatorTest.kt. הן מכסות הכללה/החרגה בגבולות,
מזהים ו־epoch-day, intervals לא תקינים, נובמבר–דצמבר, מעבר שנה, פברואר מעובר ורגיל,
עבר ועתיד, ניווט והיפוכו, רציפות תקופות, כל הימים 1–28, דחיית הגדרות ותקופות
לא תואמות, fixed Clock ושני אזורי זמן באותו instant. שעון בדיקה ניתן לקידום מאמת
ש־currentPeriod קורא שוב את השעון ואינו שומר את היום במשתנה גלובלי.

**שלב 3 הושלם.** הורצה בהצלחה הפקודה:

```powershell
.\gradlew.bat :androidApp:assembleDebug :androidApp:testDebugUnitTest
```

ההרצה השתמשה ב־JAVA_HOME של Android Studio JBR וב־JDK daemon הקיים, כמו בשלב 2.
assembleDebug עבר; כל 33 בדיקות ה־JVM באפליקציה עברו ללא דילוג:
19 חדשות לתקופות (16 Calculator ו־3 Model), ו־14 בדיקות קיימות.
נבדקו גם דוחות XML של הבדיקות ומצב Git, ו־diff נבדק לפני הקומיט.
בדיקות Room במכשיר אינן נדרשות מחדש לשינוי domain זה; לא שונו אחסון או סכמה.
לא בוצע אימות חזותי ולא חובר רכיב התקופות למסכים.

אין חסם חדש לפני שלב 4. השלב הבא הוא שכבת הגישה המרכזית, שתעביר את הגדרת
המחשבון ושעון מוזרק לרכיב הזה ותשתמש בגבולות epoch-day לשאילתות.

## מימוש שלב 4 — שכבת נתונים וגבול ViewModel

**שלב 4 הושלם.** נוסף צינור נתונים מתמשך נפרד, שעדיין אינו מחובר למסכים.
אין העתקה דו־כיוונית בין המסד לבין BudgetViewModel הזמני, ואין fallback אליו בכשל מסד.

### קבצים ואחריות

נתיבי Kotlin להלן יחסיים ל־androidApp/src/main/kotlin/com/example/manymanager/:

- **data/ManyManagerRepository.kt**: שכבת העסקים מעל Room, קשורה למחשבון המקומי
  שמתקבל מתוצאת האתחול. אין פרמטר calculatorId חופשי בפעולות שמאפשר למסך לבחור בעלים אחרים.
  בכל פעולה נקראות מחדש הגדרות המחשבון השמורות בתוך עסקה. IDs של מקורות נבדקים
  עם calculatorId; שם אינו מפתח יחסים. אין API למחיקה/ארכוב או לשינוי הוצאות.
- **domain/Models.kt**: Calculator, Category, BudgetSource, Expense (LocalDate וקטגוריה nullable),
  SourceBudget, CategorySummary, BudgetSnapshot, BudgetAdjustmentPreview,
  DataResult ו־DataError. אלו אינם Room entities ואין בהם מחרוזות מתורגמות.
  BudgetSnapshot כולל קטגוריות, מקורות עם allocated/spent/remaining, הוצאות,
  סכומי התקופה, ספירה וקיבוץ לפי categoryId, כולל null ללא רשומת seed.
- **domain/BudgetCalculations.kt**: חישוב מקור/סכום/יתרה וקבוצות מתוך העובדות בלבד.
  צבירה ב־BigInteger מונעת גלישה בתוצאות ביניים, כולל סכומים חתומים שמתקזזים.
  רק ערכים סופיים שמוחזרים ב־state מומרים ל־Long אחרי בדיקת טווח מפורשת.
  הבדיקה תואמת minSdk 30 ואינה קוראת ל־BigInteger.longValueExact,
  הזמין רק מ־[API 31](https://developer.android.com/reference/java/math/BigInteger#longValueExact()).
  גלישה בערך קבוצה או יתרה מדווחת, גם אם סכום כולל אחר עדיין נכנס ב־Long.
  אין SQL TOTAL, Double, או טבלת totals/balances.
- **data/local/ManyManagerDao.kt**: נוספו קריאות scoped לקטגוריות, מקורות, הקצאות
  והוצאות בטווח; renameSource ו־putAllocation באמצעות Upsert.
  טווח הוצאות הוא startEpochDay <= dateEpochDay < endExclusiveEpochDay.
  ה־DAO מקבל גבולות מוכנים; אין בו חישוב תקופה או יום 10.
  insertExpense הקיים נשאר primitive של אחסון לצורכי fixtures, ולא נחשף ב־Repository.
- **ManyManagerApplication.kt**: בעלות lazy על Repository יחיד מעל המסד הקיים,
  עם אותו databaseInitialization ו־Clock.systemDefaultZone() בגבול האפליקציה.
  לא נוסף service locator, DI framework או שינוי לאתחול/ברירות המחדל של CalculatorEntity.
- **PersistentDataViewModel.kt**: ViewModel חדש וממוקד ו־PersistentDataViewModelFactory
  המוזרק בבנאי. state הוא Loading/Ready(snapshot)/Failed(error), ולא הצלחה ריקה בכשל.
  זהו גבול מוכן לצריכת הנתונים בעתיד; MainActivity עדיין יוצר את BudgetViewModel המקורי.
- Gradle ו־version catalog מוסיפים תלות Android מפורשת ב־lifecycle-viewmodel-ktx **2.11.0**,
  גרסת lifecycle הקיימת. לא שונו גרסאות כלי בנייה, Room, entities או סכמה 1.

### פעולות ציבוריות

- currentPeriod(), periodContaining(LocalDate): גבולות דרך BudgetPeriodCalculator,
  לפי ההגדרה השמורה ו־Clock מוזרק. הגדרה לא נתמכת או BudgetPeriod לא קנוני נדחים.
- observeCategories(), createCategory(name): רשימת קטגוריות ויצירה שמחזירה מזהה/מודל יציב.
- observeSources(), createSourceWithInitialAllocation(name, period, initialMinor),
  renameSource(sourceId, name): מקורות קבועים, גם בתקופה שבה אין להם הקצאה.
- previewBudgetAdjustment(sourceId, period, deltaMinor): תצוגה מקדימה ללא כתיבה.
  תוצאה חתומה שלילית מותרת ב־preview, כמו בעורך הקיים; היא אינה אישור להקצאה שלילית.
- updateSourceAndAdjustAllocation(sourceId, name, period, deltaMinor): אישור שינוי שם
  והפרש תקציב ביחד; נקרא תמיד הערך השמור העדכני ולא ה־preview הישן.
- snapshot(period?), observeSnapshot(period?): תמונת מצב מלאה. null בוחר את התקופה
  של היום בכל קריאה/רענון; תקופה מפורשת נשארת הבחירה ההיסטורית של הצרכן.
  מקורות ללא שורת הקצאה מקבלים אפס, והוצאותיהם עדיין נספרות. כל הוצאה תורמת פעם אחת
  לסכום הכללי; קבוצות לפי מקור/קטגוריה נגזרות מאותן רשומות.

כל פעולה מחכה להצלחת databaseInitialization של שלב 2, בלי לשכתב הגדרות או לזרוע נתונים.
שגיאות האתחול שומרות את הסיבה המקורית; אין החלפה ברשימות זמניות או ריקות.
עבודת האחסון והצבירה רצה על dispatcher מוזרק, כברירת מחדל Dispatchers.IO.

### עסקאות ושגיאות

כל קריאת snapshot או פעולה עסקית מתבצעת ב־Room.withTransaction.
יצירת מקור + הקצאה ושינוי שם + adjustment הן יחידות אטומיות.
שמות עוברים trim, ריק נדחה וכפילויות נשארות מותרות.
סכום הקצאה שלילי, גלישת current+delta או סכום ההקצאות נדחים לפני commit.
בדיקת סכום ההקצאות נמצאת באותה עסקה שקוראת את המצב העדכני וכותבת אותו,
ולכן פעולות מקבילות אינן מאבדות הפרשים.
כשל בכתיבה השנייה מבטל את הראשונה, גם אם rename או insert source כבר בוצעו.
שינוי תקציב אינו יוצר הוצאה ואינו משנה סכומי תקופות אחרות.
יתרה שלילית אינה מגבלת כתיבה; לא הוכרעה כאן מדיניות הוצאות שליליות/אפס/החזרים.

DataError מבדיל BlankName, MissingEntity (כולל מזהה זר לאותו מחשבון),
InvalidPeriod, NegativeAllocatedBudget, AmountOverflow, InitializationFailure ו־StorageFailure.
הסיבות נשמרות לאבחון, ללא logging של רשומות כספיות. CancellationException תמיד נזרק מחדש,
גם בהמתנה לאתחול או בזמן פעולה, ולא הופך ל־Failed או להצלחה.

### עקביות ותצפיות

תצפיות ה־Repository הן cold Flow. משתמשים ב־InvalidationTracker.createFlow עם פליטה
ראשונית ורישום לטבלאות הרלוונטיות לפני קריאה; כל invalidation מפעיל קריאה שלמה בעסקה.
observeSnapshot מאזין לכל חמש הטבלאות. אין combine בין פליטות נפרדות של שמות,
הוצאות והקצאות. Room יכול לאחד invalidations, ולכן אין התחייבות לפליטה לכל קליק,
אבל כל תמונה שנפלטת עקבית פנימית ומייצגת מצב מחויב.
distinctUntilChanged מסנן תמונות זהות. שגיאת חישוב בתמונה היא Failure מפורש;
שינוי נתונים תקין יכול להחזיר את אותה תצפית ל־Success.
כשל שסוגר את זרם האחסון או כשל אתחול דורש רענון/איסוף מחדש, ולא הצלחה מדומה.

StateFlow של ה־ViewModel משתמש ב־flatMapLatest וב־stateIn בתוך viewModelScope,
עם WhileSubscribed(5000). אין collector פנימי תמידי ללא צרכן ואין collector חדש לכל render.
selectPeriod(period?) ו־refresh() מפסיקים את התצפית הקודמת ומתחילים אחת חדשה.
פעולת createCategory מחזירה Job ב־viewModelScope ותוצאה חד־פעמית ב־callback,
בנפרד מ־state מתמשך; ניקוי ה־ViewModel מבטל פעולות ממתינות ולא קורא callback הצלחה.
בשלבים הבאים פעולות המקורות יחוברו באותה דרך כשממשק המקורות יועבר.

התקופה הפעילה אינה נשמרת לצמיתות. קריאה/רענון נעזרים ב־Clock שסופק; אין scheduler.
בעת חיבור המסכים, הצרכן אחראי ל־repeatOnLifecycle, ל־refresh ב־resume/מעבר תאריך
ולטיפול בשינוי אזור זמן בגבול שעון האפליקציה. השעון הראשוני משתמש באזור המקומי,
ללא UTC לתאריכי חשבונאות. מעברי יום ללא invalidation במסד אינם יוצרים פליטה מעצמם.
מקורות תכנון: [Room invalidations](https://developer.android.com/reference/kotlin/androidx/room/InvalidationTracker),
[ViewModel factory](https://developer.android.com/topic/libraries/architecture/viewmodel/viewmodel-factories).

### מסירה מדויקת לשלב 5

1. ליצור PersistentDataViewModel דרך ה־factory עם Application.repository,
   ולא לתת DAO/Room למסכי התצוגה. לאסוף state באופן תלוי lifecycle.
   אפשר להוסיף state ממוקד של observeCategories לגבול ה־ViewModel עבור מסכי הקטגוריות,
   כדי שכשל חישוב כספי לא ימנע הצגת רשימת קטגוריות תקינה.
2. להעביר באותו שינוי את כל קריאות הקטגוריות ואת שתי כתיבות היצירה:
   מסך קטגוריות ו־„＋ New category” בגיליון. להוציא את BudgetViewModel.categories/addCategory
   ואת ExpenseUiData.categories ממסלול הקטגוריות; אין שיקוף חזרה למפה זמנית או fallback.
3. CategoryManagementUi ו־ExpenseSheetDialog יקבלו מודלי Category/בחירה לפי id.
   להחליף גם השוואות הדגשה לפי text ולהחזיק categoryId בטיוטה וב־Bundle.
   עדכוני אפשרויות ייעשו בלי לבנות מחדש amount/date/source/note.
4. אישור יצירה מחכה לתוצאת createCategory; רק בהצלחה בוחרים את ה־id שחזר וסוגרים
   את דיאלוג הקטגוריה. אם emission הרשימה מגיע אחר כך, שומרים את הבחירה ומרנדרים
   אותה כשהרשימה מתעדכנת. אין אובדן טיוטה בזמן loading/failure.
   Cancel/Back ושינוי locale/theme שומרים את חוזה הטיוטה הקיים.
5. לעדכן רק את קריאות נוכחות הקטגוריות בסיכום. המקורות, ההקצאות וכל הוצאות ה־UI
   נשארים זמניים עד שלבי ההעברה שלהם; אין להזין אותם מה־snapshot המתמשך בשלב 5.
   לעדכן אז את קטגוריות preview-model בדפדפן וה־overlay לפי מזהים, כמתוכנן.

### בדיקות שלב 4 ותוצאות

- androidApp/src/test/kotlin/com/example/manymanager/domain/BudgetCalculationsTest.kt:
  6 בדיקות JVM לצבירה מדויקת, קיבוץ לפי IDs/null, ספירה ללא כפל, אפס הקצאה,
  יתרה שלילית וגלישה בקבוצות/יתרה/סכום, כולל ביטול גלישת ביניים.
- androidApp/src/androidTest/kotlin/com/example/manymanager/data/ManyManagerRepositoryTest.kt:
  22 בדיקות מול Room/SQLite אמיתי ובדיקות גבול ViewModel. מכוסות יצירה ותצפית,
  duplicate names, rollback בעזרת triggers של fixtures שנכשלים בכתיבה השנייה,
  בידוד מחשבון, גבולות 10–9, preview ללא כתיבה, אישור מול ערך עדכני,
  30 adjustments מקבילים, תקופות אחרות, תמונות עקביות תחת כתיבות מרובות,
  null category, יתרה שלילית ללא חסימת adjustment, overflow והתאוששות מתצפית Failure,
  המתנה/כשל אתחול וביטול coroutine, loading/data/error ב־ViewModel,
  ביטול ב־clear, בחירת תקופה ורענון מהשעון.
- רשומות fixtures של הוצאות מוכנסות באמצעות DAO/SQL רק בתוך androidTest. לא נוספה
  פעולת שמירת/עריכת/מחיקת הוצאה ל־Repository או ל־UI.

הורצה בהצלחה הפקודה (עם JDK/launcher כפי שתועדו בשלב 2):

```powershell
.\gradlew.bat :androidApp:assembleDebug :androidApp:testDebugUnitTest :androidApp:connectedDebugAndroidTest :androidApp:lintDebug
```

**תוצאות:** בניית Android עברה; כל 39 בדיקות ה־JVM עברו (33 קודמות ו־6 חדשות);
כל 34 בדיקות המכשיר עברו ב־SM-S928B עם Android 17 (22 חדשות ו־12 בדיקות סכמה/מסד קודמות).
אין failures, errors או skipped בדוחות הבדיקות שנבדקו. גם lintDebug עבר ללא errors;
דוח lint כולל 54 אזהרות בפרויקט. הבדיקה נוספה לאחר איתור דרישת API 31 של
BigInteger.longValueExact והחלפתה בבדיקת טווח המתאימה ל־API 30.
לא הורצו בדיקות במכשיר API 30, ולכן אימות התאימות אליו בשלב זה הוא סטטי.
בדיקות shared לא הורצו מחדש בשלב זה.
נבדק שאין diff ב־Entities, בסכמה המיוצאת, ב־MainActivity/BudgetViewModel או ב־ui-reference.
אין אימות חזותי או end-to-end של המסכים במסגרת שלב תשתית זה.

המסכים עדיין פועלים עם המודלים הזמניים, כולל טיוטת הוצאה ופעולות הדיאלוגים.
שמירת הוצאה עדיין מושבתת. הדפדפן נשאר preview בזיכרון.
אין חסם מוצר קונקרטי לפני שלב 5; החלטות מחיקה/ארכוב ומדיניות סכומי הוצאה נשארות
לפני חשיפת הפעולות הרלוונטיות בהמשך.

## מימוש שלב 5 — קטגוריות מתמשכות וחיבור ממשק

**שלב 5 הושלם.** כל קריאות הקטגוריות והיצירה ב־Android הועברו ל־Room-backed repository.
הרשימה הזמנית ב־`BudgetViewModel` וב־`ExpenseUiData` הוסרה לחלוטין.

### קבצים שנפתחו/שונו

- **CategoryState.kt**: הוגדרו `CategoryListState` (`Loading`, `Ready`, `Failed`)
  ואת `CategoryCreationState` בנפרד מזרם הרשימה, כולל המרת `toBundle()`/`fromBundle()`
  והתקשרות שגיאות/טעינה בלוקליזציה.
- **CategoryCreationDialog.kt**: דיאלוג יצירת קטגוריה בנפרד מ־View references, כולל
  `showWithRoundedSurface()`, נעילת כפתורים בזמן כתיבה (`submitting`),
  והצגת שגיאות ממוקדות.
- **CategoryManagementUi.kt**: הציג את `CategoryListState`, טיפל במצבי `Loading`, `Failed` (כולל לחץ לניסיון חוזר)
  ו־`Ready` (כולל `no_categories`), עם שמירת העיצוב הקיים והניווט.
- **PersistentDataViewModel.kt**: הוסיף תצפית ממוקדת `categories` מתוך `observeCategories()`,
  וניהול מצב דיאלוג היצירה (`beginCategoryCreation`, `updateCategoryName`, `submitCategoryCreation`,
  `cancelCategoryCreation`, `acknowledgeCategoryCreation`). `submitCategoryCreation` כולל busy guard
  סינכרוני המונע שתי לחיצות כפולות.
- **ExpenseSheetDialog.kt & ExpenseDraft**: `ExpenseDraft` עודכן להחזיק `categoryId: String?` במקום שם.
  בחירות הקטגוריות והמקורות במיכל הוסבו להשוואה לפי מזהים יציבים (`tag == id`).
  עדכון הקטגוריות בגיליון מרענן רק את אזור הבחירות ושומר על כל שדות הטיוטה (`amount`, `date`, `note`, `sourceId`).
- **MainActivity.kt**: איסוף `categories` ו־`categoryCreation` בעזרת `repeatOnLifecycle(Lifecycle.State.STARTED)`,
  ניהול הדיאלוגים, ושמירה/שחזור `expenseDraft` ו־`categoryCreation` ב־`saveUiState`.
- **BudgetViewModel.kt & ExpenseUiData.kt**: שדות ופעולות הקטגוריות הישנות הוסרו לחלוטין.
- **res/values/ids.xml & strings.xml**: נוספו `categoryNameInput` ומחרוזות מתורגמות לכל השפות (עברית, אנגלית, רוסית)
  עבור טעינה, שגיאות ושמירת קטגוריות.
- **ui-reference/**: עודכנו `js/preview-model.js`, `js/state.js`, `js/components/category-dialog.js`,
  `js/components/expense-sheet.js`, `js/screens/categories.js`, `js/app.js`, `README.md` ו־`UI_MAP.md`
  לתמיכה במזהי קטגוריות יציבים (`category.id`), בחירת categoryId בטיוטה, ותיעוד ההבדל
  בין אחסון Room מתמשך ב־Android לבין companion בזיכרון בלבד בדפדפן.

### תוצאות אימות שלב 5

הורצו בהצלחה הפקודות:

```powershell
.\gradlew.bat :androidApp:assembleDebug :androidApp:testDebugUnitTest :androidApp:connectedDebugAndroidTest :androidApp:lintDebug
```

- **assembleDebug**: עבר בהצלחה.
- **testDebugUnitTest**: כל 39 בדיקות היחידה עברו בהצלחה (0 failures).
- **connectedDebugAndroidTest**: כל 36 בדיקות המכשיר/אינסטרומנטציה עברו בהצלחה ב־SM-S928B עם Android 17.
- **lintDebug**: עבר בהצלחה ללא שגיאות.

### מסירה לשלב 6

שלב 6 יחבר את מקורות התקציב (`BudgetSource`) ל־Repository ולמזהים יציבים בניהול ובגיליון,
וישתמש בבלוקי העסקאות הקיימים מהשכבה של שלב 4.

## מימוש שלב 6 — מקורות תקציב והקצאות מתמשכים

**שלב 6 הושלם.** כל המקורות וההקצאות ב־Android מגיעים מה־Repository שבבעלות
Application. השינויים הלא מחויבים שנמצאו בתחילת העבודה נבדקו והושלמו באותו ענף;
לא נמצאו AGENTS.md בפרויקט או בתיקיות האב שנבדקו. לא שונו הסכמה או גרסאות התלויות.

### קבצים ופעולות

- PersistentDataViewModel.kt: יצירה דרך createSourceWithInitialAllocation ושינוי שם
  והתאמה דרך updateSourceAndAdjustAllocation, כעסקאות הקיימות משלב 4. parsing מדויק
  באגורות בבקר, trim/validation בשכבת הנתונים. busy guard סינכרוני מונע שליחה כפולה.
  מצב הטופס/תוצאת הפעולה נפרדים מהתצפיות. כשל שומר שם וסכום לניסיון מפורש.
- previewBudgetAdjustment אינו כותב. שינוי קלט מבטל בקשה קודמת, מנקה מיד את התוצאה
  ומקדם revision; רק התוצאה של הקלט העדכני מתקבלת. ריק = אפס. אישור מושבת עבור
  preview חסר/שגוי או הקצאה שלילית. האישור עדיין קורא הקצאה עדכנית בתוך העסקה.
  נשמרו יתרה שלילית מותרת, בידוד תקופות ודיווח overflow ללא wrap.
- SourceCreationState ו־SourceEditingState שומרים ב־Bundle את התקופה שנלכדה,
  כל שדות הטופס וסמן פעולה שנקטעה; תוצאת commit שהתקבלה נשמרת כדי שלא תוצג כביטול.
  שחזור לעולם אינו שולח כתיבה אוטומטית. ViewModel קיים שומר פעולה בתהליך שינוי תצורה.
- BudgetSourceDialogs.kt מציג טפסים בלבד, ללא repository/DAO או חישוב התאמות.
  בזמן שליחה השדות, Cancel, Back ולחיצה מחוץ לדיאלוג נעולים. ניתוק חלון בשינוי תצורה
  אינו מבטל/משחזר בקשת כתיבה. הצלחה סוגרת רק אחרי commit; כשל משאיר את הטופס.
- SourceListState.kt ותצפית observeSources עצמאית: Loading/Ready/Failed עבור גיליון
  ההוצאה ונוכחות מקורות בסיכום. שגיאת aggregation כספית אינה מסתירה מקור תקין.
  ExpenseSheetDialog.kt מעדכן רק את אזור המקורות, בוחר לפי sourceId ואינו מאפס טיוטה.
  מקורות ללא הקצאה ובעלי שמות כפולים נשארים בחירות נפרדות.
- BudgetManagementUi.kt ו־BudgetHomeUi.kt מציגים snapshots מוכנים כולל סכומים/יתרות.
  טעינה וכשל אינם הצלחה ריקה; כשל מציע ניסיון חוזר. MainActivity מציג סכומי הוצאות
  וספירה מה־snapshot, ולכן קבוע אפס אינו דורס רשומות הוצאה קיימות.
- MainActivity.kt אוסף תצפיות פעם אחת דרך repeatOnLifecycle(STARTED), מחבר טפסים
  ושומר טיוטה ותקופה נבחרת. ManyManagerApplication.kt מספק שעון שקורא אזור זמן מקומי
  עדכני גם לאחר שינוי אזור זמן.
- MonthlyBudgetAllocations.kt והבדיקות שלו הוסרו; PaymentSource ו־paymentMethods הוסרו.
  ExpenseUiData.kt הוסר לאחר הסרת כל צרכני קבועי האפס. BudgetViewModel נשאר למצב הרחבת הפירוט בלבד.
  אין רשימת מקורות mutable משוקפת או מפת הקצאות זמנית ב־Android.

### תקופות וניווט — עבודה משלב 7 שכבר הושלמה כאן

הקצאה התחלתית, התאמה, תצוגה וניווט משתמשים ב־BudgetPeriod ובהגדרת periodStartDay
השמורה דרך Repository/BudgetPeriodCalculator. הבית משתמש בתצפית תקופה פעילה נפרדת
מהבחירה ההיסטורית בניהול. BudgetPeriodFormatter.kt מציג טווח תאריכים מקומי,
למשל 10 אוקטובר–9 נובמבר לפני 10 בנובמבר. אין חישוב day-10 במסכים או SQL.

כניסה רגילה לניהול בוחרת את התקופה הפעילה. החיצים עוברים בין תקופות; שינוי תצורה
ושחזור Activity שומרים בחירה מפורשת. טפסים שומרים את התקופה שבה נפתחו.
onResume ורסיבר רשום רק בזמן STARTED לאירועי DATE_CHANGED/TIME_SET/TIMEZONE_CHANGED
מרעננים מהשעון; אין jobs ברקע. רענון אינו מחליף בחירה היסטורית או תקופת טופס פתוח.
תקופה חדשה = אפס, המקורות נשארים ואין העתקה או carry-over. חיצי Summary נשארו מושבתים.

### HTML companion

js/budget-period.js מרכז חישוב תאריכים אזרחיים, גבולות, מפתח תקופה וניווט; אין UTC.
preview-model.js שומר מקורות עם IDs והקצאות לפי תקופה בזיכרון בלבד, ומאמת שינוי שם
והתאמה לפני שינוי כלשהו. state.js/app.js, source-dialog.js, render-helpers.js ו־home.js
מחברים ניווט, טווחים, תקופת טופס קבועה ובית של התקופה הפעילה. פירוט ב־UI_MAP.md.
קטגוריות לפי ID וכל שדות הטיוטה נשמרים. reload מאפס הכול; אין localStorage/IndexedDB.
הטווח הכספי בדפדפן מוגבל ל־Number.isSafeInteger ואינו שקול לכל קצוות Long ב־Android.

### אימות שבוצע בפועל

הפקודה הבאה עברה עם JAVA_HOME של Android Studio JBR:

```powershell
.\gradlew.bat :androidApp:assembleDebug :androidApp:testDebugUnitTest :androidApp:connectedDebugAndroidTest :androidApp:lintDebug --console=plain
```

- assembleDebug ו־lintDebug עברו. 30 בדיקות JVM עברו, ללא failures/errors/skipped.
- 42 בדיקות מכשיר עברו ב־SM-S928B / Android 17: 30 Repository/ViewModel ו־12 סכמה/מסד.
  כיסוי קיים כולל rollback בכתיבה שנייה, name+delta אטומיים, preview ללא כתיבה,
  30 התאמות מקבילות ללא lost update, overflow, בידוד תקופות, 9/10, ביטול coroutines.
- בדיקות חדשות ב־ManyManagerRepositoryTest.kt כוללות פתיחת מסד קובץ מחדש עם מקורות,
  הקצאה, rename וקשר להוצאה; שימור קלט בכשל וניסיון מפורש; cancel ללא שינוי;
  busy guard ושחזור טופס בזמן בקשה; פסילת preview ישן/קלט לא תקין; אישור פעם אחת;
  הפרדת הבית מבחירה היסטורית וזמינות רשימת מקורות גם בעת overflow בתקציב.
- tests/stage6.html הורץ ב־Chrome במכשיר דרך HTTP מקומי: **19 בדיקות עברו**.
  נבדקו גבולות 10–9, מעבר שנה, כפילויות ID, atomic validation, תקופות אפס,
  בחירה/שימור טיוטה, יצירת קטגוריה וביטולה, והפרדת הבית מהתקופה בניהול.
- בדיקת UI ידנית ב־Android: יצירת Stage6-UI עם ₪12.34, force-stop ופתיחה מחדש,
  אימות המקור/הקצאה בבית ובגיליון ההוצאה; יצירת Stage6-Category מתוך טיוטה,
  בחירת הקטגוריה החדשה תוך שמירת סכום 7.89, מקור ותאריך. שמירת הוצאה נשארה מושבתת.
  רשומות בדיקה אלו נשארו במכשיר; לא נוקה מסד ולא נמחק מידע קודם.
- נבדקו git diff/status ו־diff --check. בדיקות shared לא הורצו מחדש.
  אין טענה לאימות חזותי מלא בשלוש שפות/שתי ערכות נושא, API 30, שינוי יום/אזור זמן
  אמיתי, או הריגת תהליך באמצע commit. בדיקות הבקר אינן תחליף ל־Activity lifecycle
  end-to-end; סיבוב בזמן כתיבה וטעינת/כשל UI מלאים דורשים הרחבת בדיקות.

### העבודה המדויקת שנותרה לשלב 7

שלב 7 **אינו צריך לממש מחדש** יצירה עם הקצאה, עורך +/−, עסקאות, preview,
הסרת המודל הזמני, ניווט BudgetPeriod או תצפיות הבית/ניהול — אלו כבר מחוברים כאן.

נותרו חיזוק אימות מחזור החיים בפועל: recreate/סיבוב בזמן כתיבה ובזמן preview,
כשל אחסון המוזרק דרך UI עם retry, מעבר 9→10 בחצות בזמן מסך/טופס פתוח,
שינוי שעון ואזור זמן אמיתיים, ושחזור אחרי מות תהליך בבקשה שתוצאתה אינה ידועה.
יש להשלים מטריצת תצוגה ונגישות לתוויות תקופה ו־+/− בשלוש שפות ובשתי ערכות נושא,
כולל API 30. אם מתגלים פערים, מתקנים את החיבור הקיים ולא מוסיפים מקור אמת שני.
אין חסם קונקרטי להמשך שלב 7. פעולות הוצאות נשארות לשלב 8.

## מימוש שלב 7 — הקשחת מחזור חיים ואימות התקציב

**שלב 7 הושלם בהיקף הקשחה ואימות.** יצירת מקור עם הקצאה, עורך +/−, preview,
ניווט BudgetPeriod ותצפיות הבית/ניהול כבר הושלמו בשלב 6 ולא מומשו מחדש.
לא נוספו שמירת הוצאות, סכמה, ספריית DI, אחסון פעולות או מקור אמת נוסף.
הענף בתחילת העבודה היה feature/persistent-data נקי, על 4f797fc.

### פערים קונקרטיים שתוקנו

- **תוצאה לא ודאית אחרי שחזור תהליך:** סמן interrupted הוצג כאזהרה אך אפשר לאשר שוב
  מאותו טופס. PersistentDataViewModel.kt חוסם כעת שינוי ושליחה של טופס כזה, גם ישירות
  דרך action. BudgetSourceDialogs.kt נועל את שדותיו, מציג במפורש שתוצאת השמירה אינה
  ידועה ומציע סגירה ובדיקת הנתונים. הסגירה בוחרת את התקופה שנלכדה ומרעננת את הנתונים
  השמורים; רק התחלת פעולה חדשה במכוון מאפשרת כתיבה. אין ניסיון חוזר אוטומטי.
  טופס שנכשל בתוצאה ידועה ממשיך לשמור קלט ולאפשר retry מפורש, כקודם.
- **Back ובחירה היסטורית:** MainActivity.selectPage איפס את התקופה גם בחזרה דרך
  היסטוריית הניווט. האיפוס הועבר לכניסה מכוונת מהתפריט בלבד. שחזור ובניית views מחדש
  אינם מאפסים תקופה. הוסר גם render מיותר של snapshot ישן בזמן החלפת עמוד.
- **תוצאת חץ מאוחרת:** פתרון תקופה אסינכרוני יכול היה לדרוס בחירה מפורשת חדשה יותר.
  navigationRevision מסנן תוצאה כזאת; לא נוספה תצפית או בעלות נתונים נוספת.
- **callbacks בעת רנדור טופס:** setText בשחזור הפעיל את מאזיני הקלט והתחיל preview
  נוסף גם בלי קלט חדש. רנדור שדות מוגן כעת מ־callbacks. ביטול עורך מקדם גם את
  previewRevision. נשמרו ביטול coroutine וסינון תוצאות לפי הבקשה העדכנית.
- **כשל preview:** כשל אחסון הוצג כ״סכום לא תקין״. מוצגת כעת הודעת טעינה שנכשלה
  עם לחיצה לניסיון חוזר; overflow נשאר שגיאת סכום. משוב טעינה/כשל הוא live region.
- **נגישות כשל בבית:** הוסרו פעולות expand/collapse לא מתאימות ממצב כשל/טעינה;
  במצב כשל הפעולה הזמינה היא retry, ולא פתיחת פירוט ריק.
- **חלון תפריט מאוחר:** בדיקת שחזור מהירה חשפה callback סיום אנימציה שניסה להסיר
  חלון שכבר נותק. SideMenuDialog.kt מסמן סגירה פעם אחת, מבטל end action ומוודא
  שה־Activity לא נהרס ושהחלון עדיין מחובר לפני הסרה.
- **Bundle היסטורי:** epoch-day שלילי תקין (לפני 1970) סווג כחסר. נבדק כעת קיום מפתח
  במקום סימן המספר, ונוספו בדיקות round-trip לשני טופסי המקור.
- **תוויות:** BudgetPeriodFormatter.description מספק תאריכי התחלה וסיום מלאים
  ומתורגמים לקורא מסך בבית, בסיכום, בניהול ובטפסים. בדפדפן טווח שחוצה שנה השמיט
  את שנת ההתחלה; render-helpers.js מציג כעת את שתי השנים ותיאור נגיש בשפות he/ru/en.
  לא השתנו פריסה, פינות מעוגלות, כפתורי Add המשניים או מדיניות תקציב.

### התנהגות קיימת שאומתה ללא בנייה מחדש

ViewModel שורד ActivityScenario.recreate ושומר בקשת כתיבה יחידה ללא Activity/View
בתוך state. חלונות ישנים מנותקים; החלון החדש מציג submitting ואינו מאפשר אישור/ביטול
עד התוצאה. הצלחה נצרכת ב־UI ומסירה את הטופס; recreate נוסף אינו מבצע כתיבה נוספת.
אותם שדות ותקופת טופס נשמרים גם במסלול onConfigurationChanged של האפליקציה.

השעון הדינמי של ManyManagerApplication, הרענון ב־onResume וה־receiver של שלב 6
מספיקים לחישוב תקופה מחדש: בסימולציית 9→10 הבית עבר לתקופה חדשה עם אפס הקצאה,
המקורות נשארו, התקופה ההיסטורית והטופס הפתוח לא השתנו. אישור הטופס כתב רק לתקופה
שנלכדה. החלפת zone בשעון המוזרק באותו instant הפיקה תקופה מקומית מתאימה.
אין חישוב יום 10 חדש במסך/SQL, אין UTC לתאריכים אזרחיים, העתקת יתרות או job ברקע.

כשל name+adjustment שהוזרק באמצעות trigger במסד בדיקה ביטל את שני השינויים,
שמר את הקלט בגלוי, ואישור מפורש אחרי הסרת הכשל החיל את ההפרש פעם אחת.
overflow ב־aggregation הוצג ככשל ולא כאפס; גיליון הוצאה פתוח שמר סכום, הערה,
מקור גם לאחר recreate, ורשימת המקורות נותרה זמינה מהתצפית העצמאית.

### תשתית בדיקה ואימות בפועל

- התשתית הקיימת היא JUnit4, AndroidX Test core/runner, ActivityScenario ו־Room testing;
  UI פעיל הוא XML/Views. אין framework של DI או mocking ואין תשתית screenshot.
  נשמרה התשתית הזאת ללא תלויות חדשות. הוראות testing-setup יושמו בהיקף הממוקד;
  לא הותקנו DI/coverage/screenshot frameworks בניגוד להיקף המשימה.
- **BudgetTestApplication.kt** ו־DatabaseTestRunner.kt מאפשרים לפתוח את MainActivity
  האמיתי עם Room in-memory, SharedPreferences בעלי prefix ייעודי ושעון מוזרק.
  ManyManagerApplication נפתח להרחבה רק בגבולות database/repository לצורך החלפה
  בבדיקות. אין מתג debug בייצור או רישום שירותים גלובלי. לא נפתח/נוקה מסד הייצור.
- **BudgetLifecycleTest.kt:** שבע בדיקות מכשיר חדשות עם חלונות ו־Views מחוברים,
  recreate אמיתי, שימור כתיבה מושהית באמצעות עסקת בדיקה, preview מושהה/מבוטל,
  כשל כתיבה ו־retry, תוצאת Bundle לא ידועה בשני הטפסים, Back/כניסה מהתפריט,
  חץ ישן מול בחירה חדשה, rollover/zone והישרדות טיוטת הוצאה בכשל aggregation.
- מטריצת שפות he/ru/en ו־RTL/LTR בשתי ערכות נושא נבדקה בדיאלוגים אמיתיים ברוחב
  320dp: גובה/רוחב הטקסט של טווח, תקציב ו־preview אינם נחתכים, הטווח החוצה שנה
  מכיל שתי שנים, ל־+/− תיאור ומשמעות בחירה נגישים. נבדק במפורש uiMode של חלון
  הדיאלוג; AppCompat יורש את מצב ה־Activity, ולכן הבדיקה משחזרת Activity עם
  העדפת מראה מבודדת ולא מסתפקת ב־Context wrapper.
- הפקודה המלאה עברה עם JBR של Android Studio:
  `assembleDebug testDebugUnitTest connectedDebugAndroidTest lintDebug`
  (כולן עם קידומת :androidApp:). **30 בדיקות JVM ו־49 בדיקות מכשיר עברו**, ללא
  failures/errors/skipped, ב־SM-S928B / Android 17. אחרי הרחבת בדיקות אי־הוודאות
  והוספת בדיקת תקופה לפני 1970 הורצו שוב רק שבע בדיקות BudgetLifecycleTest ו־lint;
  עברו. בדיקות shared לא הורצו מחדש.
- browser: tests/stage6.html הורחב באותו קובץ ל־**27 בדיקות** והורץ ב־Chrome במכשיר
  דרך שרת HTTP מקומי. כולן עברו, כולל שלוש שפות, שתי שנים בטווח, תיאור נגיש,
  אישור בתקופה שנלכדה לאחר rollover וביטול. בדיקות הקטגוריה וכל שדות הטיוטה נשמרו.
  preview נשאר בזיכרון ומתאפס ב־reload; אין emulation של lifecycle Android.
- git diff/status ו־diff --check נבדקו. לא הופעלו פקודות clear-data/uninstall או
  שינוי שעון/אזור זמן. fixtures כספיים היו במסדים מבודדים בלבד.
- **ממצא חשוב בניקוי כלי הבדיקות:** אחרי connectedDebugAndroidTest נמצאה חבילת
  האפליקציה לא מותקנת. בדיקת מקורות AGP 9.5.0 שב־Gradle cache אימתה כי
  ANDROID_TEST_LEAVE_APKS_INSTALLED_AFTER_RUN הוא false כברירת מחדל, וה־runner
  מסיר בסיום את ה־APKs שהתקין. ברירת המחדל הזאת לא נבדקה לפני ההרצות הראשונות.
  gradle.properties מגדיר כעת android.injected.androidTest.leaveApksInstalledAfterRun=true.
  זו הגנת כלי בדיקה נחוצה לשמירת התקנות עתידיות, ללא שינוי קוד הייצור או סכמה.
  מצב נתוני ההתקנה לפני ההרצה הראשונה לא נלכד; אין גיבוי ידוע ואין אפשרות לאשר
  שרשומות ההתקנה הקודמת נשמרו או לשחזר אותן. אין לטעון שמסד בדיקה מבודד לבדו
  מגן מפני ניקוי התקנה שמבצע runner חיצוני.
  אחרי תיקון ההגדרה הורצו שוב שבע בדיקות ה־Activity בהצלחה; pm אישר ששתי החבילות
  נשארו מותקנות, ו־run-as test -f החזיר exit=0 עבור קובץ ההעדפות המבודד
  stage7-test-appearance.xml. האפליקציה נפתחה ידנית לאחר מכן; כותרת התקופה
  והתיאור הנגיש בעברית היו תקינים. הוצג תקציב אפס, ללא fixtures מהבדיקות.
  אין להסיק מהתקנה זו שנתוני התקנה קודמת שוחזרו.

### מגבלות האימות וההבטחות

- מעבר חצות/zone נעשה עם שעון מוזרק וקריאה מדומה ל־callback של ה־receiver האמיתי.
  לא שונה שעון המכשיר ולא אומתה מסירת broadcast של המערכת בחצות אמיתי.
- Activity recreation נבדק בפועל; process recreation נבדק באמצעות שחזור Bundle.
  לא נהרג תהליך באמצע commit. Bundle אינו journal מתמשך: force-stop או הריגה שרירותית
  אינם מבטיחים שישוחזר טופס בכלל, או שה־Bundle האחרון מתאר את הרגע שבו בוצעה כתיבה.
  גם Bundle ישן מלפני submission אינו יכול להעיד על כתיבה מאוחרת יותר.
  אין טענה ל־exactly-once בין תהליכים. הבטחה חזקה יותר דורשת החלטת מוצר וסכמה
  עם מזהה בקשה/רישום פעולות; לא נוספה מערכת כזאת בשלב זה.
- המטריצה היא בדיקת פריסה ומשמעות נגישה במכשיר, לא בדיקת screenshot/pixel parity
  ולא האזנה ידנית מלאה ל־TalkBack. מקלדת, font-scale מוגדל וכל גדלי החלון לא נבדקו.
  אין סביבת API 30 זמינה (מחובר מכשיר Android 17 בלבד); לא הותקן אמולטור חדש.
- בדיקות Android חדשות היו אוטומטיות מול Activity/Dialog; בדיקת smoke ידנית בוצעה
  לבית בעברית בלבד. אין לייחס לכך בדיקה ידנית מקצה לקצה של כל השפות. הפעלת דף הבדיקות ותוצאותיו בדפדפן נבדקו דרך UI המכשיר.

### מסירה לשלב 8 — החלטות המוצר שנותרו

לפני חשיפת פעולות הוצאות יש להכריע רק:
1. האם מותר סכום הוצאה אפס.
2. האם וכיצד מיוצגים סכומים שליליים/החזרים.
3. מהן נקודות הכניסה בממשק לעריכת הוצאה ולמחיקתה.

categoryId אופציונלי ו־sourceId חובה כבר הוסכמו ואינם נפתחים מחדש.
שמירת הוצאות עדיין מושבתת. אין חסם טכני חדש; החלטות אלה נדרשות לפני מימוש המדיניות
והפעולות של שלב 8. אין לחזור בשלב הבא על חיבורי המקורות/ההקצאות שהושלמו בשלב 6.

## מימוש שלב 8 — שמירת הוצאות ותיקון סכום

**שלב 8 הושלם בהיקף המאושר של שמירה ותיקון סכום בלבד.**

### המדיניות המאושרת והיקף

החלטות המשתמש מחליפות את ההצעה ההיסטורית ל־CRUD מלא ואת שאלות המסירה משלב 7:
סכום הוצאה חייב להיות גדול מאפס; אפס, סכום שלילי והחזרים אינם נתמכים.
קטגוריה אופציונלית (null = ״ללא קטגוריה״, כיתוב מתורגם ללא רשומה סינתטית);
מקור חובה ושייך לאותו מחשבון. מקור באפס הקצאה נשאר שמיש ומותרת יתרה שלילית.
לחיצה על שורה ב״כל ההוצאות״ פותחת תיקון סכום בלבד. לא נוספו מחיקה או עריכת
מקור, קטגוריה, תאריך או הערה. אין חסם מוצר חדש לפני שלב 9.

### פעולות וקבצים

- domain/Models.kt מוסיף NewExpense עם Long באגורות ו־LocalDate; שגיאות מובחנות
  לקלט לא תקין, סכום שאינו חיובי ותאריך שאינו ניתן לשיוך לתקופה.
- data/ManyManagerRepository.kt מוסיף createExpense ו־correctExpenseAmount.
  הקריאה, אימות בעלות המקור/קטגוריה/הוצאה והכתיבה נמצאים באותה Room transaction.
  לאחר הכתיבה ולפני commit נבנה snapshot של תקופת התאריך דרך BudgetPeriodCalculator;
  כל סכום חשוף, קבוצה ויתרה חייבים להתאים ל־Long. overflow או כשל מבטלים את העסקה.
  correction הוא UPDATE amountMinor בלבד, באותה רשומה; אין insert חלופי או totals cache.
- data/local/ManyManagerDao.kt מוסיף UPDATE scoped ומסדר הוצאות dateEpochDay DESC, id.
  אין סכמה, טבלה, תלות או מיגרציה חדשה.
- BudgetAmounts.kt משתמש באותו parser מדויק ומוסיף תוצאה typed ומצב positive.
  אין parsing או חישוב כספי ב־Double.
- ExpenseState.kt מעביר את הטיוטה למודל בלתי משתנה בבעלות PersistentDataViewModel:
  amount text, LocalDate, note, categoryId, sourceId. מצבי יצירה ותיקון נפרדים מתצפיות.
- ExpenseSheetDialog.kt מרנדר את מצב הפעולה, שומר שדות בעת עדכון בחירות, ומאפשר
  קטגוריה null. התאריך נבחר בבורר Android מקומי ונשמר ב־Bundle כ־epoch-day;
  הוא אינו מפורש ממחרוזת מקומית או דרך UTC. טיוטה חדשה מתחילה בתאריך המכשיר המקומי.
  בחירת קטגוריה חדשה אינה משנה תאריך/סכום/מקור/הערה. כפתור Save נפתח רק בקלט
  תקין ומקור זמין; המאגר מאמת מחדש בתוך העסקה.
- ExpenseCorrectionDialog.kt משתמש באותו rounded surface של טופסי הניהול, עם
  שדה סכום בלבד, Cancel ואישור יחיד. שפות he/ru/en, צבעי theme ו־RTL/LTR נשמרים.
- MainActivity.kt אוסף את שני מצבי הפעולה דרך repeatOnLifecycle ומנהל חלונות בלבד.
  AllExpensesUi.kt מציג את הרשומות מה־active snapshot עם סכום, תאריך, שם קטגוריה
  או ״ללא קטגוריה״, מקור והערה; identity נשאר ID גם בשמות כפולים.
  טעינה/ריק/כשל מובחנים וכשל מציע retry. חיצי Summary נשארים מושבתים.
- BudgetHomeUi.kt מציג יתרה לכל מקור מה־snapshot, והבית והסיכום מציגים סך/ספירה
  אמיתיים. הקצאות אינן משתנות בעת שמירה או תיקון. תצוגות הפירוק שטרם חוברו
  אינן טוענות ״אין הוצאות״ כאשר קיימות רשומות; השלמת הפירוקים היא שלב 9.
- ui-reference: preview-model.js מוסיף הוצאות באגורות ומוטציות עם בדיקה לפני שינוי;
  snapshotFor גוזר סכומים/יתרות/רשימה. budget-period.js מרכז parsing/formatting
  של civil dates. app.js, expense-sheet.js, expense-correction.js, home.js ו־summary.js
  מחברים שמירה, תיקון, רשימה וסכומים. category-ID וטיוטה מלאה נשמרים.
  זהו preview בזיכרון בלבד; reload מאפס הכול. אין IndexedDB/localStorage/repository ייצור.

### מחזור חיים ותוצאות לא ודאיות

ה־ViewModel מחזיק busy guard סינכרוני לפני coroutine. בזמן כתיבה שדות וביטול
נעולים, וחלון חדש מציג אותה פעולה ממתינה ללא שליחה נוספת. Activity ישן מנתק
listeners ומסיר חלונות; אין View/Dialog/Activity בתוך ה־ViewModel.
הצלחה מוצגת רק לאחר commit ונצרכת בהסרת הטופס. כשל ידוע משאיר קלט ומאפשר retry מפורש.
ביטול לפני submission אינו כותב. CancellationException נשמרת ואינה הופכת לכשל רגיל.

Bundle של פעולה שסומנה submitting משוחזר כ־interrupted, בלי retry או שינוי שדות.
מוצגת אי־ודאות מפורשת; סגירה מפנה לכל ההוצאות ורענון, ורק פעולה חדשה במכוון אפשרית.
אין הבטחת exactly-once אחרי מות תהליך שרירותי: Bundle אינו journal מתמשך ואינו
מובטח אחרי force-stop. Bundle ישן מלפני submission אינו מוכיח שלא בוצעה כתיבה אחריו.
לא נוסף מזהה פעולה מתמשך. שדות restorable אינם הבטחה לשחזור טיוטה durable.

### אימות שלב 8

- assembleDebug, testDebugUnitTest ו־lintDebug עברו עם JBR של Android Studio.
  31 בדיקות JVM עברו (6 parsing, 6 calculations, 19 period); shared לא הורץ מחדש.
- בהרצת connectedDebugAndroidTest המלאה עברו 36 בדיקות Repository/controller ו־12 בדיקות
  מסד/סכמה. 8 מתוך 9 בדיקות Activity עברו; בדיקת rollover קיימת חשפה תנאי המתנה
  שקיבל גם Loading בגלל השוואת null. תוקן תנאי הבדיקה לדרוש Ready לפני ההשוואה.
  הרצה ממוקדת של כל 9 בדיקות BudgetLifecycleTest עברה, ולאחר הרחבת בדיקות כשל/שחזור
  הורצו שוב כל ה־9 יחד עם build/unit/lint בהצלחה.
- נוספו 6 בדיקות Repository/controller: עם/בלי קטגוריה, אפס/שלילי, קשר חסר/זר,
  מקור אפס וחריגה מותרת, גבול 9/10 לפי תאריך, correction באותו ID ושמירת שדות,
  overflow/trigger rollback, תצפית ללא ספירה כפולה, פתיחה חוזרת של מסד קובץ מבודד,
  ושחזור submitting שחוסם שליחה. בדיקת Bundle הקודמת הוסבה לתאריך LocalDate.
- שתי בדיקות Activity חדשות מפעילות MainActivity, חלונות ו־Views אמיתיים: יצירת קטגוריה
  בטיוטה מלאה; Save ולחיצות חוזרות; recreate ו־onConfigurationChanged בזמן עסקת יצירה
  מושהית; פתיחת שורה, ביטול תיקון, כשל תיקון ושימור קלט, retry, recreate במהלך תיקון
  וחסימת resubmission. בשני סוגי הטופס נבדק שחזור Bundle לא ודאי שאינו כותב שוב.
  בדיקת כשל יצירה כוללת retry מפורש ושורת ״ללא קטגוריה״; לאחר הרחבה זו הורצה
  בדיקה יחידה זו שוב בהצלחה, ללא חזרה על יתר הסוויטות.
- בדיקות אחסון משתמשות רק ב־Room in-memory או בקובץ בשם expense-reopen-UUID.db
  שנמחק בניקוי שלו בלבד. fault injection הוא trigger במסד הבדיקה בלבד.
  android.injected.androidTest.leaveApksInstalledAfterRun=true נקרא ואומת לפני instrumentation.
  pm path אישר שהאפליקציה הייתה מותקנת לפני ההרצות וששתי חבילות ה־APK נשארו מותקנות אחריהן.
  לא הופעלו clear-data/uninstall ולא נפתח/נוקה מסד הייצור.
- Chrome במכשיר דרך HTTP המקומי הריץ tests/stage8.html: כל 32 הבדיקות עברו.
  הן כוללות 26 בדיקות מודל/רכיבים ועוד 6 בדיקות app.js אמיתיות ב־iframe מבודד:
  יצירת מקור, טיוטה/קטגוריה, Save, עדכון הבית, רשימה, ביטול ואישור תיקון.
  גם כל 27 בדיקות stages 6–7 ב־tests/stage6.html עברו שוב.
  תוצאות PASS נקראו דרך UI המכשיר. אין כאן אחסון דפדפן מתמשך.
- אין סביבת API 30 זמינה; לא הותקן אמולטור. לא נבדקו הריגת תהליך אמיתית באמצע commit,
  force-stop recovery, חצות/שינוי zone אמיתיים, TalkBack ידני מלא, font scale מוגדל
  או מטריצת screenshots מלאה לטפסי ההוצאה החדשים. שחזור process נבדק ב־Bundle בלבד.
  בדיקות Activity הן אינטגרציה אוטומטית ולא בדיקה ידנית מלאה של כל השפות וה־themes.
  מטריצת השפות/מראה הקיימת של טופסי התקציב ממשיכה לעבור.
- ההיקף הפעיל של ״כל ההוצאות״ הוא התקופה הפעילה בלבד. הוצאות שנשמרו בתאריך היסטורי
  נשמרות ומשויכות נכון ונבדקות במאגר, אך ניווט סיכום היסטורי אינו נפתח בשלב זה.

### מסירה לשלב 9

להשלים הצגת פירוקים לפי קטגוריה (כולל null) ומקור ואת בדיקות העקביות הרחבות
בין שלוש כרטיסיות הסיכום, הבית, ניהול תקציב וגיליון פתוח.
ה־Repository כבר מחשב totals/groups/remaining; אין לבנות חישוב מקביל או מקור אמת נוסף.
שמירה, תיקון סכום, רשימה וסיכומים בסיסיים חוברו בשלב 8 ואין לממשם שוב.
מחיקה, החזרים ועריכת שדות נוספים נשארים מחוץ להיקף; הרחבתם בעתיד דורשת החלטת מוצר.
