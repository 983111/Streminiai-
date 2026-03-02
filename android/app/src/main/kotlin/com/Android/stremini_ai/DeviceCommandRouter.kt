package com.Android.stremini_ai

import kotlinx.coroutines.delay

class DeviceCommandRouter {
    data class DirectCommandResult(val executed: Boolean, val statusMessage: String, val details: String)

    fun isLikelyDeviceCommand(text: String): Boolean {
        val normalized = text.lowercase()
        return KEYWORDS.any { normalized.contains(it) }
    }

    suspend fun tryDirectDeviceCommand(command: String): DirectCommandResult {
        val normalized = command.trim().lowercase()
        return when {
            normalized.contains("whatsapp") && (normalized.contains("message") || normalized.contains("send")) -> {
                val contact = extractContact(command)
                val message = extractMessage(command)
                if (contact.isNotBlank()) {
                    ScreenReaderService.runWhatsAppMessageAutomation(contact, message)
                    delay(3000)
                    DirectCommandResult(true, "WhatsApp message sent to $contact", "Sent '$message' to $contact via WhatsApp")
                } else DirectCommandResult(false, "Contact not found", "Could not extract contact name from: $command")
            }
            normalized.startsWith("open ") || normalized.startsWith("launch ") -> {
                val appName = normalized.removePrefix("open ").removePrefix("launch ").trim()
                val opened = ScreenReaderService.getInstance()?.openAppByName(appName) ?: false
                if (opened) DirectCommandResult(true, "Opened $appName", "App '$appName' launched successfully")
                else DirectCommandResult(false, "App not found", "Could not find app: $appName")
            }
            normalized.contains("go home") || normalized == "home" -> execute("go home", "Navigated home", "Pressed home button")
            normalized.contains("go back") || normalized == "back" -> execute("go back", "Navigated back", "Pressed back button")
            normalized.contains("recent apps") -> execute("recent apps", "Opened recent apps", "Showed app switcher")
            normalized.contains("take screenshot") -> execute("take screenshot", "Screenshot taken", "Screen captured")
            normalized.contains("scroll down") -> execute("scroll down", "Scrolled down", "Page scrolled down")
            normalized.contains("scroll up") -> execute("scroll up", "Scrolled up", "Page scrolled up")
            normalized.startsWith("swipe ") -> execute("swipe ${normalized.removePrefix("swipe ").trim()}", "Swiped", "Gesture performed")
            normalized.startsWith("tap ") || normalized.startsWith("click ") -> {
                ScreenReaderService.runGenericAutomation(command)
                delay(500)
                DirectCommandResult(true, "Tapped element", "Tapped: ${normalized.removePrefix("tap ").removePrefix("click ")}")
            }
            normalized.startsWith("type ") -> execute(command, "Text typed", "Typed: ${normalized.removePrefix("type ")}")
            normalized.startsWith("search for ") || normalized.startsWith("search ") -> execute(command, "Search performed", "Search request sent")
            normalized.contains("volume up") -> execute("volume up", "Volume increased", "Volume turned up")
            normalized.contains("volume down") -> execute("volume down", "Volume decreased", "Volume turned down")
            normalized.contains("mute") -> execute("mute", "Device muted", "Ringer set to silent")
            normalized.startsWith("call ") -> execute(command, "Calling...", "Initiating call")
            normalized.startsWith("go to ") || normalized.startsWith("open website") || normalized.startsWith("browse to ") -> execute(command, "Opening website", "Loading destination")
            normalized.contains("open settings") || normalized.contains("wifi") || normalized.contains("bluetooth") || normalized.contains("display settings") -> execute(command, "Opened settings", "Settings opened")
            normalized.contains("lock") -> execute("lock screen", "Screen locked", "Device locked")
            else -> DirectCommandResult(false, "Not a device command", "Sending to AI backend...")
        }
    }

    private fun execute(action: String, status: String, details: String): DirectCommandResult {
        ScreenReaderService.runGenericAutomation(action)
        return DirectCommandResult(true, status, details)
    }

    private fun extractContact(command: String): String {
        val patterns = listOf(
            Regex("(?:message|send|whatsapp)\\s+(?:to\\s+)?([a-zA-Z][a-zA-Z0-9 _.-]{1,30})(?:\\s+(?:that|saying|:|-|,)|\\s*$)", RegexOption.IGNORE_CASE),
            Regex("to\\s+([a-zA-Z][a-zA-Z0-9 _.-]{1,30})(?:\\s+(?:that|saying)|\\s*$)", RegexOption.IGNORE_CASE)
        )
        return patterns.firstNotNullOfOrNull { it.find(command)?.groupValues?.get(1)?.trim() } ?: ""
    }

    private fun extractMessage(command: String): String {
        val patterns = listOf(
            Regex("(?:that|saying|message:|with message)\\s+(.+)$", RegexOption.IGNORE_CASE),
            Regex(":\\s*(.+)$"),
            Regex("-\\s*(.+)$")
        )
        return patterns.firstNotNullOfOrNull { it.find(command)?.groupValues?.get(1)?.trim() } ?: "Hello"
    }

    private companion object {
        val KEYWORDS = listOf(
            "open", "launch", "close", "go to", "navigate to", "tap", "click", "press", "scroll", "swipe",
            "type", "write", "fill", "search for", "call", "message", "send", "whatsapp", "take screenshot",
            "volume", "brightness", "mute", "unmute", "go home", "go back", "recent apps", "notifications",
            "settings", "play", "pause", "stop", "next", "previous", "zoom in", "zoom out", "copy", "paste",
            "cut", "select all", "find", "read screen"
        )
    }
}
