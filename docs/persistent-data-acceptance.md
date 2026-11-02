# קבלת אחסון מתמשך — ManyManager

תאריך: 2026-11-02. ענף: `feature/persistent-data`.

## מצב

ההיקף המקומי המוסכם ממומש. שלבים 1–9 הושלמו; ניקוי ותיעוד שלב 10 הושלמו,
ובהרצה חוזרת לאחר חזרת החיבור **כל 61 בדיקות המכשיר עברו**, כולל שימור הסכמה המחוזק.
הכשלים הקודמים נשארים מתועדים. בהכנת release ‏0.1.0 נוסף והושלם גם התרחיש הרציף
file reopen→UI: כל 62 בדיקות המכשיר עברו (37 Repository/controller, 12 מסד ו־13 Activity).
עשרת שלבי ההיקף המקומי המוסכם הושלמו, בכפוף למגבלות האימות המפורשות להלן.

## יכולות וכללים מאושרים

- מחשבון מקומי אחד, קטגוריות ומקורות מתמשכים בעלי UUID ובעלות calculatorId.
  שמות כפולים מותרים; שינוי שם אינו משנה זהות או הפניות.
- הקצאות לפי מקור ותקופה; יצירת מקור והקצאה התחלתית ושינוי שם עם התאמה אטומיים.
  הקצאה אינה שלילית לפי הכלל הקיים. אפס הקצאה מותר ואינו חוסם הוצאות.
- הוצאה חיובית בלבד: Long באגורות, תאריך אזרחי, מקור חובה, קטגוריה אופציונלית והערה.
  null הוא ״ללא קטגוריה״ מתורגם, ללא רשומה סינתטית.
- תיקון סכום בלבד באותה רשומה; זהות, ספירה, תאריך, מקור, קטגוריה והערה נשמרים.
- totals, balances, counts ושני הפירוקים נגזרים מהעובדות, ללא טבלת יתרות או cache mutable עצמאי.
  מותרת יתרה שלילית. שינוי הקצאה אינו משנה הוצאות.
- BudgetPeriodCalculator מספק גבולות [start,endExclusive), כברירת מחדל 10–9;
  אין העתקה או נשיאה לתקופה הבאה. Home/Summary פעילים בנפרד מהבחירה בניהול.
  טופס תקציב שומר את התקופה שבה נפתח; תאריך ההוצאה קובע את תקופתה.
- טעינה, ריק וכשל מובחנים. פעולה ממתינה מוגנת מלחיצות כפולות; כשל ידוע שומר קלט.
  יצירת קטגוריה מתוך הוצאה שומרת סכום, תאריך, מקור והערה ובוחרת את המזהה החדש רק בהצלחה.

## ארכיטקטורה וסקירת שלמות

נתיבי Kotlin יחסיים ל־`androidApp/src/main/kotlin/com/example/manymanager/`:

| אחריות | קבצים |
| --- | --- |
| בעלות ואתחול | ManyManagerApplication.kt |
| פעולות עסקיות ו־Flow | data/ManyManagerRepository.kt |
| Room, מפתחות וקשרים | data/local/{ManyManagerDatabase,Entities,ManyManagerDao}.kt |
| כסף ותקופות | domain/{Models,BudgetCalculations,BudgetPeriod,BudgetPeriodCalculator}.kt, BudgetAmounts.kt |
| state וטפסים | PersistentDataViewModel.kt, ExpenseState.kt, CategoryState.kt, SourceListState.kt |
| הצגה | MainActivity.kt, BudgetHomeUi.kt, BudgetManagementUi.kt, AllExpensesUi.kt, SummaryBreakdownUi.kt |

חיפוש DAO/Room במסלול הייצור מצא שימוש עסקי דרך Repository בלבד; החריג המכוון הוא
אתחול המחשבון ב־Application. המסכים אינם ניגשים למסד. BudgetViewModel מחזיק הרחבה בלבד.
ExpenseUiData, PaymentSource ו־MonthlyBudgetAllocations כבר הוסרו בשלבים קודמים;
חיפוש שימושים לא מצא שרידים. נשמרו מצב UI, משאבי שפות/themes/accessibility ונתוני preview.
לא נמצא כשל ייצור קונקרטי שהצריך שינוי התנהגות או סכמה. הניקוי הנוכחי מתקן תיעוד מיושן
ב־README ואת שם בדיקת הסכמה שהציג עדיין את מדיניות הסכומים כלא מוכרעת.

