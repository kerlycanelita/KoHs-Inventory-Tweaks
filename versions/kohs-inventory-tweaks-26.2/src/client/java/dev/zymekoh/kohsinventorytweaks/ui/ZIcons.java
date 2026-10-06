package dev.zymekoh.kohsinventorytweaks.ui;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * The interface's icons, drawn from code on a 16 by 16 pixel grid.
 *
 * <p>Each icon is written as sixteen rows of sixteen characters and turned into
 * horizontal runs once, when the class loads; drawing is a handful of fills with no
 * texture and no allocation. Legend: {@code a} the icon's primary colour, {@code v}
 * bright violet, {@code c} deep violet, {@code b} cyan, {@code d} magenta, {@code e}
 * crimson, {@code s} silver, {@code w} bone white, {@code .} empty.</p>
 */
public enum ZIcons {
	CURSOR(
		"................",
		".a........bbbb..",
		".aa.............",
		".awa.......bbbbb",
		".awwa...........",
		".awwwa......bbb.",
		".awwwwa.........",
		".awwwwwa........",
		".awwwwwwa.......",
		".awwwwaaaa......",
		".awwawa.........",
		".awa.awa........",
		".aa...awa.......",
		".a.....awa......",
		"........aa......",
		"................"),
	TWEAKS(
		"................",
		".........aaaa...",
		"........awwa....",
		".......awwa.....",
		"......awwa......",
		".....awwa.......",
		"....aaaaaaaaa...",
		"bb.aaaawwwwa....",
		"......awwa......",
		"bbb..awwa.......",
		"....awwa........",
		"b..awwa.........",
		"..awwa..........",
		".aaa............",
		"................",
		"................"),
	ISSUES(
		"................",
		".......ee.......",
		"......eeee......",
		"......ewwe......",
		".....eewwee.....",
		".....eewwee.....",
		"....eeewweee....",
		"....eeewweee....",
		"...eeeewweeee...",
		"...eeeeeeeeee...",
		"..eeeeewweeeee..",
		"..eeeeewweeeee..",
		".eeeeeeeeeeeeee.",
		".eeeeeeeeeeeeee.",
		"................",
		"................"),
	CUSTOMIZATION(
		"................",
		"...........sss..",
		"..........sssss.",
		".........sssss..",
		"........sssss...",
		".......sssss....",
		"......csss......",
		".....ccc........",
		"....dddc........",
		"...ddddd........",
		"..dddddd........",
		"..ddddd.........",
		".ddd....bb......",
		".dd....bbbb.....",
		".......bbbb.....",
		"........bb......"),
	HIGHLIGHTER(
		"dd............dd",
		"d..............d",
		"..aaaaaaaaaaaa..",
		"..a..........a..",
		"..a..........a..",
		"..a...bbbb...a..",
		"..a..bbwwbb..a..",
		"..a..bwwwwb..a..",
		"..a..bwwwwb..a..",
		"..a..bbwwbb..a..",
		"..a...bbbb...a..",
		"..a..........a..",
		"..a..........a..",
		"..aaaaaaaaaaaa..",
		"d..............d",
		"dd............dd"),
	SCALER(
		"aaaaa......aaaaa",
		"aaaa........aaaa",
		"aa.a........a.aa",
		"a...a......a...a",
		"a....a....a....a",
		".....cccccc.....",
		".....c....c.....",
		".....c.bb.c.....",
		".....c.bb.c.....",
		".....c....c.....",
		".....cccccc.....",
		"a....a....a....a",
		"a...a......a...a",
		"aa.a........a.aa",
		"aaaa........aaaa",
		"aaaaa......aaaaa"),
	VISIBILITY(
		"................",
		"................",
		"................",
		".....aaaaaa.....",
		"...aa......aa...",
		"..a...bbbb...a..",
		".a...bbccbb...a.",
		"a....bcwwcb....a",
		"a....bcwwcb....a",
		".a...bbccbb...a.",
		"..a...bbbb...a..",
		"...aa......aa...",
		".....aaaaaa.....",
		"................",
		"................",
		"................"),
	PERFORMANCE(
		"................",
		"....aaaaaaaa....",
		"..aa........aa..",
		".a..b......b..a.",
		".a............a.",
		"a..b...........a",
		"a.........ww...a",
		"a........ww....a",
		"a.......ww.....a",
		"a......ww......a",
		"a.....cc.......a",
		".a....cc......a.",
		"................",
		"................",
		"................",
		"................"),
	SAFETY(
		"................",
		".......aa.......",
		"....aaaaaaaa....",
		"..aaaccccccaaa..",
		"..acccccccccca..",
		"..acccwwwwccca..",
		"..accwwbbwwcca..",
		"..accwbbbbwcca..",
		"..acccwbbwccca..",
		"...accwwwwcca...",
		"...acccccccca...",
		"....acccccca....",
		".....acccca.....",
		"......aaaa......",
		".......aa.......",
		"................"),
	INVENTORY(
		"................",
		".aaaa.aaaa.aaaa.",
		".acca.acca.acca.",
		".acca.acca.acca.",
		".aaaa.aaaa.aaaa.",
		"................",
		".aaaa.aaaa.aaaa.",
		".acca.abba.acca.",
		".acca.abba.acca.",
		".aaaa.aaaa.aaaa.",
		"................",
		".aaaa.aaaa.aaaa.",
		".acca.acca.acca.",
		".acca.acca.acca.",
		".aaaa.aaaa.aaaa.",
		"................"),
	CHEST(
		"................",
		"................",
		"..aaaaaaaaaaaa..",
		".acccccccccccca.",
		".acccccccccccca.",
		".acccccccccccca.",
		".aaaaaawwaaaaaa.",
		".accccwbbwcccca.",
		".accccwwwwcccca.",
		".acccccccccccca.",
		".acccccccccccca.",
		".acccccccccccca.",
		".aaaaaaaaaaaaaa.",
		"................",
		"................",
		"................"),
	SHULKER(
		"................",
		"..dddddddddddd..",
		".dvvvvvvvvvvvvd.",
		".dvvvvvvvvvvvvd.",
		".dvvvvvvvvvvvvd.",
		".dvvvvvvvvvvvvd.",
		".dddddddddddddd.",
		".dccccccccccccd.",
		".dccccwwwwccccd.",
		".dccccwwwwccccd.",
		".dccccccccccccd.",
		".dccccccccccccd.",
		".dddddddddddddd.",
		"................",
		"................",
		"................"),
	ENDER_CHEST(
		"................",
		"................",
		"..aaaaaaaaaaaa..",
		".acccccccccccca.",
		".acccccccccccca.",
		".acccccccccccca.",
		".aaaaaabbaaaaaa.",
		".accccbwwbcccca.",
		".accccbwwbcccca.",
		".acccccbbccccca.",
		".acccccccccccca.",
		".acccccccccccca.",
		".aaaaaaaaaaaaaa.",
		"................",
		"................",
		"................"),
	BARREL(
		"................",
		"...aaaaaaaaaa...",
		"..acccacccacca..",
		"..ssssssssssss..",
		"..acccacccacca..",
		"..acccacccacca..",
		"..acccacccacca..",
		"..acccacccacca..",
		"..acccacccacca..",
		"..acccacccacca..",
		"..ssssssssssss..",
		"..acccacccacca..",
		"...aaaaaaaaaa...",
		"................",
		"................",
		"................"),
	KEYBIND(
		"................",
		"................",
		"..aaaaaaaaaaaa..",
		".acccccccccccca.",
		".acvvvvvvvvvvca.",
		".acvvvwvvwvvvca.",
		".acvvvwvwvvvvca.",
		".acvvvwwvvvvvca.",
		".acvvvwvwvvvvca.",
		".acvvvwvvwvvvca.",
		".acvvvvvvvvvvca.",
		".acccccccccccca.",
		"..aaaaaaaaaaaa..",
		"................",
		"................",
		"................"),
	/** Compatibility: the mod adapted itself (a check inside a diamond). */
	SEVERITY_ADAPTED(
		"................",
		".......aa.......",
		"......aaaa......",
		".....aaccaa.....",
		"....aaccccaa....",
		"...aaccccccaa...",
		"..aaccccccbbaa..",
		".aaccccccbbccaa.",
		".aaccbbcbbcccaa.",
		"..aaccbbbbccaa..",
		"...aaccbbccaa...",
		"....aaccccaa....",
		".....aaccaa.....",
		"......aaaa......",
		".......aa.......",
		"................"),
	/** Compatibility: a feature works with limits (a raised triangle). */
	SEVERITY_WARNING(
		"................",
		".......aa.......",
		"......aaaa......",
		"......aaaa......",
		".....aaccaa.....",
		".....aaccaa.....",
		"....aaaccaaa....",
		"....aaaccaaa....",
		"...aaaaccaaaa...",
		"...aaaaaaaaaa...",
		"..aaaaaccaaaaa..",
		"..aaaaaaaaaaaa..",
		".aaaaaaaaaaaaaa.",
		".aaaaaaaaaaaaaa.",
		"................",
		"................"),
	/** Compatibility: a feature is blocked (a crossed octagon). */
	SEVERITY_CRITICAL(
		"................",
		"................",
		".....eeeeee.....",
		"....eeeeeeee....",
		"...eeeeeeeeee...",
		"..eeewweewweee..",
		"..eeeewwwweeee..",
		"..eeeeewweeeee..",
		"..eeeeewweeeee..",
		"..eeeewwwweeee..",
		"..eeewweewweee..",
		"...eeeeeeeeee...",
		"....eeeeeeee....",
		".....eeeeee.....",
		"................",
		"................");

