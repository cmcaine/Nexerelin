package exerelin.campaign.intel.fleets;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.intel.raid.OrganizeStage;
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidIntel;
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidIntel.RaidStageStatus;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.intel.OffensiveFleetIntel;
import exerelin.campaign.intel.OffensiveFleetIntel.OffensiveOutcome;
import exerelin.utilities.StringHelper;
import java.awt.Color;

public class NexOrganizeStage extends OrganizeStage {
	
	protected OffensiveFleetIntel offFltIntel;

	public NexOrganizeStage(OffensiveFleetIntel intel, MarketAPI market, float durDays) {
		super(intel, market, durDays);
		offFltIntel = intel;
	}
	
	protected Object readResolve() {
		if (offFltIntel == null)
			offFltIntel = (OffensiveFleetIntel)intel;
		
		return this;
	}
	
	@Override
	public void showStageInfo(TooltipMakerAPI info) {
		int curr = intel.getCurrentStage();
		int index = intel.getStageIndex(this);
		
		Color h = Misc.getHighlightColor();
		Color g = Misc.getGrayColor();
		Color tc = Misc.getTextColor();
		float pad = 3f;
		float opad = 10f;
		
		int days = Math.round(maxDays - elapsed);
		String strDays = RaidIntel.getDaysString(days);
		
		String timing;
		if (days >= 2) {
			timing = StringHelper.getString("nex_fleetIntel", "stageOrganizeTiming");
			timing = StringHelper.substituteToken(timing, "$theForceType", getForcesString(), true);
			timing = StringHelper.substituteToken(timing, "$strDays", strDays);
		} else {
			timing = StringHelper.getString("nex_fleetIntel", "stageOrganizeTimingSoon");
			timing = StringHelper.substituteToken(timing, "$theForceType", getForcesString(), true);
		}
		
		String raid = offFltIntel.getActionNameWithArticle();
		String key = "stageOrganize";
		boolean haveTiming = true;
		boolean printSource = false;
		if (isFailed(curr, index)) {
			key = "stageOrganizeDisrupted";
			haveTiming = false;
		} else if (curr == index) {
			boolean known = !market.isHidden() || !market.getPrimaryEntity().isDiscoverable();
			if (known) {
				printSource = true;
			} else {
				key = "stageOrganizeUnknown";
			}
		} else {
			return;
		}
		String str = StringHelper.getString("nex_fleetIntel", key);
		str = StringHelper.substituteToken(str, "$theAction", raid, true);
		if (printSource) {
			str = StringHelper.substituteToken(str, "$onOrAt", market.getOnOrAt());
			str = StringHelper.substituteToken(str, "$market", market.getName());
		}
		if (haveTiming)
			str += " " + timing;
		info.addPara(str, opad, h, "" + days);
	}
	
	protected boolean isFailed(int curr, int index) {
		if (status == RaidStageStatus.FAILURE)
			return true;
		if (curr == index && offFltIntel.getOutcome() == OffensiveOutcome.FAIL)
			return true;
		
		return false;
	}
	
	@Override
	protected String getForcesString() {
		return offFltIntel.getForceTypeWithArticle();
	}
	
	@Override
	protected String getRaidString() {
		return offFltIntel.getActionName();
	}
}