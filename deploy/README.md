## Installation

- Install Ansible on your computer.
- Checkout this repository:
  ```shell
  git clone https://github.com/bkahlert/netmon.git
  cd netmon
  ```
- Copy the [sample inventory](inventory/sample) to `inventory/berries` and adapt it to your needs:
  ```shell
  cp -r inventory/sample inventory/berries
  ```

Flash either
[FullPageOS 0.12.0](https://github.com/guysoft/FullPageOS/releases/tag/0.12.0) *(**recommended**, in particular on old devices)*, or
[Raspberry Pi OS Lite image](https://downloads.raspberrypi.org/raspios_lite_armhf/images/raspios_lite_armhf-2023-05-03/2023-05-03-raspios-bullseye-armhf-lite.img.xz)
*(requires that you install Chromium yourself)* to your SD card.

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
  ansible-playbook playbook.yml --tags pihero -l foo.local
  
  # Setup only the device foo.local declared in the given inventory, and use the specified IP address to connect
  ansible-playbook playbook.yml --tags pihero -l foo.local \
      -e "ansible_host=10.10.10.99" \
      -i inventory/other/hosts.yml
  ```
