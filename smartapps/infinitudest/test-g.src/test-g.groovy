/*
Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
in compliance with the License. You may obtain a copy of the License at: http://www.apache.org/licenses/LICENSE-2.0
Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
on an AS IS BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
for the specific language governing permissions and limitations under the License.
*/

definition(
    name: "test-g",
    namespace: "InfinitudeST",
    author: "swerb73",
    description: "Infinitude Integration for Carrier/Bryant Thermostats",
    category: "SmartThings Labs",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Meta/temp_thermo-switch.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Meta/temp_thermo-switch@2x.png",
)

preferences {
    page(name: "prefLogIn", title: "Infinitude Server")
    page(name: "pausePage", title: "Infinitude retrieving…")
    page(name: "prefListDevice", title: "Infinitude Zones")
}

/* Preferences */
def prefLogIn() {
    def showUninstall = configURL != null
    return dynamicPage(name: "prefLogIn", title: "Click next to proceed…", nextPage: "pausePage", uninstall: showUninstall, install: false) {
        section("Server URL") {
            input(name: "configURL", type: "text", title: "Local network ip_address:port", defaultValue: "192.168.2.138:3000", description: "Infinitude LAN Server Address")
        }
    }
}

def pausePage() {
    state.SystemRunning = 0
    state.thermostatList = syncSystem()
    log.debug "Query complete"

    return dynamicPage(name: "pausePage", title: "Configure", nextPage: "prefListDevice", uninstall: false, install: false) {
        section("Advanced Options") {
            input(name: "polling", title: "Server Polling (in Minutes)", type: "number", description: "in minutes", defaultValue: "5", range: "1..120")
        }
    }
}
def prefListDevice() {
    if (state.thermostatList) {
        log.debug "Got a list"
        return dynamicPage(name: "prefListDevice", title: "Thermostats", install: true, uninstall: true) {
            section("Select which thermostat/zones to use") {
                input(name: "selectedThermostats", type: "enum", required: false, multiple: true, metadata: [values: state.thermostatList])
            }
        }
    } else {
        log.debug "Empty list returned"
        return dynamicPage(name: "prefListDevice", title: "Error!", install: false, uninstall: true) {
            section("") {
                paragraph "Could not find any devices "
            }
        }
    }
}

/* Initialization */
def installed() {
    initialize()
}
def updated() {
    unschedule()
    unsubscribe()
    initialize()
}
def uninstalled() {
    unschedule()
    unsubscribe()
    getAllChildDevices().each {
        deleteChildDevice(it.deviceNetworkId)
    }
}

def initialize() {
    // Set initial states
    state.data = [:]
    state.setData = [:]
    state.SystemRunning = 1

    //selectedThermostats.each { dni, val ->
    def devices = selectedThermostats.collect {
        dni ->
            log.debug "Processing DNI: ${dni} with Value: {val}"
        def d = getChildDevice(dni)
        if (!d) {
            d = addChildDevice("SmartThingsMod", "Infinitude Thermostat", dni, null, ["label": "Stat: " + dni.split("\\|")[3]])
            log.debug "----->created ${d.displayName} with id $dni"
        } else {
            log.debug "found ${d.displayName} with id $dni already exists"
        }
        return d
    }
    log.debug "Completed creating devices"

    pollTask()
}

