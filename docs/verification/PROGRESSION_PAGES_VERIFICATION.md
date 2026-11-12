# Progression page verification

The expedition detail, campaign and six leaderboard views use current public API snapshots.
The general expedition board also keeps tier level and shared-life columns visible in a
scrollable table on narrow screens.

- Nine expedition/campaign page, unavailable-state and seed-preservation tests passed.
- Four progression presentation regressions plus the existing leaderboard/profile test passed:
  partial expedition data, probability rounding, bounded progress with actual totals,
  per-goal limits, UTC end dates, correct leaderboard units, zero scores, original ranks,
  and unavailable snapshots.
- Browser checks used live read-only data at localhost:5050 for the leaderboard podium,
  displayed-name filtering (the player's #3 rank stayed #3), expedition preparation and
  enemy/drop search. Both published expedition links were checked through route tests.
- Shared responsive rules stack journals, metrics, podiums and contribution cards and retain
  complete table columns. The normal frame-protection headers prevent iframe-based phone
  inspection; those protections were left unchanged. No claim of a phone screenshot is made.
- The updated plugin additionally publishes exact per-tier completion Marks and goal daily
  limits. The website continues to render earlier API snapshots with explicit base/default
  labels, without inventing missing rewards or guides. Eighteen public game API tests passed.
- The website deployment ZIP excludes instances, private settings, test data and dev previews.

No live gameplay actions or production deployment occurred. Source references and accuracy
notes are in Website/CONTENT_REVIEW.md. Install the updated plugin JAR and restart to expose
its additional public fields and enable the completed chat-console integration.

Minecraft heads now replace initials on all leaderboard categories and forum avatars/crests on
game player pages. Forum profile avatar choices remain independent. The same-origin image
route now resolves names through Mojang, reads the session texture property and renders
face plus hat directly from the HTTPS Minecraft texture host. It accepts only bounded
64x64/64x32 PNG skins and publishes freshly encoded 128px PNGs. Cookies and visitor IPs
are not forwarded. Profile IDs and texture paths are validated and redirects are disabled.
Heads refresh after 15 minutes with five-minute browser caching; failed refreshes retain
a prior good head for up to 24 hours in memory, with a one-minute retry. Unknown/no-custom-
skin profiles use the packaged crest. A new image URL version bypasses old MCHeads caches.
Pillow is required for rendering; no extra secrets or CSP changes are needed.
Eleven rendering/cache/failure/validation tests cover modern and legacy alpha handling,
exact pixels and nearest-neighbour scaling, malformed textures and untrusted URLs. The
leaderboard presentation and existing profile checks also passed. The local preview now
uses its Python 3.13 environment to match its installed Pillow extensions.
Live verification confirmed maylia156's custom head replaced the old Steve image. Final
preview: testing/mojang-leaderboard-heads.png. No gameplay or production deployment occurred.

## Expanded categories

Five overall boards and all 11 skills now share the website, game API, player/forum
standings, command completion and in-game category chooser. Skill boards read exact
experience from the same progression snapshot as the existing Cooking board; no values
are inferred from levels. Overall score meanings and deterministic tie ordering are
unchanged. Profiles collapse individual skill standings; navigation separates the two
groups. Older servers explicitly report unavailable categories without invented scores.

Eight website presentation/profile/privacy tests and 22 plugin ranking/public API tests passed.
The API test fills all 16 top-25 lists with maximum-length names and large scores and
checks that the leaderboard-only 64 KiB budget and unchanged other budgets hold. The
plugin JAR and website ZIP were rebuilt. Install the JAR and restart the game server
to populate the new ten skill categories; no live game server was restarted for this work.
