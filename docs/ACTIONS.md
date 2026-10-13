# Blocs (actions) de Daisychain

## Ajouter un bloc
1. Créer une classe publique, sans paramètre de constructeur, qui hérite de `ActionDefinition`.
2. L'annoter avec `@AutoService(ActionDefinition::class)`.
3. C'est tout : `ActionRegistry` la trouve seul (KSP génère `META-INF/services` à la compilation).

Exemples : `app/src/main/java/com/daisychain/engine/actions/`.
Le test `ActionRegistryTest` vérifie qu'un id n'est pas en double et que chaque bloc a un titre FR et EN.

## Modèle (fidèle au plist iOS, pour importer/exporter sans perte)
- `Workflow` : nom, icône, couleur, liste **plate** d'`ActionInstance`.
- `ActionInstance` : `uuid`, `identifier` (id iOS, ex. `is.workflow.actions.conditional`), `params`, `flow`.
- `ParamValue` : texte avec variables, nombre, booléen, variable, choix, dictionnaire, tableau, ou `Raw` (inconnu, conservé tel quel).
- Variables (`VariableSource`) : nommée, sortie d'un bloc (« variable magique »), entrée du raccourci, presse-papiers, date, « demander à l'exécution », détail de l'appareil.

## Comment les blocs fonctionnent entre eux
- **Données** : chaque bloc déclare ce qu'il accepte (`accepts`) et produit (`produces`) avec un `ContentType`. Le sélecteur de variables ne propose que les sorties compatibles.
- **Sortie implicite** : la sortie d'un bloc devient l'entrée du suivant, comme sur iOS. Une variable magique référence le `uuid` d'un bloc précédent.
- **Contrôle de flux** : `FlowSpec` liste les rôles ordonnés. Si = Si / Sinon / Fin Si ; Répéter = début / fin ; Menu = début / un rôle répétable par choix / fin. Dans la liste plate, chaque bloc d'un groupe porte `FlowMembership(groupId, role)` ; l'éditeur reconstruit l'arbre à partir de là.
- **Paramètres** : `ParamSpec.key` est la clé du plist iOS, donc aucune table de traduction à l'import.
- **Inconnu** : un identifiant que le registre ne connaît pas est conservé et affiché comme « non pris en charge ».

## Plugins (à venir)
`ActionRegistry.register(def, pluginId)` et `unregisterPlugin` existent déjà. Pistes pour la suite :
- fichiers JSON dans un dossier `plugins/` décrivant un bloc (titre, paramètres, intent Android à lancer) ;
- applications tierces qui déclarent leurs blocs via un intent-filter.
Les définitions n'utilisent que des textes, des icônes par clé et des couleurs par clé, donc elles se décrivent facilement en JSON.
