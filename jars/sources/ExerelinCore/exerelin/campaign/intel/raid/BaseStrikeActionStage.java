package exerelin.campaign.intel.raid;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.command.WarSimScript;
import com.fs.starfarer.api.impl.campaign.econ.impl.OrbitalStation;
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidIntel;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.intel.InvasionIntel;
import exerelin.campaign.intel.OffensiveFleetIntel;
import exerelin.campaign.intel.invasion.InvActionStage;
import exerelin.utilities.StringHelper;
import java.awt.Color;

public class BaseStrikeActionStage extends InvActionStage {
	
	public BaseStrikeActionStage(OffensiveFleetIntel strike, MarketAPI target) {
		super(strike, target);
	}
	
	@Override
	protected void updateStatus() {
		if (!target.isInEconomy()) {
			status = RaidIntel.RaidStageStatus.SUCCESS;
			BaseStrikeIntel intel = (BaseStrikeIntel)this.intel;
			intel.setOutcome(OffensiveFleetIntel.OffensiveOutcome.SUCCESS);
			intel.sendOutcomeUpdate();
		}
		
		super.updateStatus();
	}
	
	@Override
	public String getRaidActionText(CampaignFleetAPI fleet, MarketAPI market) {
		return StringHelper.getFleetAssignmentString("attacking", market.getName());
	}

	@Override
	public String getRaidApproachText(CampaignFleetAPI fleet, MarketAPI market) {
		return StringHelper.getFleetAssignmentString("movingInToAttack", market.getName());
	}
	
	@Override
	public void performRaid(CampaignFleetAPI fleet, MarketAPI market) {
		// do nothing, if the base is whacked we require no further action
	}
	
	@Override
	protected void autoresolve() {
		Global.getLogger(this.getClass()).info("Autoresolving base strike action");
		float str = WarSimScript.getFactionStrength(intel.getFaction(), target.getStarSystem());
		float enemyStr = WarSimScript.getFactionStrength(target.getFaction(), target.getStarSystem());
		
		float defensiveStr = enemyStr + WarSimScript.getStationStrength(target.getFaction(), 
							 target.getStarSystem(), target.getPrimaryEntity());
		InvasionIntel intel = ((InvasionIntel)this.intel);
		
		if (defensiveStr >= str) {
			status = RaidIntel.RaidStageStatus.FAILURE;
			removeMilScripts();
			giveReturnOrdersToStragglers(getRoutes());
			
			intel.setOutcome(OffensiveFleetIntel.OffensiveOutcome.TASK_FORCE_DEFEATED);
			return;
		}
		
		// kill base
		Industry station = Misc.getStationIndustry(target);
		if (station != null) {
			OrbitalStation.disrupt(station);
		}
		/*
		CampaignFleetAPI fleet = Misc.getStationFleet(target);
		if (fleet != null && fleet.isAlive())
		for (FleetMemberAPI member : fleet.getFleetData().getMembersListCopy())
		{
			fleet.removeFleetMemberWithDestructionFlash(member);
		}
		*/
		
		// base killed; we're done 
		status = RaidIntel.RaidStageStatus.SUCCESS;
		intel.setOutcome(OffensiveFleetIntel.OffensiveOutcome.SUCCESS);
		intel.sendOutcomeUpdate();
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
		
		if (curr < index) return;
		
		if (status == RaidIntel.RaidStageStatus.ONGOING && curr == index) {
			info.addPara(StringHelper.getString("nex_baseStrike", "intelStageAction"), opad);
			return;
		}
		
		OffensiveFleetIntel intel = ((OffensiveFleetIntel)this.intel);
		if (intel.getOutcome() != null) {
			String key = "intelStageAction";
			switch (intel.getOutcome()) {
			case SUCCESS:
				key += "Success";
				break;
			case FAIL:
			case TASK_FORCE_DEFEATED:
				key += "Defeated";
				break;
			case NO_LONGER_HOSTILE:
			case MARKET_NO_LONGER_EXISTS:
			case OTHER:
				key += "Aborted";
				break;
			}
			info.addPara(StringHelper.getStringAndSubstituteToken("nex_baseStrike",
						key, "$market", target.getName()), opad);
		} else if (status == RaidIntel.RaidStageStatus.SUCCESS) {			
			info.addPara("The expeditionary force has succeeded.", opad); // shouldn't happen?
		} else {
			info.addPara("The expeditionary force has failed.", opad); // shouldn't happen?
		}
	}
}
