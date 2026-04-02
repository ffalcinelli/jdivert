# scripts/vagrant-provision.ps1
# This script installs OpenJDK and prepares the environment for JDivert testing on Windows.

Write-Host "Installing OpenJDK via Chocolatey..."
choco install openjdk -y

# JDivert requires administrator privileges to load the WinDivert driver.
# The provisioning script runs as administrator in Vagrant by default.

Write-Host "Provisioning complete. You can now run tests with './gradlew test' as Administrator."