import groovy.json.JsonSlurper
def httpCallback(physicalgraph.device.HubResponse hubResponseX) {
    setLookupInfo()

    //log.debug "httpCallback - Status: {$hubResponseX.status}"
    //log.debug "httpCallback - Body: {$hubResponseX.json}"

    def object = new groovy.json.JsonSlurper().parseText(hubResponseX.body)
    state.thermostatList = [:]
    state.data = [:]
    state.outsideairtemp = 0

    def systemName = "Thermostat"

    if (hubResponseX.status == 200) {
        log.debug "-----APIRESP(systems/id/status) 3"
        state.outsideAirTemp = object.oat[0]
        object.zones[0].zone.each {
            zone ->
                if (zone.enabled[0] == "on") {
                    def dni = [app.id, systemName, zone.id[0], zone.name[0] ].join('|')
                    log.debug "DNI:: " + dni

					state.thermostatList[dni] = systemName + ":" + zone.name[0]

                    //Get the current status of each device
                    state.data[dni] = [
                        temperature: zone.rt[0],
                        humidity: zone.rh[0],
                        coolingSetpoint: zone.clsp[0],
                        heatingSetpoint: zone.htsp[0],
                        thermostatFanMode: zone.fan[0],
                        //thermostatOperatingState: zone.zoneconditioning[0],
                        thermostatOperatingState: object.mode[0],
                        thermostatActivityState: zone.currentActivity[0],
                        thermostatHoldStatus: zone.hold[0],
                        thermostatHoldUntil: zone.otmr[0],
                        //thermostatDamper: zone.damperposition[0],
                        thermostatZoneId: zone.id[0]
                    ]
                    log.debug "===== " + zone.name[0] + " ====="
                    if (state.SystemRunning) {
                        refreshChild(dni)
                    }
                    /*
                                           log.debug "Temperature: " + state.data[dni].temperature
                                           log.debug "Humidity: " + state.data[dni].humidity
                                           log.debug "Heat set point: " + state.data[dni].heatingSetpoint
                                           log.debug "Cooling set point: " + state.data[dni].coolingSetpoint
                                           log.debug "Fan: " + state.data[dni].thermostatFanMode
                                           log.debug "Operating state: " + state.data[dni].thermostatOperatingState
                                           log.debug "Current schedule mode: " + state.data[dni].thermostatActivityState
                                           log.debug "Current Hold Status: " + state.data[dni].thermostatHoldStatus
                                           log.debug "Hold Until: " + state.data[dni].thermostatHoldUntil
                                           log.debug "Current Damper Position: " + state.data[dni].thermostatDamper
                                           log.debug "Current Outside Temp: " + state.outsideAirTemp
                                           log.debug "Zone ID: " + state.data[dni].thermostatZoneId
                                           log.debug "=====Done=====" */
                }
        }
    } else {
        log.debug "API request failed"
    }

    log.debug state.thermostatList
    //xxxxx return thermostatList
}

private syncSystem() {
    def result = new physicalgraph.device.HubAction(
        method: "GET",
        path: "/api/status",
        headers: [
            "HOST": configURL
        ],
        null, [callback: httpCallback]
    )
    log.debug "syncSystem called"
    try {
        sendHubCommand(result)
    } catch (all) {
        log.error "Error executing internal web request: $all"
    }
}

private changeHtsp(zoneId, heatingSetPoint) {

    //First Adjust the Manual Comfort Profile
    def result = new physicalgraph.device.HubAction(
        method: "GET",
        path: "/api/" + zoneId + "/activity/manual",
        headers: [
            "HOST": configURL
        ],
        query: [htsp: heatingSetPoint]
    )
    log.debug "HTTP GET Parameters: " + result
    try {
        sendHubCommand(result)
    } catch (all) {
        log.error "Error executing internal web request: $all"
    }

    //Now tell the zone to use the Manual Comfort Profile
    def NowDate = new Date(now())
    log.debug "Now = ${NowDate.format('dd-MM-yy HH:mm',location.timeZone)}"
    NowDate.set(minute: NowDate.minutes + 15)
    def HoldTime = NowDate.format('HH:mm', location.timeZone)
    log.debug "Later = " + HoldTime

    result = new physicalgraph.device.HubAction(
        method: "GET",
        path: "/api/" + zoneId + "/hold",
        headers: [
            "HOST": configURL
        ],
        query: [activity: "manual", until: "24:00"]
    )
    log.debug "HTTP GET Parameters: " + result
    try {
        sendHubCommand(result)
    } catch (all) {
        log.error "Error executing internal web request: $all"
    }
}

private changeClsp(zoneId, coolingSetpoint) {

    //First Adjust the Manual Comfort Profile
    def result = new physicalgraph.device.HubAction(
        method: "GET",
        path: "/api/" + zoneId + "/activity/manual",
        headers: [
            "HOST": configURL
        ],
        query: [clsp: coolingSetpoint]
    )
    log.debug "HTTP GET Parameters: " + result
    try {
        sendHubCommand(result)
    } catch (all) {
        log.error "Error executing internal web request: $all"
    }

    //Now tell the zone to use the Manual Comfort Profile
    def NowDate = new Date(now())
    log.debug "Now = ${NowDate.format('dd-MM-yy HH:mm',location.timeZone)}"
    NowDate.set(minute: NowDate.minutes + 15)
    def HoldTime = NowDate.format('HH:mm', location.timeZone)
    log.debug "Later = " + HoldTime

    result = new physicalgraph.device.HubAction(
        method: "GET",
        path: "/api/" + zoneId + "/hold",
        headers: [
            "HOST": configURL
        ],
        query: [activity: "manual", until: "24:00"]
    )
    log.debug "HTTP GET Parameters: " + result
    try {
        sendHubCommand(result)
    } catch (all) {
        log.error "Error executing internal web request: $all"
    }
}

