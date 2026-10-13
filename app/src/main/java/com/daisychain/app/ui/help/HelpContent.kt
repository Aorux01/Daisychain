package com.daisychain.app.ui.help

import com.daisychain.engine.spec.LocalizedText
import com.daisychain.engine.spec.t

class HelpTopic(val title: LocalizedText, val body: LocalizedText)

/** What the in-app guide says. Paragraphs are separated by a blank line. */
private val coreTopics = listOf(
    HelpTopic(
        t("Getting started", "Premiers pas"),
        t(
            "A shortcut is a list of blocks that run from top to bottom. Tap New, then Add action to pick blocks. " +
                "Tap the play button to run it: each block lights up while it runs.\n\n" +
                "Touch a chip on a block to change its value. Drag the ≡ handle to move a block (a group such as If moves with everything inside it). " +
                "The ⋮ menu duplicates or deletes a block.\n\n" +
                "Inside an If, a Repeat or a Menu, use \"Add action\" in the branch where the block should go.",
            "Un raccourci est une liste de blocs qui s'exécutent de haut en bas. Touchez Nouveau, puis Ajouter une action pour choisir des blocs. " +
                "Touchez le bouton lecture pour l'exécuter : chaque bloc s'illumine pendant qu'il s'exécute.\n\n" +
                "Touchez une puce d'un bloc pour changer sa valeur. Faites glisser la poignée ≡ pour déplacer un bloc (un groupe comme Si se déplace avec tout son contenu). " +
                "Le menu ⋮ permet de dupliquer ou de supprimer un bloc.\n\n" +
                "Dans un Si, un Répéter ou un Menu, utilisez « Ajouter une action » dans la branche où le bloc doit aller.",
        ),
    ),
    HelpTopic(
        t("Input and output", "Entrée et sortie"),
        t(
            "Blocks pass a value to the next one, like a chain.\n\n" +
                "The slanted chip at the start of a block shows what it receives: by default, what the block above gives. Touch it to point it at another variable.\n\n" +
                "The chip ending the block, with an arrow, is what the block gives to the next ones: its output. Touch it to give it a name of your own, like \"letters\".\n\n" +
                "\"Set variable\" keeps a value under a name so you can use it later. \"Add to variable\" builds a list one item at a time.",
            "Les blocs se passent une valeur, comme une chaîne.\n\n" +
                "La puce en italique au début d'un bloc montre ce qu'il reçoit : par défaut, ce que donne le bloc du dessus. Touchez-la pour la diriger vers une autre variable.\n\n" +
                "La puce à la fin du bloc, avec une flèche, est ce que le bloc donne aux suivants : sa sortie. Touchez-la pour lui donner votre propre nom, comme « lettres ».\n\n" +
                "« Définir la variable » garde une valeur sous un nom pour la réutiliser plus tard. « Ajouter à la variable » construit une liste élément par élément.",
        ),
    ),
    HelpTopic(
        t("Variables", "Variables"),
        t(
            "Wherever a field takes text or a number, \"Add variable\" inserts one right in the text, so you can write around it.\n\n" +
                "You can use the output of any earlier block, a variable you named, or a built-in one:\n" +
                "• Shortcut Input: what the shortcut receives when something else starts it.\n" +
                "• Clipboard and Current Date.\n" +
                "• Ask When Run: asks you at that moment.\n" +
                "• Empty: starts a variable with nothing in it.\n" +
                "• Repeat Item and Repeat Index: inside a repeat.\n\n" +
                "\"New variable…\" creates a named variable on the spot.",
            "Là où un champ accepte du texte ou un nombre, « Ajouter une variable » en insère une dans le texte, pour écrire autour.\n\n" +
                "Vous pouvez utiliser la sortie de n'importe quel bloc précédent, une variable que vous avez nommée, ou une variable intégrée :\n" +
                "• Entrée du raccourci : ce que le raccourci reçoit quand autre chose le lance.\n" +
                "• Presse-papiers et Date actuelle.\n" +
                "• Demander à l'exécution : vous le demande à ce moment-là.\n" +
                "• Vide : démarre une variable sans rien dedans.\n" +
                "• Élément et Index de la répétition : dans une répétition.\n\n" +
                "« Nouvelle variable… » crée une variable nommée sur-le-champ.",
        ),
    ),
    HelpTopic(
        t("Blocks inside blocks", "Des blocs dans des blocs"),
        t(
            "Every branch of an If, Repeat or Menu has its own dashed placeholder, \"Add action to ...\", that names where the block will go, for example Choice « Yes » or Otherwise. " +
                "When groups are nested, each level has a different shade of grey: the placeholder below a group belongs to the branch that holds it, the one inside the group belongs to the group.\n\n" +
                "A Menu starts with two choices: touch + Add choice for more. Only the choices run; nothing goes directly under the Menu title.\n\n" +
                "You can also drag a block (by its ≡ handle) into any branch: the bar shows where it will land.",
            "Chaque branche d'un Si, d'un Répéter ou d'un Menu a son propre emplacement en pointillés, \"Ajouter une action dans ...\", qui dit où le bloc ira, par exemple Choix « Oui » ou Sinon. " +
                "Quand les groupes sont imbriqués, chaque niveau a une teinte de gris différente : l'emplacement sous un groupe appartient à la branche qui le contient, celui à l'intérieur appartient au groupe.\n\n" +
                "Un Menu commence avec deux choix : touchez + Ajouter un choix pour en avoir plus. Seuls les choix s'exécutent ; rien ne se met directement sous le titre du Menu.\n\n" +
                "Vous pouvez aussi faire glisser un bloc (par sa poignée ≡) dans n'importe quelle branche : la barre montre où il atterrira.",
        ),
    ),
    HelpTopic(
        t("If and conditions", "Si et conditions"),
        t(
            "If tests a value and runs the blocks under \"If\" when it is true, otherwise those under \"Otherwise\".\n\n" +
                "Touch \"Add condition\" to test several things. \"and\" needs every condition to be true, \"or\" needs one. " +
                "\"and\" is read before \"or\". Touch the \"and\" / \"or\" word to change it or remove the line, or use \"Remove condition\".",
            "Si teste une valeur et exécute les blocs sous « Si » quand c'est vrai, sinon ceux sous « Sinon ».\n\n" +
                "Touchez « Ajouter une condition » pour tester plusieurs choses. « et » demande que toutes les conditions soient vraies, « ou » une seule. " +
                "« et » est lu avant « ou ». Touchez le mot « et » / « ou » pour le changer ou supprimer la ligne, ou utilisez « Enlever une condition ».",
        ),
    ),
    HelpTopic(
        t("Repeat", "Répéter"),
        t(
            "Repeat runs the blocks inside a number of times. Repeat with each runs them once for every item of a list.\n\n" +
                "Inside, Repeat Item is the current item and Repeat Index counts 1, 2, 3… The first block inside also receives the item.\n\n" +
                "When the repeat ends, \"Repeat Results\" is the list of what each turn ended with. Use it as the input of the next block.",
            "Répéter exécute les blocs à l'intérieur plusieurs fois. Répéter avec chaque les exécute une fois pour chaque élément d'une liste.\n\n" +
                "À l'intérieur, Élément de la répétition est l'élément en cours et Index de la répétition compte 1, 2, 3… Le premier bloc à l'intérieur reçoit aussi l'élément.\n\n" +
                "Quand la répétition se termine, « Résultats de la répétition » est la liste de ce que chaque tour a donné. Utilisez-la comme entrée du bloc suivant.",
        ),
    ),
    HelpTopic(
        t("Dictionary and key", "Dictionnaire et clé"),
        t(
            "A dictionary is like a phone book: each entry has a key (the name you look up) and a value (what is stored under it).\n\n" +
                "In the Dictionary block, type a key and a value, then \"Add entry\" for each new line. Put all the entries in ONE Dictionary block: " +
                "two blocks make two separate dictionaries.\n\n" +
                "\"Get dictionary value\" looks something up. \"In dictionary\" says where to look (automatic: the latest Dictionary block above; with several, pick one). " +
                "\"Key\" is what to look for. Left empty, the key is the value the block receives, for example the current letter inside a repeat.\n\n" +
                "Morse example:\n" +
                "1. Dictionary: S → ..., then Add entry: O → ---\n" +
                "2. Ask for input\n" +
                "3. Split text (every character)\n" +
                "4. Repeat with each, with Get dictionary value inside (key left empty)\n" +
                "5. Combine text (spaces), from the Repeat Results\n" +
                "6. Show result",
            "Un dictionnaire est comme un annuaire : chaque entrée a une clé (le nom qu'on cherche) et une valeur (ce qui est rangé dessous).\n\n" +
                "Dans le bloc Dictionnaire, tapez une clé et une valeur, puis « Ajouter une entrée » pour chaque nouvelle ligne. Mettez toutes les entrées dans UN seul bloc Dictionnaire : " +
                "deux blocs font deux dictionnaires séparés.\n\n" +
                "« Obtenir la valeur du dictionnaire » cherche quelque chose. « Dans le dictionnaire » dit où chercher (automatique : le dernier bloc Dictionnaire au-dessus ; avec plusieurs, choisissez-en un). " +
                "« Clé » est ce qu'on cherche. Laissée vide, la clé est la valeur reçue par le bloc, par exemple la lettre en cours dans une répétition.\n\n" +
                "Exemple Morse :\n" +
                "1. Dictionnaire : S → ..., puis Ajouter une entrée : O → ---\n" +
                "2. Demander une saisie\n" +
                "3. Séparer le texte (chaque caractère)\n" +
                "4. Répéter avec chaque, avec Obtenir la valeur du dictionnaire dedans (clé laissée vide)\n" +
                "5. Assembler le texte (espaces), depuis les Résultats de la répétition\n" +
                "6. Afficher le résultat",
        ),
    ),
    HelpTopic(
        t("Text, numbers and lists", "Texte, nombres et listes"),
        t(
            "Split text cuts a text into a list (by lines, spaces, every character, or your own separator). Combine text does the opposite.\n\n" +
                "Calculate does all the maths: + − × ÷, power, square root, rounding, and statistics on a list (count, sum, average…). " +
                "Calculate expression computes a formula such as 3+4*2.\n\n" +
                "Convert changes units (length, mass, temperature…), the base of a number (binary, hexadecimal…) or text into character codes and back. " +
                "With character codes you can build your own secret codes.",
            "Séparer le texte découpe un texte en liste (par lignes, espaces, chaque caractère, ou votre propre séparateur). Assembler le texte fait l'inverse.\n\n" +
                "Calculer fait tous les calculs : + − × ÷, puissance, racine carrée, arrondi, et statistiques sur une liste (nombre, somme, moyenne…). " +
                "Calculer l'expression calcule une formule comme 3+4*2.\n\n" +
                "Convertir change des unités (longueur, masse, température…), la base d'un nombre (binaire, hexadécimal…) ou le texte en codes de caractères et inversement. " +
                "Avec les codes de caractères, vous pouvez construire vos propres codes secrets.",
        ),
    ),
    HelpTopic(
        t("Run from the home screen", "Lancer depuis l'accueil"),
        t(
            "In the menu of a shortcut, \"Add to home screen\" pins an icon that runs it. " +
                "Your favourites also appear when you press and hold the app icon.",
            "Dans le menu d'un raccourci, « Ajouter à l'écran d'accueil » épingle une icône qui le lance. " +
                "Vos favoris apparaissent aussi quand vous appuyez longuement sur l'icône de l'application.",
        ),
    ),
    HelpTopic(
        t("A red block", "Un bloc en rouge"),
        t(
            "A red block no longer exists in this version of the app (it was removed or comes from something that is gone). " +
                "Touch \"Replace\" to pick another block in its place, or delete it. A shortcut that contains one stops there when it runs.",
            "Un bloc en rouge n'existe plus dans cette version de l'application (il a été retiré ou vient de quelque chose qui a disparu). " +
                "Touchez « Remplacer » pour choisir un autre bloc à sa place, ou supprimez-le. Un raccourci qui en contient un s'arrête là quand il s'exécute.",
        ),
    ),
)

