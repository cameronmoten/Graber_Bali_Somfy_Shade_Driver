/** Graber / Bali / Somfy Shade Driver v2.1
 *  Device Type:	Z-Wave Window Shade
 *  V2.1 Author :    Evan Callia
 *  V2 Author :      Cameron Moten
 *  V1 Author : 	 Tim Yuhl
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation
 *  files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy,
 *  modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the
 *  Software is furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE
 *  WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 *  COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
 *  ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

 *  V1 History:
 *  7/20/21 - Initial work.
 *  8/1/21 - Added configurable battery report time
 *
 *  V2 History:
 *  6/2/23 - Added Ability for it to act like a Dimmer Switch in HomeKit/Hubitat
 *         - Added Ability to set State Values to not allow it to exceed a certain Up & Down Max per Blind so users don't have to set it per window with a remote.
 *         - Add Support if WindowShadeLevel is ever added to Hubitat like smartthings.
 *         - Added Support for Custom Battery Max Limits (To support Re-chargable Batteries) you can now set what "Fresh" batteries are so you don't get annoying low battery alerts.
 *  3/4/24 - Add "opening" and "closing" status
 *         - Handle "Start Level Change" and "Stop Level Change" for full dimmer compatibility
 */

import groovy.transform.Field

@Field static final Map commandClassVersions = [
		0x20: 1,    //basic
		0x26: 1,    //switchMultiLevel
		0x5E: 2,    // ZwavePlusInfo
		0x80: 1     // Battery
]

String appVersion()   { return "2.0.0" }
def setVersion(){
	state.name = "Graber-Bali-Somfy Shade Driver"
	state.version = "2.0.0"
}

@Field static final String defaultTime = "0800"

metadata {
	definition (
			name: "Graber Somfy Shade Driver",
			namespace: "camm",
			description:"Driver for Graber Z-Wave Shades V2",
			importUrl:"https://raw.githubusercontent.com/tyuhl/GraberShade/main/graber-shade-driver.groovy",
			author: "Cam Moten") {
		capability "WindowShade" // Leaving this to help classify the Window shade for Homekit etc..
        // Since Capability "WindowShadeLevel" Doesn't exist yet for hubitat.. We should add it when it does.
        // I am saving the values for both dimmer + windows to support the most edge cases. 
		capability "Battery"
		capability "Initialize"
		capability "Actuator"
		capability "Sensor"
		capability "Refresh"
		capability "Switch"
        capability "SwitchLevel" // To add support to emulate a Dimmer Switch on the Hubitat.
        capability "ChangeLevel" // To add support to emulate a Dimmer Switch on the Hubitat.


		fingerprint deviceId: "5A31", inClusters: "0x5E,0x26,0x85,0x59,0x72,0x86,0x5A,0x73,0x7A,0x6C,0x55,0x80", mfr: "26E", deviceJoinName: "Graber Somfy Shade"
	}
	preferences {
		section("Scheduled Time") {
			input name: "sched_time", type: "time", title: "Daily Battery Check Time: (Default 8:00 AM)", defaultValue: "08:00 AM"
		}
		section("Logging") {
			input "logging", "enum", title: "Log Level", required: false, defaultValue: "INFO", options: ["TRACE", "DEBUG", "INFO", "WARN", "ERROR"]
		}
        section("Max Open Value") {
			input name: "max_open", type: "number", title: "Max OPEN can the shade be? [This doesn't override remote set limits; only hubitat commands]: (Default 99 of 99%)", defaultValue: 99
		}
        section("Max Closed Value") {
			input name: "max_close", type: "number", title: "Max CLOSED can the shade be? [This doesn't override remote set limits; only hubitat commands]: (Default 0 of 99%)", defaultValue: 0
		}
        section("Battery Max Percent") {
			input name: "battery_max_percent", type: "number", title: "Max Battery Level - Advanced: ONLY use if you are using re-chargable batteries, the blinds report them as 25-70% full when freshly installed. Set the % capacity that the system will think is a NEW Full Battery [See real_battery_level state on Refresh]. (Default 100%) ", defaultValue: 100
		}
	}
}


