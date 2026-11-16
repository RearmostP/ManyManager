param([string]$Action='dump', [string]$Value='')
$ErrorActionPreference='Stop'
$deviceAdb='C:/Users/d0508/AppData/Local/Android/Sdk/platform-tools/adb.exe'
$deviceSerial='R5CWC3MY6RT'
$package='com.example.manymanager.onboardingtest'
if($Action -eq 'tap') {
    [xml]$lastUi=Get-Content -Encoding utf8 "$PSScriptRoot/onboarding-window.xml"
    $node=$lastUi.SelectNodes('//node') | Where-Object { $_.'resource-id' -eq $Value -or $_.text -eq $Value -or $_.'content-desc' -eq $Value } | Select-Object -First 1
    if(-not $node){throw "UI target not found: $Value"}
    $coords=[regex]::Matches($node.bounds,'\d+') | ForEach-Object { [int]$_.Value }
    & $deviceAdb -s $deviceSerial shell input tap ([int](($coords[0]+$coords[2])/2)) ([int](($coords[1]+$coords[3])/2))
} elseif($Action -eq 'type') {
    & $deviceAdb -s $deviceSerial shell input text $Value
} elseif($Action -eq 'relaunch') {
    & $deviceAdb -s $deviceSerial shell am force-stop $package
    & $deviceAdb -s $deviceSerial shell am start -W -n "$package/com.example.manymanager.MainActivity"
} elseif($Action -eq 'back') {
    & $deviceAdb -s $deviceSerial shell input keyevent 4
} elseif($Action -eq 'screenshot') {
    & $deviceAdb -s $deviceSerial shell screencap -p /sdcard/onboarding-device.png
    & $deviceAdb -s $deviceSerial pull /sdcard/onboarding-device.png "$PSScriptRoot/onboarding-device.png"
}
& $deviceAdb -s $deviceSerial shell uiautomator dump /sdcard/onboarding-window.xml | Out-Null
& $deviceAdb -s $deviceSerial pull /sdcard/onboarding-window.xml "$PSScriptRoot/onboarding-window.xml" | Out-Null
[xml]$currentUi=Get-Content -Encoding utf8 "$PSScriptRoot/onboarding-window.xml"
$currentUi.SelectNodes('//node') | Where-Object { $_.text -or $_.class -eq 'android.widget.EditText' } | ForEach-Object {
    '{0} | {1} | {2}' -f $_.text,$_.'resource-id',$_.bounds
}
