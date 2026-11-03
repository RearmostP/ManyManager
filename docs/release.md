# הפצות ManyManager

## ענפים קבועים ופרסום עצמאי

| יישום | ענף פיתוח | תג הפצה | שם APK | כותרת Release |
| --- | --- | --- | --- | --- |
| ManyManager | `main` | `manymanager-v<VERSION>` | `ManyManager-<VERSION>.apk` | `ManyManager <VERSION>` |
| ManyManager Simple | `original` | `simple-v<VERSION>` | `ManyManager-Simple-<VERSION>.apk` | `ManyManager Simple <VERSION>` |

היישומים ינוהלו ויפורסמו באופן עצמאי. עדכון אחד אינו מחייב בנייה או הפצה של האחר,
ושמות הגרסאות אינם חייבים להתאים. כל Release יכיל רק את ה־APK של היישום המופץ.
כל תג יצביע לקומיט המדויק שממנו נבנה ה־APK. אין לשנות שמות או לשכתב תגים
והפצות היסטוריים, לרבות `0.1.0`. לזיהוי הגרסה האחרונה של יישום יש לסנן לפי
קידומת התג המתאימה, ולא להסתמך רק על “Latest” הגלובלי של GitHub.

`original` נוצר מאותו קומיט סופי של `main`, עם אותו מימוש ואותה גרסת בסיס.
אין במשימה זו שינוי applicationId או מימוש יישום נוסף. עד שיוגדרו applicationIds
נפרדים אסור להפיץ יישומים מתפצלים עם אותה זהות חבילה או לאפס את versionCode.
לאחר ההפרדה versionCode יוכל להתקדם עצמאית לכל חבילה. רציפות עדכון לכל יישום
מחייבת שמירת אותו applicationId ואותה זהות חתימה בהפצות העוקבות שלו.

## הכנת 0.2.0 — 2026-11-03

- אוחדו שלושת commits של `feature/persistent-data` עד `88f26b1`: פרטי הוצאה,
  עריכה ומחיקה מאושרת, רשימות inline בסיכום וכרטיסים מאוחדים.
- לא נמצאה עבודת בורר אייקונים נוספת שהוכנסה ל־Git; שינוי אייקון `fcdf2b9` כבר בבסיס.
- package נשאר `com.example.manymanager`; versionName=`0.2.0`, versionCode=`2`.
- נבדקו הענפים/תגים המרוחקים והפצות GitHub: ההפצה היחידה היא `0.1.0`, בקוד 1.
  SHA-256 של ה־APK המקומי הקודם תואם ל־digest הנכס שפורסם ב־GitHub.
- נשמרו סכמה 1, העדפות, allowBackup=true, applicationId ומפתח release הקיים.
- הפלט למסירה הוא `private-release/ManyManager-0.2.0.apk`, מוחרג מ־Git.
  אין לדרוס קובץ קיים בשם זה; יש לעצור ולבדוק אם כבר קיים.
- בנייה באמצעות הפקודה המתועדת להלן, ללא InitializeKey וללא החלפת מפתח.
- במשימה זו אין יצירת תג, פרסום Release או העלאת APK. פרסום עתידי ייעשה רק
  אחרי אימות APK והתאמת התג לקומיט הבנייה המדויק, לפי הטבלה לעיל.

## אימות הכנת 0.2.0

ב־2026-11-03 עברו assembleRelease (כולל lintVitalRelease), assembleDebug,
assembleDebugAndroidTest, testDebugUnitTest ו־lintDebug.
הגדרת leaveApksInstalledAfterRun=true, ה־runner, Room בזיכרון והעדפות
עם קידומת stage7-test- נבדקו. לא הורץ instrumentation במשימה זו, כדי לא
להתקין APK בדיקה מעל האפליקציה הקיימת. תוצאות המכשיר ההיסטוריות בהמשך
אינן הרצה חדשה. לא בוצעו התקנה, הסרה או ניקוי נתונים.
לא בוצע אימות ידני חדש, בדיקת API 30, TalkBack או גיבוי/שחזור.
קיימות אזהרות deprecation ו־unused expression שאינן מכשילות את הבנייה.

לאחר commit ההכנה יש לבנות שוב מהקומיט הנקי, לאמת עם aapt/apksigner,
ולהעתיק את התוצר ללא דריסה לנתיב המסירה. יש להשוות תעודה ל־APK ההיסטורי
ולחשב SHA-256 של עותק המסירה; הנתונים בדוח המסירה מתייחסים לבינארי זה.

## תיעוד היסטורי — שחרור ManyManager 0.1.0

הסעיפים הבאים מתעדים את ההכנה המקורית. ההפצה פורסמה מאז ב־GitHub תחת התג
ההיסטורי `0.1.0`; תיאורי “לא פורסם” להלן נכונים למועד ההכנה המקורית בלבד.

זהו תיאור התוצר המקורי שנחתם, ובו תיקון סכום בלבד. [פרטי הוצאה ועריכה מלאה/מחיקה מאושרת](expense-details.md) הם פיתוח מאוחר יותר ואינם כלולים ב־APK זה. במשימת הממשק לא שונו גרסה/חתימה ולא נבנה APK שחרור חדש.

