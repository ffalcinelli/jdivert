# scripts/vagrant-provision.ps1
# This script installs OpenJDK and prepares the environment for JDivert testing on Windows.

Write-Host "Installing OpenJDK via winget..."
winget install Microsoft.OpenJDK.8 --accept-source-agreements --accept-package-agreements

# JDivert requires administrator privileges to load the WinDivert driver.
# The provisioning script runs as administrator in Vagrant by default.

Write-Host "Provisioning complete. You can now run tests with './gradlew test' as Administrator."
