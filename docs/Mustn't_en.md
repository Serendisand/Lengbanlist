# Usage Restrictions and Liability Statement

> This page is the short version for users. The complete terms are the [`LICENSE`](../LICENSE)
> (MPL-2.0) at the repository root and the `eula.yml` shipped with the plugin.
> Where the two conflict, MPL-2.0 prevails.

## 1. License: MPL-2.0

Lengbanlist is released under the **Mozilla Public License 2.0 (MPL-2.0)**.
The source lives on [GitHub](https://github.com/Serendisand/Lengbanlist).
As long as you comply with the license, you may:

- use, copy, and modify this plugin;
- distribute this plugin or your derivatives of it;
- use this plugin commercially, including on a commercial server.

Core obligations under MPL-2.0:

| Obligation | Details |
| --- | --- |
| Keep the notices | Copyright notices, license text, and disclaimers must not be removed or altered |
| Mark your changes | Modified files must state what changed and when |
| Stay open source | Modified MPL files must be distributed under MPL-2.0 or a compatible license |
| Keep the credits | The original project link and author credit must remain |

The first one is not negotiable: rebranding the plugin and stripping the author and project
link before redistributing it violates the license.

## 2. EULA: Accept It Before the Plugin Enables

The plugin ships an End User License Agreement at `plugins/Lengbanlist/eula.yml`.
**Until you change `I have read and agree to the above terms` to `yes`, the plugin stays
disabled.** That is deliberate — a punishment plugin stores player data, and server owners
should know what they are agreeing to.

The EULA covers what MPL-2.0 does not (support policy, feedback rules, data handling,
disclaimer scope). Where it conflicts with MPL-2.0, MPL-2.0 prevails.

## 3. Prohibited Conduct

1. Removing or altering copyright notices, author credits, or `eula.yml`;
2. Promoting or misrepresenting the plugin as "Lengbanlist Official", "authorized by the
   author", or "partnered with the author" without written permission;
3. Exploiting plugin vulnerabilities to disrupt servers, mass-ban legitimate players, or
   bypass server rules;
4. Using this plugin for any purpose that violates local laws or regulations.

## 4. Fan-Made Work

This is a free fan-made plugin, not affiliated with miHoYo, Mojang, or Microsoft.
Character likenesses and lines in the bundled character models belong to their respective
rights holders and are intended for non-commercial fan use only. Verify compliance yourself
before any commercial use.

## 5. Disclaimer and Limitation of Liability

The plugin is provided "as is", without any express or implied warranty. The developer is not
liable for losses caused by using or being unable to use the plugin, including but not limited
to server data loss, ban disputes, player loss, or revenue loss.

**Back up your server data and the `plugins/Lengbanlist` folder regularly.** The plugin
constantly rewrites ban, warning, and audit data; data lost without a backup is out of scope
for support.

## 6. Data and Privacy

- All data is stored locally on your server by default and is never uploaded to the author;
- The bundled bStats metrics report only non-identifying information such as plugin version,
  server version, and player count; it can be turned off in `plugins/bStats/config.yml`;
- Server owners are responsible for ensuring their collection, storage, and use of ban data
  comply with local privacy laws such as the GDPR.

## 7. Updates and Support

- Support covers the latest official stable release only; self-modified, self-compiled, or
  redistributed builds are out of scope;
- No fixed release schedule is promised; features, maintenance, and the agreement itself may
  change at any time;
- BETA features may be unstable, change, or be removed — do not rely on them in production.

---

Last updated: October 17, 2026