ה־APK מיועד להעברה פרטית ולהתקנה ידנית. לא פורסם release, לא נוצר tag ולא הועלה קובץ.
הכנת החתימה והשחרור נפרדת מתוכנית עשרת שלבי ההתמדה; במסגרתה נסגר פער בדיקת הקבלה האחרון.

## תוצר מאומת — 2026-11-02

- קובץ מקומי: `private-release/ManyManager-0.1.0.apk` (מוחרג מ־Git).
- נתיב מלא: `C:\Users\d0508\David\kotlin\ManyManager\private-release\ManyManager-0.1.0.apk`.
- package: `com.example.manymanager`; versionName: `0.1.0`; versionCode: `1`.
- minSdk: 30; targetSdk: 37. האייקון והממשק המאושר נשמרו.
- SHA-256 של ה־APK:
  `A8681A4330CBAEC82B700837FECCB19626F5D0738E8BDCE9A477FD901799913E`.
- SHA-256 של תעודת החתימה:
  `E4:AE:E9:D1:75:AC:C7:1C:7A:A6:FA:5D:96:02:3E:CB:2D:93:FD:03:B3:23:42:0A:37:9E:A7:7C:A8:DA:55:FC`.
- תעודה: CN=ManyManager Private Release, RSA 4096; alias: `manymanager-release`.
  תוקף עד 2054-03-20 (10,000 ימים ממועד היצירה).
- apksigner אימת חתימה תקינה, signer יחיד ו־APK Signature Scheme v2.
  טביעת האצבע הושוותה לזו של המפתח ב־keytool.
- aapt אימת package וגרסאות; badging/XML אינם כוללים debuggable או testOnly,
  ולכן ערכם false כברירת המחדל. allowBackup=true אומת גם ב־APK.
- לא הותקן APK ה־release על המכשיר הקיים. אין עדיין בדיקת התקנה/הרצה של release
  בטלפון בן המשפחה או בדיקת API 30 בזמן ריצה.

קובץ המקור לבנייה נמצא ב־`androidApp/build/outputs/apk/release/androidApp-release.apk`.
SHA זה מתייחס לקובץ שנמסר כעת; בבנייה חדשה יש לחשב שוב ולא להניח שזהות בתים מובטחת.

## בנייה מחדש

Windows, משורש המאגר, עם אותו חשבון Windows ואותו מפתח:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/release.ps1
```

ברירת המחדל של JavaHome היא `C:\Program Files\Android\Android Studio\jbr`;
אפשר להעביר `-JavaHome` לנתיב JDK נתמך. נשמרה הגדרת daemon JVM הקיימת של Gradle.
הסקריפט מפעיל `:androidApp:assembleRelease --no-configuration-cache --console=plain`.
אין להריץ עם debug logging או build scan המכילים פרטי חתימה.
לא הופעלו minification/resource shrinking ולא שודרגו תלויות.

לבדיקה לאחר בנייה (JAVA_HOME צריך להצביע ל־JDK):

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\build-tools\36.0.0\apksigner.bat" verify --verbose --print-certs androidApp/build/outputs/apk/release/androidApp-release.apk
& "$env:LOCALAPPDATA\Android\Sdk\build-tools\36.0.0\aapt.exe" dump badging androidApp/build/outputs/apk/release/androidApp-release.apk
Get-FileHash -Algorithm SHA256 androidApp/build/outputs/apk/release/androidApp-release.apk
```

יש להעתיק רק APK שאומת לתיקיית המסירה המוחרגת, לחשב checksum גם לעותק,
ולוודא שהתעודה נשארה אותה תעודת release. אין לשלוח keystore או קובצי credentials עם ה־APK.

## מפתח וסודות מקומיים

- מפתח: `C:\Users\d0508\.manymanager\signing\manymanager-release.p12`, מחוץ למאגר.
- סוד מקומי: `C:\Users\d0508\.manymanager\signing\release-credential.clixml`.
  מכיל PSCredential שסיסמתו מוגנת ב־Windows DPAPI לחשבון ולמחשב הנוכחיים.
- הרשאות תיקיית signing נבדקו: המשתמש הנוכחי ו־SYSTEM בלבד, ללא הרשאות בירושה.
- המפתח חדש ונפרד מ־debug; שום מפתח קיים לא הוחלף. הסיסמה אקראית ולא הודפסה.
- `scripts/release.ps1 -InitializeKey` יוצר מפתח רק אם שני קובצי החתימה חסרים.
  **אין להריץ ליצירת מפתח חלופי עבור עדכון.** אם קובץ כלשהו כבר קיים, הפעולה נכשלת.
- אפשר `-SigningDirectory` לתיקייה מאובטחת אחרת מחוץ למאגר; אין להעביר למיקום במאגר.

