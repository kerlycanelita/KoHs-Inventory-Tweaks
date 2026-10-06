package dev.zymekoh.kohsinventorytweaks.screen;

import dev.zymekoh.kohsinventorytweaks.KoHsInventoryTweaksClient;
import dev.zymekoh.kohsinventorytweaks.ui.ZBackdrop;
import dev.zymekoh.kohsinventorytweaks.ui.ZChrome;
import dev.zymekoh.kohsinventorytweaks.ui.ZDraw;
import dev.zymekoh.kohsinventorytweaks.ui.ZMascot;
import dev.zymekoh.kohsinventorytweaks.ui.ZMotion;
import dev.zymekoh.kohsinventorytweaks.ui.ZScene;
import dev.zymekoh.kohsinventorytweaks.ui.ZTheme;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.FormattedCharSequence;

/**
 * The KoHs tab, as in KoHs Anchor's and Crystal Tweaks: Zymekoh (a.k.a. Kohzemyora), who makes
 * the KoHs mods, as the KoHs Mod Suite site draws her (a hooded figure with glowing eyes and a
 * code sigil), and below her the name, the aka, what she does, links to Discord, the site,
 * Modrinth and Buy me a coffee, and "KoHs on top" to close.
 *
 * <p>The drawing is the site's own, taken apart into layers so each can move: the sigil's ring
 * and runes turn opposite ways, the eyes blink and flare, a glint runs down the blade of light,
 * the shards drift, and the whole figure answers the pointer with a slight parallax. A click on
 * her lights everything up at once. Carry the mascot up to her face and she gets cross, in the
 * most adorable way: a throbbing anger mark, narrowed red eyes, blushing cheeks, a huff of
 * steam and a "Hmph!".</p>
 *
 * <p>Everything is timed with {@code System.nanoTime} and runs whatever the motion settings,
 * like the rest of the mod's art. Geometry comes from {@link #parts}, which drawing and clicks
 * share; the drawing goes first when the body is too short.</p>
 */
public final class KohsScreen extends Screen {
	static final String DISCORD_URL = "https://discord.gg/9t2VxEF7UU";
	static final String SITE_URL = "https://kerlycanelita.github.io/KoHs-Mod-Suite/";
	static final String MODRINTH_URL = "https://modrinth.com/user/zymery_dria";
	static final String COFFEE_URL = "https://buymeacoffee.com/zymekohh";

	/** The drawing's own units: 400 wide; shown from y 10 to 460, where its fade ends. */
	private static final float ART_WIDTH = 400.0F;
	private static final float ART_TOP = 10.0F;
	private static final float ART_HEIGHT = 450.0F;
	private static final long ENTER_NANOS = 1_100_000_000L;
	private static final long FLARE_NANOS = 900_000_000L;
	/** Her face, in the drawing's units: between the eyes, a little under them. */
	private static final float FACE_X = 200.0F;
	private static final float FACE_Y = 196.0F;
	private static final float SLASH_X0 = 387.5F;
	private static final float SLASH_Y0 = 23.5F;
	private static final float SLASH_X1 = 28.5F;
	private static final float SLASH_Y1 = 437.5F;
	private static final String FINALE = "KOHS ON TOP";
	private static final int[] FINALE_COLORS = {0xFFFF4FB8, 0xFFE83EAF, 0xFFC084FC, 0xFFA855F7, 0xFFD8B4FE};
	/** The anger mark, 9 by 9: four little curves around an empty middle. */
	private static final String[] VEIN = {
		".XX...XX.",
		"X..X.X..X",
		"X.......X",
		".X.....X.",
		".........",
		".X.....X.",
		"X.......X",
		"X..X.X..X",
		".XX...XX.",
	};

	/** One layer of the drawing: its texture, its size in pixels, and where it sits in units. */
	private record Layer(Identifier texture, int textureWidth, int textureHeight, float x, float y, float width, float height) {
		float centerX() {
			return this.x + this.width / 2.0F;
		}

		float centerY() {
			return this.y + this.height / 2.0F;
		}
	}

	private record Rect(int x, int y, int width, int height) {
		static final Rect EMPTY = new Rect(0, 0, 0, 0);

		int right() {
			return this.x + this.width;
		}

		int bottom() {
			return this.y + this.height;
		}

		int centerX() {
			return this.x + this.width / 2;
		}

		int centerY() {
			return this.y + this.height / 2;
		}

		boolean contains(final double px, final double py) {
			return px >= this.x && px < this.right() && py >= this.y && py < this.bottom();
		}
	}

	private static final Layer AURA = layer("aura", 192, 192, 10, 6, 380, 380);
	private static final Layer SIGIL_RING = layer("sigil_ring", 448, 448, 25, 21, 350, 350);
	private static final Layer SIGIL_RUNES = layer("sigil_runes", 448, 448, 25, 21, 350, 350);
	private static final Layer SLASH = layer("slash", 496, 563, 12, 10, 388, 440);
	private static final Layer FIGURE = layer("figure", 512, 563, 0, 20, 400, 440);
	private static final Layer EYES = layer("eyes", 179, 76, 130, 160, 140, 60);
	private static final Layer EMBLEM = layer("emblem", 128, 128, 150, 332, 100, 100);
	private static final Layer[] SHARDS = {
		layer("shard0", 71, 71, 31, 91, 56, 56), layer("shard1", 71, 71, 316, 71, 56, 56),
		layer("shard2", 71, 71, 305, 233, 56, 56), layer("shard3", 71, 71, 41, 235, 56, 56),
		layer("shard4", 71, 71, 77, 31, 56, 56), layer("shard5", 71, 71, 277, 20, 56, 56)};
	/** The site's timing for each shard: its period and how far into it it starts, in seconds. */
	private static final float[] SHARD_PERIOD = {7.0F, 8.0F, 7.0F, 9.0F, 7.0F, 6.0F};
	private static final float[] SHARD_DELAY = {0.0F, 1.2F, 2.4F, 3.1F, 4.3F, 5.5F};
	private static final Identifier DISCORD_ICON = texture("discord");
	private static final Identifier KOHS_MARK = texture("kohs_mark");
	private static final Identifier MODRINTH_MARK = texture("modrinth_mark");
	private static final Identifier MODRINTH_OUTER = texture("modrinth_outer");
	private static final Identifier MODRINTH_INNER = texture("modrinth_inner");
	private static final Identifier COFFEE = texture("coffee");

