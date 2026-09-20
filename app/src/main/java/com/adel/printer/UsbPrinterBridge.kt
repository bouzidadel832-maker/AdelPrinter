package com.adel.printer

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log
import org.json.JSONObject
import java.io.ByteArrayOutputStream

/**
 * Pont d'impression USB pour l'imprimante d'étiquettes Smart (et compatibles TSPL).
 *
 * Rien n'est supposé "universel" : on inspecte chaque périphérique branché,
 * on identifie l'imprimante par sa classe/nom, on trouve dynamiquement
 * l'interface et le endpoint BULK OUT, on demande la permission Android,
 * puis on envoie du TSPL. Le texte arabe est rendu en bitmap (shaping RTL
 * natif d'Android) et imprimé via la commande TSPL BITMAP.
 *
 * Aucun PC, aucun Print Bridge, aucun Bluetooth, aucun Wi-Fi.
 */
class UsbPrinterBridge(private val ctx: Context) {

    companion object {
        private const val TAG = "AdelPrinter"
        private const val ACTION_USB_PERMISSION = "com.adel.printer.USB_PERMISSION"
        // Indices pour reconnaître l'imprimante Smart (et compatibles TSPL) par le nom produit.
        private val NAME_HINTS = listOf("smart", "xp-", "xprinter", "printer", "410", "thermal", "label")
    }

    private val usbManager: UsbManager =
        ctx.getSystemService(Context.USB_SERVICE) as UsbManager

    private val logBuffer = StringBuilder()
    private fun log(msg: String) {
        Log.d(TAG, msg)
        logBuffer.append(msg).append("\n")
    }
    fun drainLogs(): String { val s = logBuffer.toString(); logBuffer.setLength(0); return s }

    // ---- 1) DÉTECTION -------------------------------------------------------

    /** Retourne le 1er périphérique qui ressemble à une imprimante, sinon null. */
    fun findPrinter(): UsbDevice? {
        val devices = usbManager.deviceList
        log("== Périphériques USB détectés : ${devices.size} ==")
        var candidate: UsbDevice? = null
        for ((_, dev) in devices) {
            log(decribe(dev))
            if (looksLikePrinter(dev) && candidate == null) candidate = dev
        }
        if (candidate == null && devices.isNotEmpty()) {
            // Repli : si un seul périphérique est branché, on le prend (l'utilisateur
            // a branché l'imprimante volontairement). On ne devine jamais en silence
            // s'il y en a plusieurs.
            if (devices.size == 1) {
                candidate = devices.values.first()
                log("Aucune correspondance stricte, mais 1 seul périphérique -> candidat: ${candidate?.deviceName}")
            }
        }
        log(if (candidate != null) "-> Imprimante retenue: ${candidate.deviceName}" else "-> Aucune imprimante identifiée")
        return candidate
    }

    private fun looksLikePrinter(dev: UsbDevice): Boolean {
        // a) classe imprimante au niveau device
        if (dev.deviceClass == UsbConstants.USB_CLASS_PRINTER) return true
        // b) classe imprimante au niveau d'une interface
        for (i in 0 until dev.interfaceCount) {
            if (dev.getInterface(i).interfaceClass == UsbConstants.USB_CLASS_PRINTER) return true
        }
        // c) nom produit évocateur
        val name = (safeProduct(dev) ?: "").lowercase()
        return NAME_HINTS.any { name.contains(it) }
    }

