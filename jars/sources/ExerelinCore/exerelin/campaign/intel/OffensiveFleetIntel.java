package exerelin.campaign.intel;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import static com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin.getDaysString;
import com.fs.starfarer.api.impl.campaign.intel.raid.ActionStage;
import com.fs.starfarer.api.impl.campaign.intel.raid.BaseRaidStage;
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidIntel;
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidIntel.RaidDelegate;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.AllianceManager;
import exerelin.campaign.DiplomacyManager;
import exerelin.campaign.PlayerFactionStore;
import exerelin.campaign.fleets.InvasionFleetManager;
import exerelin.utilities.StringHelper;
import java.awt.Color;
import java.util.List;
import java.util.Set;
import org.apache.log4j.Logger;

public abstract class OffensiveFleetIntel extends RaidIntel implements RaidDelegate {
	
	public static final Object ENTERED_SYSTEM_UPDATE = new Object();
	public static final Object OUTCOME_UPDATE = new Object();
	public static final boolean DEBUG_MODE = true;
	public static final boolean INTEL_ALWAYS_VISIBLE = true;
	
	public static Logger log = Global.getLogger(OffensiveFleetIntel.class);
	
	protected MarketAPI from;
	protected MarketAPI target;
	protected FactionAPI targetFaction;
	protected OffensiveOutcome outcome;
	protected boolean isRespawn = false;
	protected boolean intelQueuedOrAdded;
	protected float fp;
	protected float orgDur;
	
	protected ActionStage action;
	
	public static enum OffensiveOutcome {
		TASK_FORCE_DEFEATED,
		MARKET_NO_LONGER_EXISTS,
		SUCCESS,
		FAIL,
		NO_LONGER_HOSTILE,
		OTHER;
		
		public boolean isFailed() {
			return this == TASK_FORCE_DEFEATED || this == FAIL;
		}
	}
	
	public OffensiveFleetIntel(FactionAPI attacker, MarketAPI from, MarketAPI target, float fp, float orgDur) {
		super(target.getStarSystem(), attacker, null);
		
		this.target = target;
		this.delegate = this;
		this.from = from;
		this.target = target;
		this.fp = fp;
		this.orgDur = orgDur;
		targetFaction = target.getFaction();
	}
	
	public void init() {
	}
	
	protected void queueIntelIfNeeded()
	{
		if (intelQueuedOrAdded) return;
		Global.getSector().getIntelManager().queueIntel(this);
		intelQueuedOrAdded = true;
	}
	
	protected void addIntelIfNeeded()
	{
		if (intelQueuedOrAdded) return;
		Global.getSector().getIntelManager().addIntel(this);
		intelQueuedOrAdded = true;
	}
	
	protected boolean shouldDisplayIntel()
	{
		if (INTEL_ALWAYS_VISIBLE) return true;
		if (Global.getSettings().isDevMode()) return true;
		LocationAPI loc = from.getContainingLocation();
		if (faction.isPlayerFaction()) return true;		
		if (AllianceManager.areFactionsAllied(faction.getId(), PlayerFactionStore.getPlayerFactionId()))
			return true;
		
		List<SectorEntityToken> sniffers = Global.getSector().getIntel().getCommSnifferLocations();
		for (SectorEntityToken relay : sniffers)
		{
			if (relay.getContainingLocation() == loc)
				return true;
		}
		return false;
	}
	
	public OffensiveOutcome getOutcome() {
		return outcome;
	}
	
	public float getFP() {
		return fp;
	}
	
	@Override
	public void notifyRaidEnded(RaidIntel raid, RaidStageStatus status) {
		log.info("Notifying raid ended: " + status + ", " + outcome);
		if (outcome == null) {
			if (status == RaidStageStatus.SUCCESS)
				outcome = OffensiveOutcome.SUCCESS;
			else
				outcome = OffensiveOutcome.FAIL;
		}
		
		if (outcome.isFailed())
		{
			float impact = fp/2;
			if (this.getCurrentStage() >= 2) impact *= 2;
			DiplomacyManager.getManager().modifyWarWeariness(faction.getId(), impact);
		}
	}

	public void sendOutcomeUpdate() {
		addIntelIfNeeded();
		sendUpdateIfPlayerHasIntel(OUTCOME_UPDATE, false);
	}
	
	public void sendEnteredSystemUpdate() {
		queueIntelIfNeeded();
		sendUpdateIfPlayerHasIntel(ENTERED_SYSTEM_UPDATE, false);
	}
	
	@Override
	public void sendUpdateIfPlayerHasIntel(Object listInfoParam, boolean onlyIfImportant, boolean sendIfHidden) {
		if (listInfoParam == UPDATE_RETURNING) {
			// we're using sendOutcomeUpdate() to send an end-of-event update instead
			return;
		}
		super.sendUpdateIfPlayerHasIntel(listInfoParam, onlyIfImportant, sendIfHidden);
	}
	
