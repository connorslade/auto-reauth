# auto-reauth [![build](https://github.com/Basicprogrammer10/auto-reauth/actions/workflows/build.yml/badge.svg)](https://github.com/Basicprogrammer10/auto-reauth/actions/workflows/build.yml) ![GitHub Release](https://img.shields.io/github/v/release/Basicprogrammer10/auto-reauth) ![Downloads](https://img.shields.io/badge/dynamic/json?url=https%3A%2F%2Fconnorcode.com%2Fapi%2Fdownloads%3Fgithub%3DBasicprogrammer10%252Fauto-reauth%26modrinth%3Dlab8OplF%26curseforge%3D977807&query=%24%5B'total-human'%5D&label=downloads&color=limegreen)

Requires: <kbd>[Minecraft 1.20.4](https://minecraft.wiki/w/Java_Edition_1.20.4)</kbd> <kbd>[Fabric API](https://modrinth.com/mod/fabric-api/version/0.96.3+1.20.4)</kbd> <kbd>[Mod Menu](https://modrinth.com/mod/modmenu/version/9.0.0)</kbd>

This is a mod that automatically re-authenticates your session when it expires.
Without this mod, you need to restart your game every time your session expires (about every day).

![Screen Recording of auto-reauth](https://github.com/Basicprogrammer10/auto-reauth/assets/50306817/0d8b2d50-7d2b-4b86-8f92-a0f6baed7e26)

## Usage

After installing, you will need to sign in through auto reauth, so it can store your authentication tokens.
To do this, open the mod menu find auto reauth and open the config menu.
Here you can click "Login" to be redirected to the Microsoft login page, after signing in (if everything goes well) you will be redirected to a page that says "You can close this tab now", at this point your authentication tokens are stored, and you can close the tab.
Now, anytime you open the multiplayer menu, auto reauth will check your session status, and re-authenticate if necessary.
The tokens may expire after some time (like 90 days) so if you start having issues with the mod, try logging in again.

Do note that using this mod will store your authentication tokens in your config folder (config/auto-reauth/config.nbt).
So just be careful about that.

## References

- [Microsoft Authentication Scheme](https://wiki.vg/Microsoft_Authentication_Scheme) &mdash; Information on how the Microsoft authentication flow works for Minecraft.
- [Auth Me](https://github.com/axieum/authme) &mdash; A mod that lets you re-authenticate your session manually.
