$ErrorActionPreference = "Stop"
Write-Host "Creating C:\java directory..."
New-Item -ItemType Directory -Force -Path "C:\java" | Out-Null

Write-Host "Downloading JDK 25 from Adoptium..."
$url = "https://api.adoptium.net/v3/binary/latest/25/ga/windows/x64/jdk/hotspot/normal/eclipse"
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
Invoke-WebRequest -Uri $url -OutFile "C:\java\jdk25.zip"

Write-Host "Extracting zip..."
Expand-Archive -Path "C:\java\jdk25.zip" -DestinationPath "C:\java" -Force

Write-Host "Locating and renaming directory..."
$dir = Get-ChildItem "C:\java" | Where-Object { $_.PSIsContainer -and $_.Name -like "jdk-25*" }
if ($dir) {
    # If a jdk25 directory already exists, delete it first
    if (Test-Path "C:\java\jdk25") {
        Remove-Item "C:\java\jdk25" -Recurse -Force
    }
    Rename-Item -Path $dir.FullName -NewName "jdk25"
    Write-Host "Renamed $dir to C:\java\jdk25"
} else {
    Write-Host "Could not find extracted jdk-25* directory!"
}

Write-Host "Cleaning up zip..."
Remove-Item "C:\java\jdk25.zip" -Force

Write-Host "Verifying installation..."
& "C:\java\jdk25\bin\java.exe" -version
