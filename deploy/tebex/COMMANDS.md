# Tebex fulfillment commands

The store targets authenticated Minecraft Java Edition UUIDs. Fulfillment is console-only and idempotent by transaction ID.

## Grant

```text
nerdadmin entitlement grant {username} supporter_monthly {transaction} 31
nerdadmin entitlement grant {username} supporter_annual {transaction} 366
nerdadmin entitlement grant {username} cosmetic.<catalog-id> {transaction}
```

## Renewal

Use the same product and a new Tebex transaction ID. The production entitlement adapter will extend an existing subscription rather than stacking gameplay benefits; there are no gameplay benefits to stack.

## Expiry, refund, and chargeback

```text
nerdadmin entitlement revoke {transaction} subscription-expired
nerdadmin entitlement revoke {transaction} refund
nerdadmin entitlement revoke {transaction} chargeback
```

Unknown usernames are deliberately not auto-created. The command reports that fulfillment is held for support review. Never compensate a failed cosmetic grant with Marks, items, XP, claims, market slots, or access.

## Required pre-launch tests

- manual purchase and duplicate delivery;
- renewal and annual expiry;
- refund and chargeback revocation;
- offline known player;
- unknown player;
- canonical Java accounts, including accounts with preserved historical identity records;
- plugin restart between payment and delivery;
- transaction replay after a database restore.
