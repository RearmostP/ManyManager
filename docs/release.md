# שחרור פרטי — ManyManager 0.1.0

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
