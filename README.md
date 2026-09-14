# Top Drive Garage Backup

Petite appli Android qui copie automatiquement le fichier de sauvegarde du jeu
**Top Drive** (`Android/data/com.hutchgames.cccg/files/Garage.dat`) vers un
dossier **Google Drive** de ton choix, en remplaçant à chaque fois l'ancienne
copie (tu n'as donc jamais plusieurs fichiers).

Je n'ai pas pu compiler l'APK moi-même (mon environnement cloud n'a pas accès
aux serveurs de Google nécessaires à tout build Android). À la place, ce
projet contient une **GitHub Action** qui compile l'APK automatiquement dès
que le code est mis en ligne sur GitHub — tu n'as toi-même aucune ligne de
code à écrire.

## Étape 1 — Mettre le projet sur GitHub

1. Si tu n'as pas de compte GitHub, crée-en un gratuitement sur
   [github.com](https://github.com/join).
2. Installe **[GitHub Desktop](https://desktop.github.com/)** (gratuit) —
   c'est l'application officielle GitHub, pas besoin de ligne de commande.
   Ouvre-la et connecte-toi avec ton compte GitHub.
3. Dans GitHub Desktop : **File > New Repository**. Donne-lui un nom, par
   exemple `top-drive-garage-backup`, choisis un dossier sur ton PC, puis
   clique **Create Repository**.
4. Dézippe le fichier `topdrive-garage-backup.zip` que je t'ai envoyé.
   **Copie tout le contenu du dossier dézippé** (y compris le dossier caché
   `.github`) directement dans le dossier du dépôt que GitHub Desktop vient
   de créer, en remplaçant les fichiers si demandé.
   - Astuce : si tu utilises "Extraire tout" / "Extract All" sur le zip
     directement à l'intérieur du dossier du dépôt, le dossier caché
     `.github` sera bien extrait même si ton explorateur de fichiers ne
     l'affiche pas — ce n'est pas grave, GitHub Desktop le détectera quand
     même.
5. Reviens dans GitHub Desktop : tu dois voir la liste des fichiers ajoutés
   (dont `.github/workflows/build.yml`). Écris un message de commit (ex:
   "Premier envoi"), clique **Commit to main**, puis **Publish repository**
   (choisis public ou privé, peu importe) ou **Push origin** si le dépôt est
   déjà publié.

## Étape 2 — Récupérer l'APK compilé

1. Va sur [github.com](https://github.com), ouvre ton dépôt.
2. Clique sur l'onglet **Actions** en haut. Tu dois voir une exécution en
   cours ("Build APK"). Attends 2 à 4 minutes qu'elle devienne verte ✅.
   - Si elle échoue (❌), clique dessus pour voir le message d'erreur et
     transmets-le-moi, je corrigerai le projet.
3. Une fois verte, va sur la page principale du dépôt puis clique sur
   **Releases** (dans le menu de droite). Ouvre la release **latest-apk** et
   télécharge le fichier **`TopDriveGarageBackup.apk`**.
   - Chaque nouvel envoi sur GitHub régénère automatiquement cette release
     et remplace l'ancien APK — tu n'as jamais à gérer plusieurs versions.

## Étape 3 — Installer l'appli sur le téléphone

1. Transfère `TopDriveGarageBackup.apk` sur ton téléphone (par mail, par
   Drive, ou télécharge-le directement depuis le téléphone en ouvrant la
   page Releases dans son navigateur).
2. **Si tu avais déjà installé une version précédente de l'appli** :
   désinstalle-la d'abord (appui long sur l'icône > Désinstaller). Les tout
   premiers builds étaient signés avec une clé différente à chaque
   compilation, donc Android refuse d'installer la nouvelle version
   par-dessus ("app non installée" / rien ne se passe). **Depuis cette
   version, ce n'est plus le cas** : les prochaines mises à jour
   s'installeront normalement par-dessus, sans réinstallation complète.
3. Ouvre le fichier APK sur le téléphone. Android va probablement demander
   d'autoriser "l'installation d'applications inconnues" pour l'app que tu
   utilises (Fichiers, Chrome...) — accepte, c'est normal pour une appli
   installée hors Play Store.
4. Installe, puis ouvre **Top Drive Backup**.
   - Si tu viens de désinstaller une ancienne version, il faudra refaire la
     configuration (étape 5 ci-dessous) : le dossier Drive choisi et
     l'autorisation Shizuku sont liés à l'installation.

## Étape 4 — Installer et activer Shizuku (une seule fois)

Depuis Android 11, aucune application ne peut lire le dossier Android/data
d'une autre application (ici Top Drive) — c'est une protection du système,
même avec la permission "tous les fichiers". **Shizuku** est une appli
gratuite, sans root, qui contourne cette limite en donnant à notre appli les
mêmes droits qu'un ordinateur connecté en USB (adb) — sauf qu'ici, tout se
fait uniquement sur le téléphone, sans PC.

1. Installe l'appli **[Shizuku](https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api)**
   depuis le Play Store (gratuite).
2. Active les options développeur si ce n'est pas déjà fait : **Paramètres
   > À propos du téléphone**, puis tape 7 fois sur **"Numéro de build"**.
3. Va dans **Paramètres > Système > Options pour les développeurs**, puis
   active **"Débogage sans fil"** (Wireless debugging).
4. Ouvre **"Débogage sans fil"**, puis **"Coupler l'appareil avec un code"**
   (Pair device with pairing code) — un code à 6 chiffres apparaît.
5. Ouvre l'appli **Shizuku**, choisis **"Démarrer via débogage sans fil"**
   (Start via wireless debugging) et suis les instructions à l'écran (elle
   utilise directement le code affiché à l'étape précédente — pas besoin de
   PC ni de câble).
6. Une fois Shizuku indiqué comme **"en cours d'exécution"**, ouvre notre
   appli **Top Drive Backup**.

   > ⚠️ Après un redémarrage du téléphone, Shizuku s'arrête généralement et
   > doit être relancé manuellement (rouvre l'appli Shizuku, répète l'étape
   > "Démarrer via débogage sans fil" si besoin). C'est une limite connue de
   > cette méthode sans root.

## Étape 5 — Configurer l'appli Top Drive Backup (une seule fois)

1. Appuie sur **"Autoriser Shizuku"** (étape 1 dans l'appli). Si tout est
   bien activé, une popup Shizuku apparaît : accepte-la. L'état doit passer
   à "✅ Connecté".
2. Appuie sur **"Choisir le dossier"** (étape 2) : le sélecteur de fichiers
   Android s'ouvre. Si "Drive" n'apparaît pas directement, ouvre le menu
   ☰ en haut à gauche du sélecteur pour le trouver. Choisis (ou crée) un
   dossier dans ton Drive, puis valide en bas à droite.
3. Vérifie que **"Sauvegarde automatique"** est activé (interrupteur en
   haut à droite de l'étape 3). Pour plus de fiabilité, appuie aussi sur
   **"Ignorer l'optimisation de batterie"** — certains téléphones (Samsung,
   Xiaomi...) arrêtent sinon les tâches de fond trop agressivement.
4. Appuie sur **"Sauvegarder maintenant"** pour tester tout de suite. Le
   texte doit afficher "Sauvegarde réussie" — va vérifier sur ton Google
   Drive que `Garage.dat` est bien apparu dans le dossier choisi.

Ensuite, l'appli vérifie le fichier toutes les 6 heures environ et le
renvoie sur Drive **uniquement s'il a changé**, en remplaçant systématiquement
l'ancien fichier.

## Remarques

- L'APK généré est un build "debug" (auto-signé), ce qui est normal et sans
  danger pour un usage personnel hors Play Store.
- Android limite les tâches de fond pour économiser la batterie : la
  sauvegarde automatique n'est donc pas à la seconde près. Rouvrir l'appli
  de temps en temps, et l'exemption de batterie (étape 5.3), aident à la
  rendre plus régulière.
- Si Shizuku n'est plus connecté après un redémarrage du téléphone (état
  "❌ Non connecté" dans l'appli), rouvre l'appli Shizuku pour la relancer
  (voir l'avertissement de l'étape 4), puis "Autoriser Shizuku" à nouveau.
- Si tu modifies plus tard le nom du fichier/dossier du jeu, ou si tu veux
  changer la fréquence (actuellement 6h), dis-le-moi et je mettrai le code
  à jour.
