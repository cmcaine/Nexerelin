package com.fs.starfarer.api.impl.campaign.rulecmd;

import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.util.Misc.Token;
import exerelin.campaign.DiplomacyManager;
import exerelin.campaign.PlayerFactionStore;
import exerelin.campaign.StatsTracker;
import exerelin.utilities.NexUtilsReputation;
import exerelin.utilities.ExerelinConfig;

public class PrisonerRepatriate extends AgentActionBase {

	@Override
	public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params, Map<String, MemoryAPI> memoryMap) {
		if (dialog == null) return false;
		
		boolean superResult = useSpecialPerson("prisoner", 1);
		if (superResult == false)
			return false;
		
		SectorEntityToken target = (SectorEntityToken) dialog.getInteractionTarget();
		
		FactionAPI faction = target.getFaction();
		TextPanelAPI text = dialog.getTextPanel();
		NexUtilsReputation.adjustPlayerReputation(faction, target.getActivePerson(), ExerelinConfig.prisonerRepatriateRepValue,
						ExerelinConfig.prisonerRepatriateRepValue, null, text);
		DiplomacyManager.getManager().getDiplomacyBrain(faction.getId()).reportDiplomacyEvent(
						PlayerFactionStore.getPlayerFactionId(), ExerelinConfig.prisonerRepatriateRepValue);
		StatsTracker.getStatsTracker().notifyPrisonersRepatriated(1);
		return true;
	}
}
