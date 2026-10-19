package dev.zymekoh.kohsinventorydebug;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

/**
 * Every regression lab in one game, one after the other: one launch per version instead of one
 * launch per lab. A lab that fails is reported and the next one still runs.
 *
 * <p>{@code -PlabQuick} shortens the labs that measure (fewer trials, shorter benchmarks) and
 * {@code -PlabOnly=a,b} runs only the named labs. Singleplayer only, like every lab here.</p>
 */
public final class LabSuite {
	@FunctionalInterface
	private interface Lab {
		void run(Minecraft minecraft) throws Exception;
	}

	private record Entry(String name, Lab lab) {
	}

	private static final List<Entry> LABS = List.of(
		new Entry("inventory-feel", InventoryFeelLab::run),
		new Entry("slot-shortcuts", SlotShortcutLab::run),
		new Entry("inventory-order", InventoryOrderLab::run),
		new Entry("close-hotbar", CloseHotbarRegressionLab::run),
		new Entry("cursor-contract", CursorContractLab::run),
		new Entry("pvp-input", PvpInputRegressionLab::run),
		new Entry("features", FeatureLab::run),
		new Entry("sfi-value", FastOpenValueLab::run)
	);

	private LabSuite() {
	}

	/** Whether the measuring labs run their short form. */
	public static boolean quick() {
		return Boolean.getBoolean("kohs.lab.quick") || System.getenv("KOHS_LAB_QUICK") != null;
	}

	public static void run(final Minecraft minecraft) {
		String only = System.getProperty("kohs.lab.only");
		List<String> wanted = only == null || only.isBlank() ? null : Arrays.asList(only.split(","));
		List<String> results = new ArrayList<>();
		int ran = 0;
		int failed = 0;
		long suiteStart = System.nanoTime();
		for (Entry entry : LABS) {
			if (wanted != null && !wanted.contains(entry.name())) {
				continue;
			}
			ran++;
			long start = System.nanoTime();
			DebugCollector.info("SUITE_LAB", "start " + entry.name() + (quick() ? " (quick)" : ""));
			String verdict = "ok";
			try {
				entry.lab().run(minecraft);
			} catch (Throwable failure) {
				failed++;
				verdict = "FAIL " + failure.getClass().getSimpleName() + ": " + failure.getMessage();
				DebugCollector.issue("SUITE_LAB_FAIL", entry.name() + "; " + verdict);
			} finally {
				settle(minecraft);
			}
			long seconds = (System.nanoTime() - start) / 1_000_000_000L;
			results.add(entry.name() + "=" + verdict + " (" + seconds + "s)");
			DebugCollector.info("SUITE_LAB", "end " + entry.name() + "; " + verdict + "; " + seconds + "s");
		}
		DebugCollector.info("SUITE_SUMMARY", "failed=" + failed + "/" + ran + "; seconds=" + (System.nanoTime() - suiteStart) / 1_000_000_000L
			+ (quick() ? "; quick" : "") + "; " + String.join("; ", results));
		if (failed > 0) {
			throw new IllegalStateException("Suite failures=" + failed + "/" + ran);
		}
	}

	/** Between labs: no screen open, no key held, and a moment for the server to answer. */
	private static void settle(final Minecraft minecraft) {
		try {
			CloseHotbarRegressionLab.atPoll(minecraft, () -> {
				if (minecraft.gui.screen() != null) {
					minecraft.gui.screen().onClose();
				}
				KeyMapping.releaseAll();
			});
			Thread.sleep(300);
		} catch (Exception ignored) {
			// The next lab sets its own state up.
		}
	}
}
