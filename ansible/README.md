# Installation <small>on a Raspberry Pi</small>

This document describes how to install the Network Monitor on your Raspberry Pi.

It has been tested with a Raspberry Pi Zero with Wireless network and with an Ethernet adapter.

[![photo of Netmon running on a Raspberry Pi Zero](../docs/netmon-rpi0.jpg)
Netmon on a Raspberry Pi Zero](../docs/netmon-rpi0.jpg)

## Installation steps

- Install Ansible on your computer.
- Checkout this repository:
  ```shell
  git clone https://github.com/bkahlert/netmon.git
  cd netmon/ansible
  ```
- Install Ansible requirements:
  ```shell
  ansible-galaxy install -r requirements.yml
  ```
- Copy the [sample inventory](inventory/sample) to `inventory/berries` and adapt it to your needs:
  ```shell
  cp -r inventory/sample inventory/berries
  ```

Flash [Raspberry Pi OS Lite image](https://downloads.raspberrypi.org/raspios_lite_armhf/images/raspios_lite_armhf-2023-05-03/2023-05-03-raspios-bullseye-armhf-lite.img.xz)
to your SD card.

- Boot your Raspberry Pi and connect it to your network.
- Start the installation using:
  ```shell
  # Setup only the device foo.local
  ansible-playbook playbook.yml -l foo.local
  
  # Setup only the device foo.local declared in the given inventory, and use the specified IP address to connect
  ansible-playbook playbook.yml -l foo.local \
      -e "ansible_host=10.10.10.99" \
      -i inventory/other/hosts.yml
  ```
  or in combination with [Pi Hero](https://github.com/bkahlert/pihero):
  ```shell
  # Setup only the device foo.local
  ansible-playbook playbook.yml --tags netmon,pihero -l foo.local
  
  # Setup only the device foo.local declared in the given inventory, and use the specified IP address to connect
  ansible-playbook playbook.yml --tags netmon,pihero -l foo.local \
      -e "ansible_host=10.10.10.99" \
      -i inventory/other/hosts.yml
  ```

## Optional steps

### Disable Wi-Fi

If you don't have a Wi-Fi interface, or just don't want to use it, you can disable the appropriate service.
This speeds up the boot process and reduces the number of log errors.

```shell
sudo systemctl stop hostapd
sudo systemctl disable hostapd
```
