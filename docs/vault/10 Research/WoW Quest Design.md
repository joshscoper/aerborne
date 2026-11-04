---
tags: [research, wow]
---
# WoW quest design

## From Jeff Kaplan's GDC 2009 talk *(S1)*
- **Directed gameplay.** The world is open, but the content that guides players is hand-built and placed around
  points of interest. Make doing quests the smartest way to play: tune XP and money so it is.
- **The "Christmas tree" hub.** A hub that lights the minimap with "!" makes players clear quests one by one with
  no sense of story; designers lose control of the experience. Offer choice thoughtfully.
- **Don't over-write.** Quest text is short (511 characters). Players skim. The game is not a book.
- **No mystery in the action.** The mystery can be in the story, but the task must be concrete: kill this,
  bring that, go there. Otherwise players look it up.
- **Never break trust.** No confusing chains, and never send a player after a target they can't kill.
- **No gimmicks** the engine wasn't built for. Polish the core loop first.
- **Interleave.** Four kill quests then four collection quests is bad flow. Give several routes to the goal, keep
  guiding the player back.
- **Cut travel.** Early quests sent players too far. Breadcrumb from quest to quest and hub to hub.
- **Collection quests:** don't ask for too many or too many kinds; tie them to a payoff; the drop chance rises
  with each kill so there are no long dry streaks (the example was 16%, 32%, 48%… up to 100%). That also removes
  lucky streaks, so the base rate has to go up.
- **Feedback:** quest-giver markers, instant progress feedback on screen and in the log. A combat target of about a
  minute per fight, since made quicker.

## From Cataclysm's remake *(S3)*
- Each zone got a **top-down story** (Westfall's Defias returning) with a mystery for new players and memories for
  old ones.
- Hubs need enough quests, an inn and a vendor.
- **Phasing** showed the world change with the story.

## Quest shapes worth using (play)
| Shape | Example of the feel | Our objective type |
| --- | --- | --- |
| Kill N of a family | "Thin the gnawers in the lower fields" | `kill` (exists) |
| Collect drops from a family | "Bring 6 ember-stained scraps from the scarecrows" | **`loot`** (new: progressive drop chance) |
| Use or inspect an object | "Burn the three cult banners", "Read the barrow stones" | **`interact`** (new: right-click a marked block) |
| Go and look | "Scout the old mill" | `visit` (exists) |
| Talk | "Ask the miller what he saw" | `talk` (exists) |
| Gather from the land | "Cut 10 heartwood" | `harvest`, `dig` (exist) |
| Bring items | "Deliver the grain to the docks" | `deliver` (exists) |
| Kill a named target | "End the Scarecrow King" | `kill` on a rare or a dungeon boss |
| Escort | "Walk the cart to the gate" | later, maybe never (gimmick risk) |

## Rules for the Reach
1. A zone has **12–20 story quests** in 3–4 short chains, plus 2–3 dailies; each hub offers **3–5 at a time**, no
   more.
2. Each chain ends with a **payoff**: a cutscene line, a changed NPC, a named kill, a dungeon door opening.
3. **Every chain's last quest breadcrumbs to the next hub.** The zone's last quest breadcrumbs to the next zone.
4. **Interleave** kill, loot, interact and visit in one area so a single trip completes several.
5. Text ≤ 3 short sentences per page; the task in the first line.
6. Collection uses the progressive chance. Quest drops only drop for players on the quest.
7. **Breadcrumb to the dungeon:** each zone's main chain ends with "go through the expedition door" and pays out on
   the clear.
