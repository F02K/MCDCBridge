# MCDCBridge

Configurable Discord bridge for Paper (1.21+). Currently: whitelist requests with embeds and buttons.

## Flow
1. `/dcbridge panel` posts an embed with a **request** button into `whitelist.panel-channel-id`.
2. A member clicks it and enters their Minecraft name in a modal.
3. The request appears as an embed with **Approve / Deny** buttons in `whitelist.review-channel-id`.
   Only members with `whitelist.staff-role-id` (or the *Manage Server* permission if empty) can use them.
4. On approval the player is added to the server whitelist. Set `whitelist.auto-approve: true` to skip the review.

All embeds, buttons and modal texts are in `config.yml`.

## Bedrock (Geyser)
With [Floodgate](https://geysermc.org/wiki/floodgate/) installed, the panel gets a second **Bedrock** button. Members enter their Xbox gamertag; the plugin adds the Floodgate prefix, looks up the Floodgate UUID and whitelists it. Approval, roles and bans work like for Java players. Without Floodgate the button is hidden.

## Build
```
gradle build
```
Requires Java 21. The jar ends up in `build/libs`; Paper downloads JDA on first start. The bot needs no privileged intents.