def getMaxOpen(){
    try {
		max_open_int = max_open as Short
		if((max_open_int > 99 || max_open_int < 0)) {
			log("Max Open Value doesn't exist or out of bounds. ${max_open_int}", "error")
		} else {
			return max_open_int
		}
    }
    catch(e) {
        log("unhandled error in GetMaxOpen: ${e.getLocalizedMessage()}", "error")
    }
    
    return 99
}
def getMaxClosed(){
    try {
    	max_close_int = max_close as Short
		if((max_close_int > 99 || max_close_int < 0)) {
			log("Max Closed Value doesn't exist or out of bounds. ${max_close_int}", "error")
		} else {
			return max_close_int
		}
    }
    catch(e) {
        log("unhandled error in getMaxClosed: ${e.getLocalizedMessage()}", "error")
    }
    
    return 0
}

def batteryMaxLevel(){
	 try {
    	battery_max = battery_max_percent as Short
		if((battery_max > 100 || battery_max < 0)) {
			log(" battery_max Value doesn't exist or out of bounds. ${battery_max}", "error")
		} else {
			return battery_max
		}
    }
    catch(e) {
        log("unhandled error in batteryMaxLevel: ${e.getLocalizedMessage()}", "error")
    }
    
    return 100
}

/**
 * Boilerplate callback methods called by the framework
 */

void installed()
{
	log("installed() called", "trace")
	setVersion()
	initialize()
}

void updated()
{
	log("updated() called", "trace")
	setVersion()
	initialize()
}

void parse(String message)
{
	log("parse called with message: ${message}", "trace")
	hubitat.zwave.Command cmd = zwave.parse(message, commandClassVersions)
	if (cmd) {
		zwaveEvent(cmd)
	}
}

/* End of built-in callbacks */

///
// Commands
///
void initialize() {
	log("initialize() called", "trace")
	refresh()
	scheduleBatteryReport()
}

void refresh()
{
	log("refresh called", "trace")
	delayBetween([
			getBatteryReport(),
			getPositionReport()
	], 200)
}

def open() {
	log("open() called", "trace")
	setShadeLevel(getMaxOpen())
}

def close() {
	log("close() called", "trace")
	setShadeLevel(getMaxClosed())
}

def on() {
	log("on() called --> calling open()", "trace")
	open()
}

def off() {
	log("off() called --> calling close()", "trace")
	close()
}

//To support it as a dimmer switch.
def setLevel(value, duration){
	log("setLevel() called", "trace")
    setShadeLevel(value)
}

def setLevel(value){
	log("setLevel() called", "trace")
    setShadeLevel(value)
}

def setPosition(value) {
	log("setPosition() called", "trace")
	setShadeLevel(value)
}

def startPositionChange(position) {
	log("startPositionChange() called with position: ${position}", "trace")
	startLevelChangeHelper(position)
}

def stopPositionChange() {
	log("stopPositionChange() called", "trace")
	try {
		sendHubCommand(new hubitat.device.HubAction(zwave.switchMultilevelV1.switchMultilevelStopLevelChange().format(), hubitat.device.Protocol.ZWAVE))
	}
	catch(e) {
		log("unhandled error: ${e.getLocalizedMessage()}", "error")
	}
}

def startLevelChange(position) {
	log("startLevelChange() called with position: ${position}", "trace")

	String pos
	switch (position.toUpperCase()) {
		case "UP":
			pos = "open"
			break
		case "DOWN":
			pos = "close"
			break
		default:
			throw new Exception("Invalid position value specified")
	}

	startPositionChange(pos)
}

def stopLevelChange() {
	log("stopLevelChange() called", "trace")
	stopPositionChange()
}