	// for intel popup in campaign screen's message area
	@Override
	protected void addBulletPoints(TooltipMakerAPI info, ListInfoMode mode) {
		Color h = Misc.getHighlightColor();
		Color g = Misc.getGrayColor();
		float pad = 3f;
		float opad = 10f;
		
		float initPad = pad;
		if (mode == ListInfoMode.IN_DESC) initPad = opad;
		
		Color tc = getBulletColorForMode(mode);
		
		bullet(info);
		boolean isUpdate = getListInfoParam() != null;
		
		float eta = getETA();
		FactionAPI other = targetFaction;
		
		info.addPara(StringHelper.getString("faction", true) + ": " + faction.getDisplayName(), initPad, tc,
				 	 faction.getBaseUIColor(), faction.getDisplayName());
		initPad = 0f;
		
		if (outcome == null)
		{
			String str = StringHelper.getStringAndSubstituteToken("nex_fleetIntel",
					"bulletTarget", "$targetFaction", other.getDisplayName());
			info.addPara(str, initPad, tc,
						 other.getBaseUIColor(), other.getDisplayName());
		}
		
		if (getListInfoParam() == ENTERED_SYSTEM_UPDATE) {
			String str = StringHelper.getString("nex_fleetIntel", "bulletArrived");
			str = StringHelper.substituteToken(str, "$forceType", getForceType(), true);
			info.addPara(str, tc, initPad);
			return;
		}
		
		if (outcome != null)
		{
			String key = "bulletCancelled";
			switch (outcome) {
				case SUCCESS:
					key = "bulletSuccess";
					break;
				case TASK_FORCE_DEFEATED:
				case FAIL:
					key = "bulletFailed";
					break;
				case MARKET_NO_LONGER_EXISTS:
					key = "bulletNoLongerExists";
					break;
				case NO_LONGER_HOSTILE:
					key = "bulletNoLongerHostile";
					break;
			}
			//String str = StringHelper.getStringAndSubstituteToken("exerelin_invasion", 
			//		key, "$target", target.getName());
			//info.addPara(str, initPad, tc, other.getBaseUIColor(), target.getName());
			String str = StringHelper.getString("nex_fleetIntel", key);
			str = StringHelper.substituteToken(str, "$forceType", getForceType(), true);
			str = StringHelper.substituteToken(str, "$action", getActionName(), true);
			info.addPara(str, tc, initPad);
		} else {
			info.addPara(system.getNameWithLowercaseType(), tc, initPad);
		}
		initPad = 0f;
		if (eta > 1 && failStage < 0) {
			String days = getDaysString(eta);
			String str = StringHelper.getStringAndSubstituteToken("nex_fleetIntel", "bulletETA", "$days", days);
			info.addPara(str, initPad, tc, h, "" + (int)Math.round(eta));
			initPad = 0f;
		}
		
		unindent(info);
	}
	
	@Override
	public String getName() {
		String base = StringHelper.getString("nex_fleetIntel", "title");
		base = StringHelper.substituteToken(base, "$action", getActionName(), true);
		base = StringHelper.substituteToken(base, "$market", target.getName());
		
		if (isEnding()) {
			if (outcome == OffensiveOutcome.SUCCESS) {
				return base + " - " + StringHelper.getString("successful", true);
			}
			else if (outcome != null && outcome.isFailed()) {
				return base + " - " + StringHelper.getString("failed", true);
			}
			return base + " - " + StringHelper.getString("over", true);
		}
		return base;
	}
	
	public String getActionName() {
		return StringHelper.getString("expedition");
	}
	
	public String getActionNameWithArticle() {
		return StringHelper.getString("theExpedition");
	}
	
	public String getForceType() {
		return StringHelper.getString("force");
	}
	
	public String getForceTypeWithArticle() {
		return StringHelper.getString("theForce");
	}
	
	public String getForceTypeIsOrAre() {
		return StringHelper.getString("is");
	}
	
	public String getForceTypeHasOrHave() {
		return StringHelper.getString("has");
	}

	public void setOutcome(OffensiveOutcome outcome) {
		this.outcome = outcome;
	}
		
	@Override
	public Set<String> getIntelTags(SectorMapAPI map) {
		Set<String> tags = super.getIntelTags(map);
		tags.add(Tags.INTEL_MILITARY);
		//tags.add(StringHelper.getString("exerelin_invasion", "invasions", true));
		if (targetFaction.isPlayerFaction())
			tags.add(Tags.INTEL_COLONIES);
		tags.add(getFaction().getId());
		tags.add(target.getFactionId());
		return tags;
	}
		
	public void terminateEvent(OffensiveOutcome outcome)
	{
		setOutcome(outcome);
		forceFail(true);
	}
	
	public void checkForTermination() {
		if (outcome != null) return;
		
		// source captured before launch
		if (getCurrentStage() <= 0 && from.getFaction() != faction) {
			terminateEvent(OffensiveOutcome.FAIL);
		}
		else if (!target.isInEconomy()) {
			terminateEvent(OffensiveOutcome.MARKET_NO_LONGER_EXISTS);
		}
		else if (!faction.isHostileTo(target.getFaction())) {
			terminateEvent(OffensiveOutcome.NO_LONGER_HOSTILE);
		}
	}
	
	// check if market should still be attacked
	@Override
	protected void advanceImpl(float amount) {
		checkForTermination();
		super.advanceImpl(amount);
	}
	
	// send fleets home
	@Override
	protected void failedAtStage(RaidStage stage) {
		BaseRaidStage stage2 = (BaseRaidStage)stage;
		stage2.giveReturnOrdersToStragglers(stage2.getRoutes());
	}
	
	// disregard market fleet size mult if needed
	@Override
	protected float getRaidFPAdjusted() {
		if (InvasionFleetManager.USE_MARKET_FLEET_SIZE_MULT)
			return super.getRaidFPAdjusted();
				
		float raidFP = getRaidFP();
		float raidStr = raidFP * InvasionFleetManager.getFactionDoctrineFleetSizeMult(faction);
		return raidStr;
	}
}