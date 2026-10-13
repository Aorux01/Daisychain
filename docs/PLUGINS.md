# Writing Daisychain plugins

A plugin is **one `.json` file** that adds blocks to the action picker. It contains **no code**: each block is a short
list of *steps* (a text template, a web request or an Android intent). That keeps plugins safe to share and easy to read.

Install it from **Settings → Plugins → Import a plugin**. Daisychain lists what the plugin can do (web requests,
talking to other apps) and asks you to confirm. Importing a plugin with the same `id` replaces the old version;
**Delete** removes it. Shortcuts that used a removed block show it in red, like any missing block.

Ready-made examples are in [docs/examples](examples/).

## Anatomy

```json
{
  "id": "demo.greetings",
  "name": { "en": "Greetings", "fr": "Salutations" },
  "icon": "bolt",
  "color": "teal",
  "blocks": [
    {
      "id": "hello",
      "title": { "en": "Say hello", "fr": "Dire bonjour" },
      "description": { "en": "Builds a greeting.", "fr": "Construit un message de bienvenue." },
      "icon": "mail",
      "color": "plum",
      "params": [
        { "key": "Who", "label": { "en": "Who", "fr": "Qui" }, "type": "text", "default": "world" }
      ],
      "output": "text",
      "steps": [
        { "type": "text", "template": "Hello {{Who}}!" }
      ]
    }
  ]
}
```

### Plugin level

| field | meaning |
|-------|---------|
| `id` | unique name: letters, digits, `.` `_` `-` (3–64 characters). Block ids become `plugin.<id>.<block id>`. |
| `name` | a string, or one string per language. It is also the group title in the block picker. |
| `icon`, `color` | for the group (optional). Icons: `bolt`, `mail`, `bell`, `link`, `location`, `camera`, `share`, `save`, `wifi`… Colors: `teal`, `blue`, `indigo`, `plum`, `rose`, `amber`, `slate`… |
| `blocks` | the list of blocks (at least one). |

### Block level

| field | meaning |
|-------|---------|
| `id` | letters, digits, `_` `-`. |
| `title`, `description` | a string or `{ "en": "…", "fr": "…", "it": "…", "de": "…", "es": "…" }`. A missing language falls back to English. |
| `icon`, `color` | the block's look. |
| `params` | the fields the user fills in (see below). |
| `steps` | what the block does, in order (see below). Required. |
| `output` | `"text"` (default: the block gives the result of its last step to the next block) or `"none"`. |

### Parameters

```json
{ "key": "City", "label": "City", "type": "text", "default": "Paris", "help": "Any town name" }
```

`type` is one of `text` (default), `textarea` (several lines), `number`, `toggle` (on/off) or `choice` (with
`"options": ["metric", "imperial"]`). Text and number fields accept variables, like every Daisychain block.

## Steps

Every step can use **placeholders** in its texts:

| placeholder | value |
|-------------|-------|
| `{{Key}}` | what the user entered for the parameter named `Key` |
| `{{input}}` | the output of the block just above (the block's input) |
| `{{result}}` | the result of the previous step of this block |

An unknown placeholder becomes empty.

### `text`

```json
{ "type": "text", "template": "Temperature in {{City}}: {{result}}" }
```

Result: the filled-in text.

### `http`

```json
{
  "type": "http",
  "method": "GET",
  "url": "https://wttr.in/{{City}}?format=%t",
  "headers": { "Accept": "text/plain" },
  "body": "{ \"q\": \"{{City}}\" }"
}
```

`method` defaults to `GET`; `headers` and `body` are optional (a body is sent as JSON). `https://` is added when the
URL has no scheme. Result: the answer of the server as text. Give it to *Get dictionary value* to pick one field of a
JSON answer.

### `intent`

```json
{
  "type": "intent",
  "action": "android.intent.action.VIEW",
  "data": "geo:0,0?q={{Place}}",
  "package": "",
  "mime": "",
  "extras": { "android.intent.extra.TEXT": "{{input}}" },
  "kind": "activity"
}
```

Starts another app, like the *Send Android intent* block (`kind`: `activity` or `broadcast`). Result: nothing.

## Rules and limits

* A plugin cannot run code, read files or see your other shortcuts. A block can only do what its steps say.
* Internet access and intents are shown to the user at install time.
* The file must be valid JSON and at most 1 MB. When it is not valid, Daisychain tells you what is wrong.
* Plugin texts are not translated automatically: write the languages you want, the rest shows in English.

## Tips

1. Start from an example and change one thing at a time.
2. Use a JSON validator (or your editor) before importing.
3. Test the block on its own in a new shortcut; *Show result* after it prints what the block gave.
4. To share: send the `.json` file. Anyone can read it before installing.