	private static final int GRID = 16;
	/** Runs as groups of four ints: column, row, length, colour slot. */
	private final int[] runs;

	ZIcons(final String... rows) {
		if (rows.length != GRID) {
			throw new IllegalArgumentException(this.name() + " needs " + GRID + " rows");
		}
		List<Integer> parsed = new ArrayList<>();
		for (int row = 0; row < GRID; row++) {
			String line = rows[row];
			if (line.length() != GRID) {
				throw new IllegalArgumentException(this.name() + " row " + row + " needs " + GRID + " columns");
			}
			int column = 0;
			while (column < GRID) {
				char symbol = line.charAt(column);
				int end = column + 1;
				while (end < GRID && line.charAt(end) == symbol) {
					end++;
				}
				if (symbol != '.') {
					parsed.add(column);
					parsed.add(row);
					parsed.add(end - column);
					parsed.add(slot(symbol));
				}
				column = end;
			}
		}
		this.runs = parsed.stream().mapToInt(Integer::intValue).toArray();
	}

	/**
	 * Draws the icon into a {@code size} square. Multiples of 16 are pixel-exact.
	 *
	 * @param primary colour for the icon's own shape ({@code a})
	 * @param opacity 0-1 multiplier for every colour, for disabled or entering icons
	 */
	public void draw(
		final GuiGraphicsExtractor graphics,
		final int x,
		final int y,
		final int size,
		final int primary,
		final float opacity
	) {
		if (size <= 0 || opacity <= 0.0F) {
			return;
		}
		if (size % GRID == 0) {
			int cell = size / GRID;
			for (int index = 0; index < this.runs.length; index += 4) {
				int x0 = x + this.runs[index] * cell;
				int y0 = y + this.runs[index + 1] * cell;
				graphics.fill(x0, y0, x0 + this.runs[index + 2] * cell, y0 + cell,
					ZTheme.fade(color(this.runs[index + 3], primary), opacity));
			}
			return;
		}
		// Other sizes scale the grid instead of dropping rows, so no stroke vanishes;
		// icons are decoration, so the transform never touches a hitbox.
		var pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(x, y);
		pose.scale(size / (float) GRID, size / (float) GRID);
		for (int index = 0; index < this.runs.length; index += 4) {
			int column = this.runs[index];
			int row = this.runs[index + 1];
			graphics.fill(column, row, column + this.runs[index + 2], row + 1,
				ZTheme.fade(color(this.runs[index + 3], primary), opacity));
		}
		pose.popMatrix();
	}

	private static int slot(final char symbol) {
		return switch (symbol) {
			case 'a' -> 0;
			case 'v' -> 1;
			case 'c' -> 2;
			case 'b' -> 3;
			case 'd' -> 4;
			case 'e' -> 5;
			case 's' -> 6;
			case 'w' -> 7;
			default -> throw new IllegalArgumentException("Unknown icon symbol " + symbol);
		};
	}

	private static int color(final int slot, final int primary) {
		return switch (slot) {
			case 0 -> primary;
			case 1 -> ZTheme.VIOLET_BRIGHT;
			case 2 -> ZTheme.VIOLET_DEEP;
			case 3 -> ZTheme.CYAN;
			case 4 -> ZTheme.MAGENTA;
			case 5 -> ZTheme.CRIMSON_BRIGHT;
			case 6 -> ZTheme.SILVER;
			default -> ZTheme.TEXT;
		};
	}
}
