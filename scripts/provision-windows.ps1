# scripts/provision-windows.ps1
# Installs the JDKs and Maven needed to build and test JDivert on Windows, without a package manager.
# JDK 25 compiles the multi-release jar (Panama adapters) and is JAVA_HOME; JDK 8 (JDK8_HOME) runs the
# tests on the JNA path: mvn clean verify -Dsurefire.jvm=$env:JDK8_HOME\bin\java.exe

$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"
$Tools = "C:\tools"
New-Item -ItemType Directory -Force -Path $Tools | Out-Null

function Get-File($url, $out) {
    for ($i = 1; $i -le 10; $i++) {
        try {
            Invoke-WebRequest -UseBasicParsing -Uri $url -OutFile $out -TimeoutSec 600
            return
        } catch {
            Write-Warning "Download of $url failed (attempt $i): $($_.Exception.Message)"
            Start-Sleep -Seconds 5
        }
    }
    throw "Could not download $url"
}

function Test-Zip($path) {
    # Mirrors occasionally serve an error page instead of the archive: check the "PK" signature.
    if (-not (Test-Path $path) -or (Get-Item $path).Length -lt 1MB) { return $false }
    $stream = [IO.File]::OpenRead($path)
    try { return ($stream.ReadByte() -eq 0x50) -and ($stream.ReadByte() -eq 0x4B) } finally { $stream.Close() }
}

function Install-Zip($urls, $dest) {
    if (Test-Path $dest) {
        Write-Host "$dest already present"
        return
    }
    foreach ($url in $urls) {
        $zip = Join-Path $env:TEMP ([IO.Path]::GetRandomFileName() + ".zip")
        $tmp = Join-Path $env:TEMP ([IO.Path]::GetRandomFileName())
        Write-Host "Installing $url -> $dest"
        Get-File $url $zip
        if (-not (Test-Zip $zip)) {
            Write-Warning "$url did not return a zip archive, trying the next one"
            Remove-Item $zip -ErrorAction SilentlyContinue
            continue
        }
        Expand-Archive -Path $zip -DestinationPath $tmp
        Move-Item -Path (Get-ChildItem $tmp | Select-Object -First 1).FullName -Destination $dest
        Remove-Item $zip, $tmp -Recurse -Force
        return
    }
    throw "Could not install $dest"
}

function Get-TemurinUrls($feature) {
    # The three newest GA builds, newest first, so that one broken release asset does not block provisioning.
    $api = "https://api.adoptium.net/v3/assets/feature_releases/$feature/ga?image_type=jdk&os=windows" +
        "&architecture=x64&jvm_impl=hotspot&page_size=3&sort_order=DESC"
    try {
        # Windows PowerShell passes a JSON array down the pipeline as one object: enumerate it explicitly.
        $releases = Invoke-RestMethod -UseBasicParsing -Uri $api
        return @(foreach ($r in $releases) { $r.binaries[0].package.link })
    } catch {
        Write-Warning "Adoptium API failed: $($_.Exception.Message)"
        return @("https://api.adoptium.net/v3/binary/latest/$feature/ga/windows/x64/jdk/hotspot/normal/eclipse")
    }
}

Install-Zip (Get-TemurinUrls 25) "$Tools\jdk25"
Install-Zip (Get-TemurinUrls 8) "$Tools\jdk8"
Install-Zip @("https://archive.apache.org/dist/maven/maven-3/3.9.9/binaries/apache-maven-3.9.9-bin.zip") "$Tools\maven"

[Environment]::SetEnvironmentVariable("JAVA_HOME", "$Tools\jdk25", "Machine")
[Environment]::SetEnvironmentVariable("JDK8_HOME", "$Tools\jdk8", "Machine")
$path = [Environment]::GetEnvironmentVariable("Path", "Machine")
foreach ($dir in @("$Tools\maven\bin", "$Tools\jdk25\bin")) {
    if ($path -notlike "*$dir*") {
        $path = "$dir;$path"
    }
}
[Environment]::SetEnvironmentVariable("Path", $path, "Machine")

Write-Host "Provisioning complete. Run the tests with:"
Write-Host '  mvn clean verify'
