# -*- mode: ruby -*-
# vi: set ft=ruby :

Vagrant.configure("2") do |config|
  
  config.vm.define "linux" do |linux|
    linux.vm.box = "bento/ubuntu-24.04"
    linux.vm.hostname = "jdivert-linux"
    linux.vm.synced_folder ".", "/jdivert"
    linux.vm.provider "virtualbox" do |vb|
      vb.memory = "2048"
      vb.cpus = 2
    end
    linux.vm.provision "shell", inline: <<-SHELL
      apt-get update
      apt-get install -y openjdk-21-jdk maven libbpf-dev clang llvm libelf-dev
    SHELL
  end

  config.vm.define "windows" do |windows|
    windows.vm.box = "gusztavvargadr/windows-11-22h2-enterprise"
    windows.vm.communicator = "winrm"
    windows.vm.synced_folder ".", "C:/jdivert"
    windows.vm.provider "virtualbox" do |vb|
      vb.memory = "4096"
      vb.cpus = 2
      vb.gui = false
      vb.customize ["modifyvm", :id, "--vram", "128"]
      vb.customize ["modifyvm", :id, "--nested-hw-virt", "on"]
    end
    windows.vm.provision "shell", path: "scripts/vagrant-provision.ps1"
  end
end
