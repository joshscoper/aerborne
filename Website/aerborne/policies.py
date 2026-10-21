"""Plain-language policy content and immutable registration Terms versions."""
import hashlib
import json

from .db import now_str


def terms_version(item):
    return hashlib.sha256(json.dumps([item['title'], item['body']], ensure_ascii=False).encode('utf-8')).hexdigest()


def save_terms_version(conn, item):
    version = terms_version(item)
    conn.execute('INSERT OR IGNORE INTO registration_terms_versions '
                 '(version,title,body,published_at,captured_at) VALUES (?,?,?,?,?)',
                 (version, item['title'], item['body'], item['updated_at'], now_str()))
    return version


PAGES = {
    'rules': ('Server rules', 'Keep Aerborne welcoming, fair and fun—in game and across the community.', """
## At a glance

- Treat people with respect and keep personal information private.
- Play and trade fairly. Report exploits instead of using them.
- If something goes wrong, use a private report or appeal.

These rules apply in game, on the website and forum, and in Aerborne’s Discord. Discord and Minecraft also have their own rules.

## 1. Treat people well

No harassment, hate speech, threats, sexual content or targeted abuse. Do not pressure someone to share personal information, images or private conversations. A joke stops being a joke when it targets or intimidates someone.

## 2. Play fairly

Do not use hacked clients, x-ray, duplication, bots or automation that plays for you. Do not exploit bugs or help others exploit them. If you are unsure whether a mod or tool is allowed, ask staff before using it.

Report exploits privately to [support@aerborne.net](mailto:support@aerborne.net). Do not post instructions that let others repeat them.

## 3. Trade honestly

Describe items and prices truthfully. No scams, impersonation, fake listings or pressure tactics. Check the item and price before confirming a trade. Do not arrange real-money trades for accounts, progress or items outside the official store.

## 4. Respect islands and other people’s work

Build only where you have permission. Do not steal, grief or destroy another player’s work. Joining an island does not give you permission to take everything on it. Agree on shared resources and major changes with the island owner.

## 5. Keep conversations useful

No spam, caps floods, unsolicited advertising or repeated demands for attention. Post in the relevant forum category or Discord channel. Do not evade a mute or ban with another account.

## 6. Protect privacy and accounts

Do not share anyone’s real name, address, passwords, payment information or other private details. Do not ask for login codes or pretend to be staff. Aerborne staff will never need your Microsoft password, website password or card details.

## Reports and staff decisions

Use the report action on a forum post or private message, or contact support privately. In game, use `/report <player> <reason>`. Include the username, what happened, when it happened and relevant evidence. Do not start a public argument or encourage others to target the person.

Staff may remove content, warn, mute or suspend accounts depending on the situation. Serious incidents may lead to an immediate ban. Buying a rank does not change these rules. If you disagree with a decision, follow the [appeals process](/support/appeals).
"""),
    'terms': ('Terms of service', 'What you agree to when you create an account or use Aerborne’s services.', """
## At a glance

- Website accounts are for people aged 13 or older.
- Follow the community rules and keep your account secure.
- Store checkout has separate Tebex terms. Your legal consumer rights still apply.

These terms cover the Aerborne Minecraft server, website and forum, operated by the Aerborne team. Contact us at [support@aerborne.net](mailto:support@aerborne.net). Read these terms before using the services. Creating a website account requires your express agreement to them.

## Age and eligibility

You must be at least 13 to create a website account. Give accurate answers during the age check. If you are not legally an adult where you live, review these terms with a parent or guardian and obtain any permission required by local law.

The age confirmation is your declaration, not identity verification or parental consent. We do not offer an under-13 website account or a parental-consent account flow. If we learn that an account belongs to a child under 13, we will restrict it and address the personal information associated with it. A parent or guardian can contact support about access or deletion.

You need a legitimate Minecraft: Java Edition account to play on the Java server. Minecraft, Discord and Tebex have their own eligibility requirements; a website account does not override them.

## Your account

Choose a username you may share publicly and keep your password and recovery code private. Do not sell, lend or impersonate another account. Tell support if you suspect someone has gained access.

Linking a Minecraft account proves control of that account. One Minecraft account can be linked to one website account at a time. Staff may investigate or correct a mistaken or misused link.

When you register, we save when you accepted these terms, the text version you accepted and confirmation that you passed the age check. See the [privacy policy](/support/privacy) for details.

## Community conduct and moderation

Follow the [server rules](/support/rules). Do not cheat, exploit bugs, harass others, misuse private information or try to disrupt the service.

Staff may remove content, limit features, mute or suspend an account to protect the community. Restrictions can apply to the server, website or both. You can ask for a review through [appeals](/support/appeals). Purchasing something does not exempt an account from moderation.

## Content you share

You keep ownership of your original posts, builds and artwork. By submitting content, you give Aerborne permission to host, display and moderate it as needed to operate the service. Only share material you have permission to use. Do not post private information or unlawful content.

Featuring someone’s work in a separate showcase or promotion requires the creator’s permission. You can ask support about removing your content; some anonymised discussion or records may need to remain for moderation or legal reasons.

## Purchases and virtual items

Available purchases use Tebex checkout and its separate terms. Read the [store terms](/support/store-terms) and final order details before paying. Store items are cosmetic; they do not buy Marks, gameplay progress or immunity from rules.

Virtual items are for use within Aerborne. They have no cash value and cannot be redeemed for money. An item described as permanent is an ongoing account entitlement while the relevant service and item remain supported, not a promise that Aerborne will operate forever.

## Availability and changes

We may fix bugs, change features or run season resets. Downtime, lost progress and service interruptions are possible. We cannot promise uninterrupted access or that every feature will remain unchanged. We will announce important service or policy changes in [News](/news).

The date on this page identifies its current update. We keep the Terms version accepted at registration; we do not treat an old checkbox as proof that someone accepted a later version.

## Your legal rights

Nothing in these terms removes consumer or privacy rights that the law gives you, or excludes responsibility where the law does not allow it. Contact support with concerns about access, content or purchases so the appropriate team can investigate.

## Minecraft and other services

Aerborne is not an official Minecraft product and is not approved by or associated with Mojang or Microsoft. Use of Minecraft is also subject to its [EULA](https://www.minecraft.net/en-us/eula) and [Usage Guidelines](https://www.minecraft.net/en-us/usage-guidelines). External services apply their own terms and privacy policies.
"""),
    'privacy': ('Privacy policy', 'What Aerborne’s website collects, how it is used and the choices you have.', """
## At a glance

- Registration needs a username and password, not an email address.
- Your profile and ordinary forum posts can be public. Messages and applications have more limited visibility.
- You can download website account data and contact support about correction or deletion.

This policy describes the Aerborne website and forum, operated by the Aerborne team. The Minecraft server also maintains gameplay and operational records, which are not included in the website’s data download. Contact [support@aerborne.net](mailto:support@aerborne.net) with privacy questions.

## Account details and age check

We store your username, a password hash, account status and sign-in/activity dates. We store a hash rather than a readable password. Website registration does not require an email address.

Before registration, we check the birth date you enter to determine eligibility. We do not save that birth date in the website database or display it on a profile. The session keeps only an eligibility result and time. An account records when the age confirmation and Terms agreement were made, plus the accepted Terms version. A short-lived ineligible result prevents simply going back to change the answer in the same browser.

## Profiles, posts and private features

- **Minecraft linking:** your Minecraft username, account ID and link details connect website features to your game account.
- **Public content:** your username, chosen profile details, badges, rank and ordinary forum posts may be viewed by visitors. Do not put private information in them. Public information may also appear in search engines or be copied by other people.
- **Messages and friends:** we store conversations, friendships, requests, blocks, notifications and privacy preferences to provide those features. Message recipients can report a message; authorised moderators can review reported content. Staff access is for operating and moderating the service.
- **Arcade:** we store signed-in game runs, scores, reward records and delivery status to run games, show rankings and prevent reward abuse.

## Team applications

The Join Us form stores the name or username, contact details, role interest, time zone, availability, answers and optional work links you submit. Only website administrators can review these applications. They use the information to assess the application and contact you about it. Applications are not automatically published or emailed to other players.

To request a copy, correction or removal of an application, email support with its reference. The account data download does not automatically include a separately submitted application.

## Security and page counts

The app stores IP addresses and related sign-in or spam-control events to limit abuse. Its security throttle records use a short retention window of about an hour. The hosting provider may keep separate access, error or security logs under its own retention settings.

The website’s page counter records daily totals by page path. Those counters do not store IP addresses, browser details or a visitor tracking ID. Moderation reports and administrator audit records help investigate issues and document staff actions.

## Cookies and browser storage

An essential session cookie keeps you signed in, protects forms and remembers registration eligibility. It can be set before you sign in. Website preferences may also use browser storage. Aerborne does not add advertising cookies through its own page-counter feature.

Embedded and linked services may receive data or use storage under their own policies. Blocking an embed may stop its preview from loading; direct links remain available.

## Other services and sharing

- **Discord:** the Discord page embeds Discord’s official server widget. Loading it connects your browser to Discord, which can receive your IP address and request information. Joining or chatting takes place on Discord under [Discord’s privacy policy](https://discord.com/privacy).
- **Google Fonts:** loading the site’s fonts connects your browser to Google. See [Google’s font privacy information](https://developers.google.com/fonts/faq/privacy).
- **Tebex:** starting checkout sends your Minecraft username, selected packages and IP address to Tebex to prepare the order. Payment details are entered on Tebex, not collected by this website. See [Tebex’s privacy policy](https://www.tebex.io/legal/privacy-policy).
- **Streams and external links:** platform links open services such as Twitch, YouTube and Kick. Any platform content you choose to open is subject to that provider’s policy. Public live-status checks do not send your website password to those platforms.

The hosting provider processes website data to run the service. Authorised staff may use account, report and security records to provide support or enforce rules. Information may also need to be preserved or disclosed when required by law. Do not send passwords or card details to support.

## Retention and deletion

Account and community data are retained while needed to provide the service and handle support or moderation. Contact support to request account or application deletion. We do not promise that every record disappears immediately when a profile is removed.

Account removal clears profile and linking details, removes messages and social connections, and removes or anonymises associated community content according to the removal request and moderation needs. Some audit, dispute, payment or enforcement records may need to remain. Hosting backups and logs can retain older copies under the hosting provider’s retention settings; ask support for details.

We keep copies of accepted Terms text so we can identify what was agreed to. These policy-text copies contain no player birth dates or passwords.

## Your choices and requests

Edit your profile and privacy preferences in [Account](/account). Signed-in members can download their website account data at [Account data](/account/data). For correction, account deletion, application access or other privacy requests, contact support. We may ask for enough information to confirm that the request concerns your account; we will not ask for your password.

Depending on where you live, additional access, deletion, correction, objection or complaint rights may apply. Tell support what you need so the request can be reviewed under the applicable law.

## Children and parent requests

Website accounts are for people aged 13 or older. We do not offer an under-13 registration or parental-consent flow. If you believe we hold personal information from a child under 13, contact [support@aerborne.net](mailto:support@aerborne.net) so the team can restrict the account, investigate and address deletion. Do not post the child’s details publicly.

The age check is an eligibility check, not proof of identity or parental consent.

## Changes and contact

We update this page when website practices change and use [News](/news) for important notices. The update date is shown on this page. Privacy contact: [support@aerborne.net](mailto:support@aerborne.net).
"""),
    'store-terms': ('Store terms and refunds', 'Know what you are buying, how delivery works and where to get purchase help.', """
## At a glance

- Available store items are cosmetic and do not grant gameplay advantages.
- Tebex handles checkout and payment terms. Check the account and final total before paying.
- Missing items go to Aerborne support; billing and refund requests go to Tebex buyer support.

## Before you buy

Read the item’s description, included cosmetics, supported platform, price and duration. Items marked unavailable or not on sale cannot be purchased. Do not assume a picture includes extras that are not listed in the description.

Follow Tebex’s separate purchaser-age and payment requirements. Having a 13+ Aerborne website account does not override them. Use a payment method you are authorised to use.

## Checkout and payment

Checkout takes place on Tebex, which acts as the merchant for the purchase under its [checkout terms](https://checkout.tebex.io/terms). Review the final currency, taxes, discounts and total there. This website does not collect your card details.

Choose the correct Minecraft account in the cart before continuing. The order is delivered to that account; contact support promptly if you make a mistake, but do not assume a purchase can be moved between accounts.

## Delivery

Delivery depends on payment confirmation and the Minecraft server processing the order. You may need to be online or rejoin the server. Timing can vary; a checkout return page alone does not prove that an item was delivered.

If an item is missing, keep your receipt and follow [purchase help](/support/purchase-help). Share your Minecraft username and transaction reference privately with support. Never post payment details or a full receipt publicly.

## Cosmetic items and duration

Store purchases do not give Marks, gear, island progress or gameplay advantages. Supporter ranks are cosmetic recognition, not staff positions or moderation powers.

Each item page explains its duration. A permanent entitlement stays with the purchased account across seasons and resets while the item and relevant service remain supported. It is not a guarantee of unlimited server uptime or operation forever. Virtual items have no cash value and cannot be redeemed for money.

## Refunds, cancellations and billing mistakes

For cancellation, duplicate charges, billing problems or refund requests, contact [Tebex buyer support](https://www.tebex.io/support-customer) and review the terms shown at checkout. Refund eligibility depends on those terms and applicable consumer law; this page does not promise or rule out a refund in every situation.

Aerborne staff can investigate missing or incorrect in-game delivery. Nothing here takes away a legal refund, cancellation or other consumer right that applies to you.

## Payment disputes and moderation

Tebex handles payment disputes. Keep the transaction reference and contact buyer support if a charge is unfamiliar. Refunded or reversed orders may have the related entitlements removed.

Purchases do not protect an account from the [server rules](/support/rules). Moderation appeals use the [appeals process](/support/appeals); refund requests remain subject to the checkout terms and applicable law.

## Contact and privacy

For delivery: [support@aerborne.net](mailto:support@aerborne.net). For billing: [Tebex buyer support](https://www.tebex.io/support-customer). See the [privacy policy](/support/privacy) for the information sent when checkout starts.
"""),
    'appeals': ('Appeals', 'Ask for a review of a mute, ban or other account restriction.', """
## At a glance

- Send one clear, private appeal.
- Explain the decision, what happened and why you think it should change.
- Do not evade the restriction while waiting for review.

## Where to send an appeal

Use the [appeals forum](/forum/appeals) if you can access it, or email [support@aerborne.net](mailto:support@aerborne.net). If you cannot sign in or are banned from Discord, email is still available. Do not put an appeal in public chat.

## What to include

Include your website and Minecraft usernames, the restriction or message you received, roughly when it happened, your explanation and relevant evidence. If you made a mistake, explain what you would do differently. Do not include passwords, login codes or payment details.

## How review works

Staff review the available information and may ask follow-up questions. A different staff member reviews the decision where possible. There is no fixed response-time guarantee. Repeated messages do not move an appeal ahead of others.

An appeal does not automatically remove a restriction or guarantee a different outcome. Do not use another account to evade it. A purchase dispute is handled through [purchase help](/support/purchase-help), not through moderation appeals.
"""),
    'bug-reports': ('Reporting bugs', 'Give staff the details they need to reproduce and fix a problem.', """
## At a glance

- Explain what you expected and what actually happened.
- Include steps, time and relevant screenshots.
- Send exploits and private information directly to support.

## Ordinary bugs

Ask in [Discord](/discord) or the [help forum](/forum/help). Include the game or website page involved, what you were doing, steps to repeat the issue, the expected result, the actual result and roughly when it happened. For game issues, include your Minecraft version and relevant error message.

Screenshots help, but remove private messages, login codes, account details and other personal information first. Do not share full logs publicly if you have not checked them for sensitive information.

## Exploits and security issues

Email [support@aerborne.net](mailto:support@aerborne.net) privately. Stop using the issue and do not distribute instructions or gain items from it. Give staff enough information to investigate without testing it against other players or accounts.

## After reporting

Keep the report in one place and add useful new details there. Staff may ask how to reproduce it. A report does not guarantee a fix date, reward or restoration of lost items.
"""),
    'purchase-help': ('Purchase help', 'Separate a missing in-game item from a billing or refund problem.', """
## At a glance

- Check the Minecraft account and Tebex receipt first.
- Ask Aerborne about delivery; ask Tebex about billing and refunds.
- Keep receipts and transaction references out of public posts.

## If an item has not arrived

Check that Tebex confirmed the payment and that you selected the right Minecraft account. Join or rejoin Aerborne so the server can process delivery. Check your cosmetics menu and the item’s description to make sure you are looking for the right entitlement.

If it is still missing, email [support@aerborne.net](mailto:support@aerborne.net) with your Minecraft username, item name, purchase time and Tebex transaction reference. Do not buy it again just to trigger delivery.

## Billing, refunds or an unfamiliar charge

Contact [Tebex buyer support](https://www.tebex.io/support-customer). Tebex handles payment processing and purchase terms. See the [store terms](/support/store-terms) and the terms shown at checkout for refund and cancellation information.

## Keep your details safe

Never post card numbers, full receipts, account passwords or login codes publicly. Support needs a transaction reference to investigate, not your payment card details. Website Terms acceptance is separate from any agreement made at Tebex checkout.
"""),
    'account-help': ('Account help', 'Keep your website and Minecraft accounts secure, linked and recoverable.', """
## At a glance

- Website sign-in and Microsoft/Minecraft sign-in are separate.
- Keep your website recovery code somewhere private.
- Support will never need your password or Microsoft login code.

## Website sign-in and recovery

Use your website username and password at [Sign in](/account/login). Website registration does not require email, so it is not an email-reset service. If you have a recovery code, use [account recovery](/account/recover). Keep that code private like a password.

If you cannot recover access, contact [support@aerborne.net](mailto:support@aerborne.net). Staff may need to establish which account is yours. Do not send passwords, identity documents or login codes. Staff will explain how to show that you control the account without sharing your Microsoft credentials.

## Linking Minecraft

Create a website account, run `/weblink` in Aerborne and enter the code on [Link Minecraft](/account/link). The code proves control of your game account. Do not share it with other players. Linking does not replace your Microsoft login or give staff access to it.

Minecraft progress follows your game account ID, so changing a Minecraft username does not create a new game account. Contact support if an account is linked incorrectly.

## Profile and privacy

Use [Account](/account) to edit your profile and social preferences. Download website data at [Account data](/account/data). Email support for account deletion, corrections or a parent/guardian privacy request. See the [privacy policy](/support/privacy) for what is stored and what may remain after removal.
"""),
}