כל פעולה עסקית רצה ב־Room transaction; הצלחה מוחזרת אחרי commit. סכומי ביניים מחושבים
ב־BigInteger ותוצאות חשופות נבדקות מול Long. יצירה ותיקון הוצאה בונים snapshot לפני commit;
overflow או כשל כתיבה מבטלים את הפעולה. יצירת מקור ושינוי שם/תקציב שומרים על אטומיות
ומאמתים סכום הקצאות. תיקון הוצאה הוא UPDATE amountMinor בלבד. ביטול coroutine אינו הצלחה.
הגנת FK מורכבת מונעת קשר בין מחשבונים ומגינה מפני orphan; sourceId אינו nullable.

אין רשת בצינור הנתונים. גם ה־manifest הממוזג של debug שנבדק אינו מבקש INTERNET.
זהו אימות קוד/תצורה, ללא שינוי קישוריות המכשיר וללא טענה להרצת מכשיר במצב offline.
ה־HTML טוען רק קובצי locale מקומיים ועובד עם preview בזיכרון, ללא localStorage/IndexedDB.

## מסד, שימור ושדרוג עתידי

Room נשאר בגרסה 1, חמש ישויות; הקובץ `databases/many-manager.db` נוצר ב־app-private storage.
הסכמה: `androidApp/schemas/com.example.manymanager.data.local.ManyManagerDatabase/1.json`,
identityHash: `bafcc93374123a999f237711f0f81500`. אין diff בסכמה ואין destructive fallback,
מחיקת מסד, seed עסקי או מיגרציה פיקטיבית. אתחול INSERT IGNORE אידמפוטנטי ואינו דורס הגדרות.
סימן הסכום נאכף ב־Repository; סכמה 1 עצמה אינה כוללת CHECK לחיוביות. לא נוספה מגבלה חדשה.

ראיות קודמות כוללות התאמת JSON ל־entities, reopen של קטגוריות/מקורות/הקצאות/הוצאות,
ואתחול ששומר הגדרות. בשלב 10 חוזקה אותה בדיקת JSON: מזינים את כל סוגי הרשומות, לרבות
קטגוריה null והגדרות 15/EUR, **לפני** פתיחת Room, ומוודאים שימור. הבדיקה עברה בהרצת
המכשיר החוזרת. אין זו בדיקת migration בין גרסאות.

בשינוי סכמה אמיתי בעתיד: להגדיל version, לשמור סכמות ישנות וחדשות, לרשום migration
אמיתית (או auto migration מתאימה), ולבדוק שדרוג מקבצים ישנים מאוכלסים, לרבות IDs,
בעלות, קשרים, כסף, תאריכים והגדרות. כשל שדרוג אינו הרשאה למחיקת נתוני משתמש.

## אימות שבוצע בשלב 10

| בדיקה | תוצאה בפועל |
| --- | --- |
| assembleDebug, testDebugUnitTest, lintDebug | עברו; משימות שלא השתנו היו UP-TO-DATE. דוחות 32 בדיקות JVM קיימות: 0 failures/errors/skipped |
| assembleDebugAndroidTest ו־lintDebug אחרי שינוי הבדיקה | קומפילציית הבדיקה, אריזת APK ו־lint הסתיימו בהצלחה |
| connectedDebugAndroidTest | ניסיון שלישי עבר עם 61 tests; בהכנת 0.1.0 עברו כל 62 tests כולל reopen→UI, ללא failures/errors/skipped, SM-S928B / Android 17. הכשלים הקודמים מתועדים להלן. |
| browser tests/stage6.html | ALL 27 PASSED ב־Edge headless, פרופיל זמני נפרד |
| browser tests/stage8.html | ALL 32 PASSED באותה שיטה, פרופיל נפרד |
| browser tests/stage9.html | ALL 35 PASSED באותה שיטה, כולל אירועי app אמיתיים ב־iframe |
| סקירת manifest/Room/DAO/Repository ושימושים | ללא צורך בשינוי ייצור; אין INTERNET ואין סכמה חדשה |
| git diff --check וסקירת diff/status | עברו; השינויים מוגבלים לתיעוד ולבדיקת שימור סכמה |

פקודות Android הופעלו עם JAVA_HOME של Android Studio JBR. פקודת הבסיס:
`./gradlew.bat :androidApp:assembleDebug :androidApp:testDebugUnitTest :androidApp:lintDebug`.
הפקודה השנייה כללה `assembleDebugAndroidTest lintDebug connectedDebugAndroidTest`, עם קידומת
`:androidApp:` ו־`--console=plain`. אין לייחס לדגל BUILD SUCCESSFUL מעבר של instrumentation.
בדיקות דפדפן הורצו משרת HTTP על 127.0.0.1, ללא framework חדש; DOM התוצאות נקרא.
ניסיון headless ראשון ללא pipe לא החזיר פלט ולא נספר; ההרצות עם לכידת stdout סיפקו PASS.
ה־IDE זיהה טיפוס arrayOf מעורב בבדיקה החדשה; נוסף Any מפורש לפני קומפילציה מוצלחת.

