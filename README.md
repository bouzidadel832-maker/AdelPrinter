# AdelPrinter — pont USB OTG pour le site Adel + imprimante Smart (Android)

L'app ouvre TON site Adel en ligne (Netlify) dans une WebView et lui ajoute un
pont d'impression USB OTG (window.AndroidPrinter) vers ton imprimante
d'étiquettes **Smart** (protocole TSPL). Aucun PC, aucun Print Bridge, aucun
Bluetooth. Le site n'est pas modifié ni remplacé.

Architecture :
  Tablette → APK AdelPrinter → WebView → https://lovely-begonia-d851a0.netlify.app/
  → window.AndroidPrinter → USB OTG → imprimante Smart

## Ce qui est inclus
- WebView plein écran qui charge ton URL Netlify actuelle (dans MainActivity.kt).
- Pont USB : détection auto de l'imprimante Smart (sans VID/PID en dur —
  reconnaissance par classe USB imprimante et par nom produit), endpoint
  BULK OUT dynamique, permission USB, logs de diagnostic.
- Impression au format TSPL par défaut (étiquettes avec gap), avec repli
  ESC/POS ou ZPL disponible via l'écran de diagnostic si besoin.
- Les boutons d'impression de ton site (Détecter / Autoriser USB / Imprimer test…)
  appellent le pont et impriment.
- Bouton flottant "🖨️ Test" (coin bas-droit) : ouvre une page de diagnostic
  EMBARQUÉE (hors-ligne) avec les 3 essais de protocole TSPL / ESC-POS / ZPL,
  pour confirmer le protocole réel de l'imprimante sans toucher au site.

## Compiler l'APK (GitHub Actions, déjà configuré)
1. Le workflow `.github/workflows/build-apk.yml` compile l'APK debug à chaque
   push sur `main`/`master`, et est aussi déclenchable manuellement
   (onglet **Actions** → "Build AdelPrinter APK" → **Run workflow**).
2. Build fini (3–6 min) → Artifacts → télécharge `AdelPrinter-apk` (app-debug.apk).

## Sur la tablette
1. Installe app-debug.apk (autorise Sources inconnues).
2. Branche l'imprimante Smart en USB OTG, ouvre AdelPrinter → ton site s'affiche.
3. Pour confirmer le protocole : appuie le bouton flottant "🖨️ Test" → Détecter
   → Autoriser USB → TSPL (déjà le format par défaut de l'imprimante Smart).
4. Pour l'usage réel : utilise les boutons d'impression de ton site.

## Changer l'URL plus tard
Dans MainActivity.kt : adelUrl = "https://…". Rien d'autre à toucher.

## Non testé ici
Compilation vérifiée par GitHub Actions ; le test USB réel avec l'imprimante
Smart se fait sur ta tablette. Le protocole TSPL est celui attendu pour cette
imprimante, mais ne le considère confirmé qu'après un tirage réel.
