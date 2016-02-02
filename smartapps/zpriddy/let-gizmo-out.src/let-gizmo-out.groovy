/**
 *  Let Gizmo Out
 *
 *  Copyright 2015 Zachary Priddy
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 */
definition(
    name: "Let Gizmo Out",
    namespace: "zpriddy",
    author: "Zachary Priddy",
    description: "Send a notification if the dog has not been out in a while. \r\n\r\nIt uses a SmartPresence Sensor to know when the dog goes outside",
    category: "Pets",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
    iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png")


preferences {
	section("Sensors") {
    	input "presenceSensors", "capability.presenceSensor", title: "Choose presence sensors", multiple: true
    }
	section("Mornings") {
    	paragraph "In the mornings the app will send you a notification if the dog has not been out in X minutes after changing mode Y only if it was in mode Z before changing."
		input "sleepingModes", "mode", title:"Mode before waking up", multiple: true, required:true
        input "morningModes", "mode", title:"Mode when waking up", multiple: true, required:true
        input "morningDelay", "number", title:"Wait this many minutes before sending notification", required: true
	}
    section("Arriving Home") {
    	paragraph "Similar to above but when you go from away to home"
        input "awayModes", "mode", title:"Away Mode", multiple: true, required: true
        input "homeModes", "mode", title:"Home Modes", multiple: true, required: true
        input "arrivingDelay", "number", title:"Wait this many minutes before sending notification", required: true, default: 20
    }
    section("Daytime") {
    	paragraph "Send a reminder that the dog needs to go out if X number of hours have passed since the dog has been out."
        input "delayTime", "number", title:"Let dog out after this many hours", required:true, default: 4
        input "manualSwitch", "capability.switch", title:"Manual override switch", required:false
    }
    section("Notifications"){
    	input "notifyDelay", "number", title:"Send a notification every X minutes until the dog has gone out", required: true, default:20 
        input "notifyModes", "mode", title:"Only send notifications if in this mode", required: true, multiple:true 
    
    }
    section("Send this message (optional, sends standard status message if not specified)"){
		input "messageText", "text", title: "Message Text", required: false
	}
	section("Via a push notification and/or an SMS message"){
        input("recipients", "contact", title: "Send notifications to") {
            input "phone", "phone", title: "Phone Number (for SMS, optional)", required: false
            input "pushAndPhone", "enum", title: "Both Push and SMS?", required: false, options: ["Yes", "No"]
        }
	}
}

def installed() {
	log.debug "Installed with settings: ${settings}"

	initialize()
}

def updated() {
	log.debug "Updated with settings: ${settings}"
    
	unsubscribe()
	initialize()
}

def initialize() {
	// TODO: subscribe to attributes, devices, locations, etc.
    subscribe(presenceSensors, "presence", presenceHandler)
   	subscribe(location, modeChangeHandler)
    subscribe(manualSwitch, "switch.on", manualHandler)
    
    state.sleeping = false 
    state.wakingUp = false
    state.away = false
    state.home = false
    state.arriving = false 
    state.notifyMode = false
    
    state.needToNotify = false
    
    state.lastNotify = now()
    state.lastOut = now()
    
    unschedule()
    runEvery5Minutes(checkStatus)
}

// TODO: implement event handlers


def presenceHandler(evt) {
	unschedule()
    runEvery5Minutes(checkStatus)
    
	log.debug "Presence Change: $evt.name: $evt.value"
    
    state.lastOut = now()
    
    state.needToNotify = false
    state.wakingUp = false
    state.arriving = false
    
}

def modeChangeHandler(evt) {
	unschedule()
    runEvery5Minutes(checkStatus)
	log.debug "Mode Change: $evt.name: $evt.value"
    
    if(evt.value in sleepingModes && !state.sleeping)
    {
    	state.sleeping = true
        state.wakingUp = false
        state.away = false
        state.home = false
        state.arriving = false 
        state.needToNotify = false
        state.notifyMode = false
        
        checkBedtime()
    }
    if(evt.value in morningModes && state.sleeping)
    {
    	state.sleeping = false
        state.wakingUp = true
        state.away = false
        state.home = false
        state.arriving = false 
        state.needToNotify = false
        state.notifyMode = false
        state.lastOut = now()
    }
    if(evt.value in awayModes)
    {
    	state.sleeping = false
        state.wakingUp = false
        state.away = true
        state.home = false
        state.arriving = false 
        state.needToNotify = false
        state.notifyMode = false
    }
    if(evt.value in homeModes && state.away)
    {
    	state.sleeping = false
        state.wakingUp = false
        state.away = false
        state.home = true
        state.arriving = true
        state.needToNotify = false
        state.notifyMode = false
        state.lastOut = now()
    }
    if(evt.value in notifyModes)
    {
    	state.notifyMode = true
    }
    

}

def manualHandler(evt) {
	unschedule()
    runEvery5Minutes(checkStatus)
    
	log.debug "Manual Override"
    state.lastOut = now()
    
    state.needToNotify = false
    state.wakingUp = false
    state.arriving = false
    
    manualSwitch.off()

}

def checkBedtime() {
	log.debug "Check Bedtime"
    
    if(now() - state.lastOut >= 60 * 60000)
    {
    	sendPushMessage("Warning: It has been over an hour since the dog has gone out and you are about to go to bed")
    }
    
}

def checkStatus() {
	log.debug "Check Status"
    log.debug "Last Out: $state.lastOut"
    def timeSince = ((now() - state.lastOut) / 60000)
    log.debug "Minutes Since Last Out: $timeSince"
    
    if(state.arriving)
    {
    	if(now() - state.lastOut >= morningDelay * 60000)
        {
        	state.lastNotify = now()
            state.needToNotify = true
            sendMessage()
        }
    }
    if(state.wakingUp)
    {
    	if(now() - state.lastOut >= arrivingDelay * 60000)
        {
        	state.lastNotify = now()
            state.needToNotify = true
            sendMessage()
        }
    }
    
    if(now() - state.lastOut >= delayTime * 60 * 60000 && !state.needToNotify)
    {
        state.needToNotify = true
        
        if(notifyModes.contains(location.mode) || state.notifyMode)
        {
        	sendMessage()
        	state.lastNotify = now()
        }
    }
    
    
    if(state.needToNotify)
    {
    	log.debug "Notify"
    	if(now() - state.lastNotify >= notifyDelay * 60000)
        {
        	if(notifyModes.contains(location.mode))
            {
                sendMessage()
                state.lastNotify = now()
            }
        }
        
    }

}



def sendMessage() {
	log.debug "Sending Notification"
	def msg = messageText ?: defaultText()
	//log.debug "$evt.name:$evt.value, pushAndPhone:$pushAndPhone, '$msg'"

    if (location.contactBookEnabled) {
        sendNotificationToContacts(msg, recipients)
    }
    else {

        if (!phone || pushAndPhone != "No") {
            log.debug "sending push"
            sendPush(msg)
        }
        if (phone) {
            log.debug "sending SMS"
            sendSms(phone, msg)
        }
    }
}

def sendPushMessage(msg) {
	//log.debug "$evt.name:$evt.value, pushAndPhone:$pushAndPhone, '$msg'"

    if (location.contactBookEnabled) {
        sendNotificationToContacts(msg, recipients)
    }
    else {

        if (!phone || pushAndPhone != "No") {
            log.debug "sending push"
            sendPush(msg)
        }
        if (phone) {
            log.debug "sending SMS"
            sendSms(phone, msg)
        }
    }
}


def defaultText() {
	//ToDo: Calculate time since the dog has gone out.
	return "The dog needs to go out"
}