    private fun safeProduct(dev: UsbDevice): String? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) dev.productName else null

    private fun safeManufacturer(dev: UsbDevice): String? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) dev.manufacturerName else null

    /** Description lisible d'un périphérique pour les logs de diagnostic. */
    fun decribe(dev: UsbDevice): String {
        val sb = StringBuilder()
        sb.append("Device ${dev.deviceName}\n")
        sb.append("  manufacturer=${safeManufacturer(dev) ?: "?"}  product=${safeProduct(dev) ?: "?"}\n")
        sb.append("  vendorId=0x%04X (${dev.vendorId})  productId=0x%04X (${dev.productId})\n"
            .format(dev.vendorId, dev.productId))
        sb.append("  deviceClass=${dev.deviceClass}  interfaces=${dev.interfaceCount}\n")
        for (i in 0 until dev.interfaceCount) {
            val itf = dev.getInterface(i)
            sb.append("   itf#$i class=${itf.interfaceClass} endpoints=${itf.endpointCount}\n")
            for (e in 0 until itf.endpointCount) {
                val ep = itf.getEndpoint(e)
                val dir = if (ep.direction == UsbConstants.USB_DIR_OUT) "OUT" else "IN"
                sb.append("      ep#$e addr=0x%02X type=${ep.type} dir=$dir\n".format(ep.address))
            }
        }
        return sb.toString()
    }

    /** Infos USB en JSON pour l'écran de test Adel. */
    fun deviceInfoJson(): String {
        val arr = StringBuilder("[")
        val devices = usbManager.deviceList.values.toList()
        devices.forEachIndexed { idx, dev ->
            val o = JSONObject()
            o.put("deviceName", dev.deviceName)
            o.put("manufacturer", safeManufacturer(dev) ?: "")
            o.put("product", safeProduct(dev) ?: "")
            o.put("vendorId", dev.vendorId)
            o.put("productId", dev.productId)
            o.put("vendorIdHex", "0x%04X".format(dev.vendorId))
            o.put("productIdHex", "0x%04X".format(dev.productId))
            o.put("deviceClass", dev.deviceClass)
            o.put("interfaceCount", dev.interfaceCount)
            o.put("isPrinter", looksLikePrinter(dev))
            o.put("hasPermission", usbManager.hasPermission(dev))
            if (idx > 0) arr.append(",")
            arr.append(o.toString())
        }
        arr.append("]")
        return arr.toString()
    }

    // ---- 2) PERMISSION ------------------------------------------------------

    private var permCallback: ((Boolean) -> Unit)? = null
    private val permReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            if (intent?.action == ACTION_USB_PERMISSION) {
                val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                log("Permission USB ${if (granted) "ACCORDÉE" else "REFUSÉE"}")
                try { ctx.unregisterReceiver(this) } catch (_: Exception) {}
                permCallback?.invoke(granted)
                permCallback = null
            }
        }
    }

    fun ensurePermission(dev: UsbDevice, cb: (Boolean) -> Unit) {
        if (usbManager.hasPermission(dev)) { cb(true); return }
        permCallback = cb
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            PendingIntent.FLAG_MUTABLE else 0
        val pi = PendingIntent.getBroadcast(ctx, 0, Intent(ACTION_USB_PERMISSION), flags)
        val filter = IntentFilter(ACTION_USB_PERMISSION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ctx.registerReceiver(permReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            ctx.registerReceiver(permReceiver, filter)
        }
        log("Demande de permission USB pour ${dev.deviceName}…")
        usbManager.requestPermission(dev, pi)
    }

    // ---- 3) ENVOI DES OCTETS ------------------------------------------------

    /** Trouve l'interface + endpoint BULK OUT ; ouvre, envoie, ferme. */
    fun sendRaw(dev: UsbDevice, data: ByteArray): Result<Unit> {
        var chosenItf: UsbInterface? = null
        var outEp: UsbEndpoint? = null
        // Priorité à une interface de classe imprimante ; sinon 1re interface
        // exposant un endpoint BULK OUT.
        outer@ for (i in 0 until dev.interfaceCount) {
            val itf = dev.getInterface(i)
            for (e in 0 until itf.endpointCount) {
                val ep = itf.getEndpoint(e)
                if (ep.direction == UsbConstants.USB_DIR_OUT &&
                    ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                    if (itf.interfaceClass == UsbConstants.USB_CLASS_PRINTER) {
                        chosenItf = itf; outEp = ep; break@outer
                    }
                    if (chosenItf == null) { chosenItf = itf; outEp = ep }
                }
            }
        }
        if (chosenItf == null || outEp == null)
            return Result.failure(IllegalStateException("Aucun endpoint BULK OUT trouvé sur l'imprimante"))

        log("Interface choisie: class=${chosenItf.interfaceClass}  endpoint OUT addr=0x%02X".format(outEp.address))

        val conn: UsbDeviceConnection = usbManager.openDevice(dev)
            ?: return Result.failure(IllegalStateException("Impossible d'ouvrir la connexion USB (openDevice a échoué)"))
        try {
            if (!conn.claimInterface(chosenItf, true))
                return Result.failure(IllegalStateException("claimInterface a échoué"))
            // Envoi par blocs (certaines imprimantes limitent la taille par transfert).
            val chunk = 16384
            var offset = 0
            while (offset < data.size) {
                val len = minOf(chunk, data.size - offset)
                val slice = data.copyOfRange(offset, offset + len)
                val sent = conn.bulkTransfer(outEp, slice, slice.size, 5000)
                if (sent < 0)
                    return Result.failure(IllegalStateException("bulkTransfer a échoué à l'offset $offset"))
                log("Envoyé $sent octets (offset $offset)")
                offset += len
            }
            return Result.success(Unit)
        } finally {
            try { conn.releaseInterface(chosenItf) } catch (_: Exception) {}
            conn.close()
            log("Connexion USB fermée proprement")
        }
    }

    // ---- 4) TSPL ------------------------------------------------------------

    /**
     * Construit une étiquette TSPL. Le texte arabe est rendu en bitmap
     * (Android connecte les lettres et gère le RTL nativement), le reste
     * en texte TSPL classique. Tous les paramètres sont ajustables.
     */
    fun buildTestLabel(cfg: LabelConfig): ByteArray {
        val out = ByteArrayOutputStream()
        val header = tsplHeader(cfg)
        out.write(header.toByteArray(Charsets.US_ASCII))
        // Ligne latine simple en TSPL
        out.write("TEXT 20,20,\"3\",0,1,1,\"Adel - TEST Smart\"\n".toByteArray(Charsets.US_ASCII))
        out.write("TEXT 20,60,\"2\",0,1,1,\"USB OTG OK\"\n".toByteArray(Charsets.US_ASCII))
        // Bloc arabe en bitmap (démonstration du shaping)
        val bmp = renderArabicBitmap("طباعة تجريبية عربية", cfg.widthDots - 40)
        out.write(bitmapToTspl(bmp, 20, 100))
        out.write("PRINT 1,1\n".toByteArray(Charsets.US_ASCII))
        return out.toByteArray()
    }

    private fun tsplHeader(cfg: LabelConfig): String {
        val sb = StringBuilder()
        sb.append("SIZE ${cfg.widthMm} mm,${cfg.heightMm} mm\n")
        sb.append("GAP ${cfg.gapMm} mm,0\n")
        sb.append("DIRECTION ${cfg.direction}\n")
        sb.append("DENSITY ${cfg.density}\n")
        sb.append("SPEED ${cfg.speed}\n")
        sb.append("CLS\n")
        return sb.toString()
    }

    // ---- 5) ARABE -> BITMAP -------------------------------------------------

    /** Rend une chaîne (arabe incluse) en bitmap 1-bit prête pour TSPL. */
    fun renderArabicBitmap(text: String, maxWidthDots: Int): Bitmap {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 34f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT   // RTL : on aligne à droite
        }
        val w = maxWidthDots.coerceAtLeast(64)
        val h = (paint.textSize * 1.6f).toInt()
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.WHITE)
        // Android applique le shaping arabe et le RTL automatiquement au drawText.
        canvas.drawText(text, (w - 6).toFloat(), paint.textSize, paint)
        return bmp
    }

    /** Convertit un bitmap en commande TSPL BITMAP (mode 0, 1bpp). */
    fun bitmapToTspl(src: Bitmap, x: Int, y: Int): ByteArray {
        val w = src.width; val h = src.height
        val bytesPerRow = (w + 7) / 8
        val body = ByteArray(bytesPerRow * h)
        for (yy in 0 until h) {
            for (xx in 0 until w) {
                val p = src.getPixel(xx, yy)
                val lum = (Color.red(p) + Color.green(p) + Color.blue(p)) / 3
                // TSPL BITMAP : bit 0 = point imprimé (noir). On met à 1 le blanc.
                if (lum > 128) {
                    val idx = yy * bytesPerRow + (xx / 8)
                    body[idx] = (body[idx].toInt() or (0x80 shr (xx % 8))).toByte()
                }
            }
        }
        val out = ByteArrayOutputStream()
        out.write("BITMAP $x,$y,$bytesPerRow,$h,0,".toByteArray(Charsets.US_ASCII))
        out.write(body)
        out.write("\n".toByteArray(Charsets.US_ASCII))
        return out.toByteArray()
    }

    data class LabelConfig(
        val widthMm: Int = 100,     // 4 pouces
        val heightMm: Int = 60,
        val gapMm: Int = 3,
        val density: Int = 8,       // 0..15
        val speed: Int = 4,         // vitesse
        val direction: Int = 1,     // 0 ou 1 (rotation du sens d'impression)
        val dpi: Int = 203
    ) {
        // 203 dpi => 8 dots/mm
        val widthDots: Int get() = widthMm * dpi / 25
    }
}