///
// Event Handlers
///
def zwaveEvent(hubitat.zwave.commands.batteryv1.BatteryReport cmd) {
	log("BatteryReport $cmd", "trace")
	def val = (cmd.batteryLevel == 0xFF ? 1 : cmd.batteryLevel)

	def max_battery_level = batteryMaxLevel()
	def real_battery = val
	if (val > max_battery_level) val = max_battery_level
	if (val < 1) val = 1

	if (max_battery_level != 100) {
		def newVal = (val / max_battery_level) * 100 as int

	    log("REAL Battery level is ${real_battery}% Before being Modified (${val}%/${max_battery_level}%)*100 => ${newVal}%", "info")
		val = newVal
		if (val < 1) val = 1
	}

	String disTxt = "${device.getDisplayName()} battery level is ${val}%"
	log("Battery level is ${val}%", "info")
	sendEvent(getEventMap("battery", val, "%", null, disTxt,true))
	sendEvent(getEventMap("real_battery_level", real_battery, "%", null, "",true))
	sendEvent(getEventMap("battery_max_percent", max_battery_level, "%", null, "",true))

	return []
}

def zwaveEvent(hubitat.zwave.commands.switchmultilevelv1.SwitchMultilevelReport cmd){
	log("SwitchMultilevelReport value: ${cmd.value}", "trace")
	shadeEvents(cmd.value,"physical")
	return []
}

def zwaveEvent(hubitat.zwave.commands.basicv1.BasicReport cmd){
	log("BasicReport value: ${cmd.value}", "trace")
	shadeEvents(cmd.value,"digital")
	return []
}

void zwaveEvent(hubitat.zwave.Command cmd) {
	log("Command Unhandled: $cmd", "trace")
}

///
// Supporting helpers
///
void getBatteryReport() {
	log("getBatteryReport() called", "trace")
	try {
		sendHubCommand(new hubitat.device.HubAction(zwave.batteryV1.batteryGet().format(), hubitat.device.Protocol.ZWAVE))
	}
	catch(e) {
		log("unhandled error: ${e.getLocalizedMessage()}", "error")
	}
}

private void scheduleBatteryReport() {
	unschedule(getBatteryReport)
	// Test  - every 2 minutes
	// def cronString = "0 */2 * ? * *"
	// every day at 6:00 am
	// def cronString = "0 0 6 ? * *"
	def dt
	if(sched_time != null) {
		dt = toDateTime(sched_time)
	}
	else {
		dt =  Date.parse("HHmm", defaultTime)
	}
	def cronString = dt.format("ss mm HH") + " ? * *"
	log("Scheduling Battery Refresh cronstring: ${cronString}", "info" )
	schedule(cronString, getBatteryReport)
}

private void setShadeLevel(value)
{
	Short curPos = device.currentValue("position")
	Short level = Math.max(Math.min(value as Short, getMaxOpen()), getMaxClosed())
    log("Set Shade Level ${level}", "info")

	if (level == curPos) {
		return // nothing to be done
	}

	String shadeText
	String positionText
	if (curPos > level) {
		shadeText = "closing"
		positionText = "${device.getDisplayName()} is closing"
	} else {
		shadeText = "opening"
		positionText = "${device.getDisplayName()} is opening"
	}

	try {
		sendEvent(getEventMap("label", shadeText, null, null, positionText, true))
		sendEvent(getEventMap("windowShade", shadeText, null, null, positionText, true))
		sendHubCommand(new hubitat.device.HubAction(zwave.switchMultilevelV1.switchMultilevelSet(value: level).format(), hubitat.device.Protocol.ZWAVE))
	}
	catch(e) {
		log("unhandled error: ${e.getLocalizedMessage()}", "error")
	}
}

