package com.adel.printer

import org.json.JSONObject
import java.io.ByteArrayOutputStream

/**
 * Construit l'étiquette TSPL d'une commande à partir du JSON envoyé par Adel.
 * Champs attendus (tous optionnels, tolérant) : num, client, tel, wilaya,
 * commune, adresse, montant, livraison, transporteur, colis, poids, frais, notes.
 *
 * Le nom client / l'adresse peuvent contenir de l'arabe : ils sont rendus en
 * bitmap. Les champs latins/chiffres restent en TEXT TSPL. Extensible plus tard
 * (code-barres BARCODE, QR QRCODE, logos via BITMAP).
 */
object LabelBuilder {
    fun build(bridge: UsbPrinterBridge, o: JSONObject): ByteArray {
        val cfg = UsbPrinterBridge.LabelConfig(
            widthMm = o.optInt("widthMm", 100),
            heightMm = o.optInt("heightMm", 80),
            density = o.optInt("density", 8),
            speed = o.optInt("speed", 4),
            direction = o.optInt("direction", 1)
        )
        val out = ByteArrayOutputStream()
        out.write("SIZE ${cfg.widthMm} mm,${cfg.heightMm} mm\n".toByteArray(Charsets.US_ASCII))
        out.write("GAP ${cfg.gapMm} mm,0\n".toByteArray(Charsets.US_ASCII))
        out.write("DIRECTION ${cfg.direction}\n".toByteArray(Charsets.US_ASCII))
        out.write("DENSITY ${cfg.density}\n".toByteArray(Charsets.US_ASCII))
        out.write("SPEED ${cfg.speed}\n".toByteArray(Charsets.US_ASCII))
        out.write("CLS\n".toByteArray(Charsets.US_ASCII))

        fun latin(x: Int, y: Int, font: String, s: String) {
            val safe = s.replace("\"", "'")
            out.write("TEXT $x,$y,\"$font\",0,1,1,\"$safe\"\n".toByteArray(Charsets.UTF_8))
        }
        fun arabic(x: Int, y: Int, s: String) {
            if (s.isBlank()) return
            val bmp = bridge.renderArabicBitmap(s, cfg.widthDots - x - 20)
            out.write(bridge.bitmapToTspl(bmp, x, y))
        }

        var y = 16
        latin(16, y, "4", "Adel  -  ${o.optString("transporteur", "").uppercase()}"); y += 44
        latin(16, y, "3", "N: ${o.optString("num", "")}"); y += 36
        // Client + adresse : possiblement arabe -> bitmap
        arabic(16, y, o.optString("client", "")); y += 44
        arabic(16, y, o.optString("adresse", "")); y += 44
        latin(16, y, "3", "Tel: ${o.optString("tel", "")}"); y += 34
        latin(16, y, "3", "${o.optString("wilaya", "")}  ${o.optString("commune", "")}"); y += 34
        latin(16, y, "4", "Montant: ${o.optString("montant", "")} DA"); y += 40
        latin(16, y, "3", "Livraison: ${o.optString("livraison", "")}   Colis: ${o.optString("colis", "1")}"); y += 34

        // Emplacements prêts pour extensions futures (décommenter quand voulu) :
        // out.write("BARCODE 16,$y,\"128\",70,1,0,2,2,\"${o.optString("num")}\"\n".toByteArray())
        // out.write("QRCODE 400,20,H,6,A,0,\"${o.optString("num")}\"\n".toByteArray())

        out.write("PRINT ${o.optInt("copies", 1)},1\n".toByteArray(Charsets.US_ASCII))
        return out.toByteArray()
    }
}
