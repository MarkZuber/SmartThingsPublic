/**
 *  ZubeTV App
 *
 *  Copyright 2015 Mark Zuber
 *
 */
definition(
    name: "ZubeTV App",
    namespace: "MarkZuber",
    author: "Mark Zuber",
    description: "App to integrate with ZubeTV home automation server.",
    category: "My Apps",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
    iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
    oauth: true)

mappings {
	path("/devices") { action: [ GET: "listDevices"] }
	path("/devices/:id") { action: [ GET: "getDevice", PUT: "updateDevice"] }
	path("/subscriptions") { action: [ GET: "listSubscriptions", POST: "addSubscription"] }
	path("/subscriptions/:id") { action: [ DELETE: "removeSubscription"] }
	path("/phrases") { action: [ GET: "listPhrases"] }
	path("/phrases/:id") { action: [ PUT: "executePhrase"] }
	path("/hubs") { action: [ GET: "listHubs" ] }
	path("/hubs/:id") { action: [ GET: "getHub" ] }
	path("/activityCallback/:dni") { action: [ POST: "activityCallback" ] }
	path("/zubetv") { action: [ GET: "getZubeTVServer", POST: "zubeTVServer" ] }
	path("/zubetv/:mac") { action: [ DELETE: "deleteZubeTVServer" ] }
	path("/hookCallback") { action: [ POST: "hookEventHandler", GET: "hookEventHandler"] }
}

preferences {
    page(name: "ConfigureServer", title: "Configure ZubeTV Server", content: "configurePage", install: false, nextPage: "deviceAuthorization") {
        section("ZubeTV Address") {
            input  "zubeTVServerAddr", "string", title: "IP:port (click icon in status bar)", multiple: false, required: true

        }
    }
	page(name: "deviceAuthorization", title: "ZubeTV device authorization", install: true) {
		section("Allow ZubeTV to control these things...") {
			input "switches", "capability.switch", title: "Which Switches?", multiple: true, required: false
			input "motionSensors", "capability.motionSensor", title: "Which Motion Sensors?", multiple: true, required: false
			input "contactSensors", "capability.contactSensor", title: "Which Contact Sensors?", multiple: true, required: false
			input "presenceSensors", "capability.presenceSensor", title: "Which Presence Sensors?", multiple: true, required: false
			input "temperatureSensors", "capability.temperatureMeasurement", title: "Which Temperature Sensors?", multiple: true, required: false
			input "accelerationSensors", "capability.accelerationSensor", title: "Which Vibration Sensors?", multiple: true, required: false
			input "waterSensors", "capability.waterSensor", title: "Which Water Sensors?", multiple: true, required: false
			input "lightSensors", "capability.illuminanceMeasurement", title: "Which Light Sensors?", multiple: true, required: false
			input "humiditySensors", "capability.relativeHumidityMeasurement", title: "Which Relative Humidity Sensors?", multiple: true, required: false
			input "alarms", "capability.alarm", title: "Which Sirens?", multiple: true, required: false
			input "locks", "capability.lock", title: "Which Locks?", multiple: true, required: false
		}
	}
}

def configurePage() {
    /*
    // room discovery request every 5 seconds
    
    int roomRefreshCount = !state.roomRefreshCount ? 0 : state.roomRefreshCount as int
    state.roomRefreshCount = roomRefreshCount + 1
    def refreshInterval = 3

    def roomOptions = state.ZubeTVRooms ?: []
    def activityOptions = state.ZubeTVActivities ?: []

	def numFoundRoom = roomOptions.size() ?: 0
    def numFoundActivities = activityOptions.size() ?: 0
	if((roomRefreshCount % 5) == 0) {
        discoverRooms()
    }    
    */
    
    
    
    return dynamicPage(name:"Credentials", title:"Discovery Started!", nextPage:"", refreshInterval:refreshInterval, install:true, uninstall: true) {
        section("Please wait while we discover your ZubeTV Rooms and Activities. Discovery can take five minutes or more, so sit back and relax! Select your room below once discovered.") {
            input "selectedrooms", "enum", required:false, title:"Select ZubeTV Rooms (${numFoundRoom} found)", multiple:true, options:roomOptions
        }
        // Virtual activity flag
        if (numFoundRoom > 0 && numFoundActivities > 0 && true)
            section("You can also add activities as virtual switches for other convenient integrations") {
                input "selectedactivities", "enum", required:false, title:"Select ZubeTV Activities (${numFoundActivities} found)", multiple:true, options:activityOptions
            }
        if (state.resethub) {
            section("Connection to the hub timed out. Please restart the hub and try again.") {}
        }
    }    
}