private void startLevelChangeHelper(String position)
{
	Short posValue = 0
	Short curPos = device.currentValue("position")
	if (position.equalsIgnoreCase("open")) {
		posValue = getMaxOpen()
	} else if (position.equalsIgnoreCase("close")) {
		posValue = getMaxClosed()
	} else {
		throw new Exception("Invalid position value specified")
	}
	if (posValue == curPos) {
		return // nothing to be done
	}

	// false if increasing, true if decreasing
	Boolean upDn = (curPos >= posValue)

	String shadeText
	String positionText
	if (!upDn) {
		shadeText = "opening"
		positionText = "${device.getDisplayName()} is opening"
	} else {
		shadeText = "closing"
		positionText = "${device.getDisplayName()} is closing"
	}

	try {
		sendEvent(getEventMap("label", shadeText, null, null, positionText, true))
		sendEvent(getEventMap("windowShade", shadeText, null, null, positionText, true))
		sendHubCommand(new hubitat.device.HubAction(zwave.switchMultilevelV1.switchMultilevelStartLevelChange(ignoreStartLevel: true,
				startLevel: 0, upDown: upDn).format(), hubitat.device.Protocol.ZWAVE))
	}
	catch(e) {
		log("unhandled error: ${e.getLocalizedMessage()}", "error")
	}
}

private void getPositionReport() {
	log("getPositionReport() called", "trace")
	try {
		sendHubCommand(new hubitat.device.HubAction(zwave.switchMultilevelV1.switchMultilevelGet().format(), hubitat.device.Protocol.ZWAVE))
	}
	catch(e) {
		log("unhandled error: ${e.getLocalizedMessage()}", "error")
	}
}

private shadeEvents(value, String type) {
	Short positionVal = value
	String positionText
	String switchText
	String shadeText
	if (positionVal+1 >= getMaxOpen()) { // e.g. 94 + 1 >= 95 (Set Value)  -- it tends to be 1% off 
		positionText = "${device.getDisplayName()} is open"
		switchText = "on"
		shadeText = "open"
	} else if (positionVal -1 <= getMaxClosed()) { // e.g. 36 - 1 <= 35 (Set Value) -- it tends to be 1% off 
		positionText = "${device.getDisplayName()} is closed"
		switchText = "off"
		shadeText = "closed"
	} else {
		positionText = "${device.getDisplayName()} is partially open"
		shadeText = "partially open"
	}
	sendEvent(getEventMap("position", positionVal, "%", null, positionText, true))
    sendEvent(getEventMap("level", positionVal, "%", null, positionText, true)) //Save the Level & shadeLevel to support all edge cases
    sendEvent(getEventMap("shadeLevel", positionVal, "%", null, positionText, true))
    sendEvent(getEventMap("label", shadeText, null, null, positionText, true))
	log("${positionText}", "debug")
	sendEvent(getEventMap("switch", switchText, null, null, null,true))
	sendEvent(getEventMap("windowShade", shadeText, null, null, positionText, true))
}

private getEventMap(name, value, unit=null, String type=null, String discText=null, displayed=false) {
	def eventMap = [
			name: name,
			value: value,
			isStateChange: true
	]
	if (unit) {
		eventMap.unit = unit
	}
	if (type) {
		eventMap.type = type
	}
	if (discText) {
		eventMap.descriptionText = discText
	}
	if (displayed) {
		eventMap.displayed = displayed
	}
	return eventMap
}

private determineLogLevel(data) {
	switch (data?.toUpperCase()) {
		case "TRACE":
			return 0
			break
		case "DEBUG":
			return 1
			break
		case "INFO":
			return 2
			break
		case "WARN":
			return 3
			break
		case "ERROR":
			return 4
			break
		default:
			return 1
	}
}

def log(Object data, String type) {
	data = "-- ${device.label} -- ${data ?: ''}"

	if (determineLogLevel(type) >= determineLogLevel(settings?.logging ?: "INFO")) {
		switch (type?.toUpperCase()) {
			case "TRACE":
				log.trace "${data}"
				break
			case "DEBUG":
				log.debug "${data}"
				break
			case "INFO":
				log.info "${data}"
				break
			case "WARN":
				log.warn "${data}"
				break
			case "ERROR":
				log.error "${data}"
				break
			default:
				log.error("-- ${device.label} -- Invalid Log Setting")
		}
	}
}