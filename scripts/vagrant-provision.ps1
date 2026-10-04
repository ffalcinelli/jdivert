# scripts/vagrant-provision.ps1
# Installs the JDKs and Maven needed to build and test JDivert on Windows, without a package manager.
# JDK 25 compiles the multi-release jar (Panama adapters) and is JAVA_HOME; JDK 8 (JDK8_HOME) runs the
# tests on the JNA path: mvn clean verify -Dsurefire.jvm=$env:JDK8_HOME\bin\java.exe

$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"   # Invoke-WebRequest is very slow with the progress bar
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

# Unpacks a zip whose content is a single top-level directory into $dest.
function Install-Zip($url, $dest) {
    if (Test-Path $dest) {
        Write-Host "$dest already present"
        return
    }
    $zip = Join-Path $env:TEMP ([IO.Path]::GetRandomFileName() + ".zip")
    $tmp = Join-Path $env:TEMP ([IO.Path]::GetRandomFileName())
    Write-Host "Installing $url -> $dest"
    Get-File $url $zip
    Expand-Archive -Path $zip -DestinationPath $tmp
    Move-Item -Path (Get-ChildItem $tmp | Select-Object -First 1).FullName -Destination $dest
    Remove-Item $zip, $tmp -Recurse -Force
}

$adoptium = "https://api.adoptium.net/v3/binary/latest/{0}/ga/windows/x64/jdk/hotspot/normal/eclipse"
Install-Zip ($adoptium -f 25) "$Tools\jdk25"
Install-Zip ($adoptium -f 8) "$Tools\jdk8"
Install-Zip "https://archive.apache.org/dist/maven/maven-3/3.9.9/binaries/apache-maven-3.9.9-bin.zip" "$Tools\maven"

[Environment]::SetEnvironmentVariable("JAVA_HOME", "$Tools\jdk25", "Machine")
[Environment]::SetEnvironmentVariable("JDK8_HOME", "$Tools\jdk8", "Machine")
$path = [Environment]::GetEnvironmentVariable("Path", "Machine")
foreach ($dir in @("$Tools\maven\bin", "$Tools\jdk25\bin")) {
    if ($path -notlike "*$dir*") {
        $path = "$dir;$path"
    }
}
[Environment]::SetEnvironmentVariable("Path", $path, "Machine")

# JDivert requires administrator privileges to load the WinDivert driver;
# Vagrant runs provisioners and winrm commands as administrator.
Write-Host "Provisioning complete. Run the tests with:"
Write-Host '  robocopy C:\jdivert C:\local_jdivert /MIR /XD .git .vagrant target .local'
Write-Host '  cd C:\local_jdivert; mvn clean verify'
