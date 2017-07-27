## Install Java Cryptography Extension (JCE) Unlimited Strength Jurisdiction Policy Files
## This script is taken from https://chocolatey.org/packages/jre8-jce

Write-Host "testtesttest"

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$packageName = $env:ChocolateyPackageName
$url = 'http://download.oracle.com/otn-pub/java/jce/8/jce_policy-8.zip'
$sha256 = 'f3020a3922efd6626c2fff45695d527f34a8020e938a49292561f18ad1320b59'

Write-Host "testtesttest22222"

$javaRegistryKeyName = if ($packageName -like 'jre*') {'Java Runtime Environment'} else {'Java Development Kit'}
$javaHome = (Get-ItemProperty -Path "HKLM:\SOFTWARE\JavaSoft\$javaRegistryKeyName\1.8" -Name JavaHome).JavaHome
$jreHome = if ($packageName -like 'jre*') {$javaHome} else {"$javaHome\jre"}
$installPath = "$jreHome\lib\security"
$tempPath = "$env:TEMP\$packageName"
$artifactPath = "$tempPath\$(Split-Path -Leaf $url)"

Write-Host "testtesttest33333"

mkdir $tempPath -ErrorAction SilentlyContinue | Out-Null
$webSession = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$webSession.Cookies.Add((New-Object System.Net.Cookie 'oraclelicense','accept-securebackup-cookie','/','.oracle.com'))

Write-Host "testtesttest44444"
Invoke-WebRequest `
    $url `
    -UseBasicParsing `
    -WebSession $webSession `
    -OutFile $artifactPath
$actualSha256 = (Get-FileHash $artifactPath -Algorithm SHA256).Hash
if ($sha256 -ne $actualSha256) {
    throw "$url downloaded to $artifactPath did not match the expected $sha256 hash"
}
Add-Type -AssemblyName System.IO.Compression.FileSystem
[IO.Compression.ZipFile]::ExtractToDirectory($artifactPath, $tempPath)
Write-Host "testtesttest55555"
Copy-Item "$tempPath\UnlimitedJCEPolicyJDK8\*.jar" $installPath
