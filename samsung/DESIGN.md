# Samsung viewing companion

Two places for one conversation: a large, quiet TV reading surface and a phone
conversation that continues when the TV app is suspended. No imitation video
player, unsupported overlay, or automatic watching claim.

Palette: midnight #132337, deep sea #203b51, fog #d8e6ec, muted blue #a7bbcb,
lamplight #edb86b, ice #94d7df. Georgia gives the opening title an editorial film
feel; Arial/system sans keeps remote controls and long answers legible.

TV: left-aligned title and viewing context on the left; a generous transcript
and composer on the right. Phone pairing is an explicit, temporary panel. The
white focus ring must remain obvious at couch distance. Phone: context above a
single conversation column, composer directly below it, useful empty state.

Reviewed against the brief: keep the launcher’s cinematic atmosphere, but remove
decorative app tiles and stock photography from this task-focused screen. No
invented movie metadata. QR is generated locally by the service, never by a
third-party QR host. Use flexbox and ES5 client syntax for Tizen 4 / Chromium 56.

## Samsung Home (0.2.0)

Opening AttuneTV now starts at Home. The existing companion remains one click away under Ask; its phone flow is unchanged. Samsung's remote Home button still opens Smart Hub.

Home uses the Insignia visual direction: a landscape fills the screen, a large thin clock sits top-left, three round shortcuts sit top-right, and the selected app title leads into a translucent favorites dock near the bottom. Color tokens: night #142a35, glass #203d51, mist #cfdee6, white #f7fbfc, warm pin #f7dc9b. Helvetica Neue/Arial gives the clock a quiet light weight where available; Arial keeps app names readable on Tizen 4. Six 16:9 tiles fit within the 5% safe margin. Longer docks scroll. No per-tile drop shadows, no automatic animation, no invented streaming metadata.

The bundled, credited landscape works offline. Inventory and favorites stay local. All apps uses a dark high-contrast surface and five-column grid; white focus outlines remain inside scroll padding. Remote Back closes a menu/grid before offering exit. Pin/reorder controls are available through Menu and the visible App options button. OS-internal services are filtered, duplicate names consolidated and unavailable icons fall back to readable names.

## Samsung artwork correction (0.2.1)

Real device photos exposed tiny square icons, Samsung placeholder symbols for Prime Video/YouTube, and excessive blue mats. Match the Insignia principle: recognizable widescreen artwork leads. Bundle six recognizable service marks for installed apps only; preserve actual OS launch IDs. Use exact aliases, no substring guesses. Native icons for other apps stay uncropped, with an edge-matched mat when local canvas reads are permitted; neutral charcoal otherwise. Persistent grid captions identify unfamiliar marks. Round the white focus ring using a shadow on the rounded artwork surface, not old Chromium's square outline.

Tokens: cinema #171819, charcoal #202124, control #282b2c, secondary #c9cccd, white #ffffff. Brand artwork supplies color. Retain Arial for remote legibility. Five columns and safe margins remain; app names sit below each 16:9 image. The mountain is only faintly visible behind the app grid, with no blue wash or per-tile drop shadow. This follows the existing product's cinematic direction, not a separate visual theme.