לשמירת הסיסמה במנהל סיסמאות, יש להריץ מקומית ובמכוון:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/release.ps1 -CopyPassword
```

הפקודה מעתיקה ללוח בלי להדפיס את הערך. יש להדביק במנהל סיסמאות מאובטח ואז לנקות:
`Set-Clipboard -Value $null`. אין להדביק בצ'אט, בלוג או בקובץ מקור.
גבה בנפרד את ה־p12 ואת הסיסמה (למשל גיבוי מוצפן ומנהל סיסמאות).
העתקת CLIXML לבדה אינה גיבוי נייד: DPAPI עשוי שלא להיפתח במחשב/חשבון אחר.
לא בוצעה בדיקת שחזור של גיבוי המפתח.

לסביבה אחרת אפשר לספק לגרסת Gradle המקומית בלבד:
`MANYMANAGER_RELEASE_STORE_FILE`, `MANYMANAGER_RELEASE_STORE_PASSWORD`,
`MANYMANAGER_RELEASE_KEY_ALIAS`, `MANYMANAGER_RELEASE_KEY_PASSWORD`.
יש להשתמש ב־`--no-configuration-cache` כדי לא לשמור פרטי חתימה ב־configuration cache.
הסקריפט טוען את DPAPI לתוך משתני תהליך בלבד ומנקה אותם בסיום.
אין צורך בסודות לבניית debug. assembleRelease ללא credentials נכשל במפורש
ב־validatePrivateReleaseSigning; כשל זה נבדק בפועל, ולא הופק תחליף unsigned/debug-signed.

## גרסאות ועדכונים

לפני כל הפצה נוספת יש להגדיל versionCode מעל כל גרסה שכבר הופצה,
ולעדכן versionName לפי הגרסה המיועדת. בהכנה זו נשמר code=1 הקיים;
בהתקנה שנבדקה היה code=1 ולא נמצאה גרסה מופצת עם קוד גבוה יותר.
אסור לשנות applicationId או את מפתח החתימה לצורך עדכון רגיל.
אובדן המפתח או הסיסמה עלול למנוע עדכון מותקן ללא אובדן רציפות ההתקנה/נתונים.

ה־APK המותקן נשלף לקריאה בלבד ונבדק ב־apksigner: תעודתו CN=Android Debug,
SHA-256 ‏6c096c31679a137448c1c8e25be4ce754b344dec504687b419d67342b5d4b88a,
השונה מתעודת release. לכן release לא יכול לעדכן אותה ישירות
באותו package, גם עם אותו קוד גרסה. אין להסיר אותה כפתרון אוטומטי ואין למחוק נתונים.
העברה למכשיר משפחתי שבו אין התקנה מתנגשת אינה דורשת החלפת package.

## בדיקות וקבלה

- assembleDebug, assembleDebugAndroidTest, testDebugUnitTest ו־lintDebug עברו;
  32 בדיקות JVM עברו.
- כל 62 בדיקות המכשיר עברו ב־SM-S928B / Android 17, ללא failures/errors/skipped.
  כולל fileBackedRecordsReopenAndDisplayInFreshActivity: קובץ UUID מבודד,
  שמירה→סגירה→פתיחה מחדש→Activity חדשה עם אותן רשומות וסכומים.
- runner/העדפות מבודדים ו־leaveApksInstalledAfterRun=true נבדקו לפני instrumentation;
  שתי החבילות נותרו מותקנות אחריו. לא בוצעו clear-data/uninstall.
- הבנייה החתומה עברה לרבות lintVitalRelease. נותרו אזהרות deprecation קיימות;
  libandroidx.graphics.path.so נארזה ללא stripping לאחר אזהרת הכלי. אין כשל בנייה.
- [דוח הקבלה](persistent-data-acceptance.md) מפרט מגבלות TalkBack, גופן/מקלדת,
  API 30, מות תהליך ו־pixel parity. אין טענה שהן אומתו כעת.

## גיבוי והתקנה פרטית

החלטת המוצר: **להשאיר זכאות לגיבוי Android אוטומטי ולהעברת מכשיר**.
allowBackup=true נשמר, ללא כלל שמחריג את המסד. גיבוי/restore/העברה בפועל לא נבדקו
ואינם מובטחים. הצפנה נשארת מחוץ להיקף; app-private אינו הצפנה.

להתקנה: להעביר את `ManyManager-0.1.0.apk` ב־USB או בערוץ פרטי לבחירת המשתמש,
לפתוח בטלפון Samsung ולאשר התקנה. Android עשוי לבקש הרשאה להתקנה מהיישום שממנו
נפתח הקובץ (״הקבצים שלי״ או יישום הודעות). יש לאפשר רק למקור שבחרת, ולהסיר הרשאה
אחרי ההתקנה אם אינה נחוצה. אם מדיניות המכשיר חוסמת התקנה, יש לפעול דרך הגדרותיו,
לא לעקוף הגנה. אם קיימת גרסה עם חתימה שונה, לעצור ולבדוק לפני הסרה.
הסוכן לא שלח את הקובץ לאיש ולא התקין release אוטומטית.