/** Topics added with the import, automation, plugin and system-control features: written in all four languages. */
private val extraTopics = listOf(
    HelpTopic(
        t("Import and export", "Importer et exporter", "Importare ed esportare", "Importieren und exportieren", "Importar y exportar"),
        t(
            "Open a .shortcut file from your Files app, a message or the browser, or use the import button in the library: Daisychain converts it into blocks. Blocks that do not exist yet stay in the shortcut, in red.\n\n" +
                "Shortcuts shared from recent iPhones are signed by Apple, and Daisychain cannot read signed files yet. To get an unsigned one, ask the author to export it from an older iOS, or rebuild it here from screenshots.\n\n" +
                "Export (in the ⋮ menu of the editor) writes a .shortcut file. It is not signed: sign it on a Mac with the command \"shortcuts sign\" before opening it on iOS.",
            "Ouvrez un fichier .shortcut depuis votre application Fichiers, un message ou le navigateur, ou utilisez le bouton d'import de la bibliothèque : Daisychain le convertit en blocs. Les blocs qui n'existent pas encore restent dans le raccourci, en rouge.\n\n" +
                "Les raccourcis partagés depuis les iPhone récents sont signés par Apple, et Daisychain ne sait pas encore lire les fichiers signés. Pour obtenir un fichier non signé, demandez à son auteur de l'exporter depuis un ancien iOS, ou reconstruisez-le ici à partir de captures d'écran.\n\n" +
                "L'export (dans le menu ⋮ de l'éditeur) écrit un fichier .shortcut. Il n'est pas signé : signez-le sur un Mac avec la commande \"shortcuts sign\" avant de l'ouvrir sur iOS.",
            "Apri un file .shortcut dall'app File, da un messaggio o dal browser, oppure usa il pulsante di importazione nella libreria: Daisychain lo converte in blocchi. I blocchi che non esistono ancora restano nel comando, in rosso.\n\n" +
                "I comandi condivisi dagli iPhone recenti sono firmati da Apple e Daisychain non sa ancora leggere i file firmati. Per ottenerne uno non firmato, chiedi all'autore di esportarlo da un iOS più vecchio, oppure ricostruiscilo qui a partire da schermate.\n\n" +
                "L'esportazione (nel menu ⋮ dell'editor) scrive un file .shortcut. Non è firmato: firmalo su un Mac con il comando \"shortcuts sign\" prima di aprirlo su iOS.",
            "Öffne eine .shortcut-Datei aus der Dateien-App, einer Nachricht oder dem Browser, oder nutze die Import-Schaltfläche in der Bibliothek: Daisychain wandelt sie in Blöcke um. Blöcke, die es noch nicht gibt, bleiben im Kurzbefehl, in Rot.\n\n" +
                "Von aktuellen iPhones geteilte Kurzbefehle sind von Apple signiert, und Daisychain kann signierte Dateien noch nicht lesen. Um eine unsignierte zu bekommen, bitte den Autor, sie von einem älteren iOS zu exportieren, oder baue sie hier anhand von Screenshots nach.\n\n" +
                "Der Export (im ⋮-Menü des Editors) schreibt eine .shortcut-Datei. Sie ist nicht signiert: Signiere sie auf einem Mac mit dem Befehl \"shortcuts sign\", bevor du sie unter iOS öffnest.",
            "Abre un archivo .shortcut desde tu app de Archivos, un mensaje o el navegador, o usa el botón de importar de la biblioteca: Daisychain lo convierte en bloques. Los bloques que aún no existen se quedan en el atajo, en rojo.\n\nLos atajos compartidos desde iPhone recientes están firmados por Apple, y Daisychain aún no puede leer archivos firmados. Para conseguir uno sin firmar, pide al autor que lo exporte desde un iOS más antiguo, o reconstrúyelo aquí a partir de capturas de pantalla.\n\nExportar (en el menú ⋮ del editor) escribe un archivo .shortcut. No está firmado: fírmalo en un Mac con el comando \"shortcuts sign\" antes de abrirlo en iOS.",
        ),
    ),
    HelpTopic(
        t("Automations", "Automatisations", "Automazioni", "Automationen", "Automatizaciones"),
        t(
            "An automation runs a shortcut when something happens: at a time, when the charger is connected, the battery gets low, you join a Wi-Fi network, a Bluetooth device connects, airplane mode changes, or an NFC tag is scanned.\n\n" +
                "\"Ask before running\" shows a notification you tap to confirm. Without it, the shortcut runs on its own, in the background: blocks that need the screen (photos, dialogs) cannot work then.\n\n" +
                "Triggers that watch the phone keep a quiet notification while they are active. For an NFC tag, write the address shown in the editor on the tag as a link, with any NFC writer app.",
            "Une automatisation lance un raccourci quand quelque chose se produit : à une heure, quand le chargeur est branché, que la batterie est faible, que vous rejoignez un réseau Wi-Fi, qu'un appareil Bluetooth se connecte, que le mode avion change, ou qu'un badge NFC est scanné.\n\n" +
                "\"Demander avant de lancer\" affiche une notification que vous touchez pour confirmer. Sans cela, le raccourci s'exécute seul, en arrière-plan : les blocs qui ont besoin de l'écran (photos, boîtes de dialogue) ne peuvent alors pas fonctionner.\n\n" +
                "Les déclencheurs qui surveillent le téléphone gardent une notification discrète tant qu'ils sont actifs. Pour un badge NFC, écrivez l'adresse affichée dans l'éditeur sur le badge comme un lien, avec n'importe quelle application d'écriture NFC.",
            "Un'automazione esegue un comando quando succede qualcosa: a una certa ora, quando il caricatore è collegato, la batteria è scarica, ti connetti a una rete Wi-Fi, si connette un dispositivo Bluetooth, cambia la modalità aereo o viene scansionato un tag NFC.\n\n" +
                "«Chiedi prima di eseguire» mostra una notifica da toccare per confermare. Senza, il comando viene eseguito da solo, in background: i blocchi che richiedono lo schermo (foto, finestre di dialogo) non possono funzionare.\n\n" +
                "I trigger che sorvegliano il telefono mantengono una notifica discreta finché sono attivi. Per un tag NFC, scrivi sul tag l'indirizzo mostrato nell'editor come link, con una qualsiasi app di scrittura NFC.",
            "Eine Automation führt einen Kurzbefehl aus, wenn etwas passiert: zu einer Uhrzeit, wenn das Ladegerät angeschlossen wird, der Akku schwach ist, du dich mit einem WLAN verbindest, sich ein Bluetooth-Gerät verbindet, der Flugmodus wechselt oder ein NFC-Tag gescannt wird.\n\n" +
                "„Vor dem Ausführen fragen“ zeigt eine Benachrichtigung, die du zur Bestätigung antippst. Ohne sie läuft der Kurzbefehl allein im Hintergrund: Blöcke, die den Bildschirm brauchen (Fotos, Dialoge), können dann nicht funktionieren.\n\n" +
                "Auslöser, die das Telefon beobachten, halten eine dezente Benachrichtigung, solange sie aktiv sind. Für ein NFC-Tag schreibst du die im Editor angezeigte Adresse als Link auf das Tag, mit einer beliebigen NFC-Schreib-App.",
            "Una automatización ejecuta un atajo cuando ocurre algo: a una hora, cuando se conecta el cargador, la batería está baja, te unes a una red Wi-Fi, se conecta un dispositivo Bluetooth, cambia el modo avión o se escanea una etiqueta NFC.\n\n«Preguntar antes de ejecutar» muestra una notificación que tocas para confirmar. Sin eso, el atajo se ejecuta solo, en segundo plano: los bloques que necesitan la pantalla (fotos, cuadros de diálogo) no pueden funcionar entonces.\n\nLos desencadenantes que vigilan el teléfono mantienen una notificación discreta mientras están activos. Para una etiqueta NFC, escribe en ella como enlace la dirección que muestra el editor, con cualquier app para escribir NFC.",
        ),
    ),
    HelpTopic(
        t("Wi-Fi, Bluetooth and mobile data", "Wi-Fi, Bluetooth et données mobiles", "Wi-Fi, Bluetooth e dati mobili", "WLAN, Bluetooth und mobile Daten", "Wi-Fi, Bluetooth y datos móviles"),
        t(
            "Android does not let apps switch Wi-Fi, Bluetooth, mobile data or airplane mode. The free Shizuku app lends that power to Daisychain.\n\n" +
                "1. Install Shizuku (Settings → Advanced control opens it).\n" +
                "2. Start it once, as its screen explains (Wireless debugging, no computer needed on Android 11 and later). It must be started again after each restart of the phone.\n" +
                "3. In Settings → Advanced control, touch \"Allow Daisychain\".\n\n" +
                "Then the blocks Set Wi-Fi, Set Bluetooth, Set mobile data, Set airplane mode and Run system command work. Set ringer mode and Do Not Disturb need no Shizuku, only a permission Android asks for.\n\n" +
                "The hotspot cannot be switched on by apps: its block opens the screen. \"Get hotspot details\" gives the name and, when the phone allows it, the password, ready for a QR code.",
            "Android ne laisse pas les applications activer le Wi-Fi, le Bluetooth, les données mobiles ou le mode avion. L'application gratuite Shizuku prête ce pouvoir à Daisychain.\n\n" +
                "1. Installez Shizuku (Réglages → Contrôle avancé l'ouvre).\n" +
                "2. Démarrez-la une fois, comme son écran l'explique (débogage sans fil, sans ordinateur sur Android 11 et plus). Il faut la redémarrer après chaque redémarrage du téléphone.\n" +
                "3. Dans Réglages → Contrôle avancé, touchez « Autoriser Daisychain ».\n\n" +
                "Les blocs Régler le Wi-Fi, Régler le Bluetooth, Régler les données mobiles, Régler le mode avion et Exécuter une commande système fonctionnent alors. Régler le mode sonnerie et Ne pas déranger n'ont pas besoin de Shizuku, seulement d'une autorisation qu'Android demande.\n\n" +
                "Le partage de connexion ne peut pas être activé par une application : son bloc ouvre l'écran. « Obtenir les infos du partage » donne le nom et, quand le téléphone le permet, le mot de passe, prêts pour un QR code.",
            "Android non permette alle app di attivare Wi-Fi, Bluetooth, dati mobili o modalità aereo. L'app gratuita Shizuku presta questo potere a Daisychain.\n\n" +
                "1. Installa Shizuku (Impostazioni → Controllo avanzato la apre).\n" +
                "2. Avviala una volta, come spiega la sua schermata (debug wireless, nessun computer necessario da Android 11). Va riavviata a ogni riavvio del telefono.\n" +
                "3. In Impostazioni → Controllo avanzato, tocca «Autorizza Daisychain».\n\n" +
                "Da quel momento funzionano i blocchi Imposta Wi-Fi, Imposta Bluetooth, Imposta dati mobili, Imposta modalità aereo ed Esegui comando di sistema. Imposta modalità suoneria e Non disturbare non richiedono Shizuku, solo un permesso che Android chiede.\n\n" +
                "L'hotspot non può essere attivato dalle app: il suo blocco apre la schermata. «Ottieni dettagli dell'hotspot» fornisce il nome e, quando il telefono lo consente, la password, pronti per un codice QR.",
            "Android erlaubt Apps nicht, WLAN, Bluetooth, mobile Daten oder den Flugmodus zu schalten. Die kostenlose App Shizuku leiht Daisychain diese Berechtigung.\n\n" +
                "1. Installiere Shizuku (Einstellungen → Erweiterte Steuerung öffnet sie).\n" +
                "2. Starte sie einmal, wie ihr Bildschirm erklärt (drahtloses Debugging, ab Android 11 ohne Computer). Nach jedem Neustart des Telefons muss sie erneut gestartet werden.\n" +
                "3. Tippe in Einstellungen → Erweiterte Steuerung auf „Daisychain erlauben“.\n\n" +
                "Dann funktionieren die Blöcke WLAN einstellen, Bluetooth einstellen, Mobile Daten einstellen, Flugmodus einstellen und Systembefehl ausführen. Klingelmodus einstellen und „Nicht stören“ brauchen kein Shizuku, nur eine Berechtigung, nach der Android fragt.\n\n" +
                "Der Hotspot kann von Apps nicht eingeschaltet werden: Sein Block öffnet den Bildschirm. „Hotspot-Details abrufen“ liefert den Namen und, wenn das Telefon es erlaubt, das Passwort, bereit für einen QR-Code.",
            "Android no permite que las apps activen el Wi-Fi, el Bluetooth, los datos móviles ni el modo avión. La app gratuita Shizuku le presta ese poder a Daisychain.\n\n1. Instala Shizuku (Ajustes → Control avanzado la abre).\n2. Inícialo una vez, como explica su pantalla (depuración inalámbrica, sin ordenador desde Android 11). Hay que volver a iniciarlo tras cada reinicio del teléfono.\n3. En Ajustes → Control avanzado, toca «Autorizar a Daisychain».\n\nEntonces funcionan los bloques Ajustar Wi-Fi, Ajustar Bluetooth, Ajustar datos móviles, Ajustar modo avión y Ejecutar comando del sistema. Ajustar modo de timbre y No molestar no necesitan Shizuku, solo un permiso que Android pide.\n\nEl punto de acceso no puede activarlo una app: su bloque abre la pantalla. «Obtener detalles del punto de acceso» da el nombre y, cuando el teléfono lo permite, la contraseña, listos para un código QR.",
        ),
    ),
    HelpTopic(
        t("Plugins", "Plugins", "Plugin", "Plugins", "Plugins"),
        t(
            "A plugin is a .json file that adds blocks. It contains no code: each block is a list of steps (a web request, an Android intent or a text template).\n\n" +
                "Settings → Plugins → Import a plugin. Daisychain shows what the plugin can do before you install it: install only plugins you trust.\n\n" +
                "To write your own, see docs/PLUGINS.md in the project.",
            "Un plugin est un fichier .json qui ajoute des blocs. Il ne contient aucun code : chaque bloc est une liste d'étapes (une requête web, un intent Android ou un modèle de texte).\n\n" +
                "Réglages → Plugins → Importer un plugin. Daisychain montre ce que le plugin peut faire avant de l'installer : n'installez que des plugins de confiance.\n\n" +
                "Pour écrire le vôtre, voyez docs/PLUGINS.md dans le projet.",
            "Un plugin è un file .json che aggiunge blocchi. Non contiene codice: ogni blocco è un elenco di passaggi (una richiesta web, un intent Android o un modello di testo).\n\n" +
                "Impostazioni → Plugin → Importa un plugin. Daisychain mostra cosa può fare il plugin prima dell'installazione: installa solo plugin di cui ti fidi.\n\n" +
                "Per scrivere il tuo, vedi docs/PLUGINS.md nel progetto.",
            "Ein Plugin ist eine .json-Datei, die Blöcke hinzufügt. Sie enthält keinen Code: Jeder Block ist eine Liste von Schritten (eine Webanfrage, ein Android-Intent oder eine Textvorlage).\n\n" +
                "Einstellungen → Plugins → Plugin importieren. Daisychain zeigt vor der Installation, was das Plugin kann: Installiere nur Plugins, denen du vertraust.\n\n" +
                "Wie du dein eigenes schreibst, steht in docs/PLUGINS.md im Projekt.",
            "Un plugin es un archivo .json que añade bloques. No contiene código: cada bloque es una lista de pasos (una solicitud web, un intent de Android o una plantilla de texto).\n\nAjustes → Plugins → Importar un plugin. Daisychain muestra lo que puede hacer el plugin antes de instalarlo: instala solo plugins de confianza.\n\nPara escribir el tuyo, consulta docs/PLUGINS.md en el proyecto.",
        ),
    ),
)

val helpTopics: List<HelpTopic> = coreTopics + extraTopics