## ראיות קיימות שמוחזרו ותרחיש קבלה

לא שוכפלה סוויטת end-to-end חדשה. הבדיקות הקיימות וההרצות שתועדו בשלבים 7–9 מספקות:
37 Repository/controller, 12 מסד ו־12 Activity/Dialog שעברו ב־SM-S928B / Android 17
(כולל rerun ממוקד אחרי תיקוני בדיקות). לאחר מכן אותה סוויטה הורצה מחדש בשלב 10:
דוח XML עדכני מאשר 61 בדיקות, ללא failures/errors/skipped, לרבות בדיקת הסכמה המחוזקת.

| חלק בתרחיש מבודד | כיסוי קיים |
| --- | --- |
| מסד ריק, מחשבון יחיד וללא seed; category/source/initial allocation | ManyManagerDatabaseTest, ManyManagerRepositoryTest |
| הוצאה עם/בלי קטגוריה, מקור אפס ויתרה שלילית | positiveExpensesWithAndWithoutCategoryUseDatePeriodAndAllowOverspending |
| Home, פירוט בית, ניהול, שני פירוקי Summary וכל ההוצאות | summaryHomeManagementAndDraftConvergeOnSameCommittedFacts |
| correction באותו ID וכל השדות האחרים; rename/adjustment ללא שינוי spending | correctionUpdatesOnlySameRecordAndObservationNeverDoubleCounts; breakdownsFollowCommittedCorrectionsRenamesAndBudgetAdjustments |
| reopen עם רשומות שמורות | recordsSurviveClosingAndReopeningFileDatabase; repositorySourcesAllocationsAndRelationshipsSurviveDatabaseReopen; savedExpenseAndCorrectionSurviveIsolatedFileDatabaseReopen |
| 9/10, מעבר שנה, תקופה ללא copying; captured/historical selection | בדיקות domain, rolloverAndTimezoneRefreshKeepHistoricalSelectionAndCapturedForm, browser stage6/9 |
| קטגוריה מתוך טיוטה מלאה, cancellation, invalid input, failure/retry | expenseSaveRecreationCategoryDraftAndAmountCorrection, failedExpenseWritePreservesInputAndUnknownRestoreCannotSubmit, בדיקות טרנזקציות ו־busy guard |
| stale preview וכתיבה בזמן שינוי תצורה | latestPreviewDisablesInvalidInputAndAdjustmentCannotBeAppliedTwice ובדיקות BudgetLifecycleTest |

בהכנת release ‏0.1.0 נוספה fileBackedRecordsReopenAndDisplayInFreshActivity ב־BudgetLifecycleTest:
היא פותחת acceptance-ui-UUID.db ריק, מאתחלת מחשבון, יוצרת דרך Repository קטגוריה,
שני מקורות (אחד באפס), הקצאה ושתי הוצאות עם/בלי קטגוריה; סוגרת Room ופותחת אותו מחדש.
Repository חדש על אותו קובץ מוזרק רק ל־BudgetTestApplication, ואז נוצרת MainActivity חדשה
עם ViewModel חדש. נבדקים אותם IDs, שדות ושמות, הקצאה 10000, spending ‏1500,
יתרה 8500, שורות שתי קבוצות הסיכום ורשימת ההוצאות, ומוצג סכום הבית גם לאחר recreate נוסף.
הקובץ נמחק רק בניקוי ה־UUID של אותה בדיקה. העדפות הבדיקה נשארות עם prefix מבודד.
הבדיקה עברה בתוך סוויטה מלאה של 62 tests, ללא failures/errors/skipped, ב־SM-S928B / Android 17.
זהו reopen אמיתי של קובץ ואחריו UI חדשה באותו תהליך; לא בדיקת מות תהליך או restore מגיבוי.

## בטיחות בדיקות ותקרית ההתקנה

לפני instrumentation נקרא ואומת `android.injected.androidTest.leaveApksInstalledAfterRun=true`.
DatabaseTestRunner מחליף Application ב־BudgetTestApplication: Room in-memory והעדפות
עם prefix ייעודי. בדיקת reopen→UI מחליפה זמנית את ה־Repository רק ב־Application של הבדיקות.
בדיקות קובץ משתמשות בשמות UUID ומוחקות רק קבצים שהן עצמן יצרו.
הבדיקות אינן פותחות את many-manager.db. נבדקו fixtures וה־runner לפני ההרצה.