	private final Screen parent;
	private final ZBackdrop backdrop = new ZBackdrop();
	private long enteredAt = System.nanoTime();
	private long flareAt = -1L;
	private long lastFrame = this.enteredAt;
	private final float[] buttonHover = new float[4];
	private final float[] chipHover = new float[6];
	private float artHover;
	private float parallaxX;
	private float parallaxY;
	private Rect body = Rect.EMPTY;
	private int panelX;
	private int panelY;
	private int panelWidth;
	private int panelHeight;
	// Her temper: how cross she is now, since when, and the steam it lets off.
	private float anger;
	private boolean cross;
	private long crossSince;
	private long lastSteam;
	private final List<float[]> steam = new ArrayList<>();
	/** Her face on screen in the last frame. */
	private float faceShownX = Float.NaN;
	private float faceShownY = Float.NaN;

	public KohsScreen(final Screen parent) {
		super(Component.translatable("screen.kohs_inventory_tweaks.kohs"));
		this.parent = parent;
	}

	private static Identifier texture(final String name) {
		return Identifier.fromNamespaceAndPath(KoHsInventoryTweaksClient.MOD_ID, "textures/gui/zymery/" + name + ".png");
	}

	private static Layer layer(final String name, final int textureWidth, final int textureHeight, final float x, final float y,
		final float width, final float height) {
		return new Layer(texture(name), textureWidth, textureHeight, x, y, width, height);
	}

	@Override
	protected void init() {
		int horizontalMargin = this.width < 420 ? 6 : this.width < 700 ? 16 : 42;
		int verticalMargin = this.height < 280 ? 6 : 14;
		this.panelWidth = Math.min(760, Math.max(1, this.width - horizontalMargin * 2));
		this.panelHeight = Math.max(1, this.height - verticalMargin * 2);
		this.panelX = (this.width - this.panelWidth) / 2;
		this.panelY = (this.height - this.panelHeight) / 2;
		// Room for the mascot's head above its floor, the Back button's row, so the closing words never stand behind it.
		ZMascot.Prefs mascot = ZMascot.prefs();
		int headroom = mascot.enabled ? Math.max(0, 20 * mascot.size - 28) + 2 : 0;
		this.body = new Rect(this.panelX + 8, this.panelY + 8, this.panelWidth - 16, Math.max(1, this.panelHeight - 44 - headroom));
		this.addRenderableWidget(new GlassButton(
			this.panelX + this.panelWidth - Math.min(104, this.panelWidth - 16),
			this.panelY + this.panelHeight - 29,
			Math.min(94, this.panelWidth - 16),
			21,
			Component.translatable("gui.back"),
			button -> this.onClose(),
			GlassButton.Variant.PRIMARY
		));
		if (this.lastFrame == this.enteredAt) {
			play("block.amethyst_block.resonate", 1.3F, 0.5F);
		}
	}

	@Override
	public void onClose() {
		this.minecraft.setScreen(this.parent);
	}

	@Override
	public void extractBackground(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float a) {
		this.backdrop.draw(graphics, this.width, this.height, mouseX, mouseY);
	}

	// ---- arrangement ---------------------------------------------------------

	/**
	 * Top to bottom: the drawing, the eyebrow, the name, the aka, what she does, the three links
	 * and the closing words. The specialties stand on both sides of the drawing when there is room.
	 */
	private record Parts(Rect art, float scale, boolean compact, int eyebrowY, int nameY, float nameScale, int akaY,
		List<FormattedCharSequence> role, int roleY, Rect[] buttons, int finaleY, float finaleScale, Rect[] chips) {
	}

	private Parts parts(final Rect body) {
		boolean compact = body.height() < 200 || body.width() < 330;
		boolean tall = body.height() >= 250;
		int eyebrow = compact ? 0 : 12;
		String name = text("screen.kohs_inventory_tweaks.kohs.name");
		float nameScale = Math.min(compact ? 1.6F : tall ? 2.2F : 2.0F, (body.width() - 12) / (float) Math.max(1, this.font.width(name)));
		int nameHeight = Math.round(9 * nameScale) + 3;
		int aka = 12;
		int textWidth = Math.min(body.width() - 16, 470);
		List<FormattedCharSequence> role = this.font.split(Component.translatable("screen.kohs_inventory_tweaks.kohs.role"), Math.max(60, textWidth));
		int buttonHeight = compact ? 14 : 16;
		// The four links, as wide as their names need and centred together; two rows of two when one row would not fit.
		String[] labels = linkLabels();
		int gap = 6;
		int[] widths = new int[4];
		int rowWidth = gap * 3;
		for (int index = 0; index < 4; index++) {
			widths[index] = this.font.width(labels[index]) + buttonHeight + 14;
			rowWidth += widths[index];
		}
		boolean twoRows = rowWidth > body.width() - 8;
		int buttonsHeight = twoRows ? buttonHeight * 2 + 4 : buttonHeight;
		float finaleScale = Math.min(compact ? 1.4F : 2.0F, (body.width() - 12) / (float) Math.max(1, finaleWidth(1.0F)));
		int finaleHeight = Math.round(9 * finaleScale) + 2;
		int maxRole = tall ? 3 : 2;
		// The eyebrow sits on the drawing's faded foot, so the drawing gets those pixels back.
		int overlap = eyebrow > 0 ? 6 : 0;
		int artHeight;
		while (true) {
			int roleLines = Math.min(maxRole, role.size());
			int text = eyebrow + nameHeight + aka + roleLines * 10 + 5 + buttonsHeight + 6 + finaleHeight;
			artHeight = Math.min(compact ? 110 : 190, body.height() - text - 6 + overlap);
			if (artHeight >= 56 || maxRole <= 1) {
				break;
			}
			maxRole--;
		}
		if (artHeight < 56) {
			artHeight = 0;
		}
		if (role.size() > maxRole) {
			// What she does, said shorter, before any of it is cut.
			List<FormattedCharSequence> brief = this.font.split(Component.translatable("screen.kohs_inventory_tweaks.kohs.role_short"),
				Math.max(60, textWidth));
			role = brief.size() <= maxRole ? brief : brief.subList(0, maxRole);
		}
		int artWidth = Math.round(artHeight * ART_WIDTH / ART_HEIGHT);
		int artSpace = artHeight > 0 ? artHeight + 4 - overlap : 0;
		int total = artSpace + eyebrow + nameHeight + aka + role.size() * 10 + 5 + buttonsHeight + 6 + finaleHeight;
		int y = body.y() + Math.max(0, (body.height() - total) / 2);
		Rect art = artHeight > 0 ? new Rect(body.centerX() - artWidth / 2, y, artWidth, artHeight) : Rect.EMPTY;
		y += artSpace;
		int eyebrowY = y;
		y += eyebrow;
		int nameY = y;
		y += nameHeight;
		int akaY = y;
		y += aka;
		int roleY = y;
		y += role.size() * 10 + 5;
		Rect[] buttons = new Rect[4];
		if (twoRows) {
			int each = Math.max(40, (Math.min(body.width() - 8, 300) - gap) / 2);
			int left = body.centerX() - (each * 2 + gap) / 2;
			for (int index = 0; index < 4; index++) {
				buttons[index] = new Rect(left + index % 2 * (each + gap), y + index / 2 * (buttonHeight + 4), each, buttonHeight);
			}
		} else {
			int x = body.centerX() - rowWidth / 2;
			for (int index = 0; index < 4; index++) {
				buttons[index] = new Rect(x, y, widths[index], buttonHeight);
				x += widths[index] + gap;
			}
		}
		y += buttonsHeight + 6;
		int finaleY = y;
		// The specialties, three on each side of the drawing, when both sides have room.
		Rect[] chips = new Rect[0];
		int side = (body.width() - artWidth) / 2 - 18;
		if (artHeight >= 80 && side >= 118) {
			chips = new Rect[6];
			int chipWidth = Math.min(side, 150);
			int chipHeight = 16;
			int chipGap = Math.min(10, Math.max(4, (artHeight - chipHeight * 3) / 4));
			int top = art.y() + (artHeight - chipHeight * 3 - chipGap * 2) / 2;
			for (int index = 0; index < 3; index++) {
				int chipY = top + index * (chipHeight + chipGap);
				chips[index] = new Rect(art.x() - 14 - chipWidth, chipY, chipWidth, chipHeight);
				chips[index + 3] = new Rect(art.right() + 14, chipY, chipWidth, chipHeight);
			}
		}
		return new Parts(art, artHeight / ART_HEIGHT, compact, eyebrowY, nameY, nameScale, akaY, role, roleY, buttons, finaleY,
			finaleScale, chips);
	}

