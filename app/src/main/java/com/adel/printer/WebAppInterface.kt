package com.adel.printer

import android.webkit.JavascriptInterface
import android.webkit.WebView
import org.json.JSONObject

/**
 * API exposée à la Web App Adel via window.AndroidPrinter.
 * Toutes les méthodes renvoient une chaîne JSON { ok, message, ... }
 * SANS jamais bloquer le thread UI pour l'USB (callbacks pour la permission).
 *
 * Sécurité : @JavascriptInterface expose UNIQUEMENT ces méthodes. Aucune autre
 * capacité Android n'est accessible depuis le JS.
 */
class WebAppInterface(
    private val webView: WebView,
    private val bridge: UsbPrinterBridge
) {
    private fun ok(msg: String, extra: JSONObject? = null): String {
        val o = extra ?: JSONObject()
        o.put("ok", true); o.put("message", msg); return o.toString()
    }
    private fun err(msg: String): String =
        JSONObject().put("ok", false).put("message", msg).toString()

    /** Le JS peut ainsi savoir qu'il tourne bien dans le wrapper. */
    @JavascriptInterface
    fun isAvailable(): Boolean = true

    /** Liste des périphériques USB (JSON) pour l'écran de test. */
    @JavascriptInterface
    fun getUsbInfo(): String {
        return try { JSONObject().put("ok", true).put("devices", bridge.deviceInfoJson()).toString() }
        catch (e: Exception) { err("getUsbInfo: ${e.message}") }
    }

    /** Détecte l'imprimante. Renvoie ok=false avec message clair si absente. */
    @JavascriptInterface
    fun detectPrinter(): String {
        val dev = bridge.findPrinter()
            ?: return err("Imprimante Smart non connectée. Vérifiez la connexion USB OTG.")
        val o = JSONObject()
        o.put("deviceName", dev.deviceName)
        o.put("productId", dev.productId)
        o.put("vendorId", dev.vendorId)
        return ok("Imprimante détectée", o)
    }

    /** Demande la permission USB (asynchrone). Le résultat revient au JS via callback JS. */
    @JavascriptInterface
    fun requestPermission(jsCallback: String) {
        val dev = bridge.findPrinter()
        if (dev == null) { callJs(jsCallback, err("Imprimante non connectée")); return }
        bridge.ensurePermission(dev) { granted ->
            callJs(jsCallback, if (granted) ok("Permission accordée") else err("Permission USB refusée"))
        }
    }

    /** Imprime l'étiquette de TEST. Vérifie présence + permission avant d'envoyer. */
    @JavascriptInterface
    fun printTest(): String {
        val dev = bridge.findPrinter()
            ?: return err("Imprimante non connectée. Vérifiez la connexion USB OTG.")
        if (!hasPerm(dev)) return err("Permission USB non accordée. Appuyez d'abord sur \"Autoriser USB\".")
        val bytes = bridge.buildTestLabel(UsbPrinterBridge.LabelConfig())
        val r = bridge.sendRaw(dev, bytes)
        return if (r.isSuccess) ok("Impression envoyée")
        else err("Échec impression : ${r.exceptionOrNull()?.message}")
    }

    /**
     * Teste un protocole précis (TSPL, ESCPOS ou ZPL) : envoie une petite
     * étiquette de test. Sert à découvrir le protocole réel de l'imprimante.
     */
    @JavascriptInterface
    fun printProtocol(name: String): String {
        val dev = bridge.findPrinter()
            ?: return err("Imprimante non connectée. Vérifiez la connexion USB OTG.")
        if (!hasPerm(dev)) return err("Permission USB non accordée. Appuyez d'abord sur \"Autoriser USB\".")
        val kind = when (name.trim().uppercase()) {
            "TSPL" -> Protocols.Kind.TSPL
            "ESCPOS", "ESC/POS", "ESC-POS" -> Protocols.Kind.ESCPOS
            "ZPL" -> Protocols.Kind.ZPL
            else -> return err("Protocole inconnu: $name")
        }
        val bytes = Protocols.testLabel(kind)
        val r = bridge.sendRaw(dev, bytes)
        return if (r.isSuccess) ok("Test $name envoyé (${bytes.size} octets)")
        else err("Échec $name : ${r.exceptionOrNull()?.message}")
    }

    /** Impression d'une étiquette de commande (branché plus tard aux vraies commandes). */
    @JavascriptInterface
    fun printOrderLabel(orderJson: String): String {
        val dev = bridge.findPrinter()
            ?: return err("Imprimante Smart non connectée. Vérifiez la connexion USB OTG.")
        if (!hasPerm(dev)) return err("Permission USB non accordée.")
        return try {
            val order = JSONObject(orderJson)
            val bytes = LabelBuilder.build(bridge, order)
            val r = bridge.sendRaw(dev, bytes)
            if (r.isSuccess) ok("Impression envoyée")
            else err("Échec impression : ${r.exceptionOrNull()?.message}")
        } catch (e: Exception) { err("printOrderLabel: ${e.message}") }
    }

    /** Renvoie les logs de diagnostic accumulés (USB, endpoints, permission, envoi). */
    @JavascriptInterface
    fun getLogs(): String = JSONObject().put("ok", true).put("logs", bridge.drainLogs()).toString()

    private fun hasPerm(dev: android.hardware.usb.UsbDevice): Boolean {
        val um = webView.context.getSystemService(android.content.Context.USB_SERVICE)
                as android.hardware.usb.UsbManager
        return um.hasPermission(dev)
    }

    private fun callJs(fnName: String, arg: String) {
        webView.post {
            val escaped = arg.replace("\\", "\\\\").replace("'", "\\'")
            webView.evaluateJavascript("window.$fnName && window.$fnName('$escaped');", null)
        }
    }
}