בדיקת ADB הראשונית לא מצאה מכשיר ולכן לא ניתן היה לתעד מצב התקנה התחלתי.
Gradle גילה מאוחר יותר מכשיר אלחוטי והתחיל ניסיון התקנה; בדיקת pm במקביל לכשל החיבור
החזירה closed/offline. כשהחיבור חזר, pm path אישר ששתי החבילות מותקנות, לפני ניסיון שני.
ההרצה השנייה נכשלה מיד ב־No connected devices! (exit=1), ללא בדיקות.
לאחר שהמשתמש דיווח שהאפליקציה רצה, אומתו מחדש החיבור, שתי החבילות והגדרת השארת
ה־APK מותקן. ההרצה השלישית עברה עם 61 בדיקות; pm path אישר את שתי החבילות גם בסיומה.
לא הופעלו clear-data/uninstall, לא שונו שעון, zone או רשת ולא נמחק מסד משתמש.
נוכחות החבילות אומתה אחרי ניסיון ההתקנה הראשון; שלמות נתוני הייצור לא נבדקה.
אין ראיה לאובדן נתונים ואין להסיק מנוכחות APK שכל נתוני ההתקנה אומתו.

## מחזור חיים, נגישות ומה לא נבדק

Reopen של SQLite מאמת אחסון; ActivityScenario.recreate מאמת יצירת Activity מחדש באותו
תהליך; שחזור Bundle מדמה שחזור state אך אינו הריגת תהליך אמיתית. אין השוואה ביניהם.
בדיקות קודמות מכסות כתיבות מושהות, duplicate taps, שגיאות, preview ישן ורענון שעון מוזרק.
Bundle שמסומן submitting הופך ל־unknown outcome, ללא replay; אין בדיוק־פעם־אחת אחרי
מות תהליך שרירותי ואין הבטחה לשחזור טיוטה לאחר force-stop או מ־Bundle ישן.

קיימות בדיקות גאומטריה/סמנטיקה ברוחב 320dp, he/ru/en, RTL/LTR ו־light/dark
לדיאלוגי תקציב ולשמות ארוכים בפירוקים. אין מטריצה ידנית מלאה לכל טופסי ההוצאה.
לא בוצעו בשלב 10: API 30 runtime, TalkBack מלא, גופן מוגדל, מקלדת בפועל,
pixel parity Android/HTML, restore מגיבוי, חצות/zone אמיתיים, או הריגת תהליך בזמן commit.
לא הותקנה סביבת אמולציה חדשה. בדיקות shared לא הורצו מחדש.

## גיבוי והצפנה

ב־manifest בפועל `allowBackup=true`; לא מוגדרים fullBackupContent/dataExtractionRules
ולא נמצאו כללי גיבוי שמחריגים את המסד. Room משתמש במיקום databases רגיל ולא ב־noBackupFilesDir.
מכאן שהמסד **עשוי להיכלל** בגיבוי Android או בהעברת מכשיר, בהתאם למערכת ולהגדרות.
אין בפרויקט הצפנת מסד/ניהול מפתחות. app-private אינו הצפנה או הבטחה לגיבוי.
החלטת מוצר לקראת release ‏0.1.0: לשמור על זכאות לגיבוי Android אוטומטי ולהעברת מכשיר,
עם allowBackup=true וללא החרגת המסד. לא נבדקו backup/restore או העברה בפועל.
הצפנה נשארת מחוץ להיקף; זכאות אינה הבטחה שגיבוי יבוצע או שישוחזר.

## חסמים לעומת הרחבות עתידיות

פער file reopen→UI הושלם בבדיקה המבודדת המתוארת לעיל. כל 62 בדיקות המכשיר עברו
ושתי החבילות נשארו מותקנות; אין חסם קבלה ידוע שנותר בהיקף ההתמדה המוסכם.
אין אימות של רשומות הייצור; fixtures נשארו מבודדים. מגבלות האימות לעיל נשארות בתוקף.

מחוץ להיקף: מחיקת הוצאות, החזרים/לא־חיוביות, עריכת שדות נוספים, Summary היסטורי,
ריבוי מחשבונים ב־UI, חשבונות, הצפנה, cloud sync או export. שינוי עתידי של יום תחילת תקופה
דורש החלטת מוצר. זכאות לגיבוי/העברה נשמרת בהחלטה מפורשת; הביצוע בפועל לא נבדק.