	private static String[] linkLabels() {
		return new String[] {
			text("screen.kohs_inventory_tweaks.kohs.discord"),
			text("screen.kohs_inventory_tweaks.kohs.site"),
			text("screen.kohs_inventory_tweaks.kohs.modrinth"),
			text("screen.kohs_inventory_tweaks.kohs.coffee")
		};
	}

	// ---- input ---------------------------------------------------------------

	@Override
	public boolean mouseClicked(final MouseButtonEvent event, final boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) {
			return true;
		}
		if (event.button() != 0) {
			return false;
		}
		Parts parts = parts(this.body);
		String[] urls = {DISCORD_URL, SITE_URL, MODRINTH_URL, COFFEE_URL};
		for (int index = 0; index < 4; index++) {
			if (parts.buttons()[index].contains(event.x(), event.y())) {
				play("ui.button.click", 1.0F, 1.0F);
				ConfirmLinkScreen.confirmLinkNow(this, URI.create(urls[index]));
				return true;
			}
		}
		if (parts.art().contains(event.x(), event.y())) {
			// Her eyes flare, the blade lights up and the sigil sends out a ring.
			this.flareAt = System.nanoTime();
			play("block.amethyst_block.chime", 0.8F, 0.9F);
			play("block.respawn_anchor.charge", 1.6F, 0.35F);
			return true;
		}
		return false;
	}

	private static void play(final String id, final float pitch, final float volume) {
		net.minecraft.client.Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(
			SoundEvent.createVariableRangeEvent(Identifier.withDefaultNamespace(id)), pitch, volume));
	}

	// ---- drawing ---------------------------------------------------------------

	@Override
	public void extractRenderState(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float a) {
		long now = System.nanoTime();
		float frameMillis = Math.min(50.0F, (now - this.lastFrame) / 1_000_000.0F);
		this.lastFrame = now;
		float response = 1.0F - (float) Math.exp(-frameMillis / 70.0F);
		double seconds = now / 1_000_000_000.0D;
		float since = (now - this.enteredAt) / 1_000_000_000.0F;
		UiRender.panel(graphics, this.panelX, this.panelY, this.panelWidth, this.panelHeight, 10, UiTheme.GLASS, UiTheme.BORDER);
		Parts parts = parts(this.body);
		Rect art = parts.art();

		// The pointer tilts the drawing a little: the far layers less than the figure.
		boolean overArt = art.contains(mouseX, mouseY);
		this.artHover += ((overArt ? 1.0F : 0.0F) - this.artHover) * response;
		float targetX = 0.0F;
		float targetY = 0.0F;
		if (art.width() > 0 && mouseX >= 0) {
			targetX = ZMotion.clamp01((mouseX - this.body.x()) / (float) Math.max(1, this.body.width())) * 2.0F - 1.0F;
			targetY = ZMotion.clamp01((mouseY - this.body.y()) / (float) Math.max(1, this.body.height())) * 2.0F - 1.0F;
		}
		this.parallaxX += (targetX - this.parallaxX) * response * 0.5F;
		this.parallaxY += (targetY - this.parallaxY) * response * 0.5F;

		if (art.width() > 0) {
			this.drawArt(graphics, parts, seconds, since, now, frameMillis / 1000.0F);
		}
		this.drawChips(graphics, parts, mouseX, mouseY, since, response, seconds);
		this.drawText(graphics, parts, since, seconds);
		this.drawButtons(graphics, parts, mouseX, mouseY, since, response, seconds);
		this.drawFinale(graphics, parts, since, seconds);
		super.extractRenderState(graphics, mouseX, mouseY, a);
		ZChrome.openingVeil(graphics, this.width, this.height, ZMotion.progress(this.enteredAt, 280_000_000L));
	}

	/** How far into its own entrance an element is, from its start time in seconds. */
	private static float appear(final float since, final float start, final float length) {
		return ZMotion.easeOutCubic(ZMotion.clamp01((since - start) / length));
	}

	private void drawArt(final GuiGraphicsExtractor graphics, final Parts parts, final double time, final float since, final long now,
		final float dt) {
		Rect art = parts.art();
		float scale = parts.scale();
		float originX = art.x();
		float originY = art.y() - ART_TOP * scale;
		float flare = this.flareAt < 0L ? 0.0F : 1.0F - ZMotion.clamp01((now - this.flareAt) / (float) FLARE_NANOS);
		float glow = Math.max(flare, 0.35F * this.artHover);
		float bob = (float) Math.sin(time * Math.PI * 2.0D / 5.0D) * 1.2F;
		float figureIn = appear(since, 0.12F, 0.7F);
		float lift = ((1.0F - figureIn) * 14.0F + bob) * scale;

		// The mascot carried up to her face makes her cross; she calms down once it is gone.
		float faceX = originX + (FACE_X + this.parallaxX * 3.0F) * scale;
		float faceY = originY + FACE_Y * scale + lift;
		this.faceShownX = faceX;
		this.faceShownY = faceY;
		float[] cat = ZMascot.carriedHead();
		boolean near = cat != null && Math.hypot(cat[0] - faceX, cat[1] - faceY) < 78.0F * scale + 14.0F;
		this.anger += ((near ? 1.0F : 0.0F) - this.anger) * (1.0F - (float) Math.exp(-dt * (near ? 9.0F : 1.6F)));
		if (!this.cross && this.anger > 0.5F) {
			this.cross = true;
			this.crossSince = now;
			play("entity.allay.hurt", 1.55F, 0.4F);
			play("block.note_block.bit", 0.7F, 0.25F);
		} else if (this.cross && this.anger < 0.2F) {
			this.cross = false;
		}
		float temper = ZMotion.smoothstep(this.anger);
		// A quick shake when she first gets cross, then a small huffing bob.
		float onset = this.cross ? ZMotion.clamp01(1.0F - (now - this.crossSince) / 450_000_000.0F) : 0.0F;
		float shake = (float) Math.sin(time * Math.PI * 2.0D * 14.0D) * 2.2F * onset * temper
			+ (float) Math.sin(time * Math.PI * 2.0D * 2.4D) * 0.6F * temper;

		graphics.nextStratum();
		graphics.enableScissor(art.x() - 2, art.y() - 2, art.right() + 2, art.bottom());
		// The aura breathes; cross, it warms toward red.
		float auraIn = appear(since, 0.0F, 0.6F);
		float breathe = 0.84F + 0.16F * (float) Math.sin(time * Math.PI * 2.0D / 4.0D);
		float auraScale = 0.86F + 0.14F * auraIn + 0.02F * (float) Math.sin(time * 1.3D);
		this.drawLayer(graphics, AURA, originX, originY, scale, 0.6F, 0.0F, auraScale, auraScale,
			ZTheme.mix(ZTheme.fade(0xFFFFFFFF, auraIn * Math.min(1.0F, breathe + glow * 0.3F)), 0xFFFF6A8A, temper * 0.5F));
		// The sigil: the ring one way, the runes the other; a burst of speed as it appears.
		float sigilIn = appear(since, 0.08F, 0.8F);
		double burst = (1.0D - sigilIn) * Math.PI * 0.9D + temper * time * 1.4D;
		float sigilScale = 1.14F - 0.14F * sigilIn;
		this.drawLayer(graphics, SIGIL_RING, originX, originY, scale, 1.2F, (float) (time * Math.PI * 2.0D / 80.0D + burst),
			sigilScale, sigilScale, ZTheme.fade(0xFFFFFFFF, sigilIn));
		this.drawLayer(graphics, SIGIL_RUNES, originX, originY, scale, 1.6F, (float) (-time * Math.PI * 2.0D / 120.0D - burst),
			sigilScale, sigilScale, ZTheme.fade(0xFFFFFFFF, sigilIn * (0.8F + 0.2F * glow)));
		if (flare > 0.0F) {
			// The flare's ring leaves the sigil.
			float ring = 1.0F - flare;
			ring(graphics, originX + 200 * scale, originY + 196 * scale, (150 + 60 * ring) * scale, ZTheme.alpha(0xFF4FB8, Math.round(200 * flare)));
		}
		// The blade of light, pulsing, with a glint running down it now and then.
		float slashIn = appear(since, 0.25F, 0.35F);
		float slashPulse = 0.65F + 0.2F * (float) Math.sin(time * Math.PI * 2.0D / 6.0D);
		this.drawLayer(graphics, SLASH, originX, originY, scale, 2.2F, 0.0F, 1.0F, 1.0F,
			ZTheme.fade(0xFFFFFFFF, slashIn * Math.min(1.0F, slashPulse + glow)));
		// The figure, rising into place and breathing.
		float shifted = originX + shake * scale;
		this.drawLayer(graphics, FIGURE, shifted, originY + lift, scale, 3.0F, 0.0F, 1.0F, 1.0F, ZTheme.fade(0xFFFFFFFF, figureIn));
		// Her eyes light up last, blink every five seconds and flare on a click; cross, they narrow and redden.
		float eyesIn = appear(since, 0.55F, 0.25F);
		float blink = 1.0F;
		double phase = (time % 5.0D) / 5.0D;
		if (phase > 0.46D && phase < 0.5D && temper < 0.5F) {
			blink = phase < 0.48D ? (float) (1.0D - (phase - 0.46D) / 0.02D * 0.85D) : (float) (0.15D + (phase - 0.48D) / 0.02D * 0.85D);
		}
		float eyes = eyesIn * blink;
		int eyeTint = ZTheme.mix(0xFFFFFFFF, 0xFFFF4D6D, temper);
		float narrow = 1.0F - 0.42F * temper;
		this.drawLayer(graphics, EYES, shifted, originY + lift, scale, 3.0F, 0.0F, 1.0F, narrow, ZTheme.fade(eyeTint, eyes));
		if (glow > 0.02F || temper > 0.05F || since > 0.55F && since < 0.9F) {
			// A second pass brightens them: the ignition, a hover, a flare, a temper.
			float ignite = Math.max(0.0F, 1.0F - Math.abs(since - 0.7F) / 0.2F);
			float bright = Math.max(Math.max(glow, ignite), temper * 0.8F);
			this.drawLayer(graphics, EYES, shifted, originY + lift, scale, 3.0F, 0.0F, 1.0F + 0.08F * bright, narrow * (1.0F + 0.08F * bright),
				ZTheme.fade(eyeTint, eyes * bright));
		}
		// The code emblem pulses.
		float emblemPulse = 0.86F + 0.14F * (float) Math.sin(time * Math.PI * 2.0D / 3.2D);
		this.drawLayer(graphics, EMBLEM, shifted, originY + lift, scale, 3.0F, 0.0F, 0.98F + 0.04F * emblemPulse, 0.98F + 0.04F * emblemPulse,
			ZTheme.fade(0xFFFFFFFF, figureIn * emblemPulse));
		// The shards drift up and turn, each on its own clock.
		for (int index = 0; index < SHARDS.length; index++) {
			float shardIn = appear(since, 0.3F + index * 0.06F, 0.4F);
			double shardPhase = (time + SHARD_DELAY[index]) / SHARD_PERIOD[index];
			float drift = (float) (0.5D - 0.5D * Math.cos(shardPhase * Math.PI * 2.0D));
			this.drawLayer(graphics, SHARDS[index], originX, originY - drift * 10.0F * scale, scale, 2.6F,
				(float) Math.toRadians(14.0D * drift), 1.0F + 0.3F * flare, 1.0F + 0.3F * flare, ZTheme.fade(0xFFFFFFFF, shardIn));
		}
		if (temper > 0.02F) {
			this.drawTemper(graphics, originX + shake * scale, originY + lift, scale, time, temper, now);
		}
		graphics.disableScissor();
		graphics.nextStratum();
		this.glint(graphics, originX, originY, scale, time, flare, slashIn);
		sparks(graphics, art, time, auraIn);
		if (temper > 0.3F) {
			this.drawHmph(graphics, faceX, faceY, scale, temper, now);
		}
	}

	/** Cross, and adorable about it: blushing cheeks, a throbbing anger mark and puffs of steam. */
	private void drawTemper(final GuiGraphicsExtractor graphics, final float originX, final float originY, final float scale,
		final double time, final float temper, final long now) {
		// Pink cheeks under the eyes.
		for (float cheek : new float[] {163.0F, 237.0F}) {
			ZScene.sprite(graphics, "glow", originX + cheek * scale, originY + 216.0F * scale, Math.max(12.0F, 70.0F * scale), 8,
				ZTheme.fade(0xFFFF6FA8, 0.95F * temper));
		}
		// The anger mark on her hood, throbbing.
		float throb = 1.0F + 0.16F * (float) Math.sin(time * Math.PI * 2.0D * 3.0D) * temper;
		float cell = Math.max(2.0F, 6.5F * scale) * throb * (0.6F + 0.4F * temper);
		float markX = originX + 272.0F * scale - cell * 4.5F;
		float markY = originY + 104.0F * scale - cell * 4.5F;
		int vein = ZTheme.fade(0xFFFF3D66, temper);
		int veinLight = ZTheme.fade(0xFFFFB0C4, temper);
		for (int row = 0; row < VEIN.length; row++) {
			for (int column = 0; column < VEIN[row].length(); column++) {
				if (VEIN[row].charAt(column) == 'X') {
					int x0 = Math.round(markX + column * cell);
					int y0 = Math.round(markY + row * cell);
					int x1 = Math.round(markX + (column + 1) * cell);
					int y1 = Math.round(markY + (row + 1) * cell);
					graphics.fill(x0, y0, x1, y1, vein);
					if (x1 - x0 >= 3) {
						graphics.fill(x0, y0, x0 + (x1 - x0) / 3, y0 + (y1 - y0) / 3, veinLight);
					}
				}
			}
		}
		// Steam from the top of her hood.
		if (temper > 0.6F && now - this.lastSteam > 260_000_000L) {
			this.lastSteam = now;
			boolean left = this.steam.size() % 2 == 0;
			this.steam.add(new float[] {originX + (left ? 168.0F : 236.0F) * scale, originY + 56.0F * scale, now, left ? -1.0F : 1.0F});
		}
		this.steam.removeIf(puff -> now - (long) puff[2] > 900_000_000L);
		for (float[] puff : this.steam) {
			float life = (now - (long) puff[2]) / 900_000_000.0F;
			int frame = Math.min(7, (int) (life * 8));
			float px = puff[0] + puff[3] * life * 22.0F * scale;
			float py = puff[1] - life * 46.0F * scale;
			ZScene.sprite(graphics, "generic_" + (7 - frame), px, py, (18.0F + 22.0F * life) * scale, 8,
				ZTheme.fade(0xFFFFF0F6, (1.0F - life) * temper));
		}
	}

	/** A little bubble beside her face: "Hmph!". */
	private void drawHmph(final GuiGraphicsExtractor graphics, final float faceX, final float faceY, final float scale, final float temper,
		final long now) {
		String word = text("screen.kohs_inventory_tweaks.kohs.hmph");
		int width = this.font.width(word) + 10;
		int height = 14;
		float pop = this.cross ? ZMotion.easeOutBack(ZMotion.clamp01((now - this.crossSince) / 220_000_000.0F)) : 1.0F;
		int x = Math.round(faceX - 120.0F * scale - width);
		int y = Math.round(faceY - 40.0F * scale - height);
		x = Math.max(this.body.x() + 2, x);
		graphics.pose().pushMatrix();
		graphics.pose().translate(x + width, y + height);
		graphics.pose().scale(pop, pop);
		graphics.pose().translate(-(x + width), -(y + height));
		ZDraw.glass(graphics, x, y, width, height, ZTheme.fade(ZTheme.SURFACE, temper), ZTheme.fade(0xFFFF6A8A, temper));
		graphics.fill(x + width - 4, y + height, x + width - 1, y + height + 2, ZTheme.fade(0xFFFF6A8A, temper));
		graphics.text(this.font, word, x + 5, y + 3, ZTheme.fade(0xFFFFD6E2, temper), false);
		graphics.pose().popMatrix();
	}

	/**
	 * One layer at its place. {@code depth} is how far the pointer moves it, in units; {@code angle}
	 * and the two {@code grow}s turn and scale it around its own centre.
	 */
	private void drawLayer(final GuiGraphicsExtractor graphics, final Layer layer, final float originX, final float originY, final float scale,
		final float depth, final float angle, final float growX, final float growY, final int color) {
		if (((color >>> 24) & 255) < 4) {
			return;
		}
		float centerX = originX + (layer.centerX() + this.parallaxX * depth) * scale;
		float centerY = originY + (layer.centerY() + this.parallaxY * depth * 0.6F) * scale;
		graphics.pose().pushMatrix();
		graphics.pose().translate(centerX, centerY);
		if (angle != 0.0F) {
			graphics.pose().rotate(angle);
		}
		graphics.pose().scale(layer.width() * scale * growX / layer.textureWidth(), layer.height() * scale * growY / layer.textureHeight());
		graphics.blit(RenderPipelines.GUI_TEXTURED, layer.texture(), -layer.textureWidth() / 2, -layer.textureHeight() / 2, 0.0F,
			0.0F, layer.textureWidth(), layer.textureHeight(), layer.textureWidth(), layer.textureHeight(),
			layer.textureWidth(), layer.textureHeight(), color);
		graphics.pose().popMatrix();
	}

	private static void ring(final GuiGraphicsExtractor graphics, final float cx, final float cy, final float radius, final int color) {
		int steps = Math.max(24, Math.round(radius * 3.0F));
		for (int step = 0; step < steps; step++) {
			double angle = step * Math.PI * 2.0D / steps;
			int x = Math.round(cx + (float) Math.cos(angle) * radius);
			int y = Math.round(cy + (float) Math.sin(angle) * radius);
			graphics.fill(x, y, x + 2, y + 2, color);
		}
	}

	/** A point of light running down the blade every few seconds, or at once on a flare. */
	private void glint(final GuiGraphicsExtractor graphics, final float originX, final float originY, final float scale, final double time,
		final float flare, final float alpha) {
		double period = 4.5D;
		double run = 0.55D;
		double phase = time % period;
		float t = flare > 0.0F ? 1.0F - flare : (float) (phase / run);
		if (t < 0.0F || t > 1.0F || alpha <= 0.05F) {
			return;
		}
		float eased = (float) (-(Math.cos(Math.PI * t) - 1.0D) / 2.0D);
		for (int step = 0; step < 10; step++) {
			float s = eased - step * 0.012F;
			if (s < 0.0F) {
				break;
			}
			float ux = SLASH_X0 + (SLASH_X1 - SLASH_X0) * s + this.parallaxX * 2.2F;
			float uy = SLASH_Y0 + (SLASH_Y1 - SLASH_Y0) * s + this.parallaxY * 1.3F;
			int x = Math.round(originX + ux * scale);
			int y = Math.round(originY + uy * scale);
			int size = step == 0 ? 3 : step < 4 ? 2 : 1;
			int tone = ZTheme.alpha(step == 0 ? 0xFFFFFF : 0xFFD1EC, Math.round(255 * alpha * (1.0F - step / 10.0F)));
			graphics.fill(x - size / 2, y - size / 2, x - size / 2 + size, y - size / 2 + size, tone);
		}
	}

	/** Pink and violet sparks rising around her. */
	private static void sparks(final GuiGraphicsExtractor graphics, final Rect art, final double time, final float alpha) {
		if (alpha <= 0.05F) {
			return;
		}
		int span = art.height() + 10;
		for (int index = 0; index < 14; index++) {
			double speed = 9.0D + index % 5 * 3.0D;
			double phase = index * 0.618D;
			int x = art.x() + Math.floorMod(index * 53 + 17, Math.max(1, art.width())) + (int) Math.round(Math.sin(time * 0.9D + phase * 5.0D) * 3.0D);
			int y = art.bottom() - (int) ((time * speed + phase * span) % span);
			float life = 1.0F - (art.bottom() - y) / (float) span;
			int color = index % 3 == 0 ? 0xFF4FB8 : index % 3 == 1 ? 0xC084FC : 0xF5D0FE;
			int size = index % 4 == 0 ? 2 : 1;
			graphics.fill(x, y, x + size, y + size, ZTheme.alpha(color, Math.round(200 * alpha * life)));
		}
	}

	private void drawChips(final GuiGraphicsExtractor graphics, final Parts parts, final int mouseX, final int mouseY, final float since,
		final float response, final double seconds) {
		Rect[] chips = parts.chips();
		for (int index = 0; index < chips.length; index++) {
			Rect chip = chips[index];
			boolean left = index < 3;
			float in = appear(since, 0.35F + (index % 3) * 0.08F, 0.45F);
			if (in <= 0.02F) {
				continue;
			}
			this.chipHover[index] += ((chip.contains(mouseX, mouseY) ? 1.0F : 0.0F) - this.chipHover[index]) * response;
			float hover = this.chipHover[index];
			int slide = Math.round((1.0F - in) * 18.0F) * (left ? -1 : 1);
			int x = chip.x() + slide;
			ZDraw.glass(graphics, x, chip.y(), chip.width(), chip.height(),
				ZTheme.fade(ZTheme.mix(0x901D0D32, 0xC02A1248, hover), in), ZTheme.fade(ZTheme.mix(0x9A6A2A9A, 0xE0FF8AD8, hover), in));
			// A slow scan of light along each chip, one after the other.
			double scan = ((seconds * 0.35D) + index / 6.0D) % 1.0D;
			int scanX = x + (int) Math.round(scan * (chip.width() + 20)) - 10;
			graphics.enableScissor(x + 1, chip.y() + 1, x + chip.width() - 1, chip.bottom() - 1);
			graphics.fill(scanX, chip.y() + 1, scanX + 2, chip.bottom() - 1, ZTheme.alpha(0xF5D0FE, Math.round(30 * in)));
			graphics.disableScissor();
			int diamondX = left ? x + chip.width() - 8 : x + 7;
			ZDraw.diamond(graphics, diamondX, chip.centerY(), 2, ZTheme.fade(0xFFFF4FB8, in));
			String label = ellipsis(text("screen.kohs_inventory_tweaks.kohs.skill." + (index + 1)), chip.width() - 20);
			int textX = left ? x + chip.width() - 14 - this.font.width(label) : x + 14;
			graphics.text(this.font, label, textX, chip.y() + (chip.height() - 8) / 2,
				ZTheme.fade(ZTheme.mix(ZTheme.TEXT_MUTED, ZTheme.TEXT, hover), in), false);
		}
	}

	private void drawText(final GuiGraphicsExtractor graphics, final Parts parts, final float since, final double seconds) {
		int centerX = this.body.centerX();
		if (!parts.compact()) {
			float in = appear(since, 0.3F, 0.35F);
			String eyebrow = "◆ " + text("screen.kohs_inventory_tweaks.kohs.eyebrow").toUpperCase(Locale.ROOT);
			graphics.text(this.font, eyebrow, centerX - this.font.width(eyebrow) / 2, parts.eyebrowY() + Math.round((1.0F - in) * 4),
				ZTheme.fade(0xFFFF8AD8, in), false);
		}
		// The name decodes itself: letters run through signs until each settles.
		String name = text("screen.kohs_inventory_tweaks.kohs.name").toUpperCase(Locale.ROOT);
		float decode = appear(since, 0.35F, 0.55F);
		String shown = name;
		if (decode < 1.0F) {
			String signs = "<>/\\#*+=ZYMEKOHS";
			StringBuilder scrambled = new StringBuilder(name.length());
			long tick = (long) (since * 30.0F);
			for (int index = 0; index < name.length(); index++) {
				char real = name.charAt(index);
				boolean settled = real == ' ' || decode > (index + 1) / (float) (name.length() + 1);
				scrambled.append(settled ? real : signs.charAt((int) Math.floorMod(tick * 31 + index * 17L, signs.length())));
			}
			shown = scrambled.toString();
		}
		float nameIn = appear(since, 0.35F, 0.3F);
		this.bigText(graphics, shown, centerX + 1, parts.nameY() + 1, parts.nameScale(), ZTheme.fade(0xFF6B0F5A, nameIn));
		this.bigText(graphics, shown, centerX, parts.nameY(), parts.nameScale(), ZTheme.fade(ZTheme.TEXT, nameIn));
		if (decode >= 1.0F) {
			// A band of light runs across the settled name.
			float width = this.font.width(name) * parts.nameScale();
			float band = (float) ((seconds * 0.45D) % 1.6D) - 0.3F;
			float x = centerX - width / 2.0F;
			for (int index = 0; index < name.length(); index++) {
				String letter = String.valueOf(name.charAt(index));
				float letterWidth = this.font.width(letter) * parts.nameScale();
				float at = (x + letterWidth / 2.0F - (centerX - width / 2.0F)) / Math.max(1.0F, width);
				float light = Math.max(0.0F, 1.0F - Math.abs(at - band) / 0.12F);
				if (light > 0.02F) {
					graphics.pose().pushMatrix();
					graphics.pose().translate(x, parts.nameY());
					graphics.pose().scale(parts.nameScale(), parts.nameScale());
					graphics.text(this.font, letter, 0, 0, ZTheme.alpha(0xFFFFFF, Math.round(200 * light)), false);
					graphics.pose().popMatrix();
				}
				x += letterWidth;
			}
		}
		// a.k.a. kohzemyora: the other name, from pink to lilac.
		float akaIn = appear(since, 0.55F, 0.35F);
		String prefix = text("screen.kohs_inventory_tweaks.kohs.aka") + " ";
		String first = "kohze";
		String second = "myora";
		int width = this.font.width(prefix + first + second);
		int x = centerX - width / 2;
		int y = parts.akaY() + Math.round((1.0F - akaIn) * 4);
		graphics.text(this.font, prefix, x, y, ZTheme.fade(ZTheme.TEXT_DISABLED, akaIn), false);
		x += this.font.width(prefix);
		graphics.text(this.font, first, x, y, ZTheme.fade(0xFFFF8AD8, akaIn), false);
		x += this.font.width(first);
		graphics.text(this.font, second, x, y, ZTheme.fade(0xFFC084FC, akaIn), false);
		// What she does.
		int lineY = parts.roleY();
		for (int index = 0; index < parts.role().size(); index++) {
			float in = appear(since, 0.65F + index * 0.07F, 0.35F);
			FormattedCharSequence line = parts.role().get(index);
			graphics.text(this.font, line, centerX - this.font.width(line) / 2, lineY + Math.round((1.0F - in) * 4),
				ZTheme.fade(ZTheme.TEXT_MUTED, in), false);
			lineY += 10;
		}
	}

	private void bigText(final GuiGraphicsExtractor graphics, final String text, final int centerX, final int y, final float scale, final int color) {
		if (((color >>> 24) & 255) < 8) {
			return;
		}
		float width = this.font.width(text) * scale;
		graphics.pose().pushMatrix();
		graphics.pose().translate(centerX - width / 2.0F, y);
		graphics.pose().scale(scale, scale);
		graphics.text(this.font, text, 0, 0, color, false);
		graphics.pose().popMatrix();
	}

	private void drawButtons(final GuiGraphicsExtractor graphics, final Parts parts, final int mouseX, final int mouseY, final float since,
		final float response, final double seconds) {
		String[] labels = linkLabels();
		int[] glows = {0xFF5865F2, 0xFFFF4FB8, 0xFF1BD96A, 0xFFFFC85A};
		for (int index = 0; index < 4; index++) {
			Rect button = parts.buttons()[index];
			float in = appear(since, 0.8F + index * 0.07F, 0.35F);
			if (in <= 0.02F) {
				continue;
			}
			this.buttonHover[index] += ((button.contains(mouseX, mouseY) ? 1.0F : 0.0F) - this.buttonHover[index]) * response;
			float hover = this.buttonHover[index];
			if (hover > 0.05F) {
				ZDraw.halo(graphics, button.x(), button.y(), button.width(), button.height(), glows[index], Math.round(150 * hover * in));
			}
			if (index == 3) {
				// Buy me a coffee wears warm gold over the purple.
				ZDraw.glass(graphics, button.x(), button.y(), button.width(), button.height(),
					ZTheme.fade(ZTheme.mix(0xF0503012, 0xF0805018, hover), in), ZTheme.fade(ZTheme.mix(0xFFC89040, 0xFFFFC85A, hover), in));
			} else {
				ZDraw.glass(graphics, button.x(), button.y(), button.width(), button.height(),
					ZTheme.fade(ZTheme.mix(index == 1 ? 0xF0331760 : ZTheme.SURFACE_LIGHT, ZTheme.SURFACE_HOVER, hover), in),
					ZTheme.fade(ZTheme.mix(ZTheme.BORDER, ZTheme.BORDER_HOT, hover), in));
			}
			int icon = button.height() - 6;
			String label = ellipsis(labels[index], button.width() - icon - 12);
			int contentWidth = icon + 4 + this.font.width(label);
			int x = button.x() + (button.width() - contentWidth) / 2;
			int iconY = button.y() + 3;
			switch (index) {
				case 0 -> icon(graphics, DISCORD_ICON, 64, x, iconY, icon, 0.0F, ZTheme.fade(ZTheme.mix(0xFFB4BBFF, 0xFFFFFFFF, hover), in));
				case 1 -> icon(graphics, KOHS_MARK, 96, x, iconY, icon, 0.0F, ZTheme.fade(0xFFFFFFFF, in));
				case 3 -> {
					// The cup rocks a little and steams.
					float rock = (float) Math.sin(seconds * 2.4D) * 0.08F * (0.4F + hover);
					icon(graphics, COFFEE, 32, x, iconY, icon, rock, ZTheme.fade(0xFFFFFFFF, in));
					for (int puff = 0; puff < 3; puff++) {
						double rise = (seconds * 0.9D + puff / 3.0D) % 1.0D;
						int px = Math.round(x + icon / 2.0F - 2 + puff * 2 + (float) Math.sin(seconds * 3.0D + puff) * 1.2F);
						int py = Math.round(button.y() + 1 - (float) rise * 5.0F);
						graphics.fill(px, py, px + 1, py + 1, ZTheme.fade(0xFFFFF4E6, (1.0F - (float) rise) * 0.63F * in));
					}
				}
				default -> {
					int green = ZTheme.fade(ZTheme.mix(0xFF1BD96A, 0xFF9CFFC6, hover), in);
					float turn = (float) (seconds / 4.0D * Math.PI * 2.0D) * (0.3F + hover);
					icon(graphics, MODRINTH_OUTER, 256, x, iconY, icon, turn, green);
					icon(graphics, MODRINTH_INNER, 256, x, iconY, icon, -turn * 0.66F, green);
					icon(graphics, MODRINTH_MARK, 256, x, iconY, icon, 0.0F, green);
				}
			}
			graphics.text(this.font, label, x + icon + 4, button.y() + (button.height() - 8) / 2,
				ZTheme.fade(ZTheme.mix(ZTheme.TEXT, 0xFFFFFFFF, hover), in), true);
		}
	}

	private static void icon(final GuiGraphicsExtractor graphics, final Identifier texture, final int textureSize, final int x, final int y,
		final int size, final float angle, final int color) {
		graphics.pose().pushMatrix();
		graphics.pose().translate(x + size / 2.0F, y + size / 2.0F);
		if (angle != 0.0F) {
			graphics.pose().rotate(angle);
		}
		graphics.pose().scale(size / (float) textureSize, size / (float) textureSize);
		graphics.blit(RenderPipelines.GUI_TEXTURED, texture, -textureSize / 2, -textureSize / 2, 0.0F, 0.0F, textureSize,
			textureSize, textureSize, textureSize, textureSize, textureSize, color);
		graphics.pose().popMatrix();
	}

	/** The width of "KOHS ON TOP" at {@code scale}, letter by letter with a pixel between. */
	private int finaleWidth(final float scale) {
		int width = 0;
		for (int index = 0; index < FINALE.length(); index++) {
			width += this.font.width(String.valueOf(FINALE.charAt(index))) + 1;
		}
		return Math.round((width - 1) * scale);
	}

	/**
	 * "KOHS ON TOP": the letters rise in one by one, then ride a slow wave in a gradient from
	 * magenta to lilac; every few seconds the line glitches for a moment, as the site's title does.
	 */
	private void drawFinale(final GuiGraphicsExtractor graphics, final Parts parts, final float since, final double time) {
		float scale = parts.finaleScale();
		int total = this.finaleWidth(scale);
		float x = this.body.centerX() - total / 2.0F;
		int y = parts.finaleY();
		double glitchPhase = time % 3.4D;
		boolean glitch = glitchPhase < 0.14D && since > 1.6F;
		float jitter = glitch ? (float) Math.sin(time * 90.0D) * 1.5F : 0.0F;
		graphics.nextStratum();
		for (int index = 0; index < FINALE.length(); index++) {
			String letter = String.valueOf(FINALE.charAt(index));
			float in = appear(since, 0.95F + index * 0.045F, 0.3F);
			if (letter.equals(" ") || in <= 0.02F) {
				x += (this.font.width(letter) + 1) * scale;
				continue;
			}
			float wave = (float) Math.sin(time * 3.2D - index * 0.55D) * 1.4F;
			float rise = (1.0F - in) * 8.0F;
			float shift = (float) ((time * 0.25D + index / (double) FINALE.length()) % 1.0D);
			int color = gradient(shift);
			float letterY = y + wave + rise;
			if (glitch) {
				// Two torn copies, magenta and cyan, either side.
				this.drawLetter(graphics, letter, x - 1.5F + jitter, letterY, scale, ZTheme.fade(0xB0FF4FB8, in));
				this.drawLetter(graphics, letter, x + 1.5F - jitter, letterY, scale, ZTheme.fade(0xB052F2FF, in));
			}
			this.drawLetter(graphics, letter, x + 1.0F, letterY + 1.0F, scale, ZTheme.fade(0xFF3B0A5A, in));
			this.drawLetter(graphics, letter, x, letterY, scale, ZTheme.fade(color, in));
			x += (this.font.width(letter) + 1) * scale;
		}
		graphics.nextStratum();
	}

	private void drawLetter(final GuiGraphicsExtractor graphics, final String letter, final float x, final float y, final float scale, final int color) {
		if (((color >>> 24) & 255) < 8) {
			return;
		}
		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y);
		graphics.pose().scale(scale, scale);
		graphics.text(this.font, letter, 0, 0, color, false);
		graphics.pose().popMatrix();
	}

	/** A colour along the finale's gradient, looping. */
	private static int gradient(final float position) {
		float scaled = position * FINALE_COLORS.length;
		int from = (int) Math.floor(scaled) % FINALE_COLORS.length;
		int to = (from + 1) % FINALE_COLORS.length;
		return ZTheme.mix(FINALE_COLORS[from], FINALE_COLORS[to], scaled - (float) Math.floor(scaled));
	}

	private String ellipsis(final String text, final int width) {
		if (this.font.width(text) <= width) {
			return text;
		}
		String dots = "…";
		return this.font.plainSubstrByWidth(text, Math.max(0, width - this.font.width(dots))) + dots;
	}

	private static String text(final String key) {
		return Component.translatable(key).getString();
	}
}