_title, _summary, _body = PAGES['privacy']
PAGES['privacy'] = (_title, _summary, _body + """

## Creator program

If you apply, we store your website account link, channel or portfolio links, content plans,
contact details and application review status. Administrators use these to review your
application and contact you. These application details are not public.

Accepted creators can request a code by providing the requested code and their Tebex Wallet
reference. Only that creator and administrators can see the wallet reference here. We share
it with Tebex to configure the code and its payment allocation. Do not provide bank details,
wallet passwords or payment card information.

Priority tickets and replies are private to that creator and administrators. We store them
to provide support. Signed Tebex notifications supply transaction references, creator codes,
amounts, currencies and payment status for performance metrics. We do not save the customer
names, emails, addresses or payment details from those notifications in the creator records.
Creators see aggregate sales and estimates, not buyer identities.

Your account data export includes your creator application, code requests and support tickets.
On account deletion we remove your contact details, application text, wallet reference and
tickets, and disable local code access. Minimal code and transaction records may remain for
financial reconciliation and payment disputes; they are not public. Ask support about removal
of an application or correction of a wallet reference. Tebex has its own privacy, financial
record and withdrawal requirements.
""")
_title, _summary, _body = PAGES['store-terms']
PAGES['store-terms'] = (_title, _summary, _body + """

## Creator codes

You can enter an active creator code in the cart. Eligible purchases receive a 10% customer
discount and support that creator with a 25% revenue share under the creator program. Tebex
verifies the code and calculates the final discount and allocation. Review the total before
paying. Fees, taxes, refund adjustments, discount stacking and product eligibility follow
Tebex's checkout settings. For subscriptions, code discounts and revenue share apply only to
the initial payment. Creator dashboard estimates are not payout records.
""")
