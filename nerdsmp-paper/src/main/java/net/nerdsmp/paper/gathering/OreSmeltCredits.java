package net.nerdsmp.paper.gathering;

import java.util.Objects;
import java.util.UUID;

/** A furnace owns at most one output stack of proven ore-smelting work. */
public record OreSmeltCredits(UUID furnaceId, long sequence, String output, int credits, int observed) {
    public static final int MAX_STACK = 64;
    public OreSmeltCredits {
        Objects.requireNonNull(furnaceId);
        if (sequence < 0 || sequence == Long.MAX_VALUE || !metalOutput(output)
            || credits < 0 || observed < 0 || credits > observed || observed > MAX_STACK)
            throw new IllegalArgumentException("Invalid ore-smelting receipt");
    }

    public static OreSmeltCredits fresh(String output, int observed) {
        return new OreSmeltCredits(UUID.randomUUID(), 0, output, 0, observed);
    }

    public static boolean metalOutput(String output) {
        return "IRON_INGOT".equals(output) || "COPPER_INGOT".equals(output)
            || "GOLD_INGOT".equals(output) || "NETHERITE_SCRAP".equals(output);
    }

    /** Unexpected additions or item replacement cannot manufacture smelting receipts. */
    public OreSmeltCredits reconcile(String actualOutput, int actualAmount) {
        if (actualAmount < 0 || actualAmount > MAX_STACK) throw new IllegalArgumentException("Invalid output count");
        if (actualAmount == 0) return new OreSmeltCredits(furnaceId, sequence, output, 0, 0);
        if (!metalOutput(actualOutput)) return new OreSmeltCredits(furnaceId, sequence, output, 0, 0);
        if (!output.equals(actualOutput) || actualAmount > observed)
            return new OreSmeltCredits(furnaceId, sequence, actualOutput, 0, actualAmount);
        return new OreSmeltCredits(furnaceId, sequence, output,
            Math.max(0, credits - (observed - actualAmount)), actualAmount);
    }

    /** Only call after a finalized smelt consumes one eligible input and adds one native output. */
    public OreSmeltCredits smelt(String nativeOutput, int before, int after) {
        if (!metalOutput(nativeOutput) || before < 0 || before >= MAX_STACK || after != before + 1)
            throw new IllegalArgumentException("Unproven ore-smelting result");
        OreSmeltCredits aligned = reconcile(nativeOutput, before);
        return new OreSmeltCredits(furnaceId, sequence, nativeOutput, aligned.credits + 1, after);
    }

    /** Extraction is post-removal. Replaying the same event cannot match the saved count twice. */
    public Withdrawal extract(String extractedOutput, int removed, int remaining) {
        if (!output.equals(extractedOutput) || removed <= 0 || removed > MAX_STACK
            || remaining < 0 || remaining > MAX_STACK || remaining + removed != observed)
            return new Withdrawal(reconcile(extractedOutput, Math.clamp(remaining, 0, MAX_STACK)), 0, sequence);
        int earned = Math.min(credits, removed);
        long nextSequence = sequence + 1;
        if (nextSequence == Long.MAX_VALUE) throw new IllegalStateException("Ore receipt sequence exhausted");
        return new Withdrawal(new OreSmeltCredits(furnaceId, nextSequence, output,
            credits - earned, remaining), earned, nextSequence);
    }

    public OreSmeltCredits unverified(String actualOutput, int actualAmount) {
        String type = metalOutput(actualOutput) ? actualOutput : output;
        int count = metalOutput(actualOutput) ? Math.clamp(actualAmount, 0, MAX_STACK) : 0;
        return new OreSmeltCredits(furnaceId, sequence, type, 0, count);
    }

    public String encode() {
        return "1|" + furnaceId + "|" + sequence + "|" + output + "|" + credits + "|" + observed;
    }

    public static OreSmeltCredits decode(String serialized) {
        if (serialized == null || serialized.length() > 150) throw new IllegalArgumentException("Invalid ore receipt length");
        String[] fields = serialized.split("\\|", -1);
        if (fields.length != 6 || !fields[0].equals("1")) throw new IllegalArgumentException("Unsupported ore receipt schema");
        UUID id = UUID.fromString(fields[1]);
        if (!id.toString().equals(fields[1])) throw new IllegalArgumentException("Invalid furnace identity");
        return new OreSmeltCredits(id, Long.parseLong(fields[2]), fields[3],
            Integer.parseInt(fields[4]), Integer.parseInt(fields[5]));
    }

    public record Withdrawal(OreSmeltCredits next, int creditedItems, long operationSequence) { }
}
