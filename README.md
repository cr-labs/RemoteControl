# RemoteControl


## Over-the-wire control of server apps

RemoteControl allows over-the-wire control of server apps. It also allows apps to stream performance, state, or event data back to listening apps such as a Flash front-end (as with SK).

RemoteControl at present provides for basic authentication via a shared secret, for replay detection, and for limits on
controllers based on IP address or host name. AT present it's all TCP over a plain TCP link, but I hope it is generic
enough to be retrofitted in the future to be transport-agnostic - supporting web, chat or other interfaces.

RemoteControl has these components:<br />
- RemoteControl -- this class, for inclusion in server-side apps that are to be controlled<br />
- RemoteControlConfig -- holds all the configuration data structure that can be set in a config file<br />
- RemoteControlClient -- command line or interactive Java app for sending commands and receiving responses from a RemoteControl-enabled service. Looks like Telnet but performs login and authentication behind the scenes.<br />
- RemoteControlClientConfig -- holds configuration data structure for the RemoteControlClient<br />
- RemoteControlLib -- Java library for apps that want to embed RemoteController functions - hardcodes the commands that clients and server must agree on<br />