Map discoverRooms() {
    log.trace "Discovering rooms..."
    discovery()
    if (getZubeTVRooms() != []) {
        def rooms = state.Harmonydevices.hubs
        log.trace devices.toString()
        def activities = [:]
        def hubs = [:]
        devices.each {
        	def hubkey = it.key
            def hubname = getHubName(it.key)
            def hubvalue = "${hubname}"
            hubs["harmony-${hubkey}"] = hubvalue
        	it.value.response.data.activities.each {
                def value = "${it.value.name}"
                def key = "harmony-${hubkey}-${it.key}"
                activities["${key}"] = value
           }
        }
        state.HarmonyHubs = hubs
        state.HarmonyActivities = activities
    }
}

def installed() {
    log.debug "Installed ${app.label} with address '${settings.zubeTVServerAddr}' on hub '${settings.theHub.name}'"
	initialize()
}

def updated() {
	log.debug "Updated with settings: ${settings}"

	unsubscribe()
	initialize()
}

def initialize() {
	// TODO: subscribe to attributes, devices, locations, etc.
    def parts = zubeTVServerAddr.split(":")
    def iphex = convertIPtoHex(parts[0])
    def porthex = convertPortToHex(parts[1])
    def dni = "$iphex:$porthex"
    def hubNames = location.hubs*.name.findAll { it }
    def d = addChildDevice("com.zubetv", "ZubeTV", dni, theHub.id, [label:"${app.label}", name:"ZubeTV"])
    log.trace "created ZubeTV '${d.displayName}' with id $dni"
}

private String convertIPtoHex(ipAddress) {
    String hex = ipAddress.tokenize( '.' ).collect {  String.format( '%02X', it.toInteger() ) }.join()
    return hex

}

private String convertPortToHex(port) {
    String hexport = port.toString().format( '%04X', port.toInteger() )
    return hexport
}

// TODO: implement event handlers

def listHubs() {
	location.hubs?.findAll { it.type.toString() == "PHYSICAL" }?.collect { hubItem(it) }
}

def getHub() {
	def hub = location.hubs?.findAll { it.type.toString() == "PHYSICAL" }?.find { it.id == params.id }
	if (!hub) {
		render status: 404, data: '{"msg": "Hub not found"}'
	} else {
		hubItem(hub)
	}
}

private getAllDevices() {
	([] + switches + motionSensors + contactSensors + presenceSensors + temperatureSensors + accelerationSensors + waterSensors + lightSensors + humiditySensors + alarms + locks)?.findAll()?.unique { it.id }
}

def listDevices() {
	log.debug "getDevices, params: ${params}"
	allDevices.collect {
		deviceItem(it)
	}
}

def getDevice() {
	log.debug "getDevice, params: ${params}"
	def device = allDevices.find { it.id == params.id }
	if (!device) {
		render status: 404, data: '{"msg": "Device not found"}'
	} else {
		deviceItem(device)
	}
}

def updateDevice() {
	def data = request.JSON
	def command = data.command
	def arguments = data.arguments

	log.debug "updateDevice, params: ${params}, request: ${data}"
	if (!command) {
		render status: 400, data: '{"msg": "command is required"}'
	} else {
		def device = allDevices.find { it.id == params.id }
		if (device) {
        	if (device.hasCommand("$command")) {
                if (arguments) {
                    device."$command"(*arguments)
                } else {
                    device."$command"()
                }
                render status: 204, data: "{}"
           	} else {
				render status: 404, data: '{"msg": "Command not supported by this Device"}'
			}
		} else {
			render status: 404, data: '{"msg": "Device not found"}'
		}
	}
}

