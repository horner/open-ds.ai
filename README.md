# OpenDS.ai

A fork of [OpenDS](https://github.com/Boomaa23/open-ds) — a reverse-engineered lightweight FRC Driver Station alternative for Windows, Linux, and macOS — extended with a virtual gamepad controller and a built-in MCP server for AI assistant integration.


## Features
OpenDS.ai is a fully functional FIRST Robotics Competition (FRC) Driver Station 
alternative for Windows, Linux, and macOS systems.
All the features of the official Driver Station are implemented in OpenDS.ai, 
meaning teams can use it in the place of the official Driver Station 
when testing robot features away from the competition.

OpenDS.ai is extremely lightweight (about 1 MB) and does not require an 
installation of any kind, unlike the official Driver Station which 
has a lengthy installation process and heavy install footprint.

NOTE: OpenDS.ai may not be used during FRC-legal competitions as per 
rules R710 and R901 (previously R66 and R88). 
OpenDS.ai is intended for testing use only.

* Robot
    * Enable and disable
    * Change mode (teleop/auto/test)
    * Change alliance station (1/2/3 & red/blue)
    * Send game data
    * Change team number
    * USB Joystick and Xbox controller input support
    * Restart robot code and RoboRIO
    * Emergency stop
* Statistics
    * Robot voltage
    * Connections
    * Brownouts
    * Match time left (FMS)
    * CAN Bus
    * RoboRIO disk/RAM/CPU/version
    * Disable/Rail faults
    * Logging to `.dslog` files
* NetworkTables
    * Read Shuffleboard and SmartDashboard packets
    * Display NetworkTables passed data
* FMS
    * Connect to a offseason FMS or Cheesy Arena
    * Choose to connect or not
* Support
    * Lightweight executable
    * Windows, Linux, and macOS support
    * No install prerequisites
    * Easily modifiable for updated protocol years
    * Command-line (CLI) parameters
    

## Setup
Download the latest release jar from the [Releases](../../releases/latest) page and run. There are no prerequisites besides having a Java installation with [JRE 8](https://adoptopenjdk.net/) or newer. The JRE is included with any installation of the same JDK version.

If you do not have Java and/or want a single install/run script, download the launch script from the [Releases](../../releases/latest) page instead and use it to start OpenDS.ai. It will download OpenDS.ai and a copy of Java for it to use. Use the same script to re-launch OpenDS.ai.


### Troubleshooting
If you run into issues, ensure that you are running a 64-bit installation of either Windows 7/8.1/10/11, Linux kernel version 2.6.35 or greater, or macOS 10 (OSX) or newer.

Try launching from the command line (`java -jar open-ds.jar`) and observing the console output for additional details. You can also launch with debug (`--debug`) to print more information to the console.

If you are using the WPILib simulator (instead of a physical robot), ensure you have the following line in your `build.gradle` (or equivalent in `build.gradle.kts`).
```groovy
wpi.sim.addDriverstation().defaultEnabled = true
```

If issues persist, please report them on the [Issues](../../issues) section of this GitHub and they will be resolved as soon as possible.


## License
OpenDS.ai may be used without restriction for the purpose of testing robots by teams and individuals.

See [LICENSE.txt](LICENSE.txt) for more details.


## Contributing
If you find a bug or issue with OpenDS.ai, please report it on the [Issues](../../issues) section of this GitHub.

For protocol changes in future years, OpenDS.ai is easily modifiable. Ports, IP addresses, display layouts, and packet creation/parsing are all re-formattable.


## Acknowledgements


Special thanks to [@Boomaa23](https://github.com/Boomaa23) for creating and open-sourcing [OpenDS](https://github.com/Boomaa23/open-ds), which is the foundation this project is built on. The thoughtful feedback on [PR #36](https://github.com/Boomaa23/open-ds/pull/36) — particularly around Swing event thread safety for virtual controllers — is greatly appreciated and has informed the design of this fork.
