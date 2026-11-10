[CmdletBinding()]
param(
    [ValidateSet('Apk', 'Bundle')]
    [string]$Artifact = 'Apk',
    [switch]$Verify,
    [switch]$InitializeKey,
    [switch]$CopyPassword,
    [string]$SigningDirectory = (Join-Path $env:USERPROFILE '.manymanager\signing'),
    [string]$JavaHome = 'C:\Program Files\Android\Android Studio\jbr'
)
$ErrorActionPreference = 'Stop'
if ($env:OS -ne 'Windows_NT') { throw 'This helper requires Windows DPAPI. Other systems may supply the documented environment variables.' }
$repositoryRoot = Split-Path $PSScriptRoot -Parent
$signingRoot = [IO.Path]::GetFullPath($SigningDirectory)
if ($signingRoot.StartsWith($repositoryRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase) -or $signingRoot -eq $repositoryRoot) {
    throw 'Signing material must be outside the repository.'
}
$keyFile = Join-Path $signingRoot 'manymanager-release.p12'
$credentialFile = Join-Path $signingRoot 'release-credential.clixml'
$alias = 'manymanager-release'
$keytool = Join-Path $JavaHome 'bin\keytool.exe'
if (!(Test-Path -LiteralPath $keytool)) { throw 'Set -JavaHome to a JDK containing keytool.' }
if ($InitializeKey -and $CopyPassword) { throw 'Choose only one operation.' }

if ($InitializeKey) {
    if ((Test-Path -LiteralPath $keyFile) -or (Test-Path -LiteralPath $credentialFile)) {
        throw 'Signing material already exists. It will never be overwritten.'
    }
    [IO.Directory]::CreateDirectory($signingRoot) | Out-Null
    $sid = [Security.Principal.WindowsIdentity]::GetCurrent().User
    $acl = New-Object Security.AccessControl.DirectorySecurity
    $acl.SetOwner($sid)
    $acl.SetAccessRuleProtection($true, $false)
    foreach ($principal in @($sid, [Security.Principal.SecurityIdentifier]::new('S-1-5-18'))) {
        $rule = [Security.AccessControl.FileSystemAccessRule]::new(
            $principal, 'FullControl', 'ContainerInherit,ObjectInherit', 'None', 'Allow')
        $acl.AddAccessRule($rule)
    }
    Set-Acl -LiteralPath $signingRoot -AclObject $acl
    $bytes = New-Object byte[] 48
    $random = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $random.GetBytes($bytes) } finally { $random.Dispose() }
    $password = [Convert]::ToBase64String($bytes)
    $secure = ConvertTo-SecureString $password -AsPlainText -Force
    $credential = [PSCredential]::new($alias, $secure)
    # Export-Clixml protects SecureString with DPAPI for this Windows user and machine.
    $credential | Export-Clixml -LiteralPath $credentialFile
    try {
        $env:MANYMANAGER_RELEASE_STORE_PASSWORD = $password
        & $keytool -genkeypair -keystore $keyFile -storetype PKCS12 -alias $alias -keyalg RSA -keysize 4096 -validity 10000 -dname 'CN=ManyManager Private Release' -storepass:env MANYMANAGER_RELEASE_STORE_PASSWORD -keypass:env MANYMANAGER_RELEASE_STORE_PASSWORD
        if ($LASTEXITCODE -ne 0) { throw 'Keystore creation failed. Preserve any generated files and inspect locally; do not overwrite.' }
    } finally {
        Remove-Item Env:MANYMANAGER_RELEASE_STORE_PASSWORD -ErrorAction SilentlyContinue
        $password = $null
        [Array]::Clear($bytes, 0, $bytes.Length)
    }
    Write-Output "Release keystore created at $keyFile. Back up the key and password before distributing updates."
    return
}

if (!(Test-Path -LiteralPath $keyFile) -or !(Test-Path -LiteralPath $credentialFile)) {
    throw 'Release signing material is missing. Run scripts/release.ps1 -InitializeKey once, or supply the environment variables directly.'
}
$credential = Import-Clixml -LiteralPath $credentialFile
if ($credential -isnot [PSCredential] -or $credential.UserName -ne $alias) { throw 'Unexpected credential format or alias.' }
if ($CopyPassword) {
    # Deliberate user-only retrieval; nothing is printed or written in plaintext to disk.
    Set-Clipboard -Value $credential.GetNetworkCredential().Password
    Write-Output 'Password copied to clipboard. Paste into your password manager, then run Set-Clipboard -Value $null.'
    return
}

try {
    $env:JAVA_HOME = $JavaHome
    $env:MANYMANAGER_RELEASE_STORE_FILE = $keyFile
    $env:MANYMANAGER_RELEASE_KEY_ALIAS = $alias
    $env:MANYMANAGER_RELEASE_STORE_PASSWORD = $credential.GetNetworkCredential().Password
    $env:MANYMANAGER_RELEASE_KEY_PASSWORD = $env:MANYMANAGER_RELEASE_STORE_PASSWORD
    Push-Location $repositoryRoot
    try {
        # Do not serialize signing credentials into the Gradle configuration cache.
        if ($Verify) {
            & .\gradlew.bat :androidApp:testDebugUnitTest --rerun :androidApp:lintRelease --no-configuration-cache --console=plain
            if ($LASTEXITCODE -ne 0) { throw 'Release verification failed; bundle build was not started.' }
        }
        $releaseTask = if ($Artifact -eq 'Bundle') { ':androidApp:bundleRelease' } else { ':androidApp:assembleRelease' }
        & .\gradlew.bat $releaseTask --no-configuration-cache --console=plain
        if ($LASTEXITCODE -ne 0) { throw 'Signed release build failed.' }
        if ($Artifact -eq 'Bundle') {
            # Place the JAR manifest/signature entries first for streaming verifiers too.
            # Reuse the same existing key; no application payload is changed.
            $bundleFile = Join-Path $repositoryRoot 'androidApp\build\outputs\bundle\release\androidApp-release.aab'
            & (Join-Path $JavaHome 'bin\jarsigner.exe') -keystore $keyFile -storepass:env MANYMANAGER_RELEASE_STORE_PASSWORD -keypass:env MANYMANAGER_RELEASE_KEY_PASSWORD $bundleFile $alias
            if ($LASTEXITCODE -ne 0) { throw 'Bundle JAR signing failed.' }
        }
    } finally { Pop-Location }
} finally {
    foreach ($name in @('MANYMANAGER_RELEASE_STORE_FILE', 'MANYMANAGER_RELEASE_KEY_ALIAS', 'MANYMANAGER_RELEASE_STORE_PASSWORD', 'MANYMANAGER_RELEASE_KEY_PASSWORD')) {
        Remove-Item "Env:$name" -ErrorAction SilentlyContinue
    }
}