private changeProfile(zoneId, nextProfile) {
    //Now tell the zone to use the nextProfile Comfort Profile
    log.debug "Changing Profile for Zone " + zoneId + " to " + nextProfile
    def querys = [:]
    if(nextProfile == "auto") {
        querys = [hold: "off"]
    }
    else {
        querys = [activity: nextProfile, until: "24:00"]
    }
    def result = new physicalgraph.device.HubAction(
        method: "GET",
        path: "/api/" + zoneId + "/hold",
        headers: [
            "HOST": configURL
        ],
        query: querys
    )
    log.debug "HTTP GET Parameters: " + result
    try {
        sendHubCommand(result)
    } catch (all) {
        log.error "Error executing internal web request: $all"
    }
}

def setLookupInfo() {
    state.lookup = [
        thermostatOperatingState: [:],
        thermostatFanMode: [:],
        thermostatMode: [:],
        activity: [:],
        coolingSetPointHigh: [:],
        coolingSetPointLow: [:],
        heatingSetPointHigh: [:],
        heatingSetPointLow: [:],
        differenceSetPoint: [:],
        temperatureRangeF: [:]
    ]
    state.lookup.thermostatMode["off"] = "off"
    state.lookup.thermostatMode["cool"] = "cool"
    state.lookup.thermostatMode["heat"] = "heat"
    state.lookup.thermostatMode["fanonly"] = "off"
    state.lookup.thermostatMode["auto"] = "auto"
    state.lookup.thermostatOperatingState["heat"] = "heating"
    state.lookup.thermostatOperatingState["hpheat"] = "heating"
    state.lookup.thermostatOperatingState["cool"] = "cooling"
    state.lookup.thermostatOperatingState["off"] = "idle"
    state.lookup.thermostatOperatingState["fanonly"] = "fan only"
    state.lookup.thermostatFanMode["off"] = "auto"
    state.lookup.thermostatFanMode["low"] = "circulate"
    state.lookup.thermostatFanMode["med"] = "on"
    state.lookup.thermostatFanMode["high"] = "on"
}

// lookup value translation
def lookupInfo(lookupName, lookupValue, lookupMode) {
    if (lookupName == "thermostatFanMode") {
        if (lookupMode) {
            return state.lookup.thermostatFanMode.getAt(lookupValue.toString())
        } else {
            return state.lookup.thermostatFanMode.find {
                it.value == lookupValue.toString()
            } ?.key
        }
    }
    if (lookupName == "thermostatMode") {
        if (lookupMode) {
            return state.lookup.thermostatMode.getAt(lookupValue.toString())
        } else {
            return state.lookup.thermostatMode.find {
                it.value == lookupValue.toString()
            } ?.key
        }
    }
    if (lookupName == "thermostatOperatingState") {
        if (lookupMode) {
            return state.lookup.thermostatOperatingState.getAt(lookupValue.toString())
        } else {
            return state.lookup.thermostatOperatingState.find {
                it.value == lookupValue.toString()
            } ?.key
        }
    }
}
def pollTask() {
    syncSystem()
    runIn(60 * settings.polling, pollTask)
}
def refreshChild(dni) {
    log.debug "Refreshing for child " + dni
    //def dni = [ app.id, systemID, zone.’@id’.text().toString() ].join(’|’)
    def d = getChildDevice(dni)
    if (d) {
        log.debug "–Refreshing Child Zone ID: " + state.data[dni].thermostatZoneId
        d.zUpdate(state.data[dni].temperature,
            state.data[dni].thermostatOperatingState,
            state.data[dni].humidity,
            state.data[dni].heatingSetpoint,
            state.data[dni].coolingSetpoint,
            state.data[dni].thermostatFanMode,
            state.data[dni].thermostatActivityState,
            state.outsideAirTemp,
            state.data[dni].thermostatHoldStatus,
            state.data[dni].thermostatHoldUntil,
            state.data[dni].thermostatDamper,
            state.data[dni].thermostatZoneId)
        log.debug "Data sent to DH"
    } else {
        log.debug "Skipping refresh for unused thermostat"
    }
}
