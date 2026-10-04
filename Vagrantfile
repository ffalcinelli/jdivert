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
    # JDK 8 runs the JNA adapter, JDK 22+ builds the multi-release jar and runs
    # the Panama adapter.  libebpfdivert.so is self-contained: nothing else needed.
    linux.vm.provision "shell", inline: <<-SHELL
      export DEBIAN_FRONTEND=noninteractive
      apt-get update
      apt-get install -y openjdk-8-jdk maven curl
      if ! apt-get install -y openjdk-25-jdk; then
        mkdir -p /opt/jdk25
        curl -fsSL --retry 10 --retry-all-errors \
          "https://api.adoptium.net/v3/binary/latest/25/ga/linux/x64/jdk/hotspot/normal/eclipse" \
          | tar -xz -C /opt/jdk25 --strip-components=1
        ln -sfn /opt/jdk25 /usr/lib/jvm/java-25-openjdk-amd64
      fi
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
