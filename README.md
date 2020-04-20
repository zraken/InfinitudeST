# InfinitudeST Device Handler Screenshot

![Device Screen](https://raw.githubusercontent.com/zraken/InfinitudeST/master/resources/screen1.jpeg)

## Requirements

Infinitude (https://github.com/nebulous/infinitude 5), doesn’t require serial integration to higher resolution access to the thermostat, air handler, heat pump and other devices (RS485 interface). You’re welcome to integrate via RS485 to get at this data but it isn’t really used by this integration and further you MUST complete the proxy integration from the wall stat for this to work. I’ve tested this on my 5 zone system and all zones are supported.
You must STOP using the Bryant/Carrier iOS/Android app to control the thermostat, while it sometimes work it usually breaks infinitude (there are open bugs on this) requiring you to manually clear the infinitude cache and restart – not good. Once you have infinitude up and running you really don’t need the app because you can either use the web interface or the DH via smartthings.
You’re welcome to continue to use all scheduling via your thermostat, these seem to remain working, but if you really want the power of geo fencing I’d recommend you disable all scheduling (I set all zones to away at 00:15 [12:15 am]) then control it with something like Webcore. I’m happy to publish my Webcore, it’s highly custom to the way my family interacts with our house (presence, motion, doors open/closed, etc) but I’m happy to share.

## Install Instructions

**First**: confirm you have Infinitude working from link above, I recommend you configure the updates to be applied ~ 15s

**Second**: install SmartApp via [Smartthings IDE](https://graph.api.smartthings.com/)

**Third**: Install Device Handler via [Smartthings IDE](https://graph.api.smartthings.com/)

**Fourth**: Configure SmartApp with the IP/Port of Infinitude, if all works as expected it will discover your zones and configure your themostats inside smartthings using the supplied DH

**Fifth**: Enjoy

## Usage
You must set the furnace climate mode (heat/cool/auto/fan only/off) via Infinitude web interface (haven’t implemented via DH/SmartApp yet), I usually leave it in heat/cool and don’t use auto, not sure how well infinitude works with that yet…
Once configured the DH can be used to change the profile by clicking the Profile tile in the top left, the order is Home, Away, Sleep, Wake, then Home…
You can also use the DH to change the heat/cool setpoint using the respective up/down arrows. NOTE: This will also change the Profile to manual (Bryant/Carrier behavior) and set the Hold time to Midnight.
NOTE: I haven’t been able to syn the config changes to Infinitude very well, there is a ~ 15 second delay for every change/touch in the DH. It seems to queue up changes you make and get them all applied but it can take a while, please wait 15-60s for it to fully apply).
Pressing Refresh Tile will refresh the SmartApp connection for all zones back to Infinitude, again NOTE that Infinitude may not adjust for 15-60s depending on your configuration.

###### Contributed by swerb73
