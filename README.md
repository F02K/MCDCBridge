# MCDCBridge

Configurable Discord bridge for Paper (1.21+). Currently: whitelist requests with embeds and buttons.

## Flow
1. `/dcbridge panel` posts an embed with a **request** button into `whitelist.panel-channel-id`.
2. A member clicks it and enters their Minecraft name in a modal.
3. The request appears as an embed with **Approve / Deny** buttons in `whitelist.review-channel-id`.
   Only members with `whitelist.staff-role-id` (or the *Manage Server* permission if empty) can use them.
4. On approval the player is added to the server whitelist. Set `whitelist.auto-approve: true` to skip the review.

Texts live in `plugins/MCDCBridge/lang/<code>.yml` (see Languages); colors and button styles in `config.yml`.

## Languages
English (`en`) and German (`de`) are included. Add more by dropping e.g. `fr.yml` into `plugins/MCDCBridge/lang/`; missing keys fall back to English.

The Discord server language is read from the guild (`Guild#getLocale()`, the *Primary Language* under Server Settings > Community > Overview). Only Community servers can set it - other servers always report `en-US`. `/dcbridge language` shows what the bot detects.

```yaml
language:
  minecraft: "en"    # commands, kick messages: a code, or "discord" to follow the Discord server
  discord: "auto"    # embeds, buttons, modals: "auto" = Discord server, "minecraft", or a code
```
So both sides can be set independently, or kept in sync with the Discord server. When the server language changes, the panel is re-posted in the new language.

## Updates
On start the plugin updates `config.yml` and the `lang/*.yml` files of older installs: new keys are added, your values are kept, and texts you customised in the old `config.yml` are moved to `lang/de.yml`. Before any change a copy is saved to `plugins/MCDCBridge/backups/` (newest 10 per file are kept); if the backup fails, nothing is modified.

## Bedrock (Geyser)
With [Floodgate](https://geysermc.org/wiki/floodgate/) installed, the panel gets a second **Bedrock** button. Members enter their Xbox gamertag; the plugin adds the Floodgate prefix, looks up the Floodgate UUID and whitelists it. Approval, roles and bans work like for Java players. Without Floodgate the button is hidden.

## Build
```
gradle build
```
Requires Java 21. The jar ends up in `build/libs`; Paper downloads JDA on first start. The bot needs no privileged intents.
