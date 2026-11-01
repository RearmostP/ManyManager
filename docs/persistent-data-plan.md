# תוכנית אחסון מתמשך — ManyManager

תוכנית לעשרה שלבים, עם ממצאי הבסיס של שלב 1 ומעקב מימוש של שלב 2.
ממצאי הקוד בסעיף הבסיס מתארים את המצב לפני שלב 2; מצב התשתית העדכני מתועד בסוף המסמך.
שמירת הוצאות וחיבור המסכים למסד עדיין אינם ממומשים.

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

### 8 — יצירה, עריכה ומחיקת הוצאות

**תוצאה:** save מופעל כשיש קלט תקין; CRUD מתמשך לפי id, כולל בחירת category/source ותאריך.
**קיימים:** ExpenseSheetDialog.kt, MainActivity.kt, BudgetViewModel.kt, dialog_expense.xml,
page_summary.xml/panel_all.xml והתרגומים במידה שנדרש לחיבור פעולות.
**חדשים:** בקר/renderer ממוקד לרשימת הוצאות, למשל ExpenseListUi.kt, ובדיקות CRUD.
**תלות:** 4–7; החלטות אפס/החזר/סכום שלילי וכללי עריכה/מחיקה.
קטגוריה אופציונלית כבר הוכרעה בשלב 2.
**בדיקה:** אגורות מדויקות, תאריכים משני צדי הגבול, תיקון תאריך שמעביר תקופה,
הערה ריקה, כשל FK, restart, מחיקה והחזרת totals לתוצאה נכונה.
מקור בהקצאת אפס והוצאה שמביאה ליתרה שלילית חייבים לעבוד.
**פתוח:** אין כרגע עיצוב מאושר לכניסה לעריכה/מחיקה. משתמשים ברשימה ובגיליון הקיימים
ככל האפשר, ומכריעים affordance לפני מימוש בלי להמציא מסך חדש.
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
| החזרים, אפס או הוצאה שלילית? | parser תקציב דוחה סימן שלילי, אך אינו מדיניות הוצאות. אין כתיבת הוצאות כיום. | לפני ולידציית הוצאות בשלב 8; לא להוסיף CHECK מגביל בשלב 2. |
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

אין כעת Repository, חישוב יום 10, חיבור ViewModel למסד או שמירת הוצאות בממשק.
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
