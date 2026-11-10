# העלאה ראשונה ל־Google Play — המהדורה האישית

הוכן מקומית בלבד ב־10.11.2026. שום קובץ לא הועלה ל־Play ולא פורסמה גרסה.

החבילה המיועדת: `com.example.manymanager`, שם גרסה `0.3.0`, קוד גרסה `4`, ‏target SDK ‏37. שם האפליקציה, האייקון, namespace ומסד הנתונים נשמרו. ה־APK האישי האחרון ב־GitHub הוא קוד 3; היסטוריית הקודים ב־Play עדיין לא אומתה.

לבנייה חוזרת, משורש המאגר:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/release.ps1 -Artifact Bundle -Verify
```

תוצר Gradle: `androidApp/build/outputs/bundle/release/androidApp-release.aab`. עותק למסירה: `private-release/ManyManager-0.3.0-vc4-play.aab`. פרטי האימות וטביעות האצבע נמצאים ב־[מדריך ההפצה](release.md). קובצי החתימה נשארים מחוץ ל־Git.

## טיוטת Internal testing בלבד

1. היכנס לחשבון המפתח הנכון ב־Play Console. בדוק אם כבר קיימת אפליקציה עם מזהה החבילה המדויק. ב־Latest releases and bundles / All app bundles בדוק את כל המסלולים והטיוטות: קוד 4 חייב להיות פנוי וגבוה מכל קוד רלוונטי שכבר שימש. אם אינו מתאים, יש להעלות את הקוד ולבנות מחדש; אין לשנות את שם החבילה.
2. אם צריך ליצור אפליקציה, שמור על השם הקיים ועל `com.example.manymanager`; בחר שפה, סוג אפליקציה ומודל תשלום, והשלם את הצהרות החשבון הדרושות. קבלת המזהה המדויק על ידי Play היא בדיקה ממתינה, שאינה ניתנת לאישור מקומי.
3. פתח **Test and release → Testing → Internal testing → Create new release**. לפני השלמת הגדרת Play App Signing, בדוק את אפשרויות מפתח החתימה. לשימור עדכונים מעל התקנות GitHub, הכוונה היא לספק ל־Play את מפתח החתימה הקיים באמצעות תהליך PEPK המאובטח שמציג ה־Console, ולא לאמץ מפתח חלופי בלי החלטה מפורשת. אל תעלה keystore או סיסמה כקובץ רגיל.
4. העלה בעצמך את ה־AAB החתום, המתן לעיבוד ובדוק package, קוד/שם גרסה, target SDK, התקנים נתמכים, native libraries ותאימות 16 KB. אם יש דחייה, שמור את **הנוסח המדויק**; לא נבחר מזהה חלופי. שמור כטיוטה בלבד ואל תתחיל rollout או פרסום.
5. ב־**Play app signing / App integrity** בדוק בנפרד את תעודת **Upload key** ואת תעודות **App signing key**. ה־AAB הנוכחי חתום במפתח המקומי הקיים; הדבר מאמת את ההעלאה בלבד. עבור זהות החתימה הקיימת, SHA‑256 הצפוי הוא:
   `E4:AE:E9:D1:75:AC:C7:1C:7A:A6:FA:5D:96:02:3E:CB:2D:93:FD:03:B3:23:42:0A:37:9E:A7:7C:A8:DA:55:FC`.
   אם Play מציג מפתחות נוספים/שדרוגי חתימה, יש לבדוק גם רצף חתימה תקף לכל גרסאות Android הרלוונטיות. התאמה של Upload key לבדה אינה מבטיחה עדכון התקנת GitHub. לפני rollout יש לאמת APK ש־Play יצר ואת מסלול העדכון, במכשיר בדיקה נפרד, בלי להסיר את התקנת המשתמש.
6. השלם את ה־Dashboard: אימות חשבון ופרטי קשר, עמוד חנות ונכסים, מדיניות פרטיות ו־Data safety לפי ההתנהגות והספריות בפועל, סיווג תוכן, קהל יעד, פרסומות, גישה לאפליקציה והצהרות תכונות פיננסיות ככל שנדרש. בנייה מקומית מוצלחת אינה אישור מדיניות או גישה ל־Production.
7. אם זהו חשבון מפתח מסוג Personal שנוצר אחרי 13.11.2023, נדרש בדרך כלל **Closed testing עם לפחות 12 בודקים שהצטרפו ברציפות במשך 14 ימים**, ואז בקשת גישה ל־Production. ‏Internal testing ובדיקות המכשיר המקומיות אינם ממלאים דרישה זו. סוג המהדורה האישית של האפליקציה אינו קובע את סוג חשבון המפתח.

מומלץ לרשום בנפרד אישור קבלת package וקוד הגרסה, תעודות חתימה סופיות, תוצאות עיבוד/בדיקות Play והחלטת גישה ל־Production. עד אז אלה בדיקות ממתינות.

מקורות רשמיים שנבדקו: [target API](https://support.google.com/googleplay/android-developer/answer/11926878?hl=en), [App Signing](https://developer.android.com/studio/publish/app-signing), [ניהול מפתחות ב־Play](https://support.google.com/googleplay/android-developer/answer/9842756?hl=en), [דרישות לחשבונות Personal](https://support.google.com/googleplay/android-developer/answer/14151465?hl=en), [יצירת אפליקציה וקודי גרסה](https://support.google.com/googleplay/android-developer/answer/9859152?hl=en), [מסלולי בדיקה](https://support.google.com/googleplay/android-developer/answer/9845334?hl=en), [תאימות 16 KB](https://developer.android.com/guide/practices/page-sizes).
