# Adel Printer — Pont USB Android pour Xprinter XP-410B

WebView plein écran qui charge Adel EN LIGNE (Netlify) et fournit à la web app
un pont d'impression USB OTG via `window.AndroidPrinter`. Aucune copie locale du
HTML : tes mises à jour Netlify sont visibles immédiatement.

## Avant de compiler
1. Ouvre `app/src/main/java/com/adel/printer/MainActivity.kt`
   → remplace `https://TON-SITE.netlify.app/` par TON URL Netlify réelle.

## Compiler l'APK (Android Studio)
1. Android Studio → Open → sélectionne le dossier `AdelPrinter`.
2. Laisse Gradle synchroniser (il télécharge le SDK/plugins).
3. Build → Build Bundle(s)/APK(s) → **Build APK(s)**.
4. L'APK est dans `app/build/outputs/apk/debug/app-debug.apk`.

## Installer sur la tablette D-TECH T101
1. Copie l'APK sur la tablette (câble/clé USB) ou via `adb install app-debug.apk`.
2. Autorise « Sources inconnues » si demandé.
3. Branche la XP-410B en USB-C OTG.
4. Ouvre « Adel Printer ». Android proposera d'ouvrir l'app quand l'imprimante
   est branchée (grâce au filtre USB) — accepte.

## Tester (écran TEST IMPRIMANTE dans Adel)
Dans Adel → menu admin → **Test imprimante** :
1. **Détecter l'imprimante** → doit afficher XP-410B (nom/VID/PID).
2. **Afficher informations USB** → liste des périphériques + interfaces/endpoints.
3. **Autoriser USB** → boîte de dialogue Android, accepte.
4. **Imprimer test** → petite étiquette (latin + arabe bitmap) sur la XP-410B.
5. **Imprimer étiquette exemple** → étiquette de commande complète.

Le panneau « Diagnostic » affiche les logs (devices, manufacturer, product,
vendorId, productId, interface, endpoint, permission, octets envoyés).

## Notes techniques
- **Aucun VID/PID en dur** : la détection inspecte classe imprimante / nom
  produit, puis trouve dynamiquement l'endpoint BULK OUT.
- **Arabe** : rendu en bitmap par Android (shaping RTL correct, lettres liées),
  imprimé en TSPL `BITMAP`. Le latin/chiffres reste en TSPL `TEXT`.
- **TSPL paramétrable** : SIZE (largeur/hauteur), GAP, DENSITY, SPEED, DIRECTION,
  position X/Y — voir `LabelConfig` et `LabelBuilder`.
- Extensions prêtes (commentées) : BARCODE 128, QRCODE, logos via BITMAP.

## Ce qui n'est PAS testé ici
La compilation et le test USB réel doivent être faits sur ta tablette : cet
environnement n'a ni SDK Android ni imprimante. Le code est complet et
l'algorithme TSPL/bitmap a été vérifié, mais NE considère l'impression comme
fonctionnelle qu'après un vrai tirage sur la XP-410B.
