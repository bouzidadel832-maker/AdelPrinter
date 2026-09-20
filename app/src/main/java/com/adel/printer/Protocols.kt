package com.adel.printer

import java.io.ByteArrayOutputStream

/**
 * Échantillons d'étiquette de TEST pour les 3 protocoles courants des
 * imprimantes d'étiquettes/tickets. On n'ASSUME pas le protocole : on les
 * envoie un par un et l'utilisateur regarde lequel imprime correctement.
 *
 *  - TSPL  : imprimantes d'étiquettes type Smart/Xprinter/Gprinter/Zebra-compatibles
 *  - ESCPOS: imprimantes tickets 58/80mm (beaucoup de "Smart" en sont)
 *  - ZPL   : imprimantes Zebra et compatibles
 *
 * Chaque payload est du binaire brut envoyé tel quel au endpoint BULK OUT.
 */
object Protocols {

    enum class Kind { TSPL, ESCPOS, ZPL }

    fun testLabel(kind: Kind): ByteArray = when (kind) {
        Kind.TSPL   -> tspl()
        Kind.ESCPOS -> escpos()
        Kind.ZPL    -> zpl()
    }

    // ---- TSPL (étiquette 100x60 mm) ----
    private fun tspl(): ByteArray {
        val sb = StringBuilder()
        sb.append("SIZE 100 mm,60 mm\r\n")
        sb.append("GAP 3 mm,0\r\n")
        sb.append("DIRECTION 1\r\n")
        sb.append("DENSITY 8\r\n")
        sb.append("SPEED 4\r\n")
        sb.append("CLS\r\n")
        sb.append("TEXT 30,30,\"3\",0,1,1,\"ADEL - TEST TSPL\"\r\n")
        sb.append("TEXT 30,80,\"2\",0,1,1,\"Si vous lisez ceci: TSPL OK\"\r\n")
        sb.append("BARCODE 30,130,\"128\",70,1,0,2,2,\"ADEL123\"\r\n")
        sb.append("PRINT 1,1\r\n")
        return sb.toString().toByteArray(Charsets.US_ASCII)
    }

    // ---- ESC/POS (ticket) ----
    private fun escpos(): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(0x1B, 0x40))                 // ESC @  init
        out.write(byteArrayOf(0x1B, 0x61, 0x01))           // ESC a 1  centrer
        out.write(byteArrayOf(0x1D, 0x21, 0x11))           // GS ! taille double
        out.write("ADEL - TEST\n".toByteArray(Charsets.US_ASCII))
        out.write(byteArrayOf(0x1D, 0x21, 0x00))           // taille normale
        out.write("Si vous lisez ceci:\n".toByteArray(Charsets.US_ASCII))
        out.write("ESC/POS OK\n".toByteArray(Charsets.US_ASCII))
        out.write("\n\n\n".toByteArray(Charsets.US_ASCII))
        out.write(byteArrayOf(0x1D, 0x56, 0x00))           // GS V 0  coupe (si dispo)
        return out.toByteArray()
    }

    // ---- ZPL (étiquette) ----
    private fun zpl(): ByteArray {
        val sb = StringBuilder()
        sb.append("^XA\r\n")
        sb.append("^CF0,40\r\n")
        sb.append("^FO40,40^FDADEL - TEST ZPL^FS\r\n")
        sb.append("^FO40,100^A0N,28,28^FDSi vous lisez ceci: ZPL OK^FS\r\n")
        sb.append("^FO40,150^BCN,80,Y,N,N^FDADEL123^FS\r\n")
        sb.append("^XZ\r\n")
        return sb.toString().toByteArray(Charsets.US_ASCII)
    }
}