def listPhrases() {
	location.helloHome.getPhrases()?.collect {[
		id: it.id,
		label: it.label
	]}
}

def executePhrase() {
	log.debug "executedPhrase, params: ${params}"
	location.helloHome.execute(params.id)
	render status: 204, data: "{}"
}

def listSubscriptions() {
	log.debug "listSubscriptions()"
	app.subscriptions?.findAll { it.device?.device && it.device.id }?.collect {
		def deviceInfo = state[it.device.id]
		def response = [
			id: it.id,
			deviceId: it.device.id,
			attributeName: it.data,
			handler: it.handler
		]
		if (!state.harmonyHubs) {
			response.callbackUrl = deviceInfo?.callbackUrl
		}
		response
	} ?: []
}

def addSubscription() {
	def data = request.JSON
	def attribute = data.attributeName
	def callbackUrl = data.callbackUrl

	log.debug "addSubscription, params: ${params}, request: ${data}"
	if (!attribute) {
		render status: 400, data: '{"msg": "attributeName is required"}'
	} else {
		def device = allDevices.find { it.id == data.deviceId }
		if (device) {
			if (!state.harmonyHubs) {
				log.debug "Adding callbackUrl: $callbackUrl"
				state[device.id] = [callbackUrl: callbackUrl]
			}
			log.debug "Adding subscription"
			def subscription = subscribe(device, attribute, deviceHandler)
			if (!subscription || !subscription.eventSubscription) {
				subscription = app.subscriptions?.find { it.device?.device && it.device.id == data.deviceId && it.data == attribute && it.handler == 'deviceHandler' }
			}

			def response = [
				id: subscription.id,
				deviceId: subscription.device.id,
				attributeName: subscription.data,
				handler: subscription.handler
			]
			if (!state.harmonyHubs) {
				response.callbackUrl = callbackUrl
			}
			response
		} else {
			render status: 400, data: '{"msg": "Device not found"}'
		}
	}
}

def removeSubscription() {
	def subscription = app.subscriptions?.find { it.id == params.id }
	def device = subscription?.device

	log.debug "removeSubscription, params: ${params}, subscription: ${subscription}, device: ${device}"
	if (device) {
		log.debug "Removing subscription for device: ${device.id}"
		state.remove(device.id)
		unsubscribe(device)
	}
	render status: 204, data: "{}"
}

def deviceHandler(evt) {
	def deviceInfo = state[evt.deviceId]
	if (state.zubeTVServers) {
		state.zubeTVServers.each { zubeTVServer ->
			sendToZubeTV(evt, zubeTVServer.callbackUrl)
		}
	} else if (deviceInfo) {
		if (deviceInfo.callbackUrl) {
			sendToZubeTV(evt, deviceInfo.callbackUrl)
		} else {
			log.warn "No callbackUrl set for device: ${evt.deviceId}"
		}
	} else {
		log.warn "No subscribed device found for device: ${evt.deviceId}"
	}
}

def sendToZubeTV(evt, String callbackUrl) {
	def callback = new URI(callbackUrl)
	def host = callback.port != -1 ? "${callback.host}:${callback.port}" : callback.host
	def path = callback.query ? "${callback.path}?${callback.query}".toString() : callback.path
	sendHubCommand(new physicalgraph.device.HubAction(
		method: "POST",
		path: path,
		headers: [
			"Host": host,
			"Content-Type": "application/json"
		],
		body: [evt: [deviceId: evt.deviceId, name: evt.name, value: evt.value]]
	))
}

private deviceItem(device) {
	[
		id: device.id,
		label: device.displayName,
		currentStates: device.currentStates,
		capabilities: device.capabilities?.collect {[
			name: it.name
		]},
		attributes: device.supportedAttributes?.collect {[
			name: it.name,
			dataType: it.dataType,
			values: it.values
		]},
		commands: device.supportedCommands?.collect {[
			name: it.name,
			arguments: it.arguments
		]},
		type: [
			name: device.typeName,
			author: device.typeAuthor
		]
	]
}

private hubItem(hub) {
	[
		id: hub.id,
		name: hub.name,
		ip: hub.localIP,
		port: hub.localSrvPortTCP
	]
}
