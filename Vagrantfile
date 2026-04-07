# -*- mode: ruby -*-
# vi: set ft=ruby :

# Vagrantfile for JDivert local testing on Windows 11 using VirtualBox.
# Requirements:
# - Vagrant (https://www.vagrantup.com/)
# - VirtualBox (https://www.virtualbox.org/)

Vagrant.configure("2") do |config|
 # Box for Windows 11 22H2 Enterprise
 config.vm.box = "gusztavvargadr/windows-11-22h2-enterprise"

 config.vm.provider "virtualbox" do |vb|
 vb.name = "jdivert-win11"
 vb.memory = "4096"
 vb.cpus = 2
 vb.gui = false # Set to true to see the GUI
 vb.customize ["modifyvm", :id, "--vram", "128"]
 vb.customize ["modifyvm", :id, "--nested-hw-virt", "on"]
 end

 # WinRM is used for Windows communication
 config.vm.communicator = "winrm"

 # Synchronize the current directory to C:/jdivert in the guest VM
 config.vm.synced_folder ".", "C:/jdivert"

 # Run the provisioning script to install dependencies
 config.vm.provision "shell", path: "scripts/vagrant-provision.ps1"

 config.vm.post_up_message = <<-MESSAGE
 -----------------------------------------------------------------------
 Windows 11 VM for JDivert is up and running!

 To run tests within the VM:
 vagrant powershell -c 'cd C:/jdivert; ./gradlew test'

 To get an interactive PowerShell session:
 vagrant powershell
 -----------------------------------------------------------------------
 MESSAGE
end
