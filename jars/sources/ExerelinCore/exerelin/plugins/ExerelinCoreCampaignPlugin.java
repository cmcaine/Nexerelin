package exerelin.plugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.PluginPick;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.CoreCampaignPluginImpl;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.RepActionEnvelope;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.RepActions;
import exerelin.campaign.DiplomacyManager;
import exerelin.campaign.PlayerFactionStore;
import exerelin.campaign.ExerelinReputationPlugin;
import exerelin.world.ResponseFleetManager;

@SuppressWarnings("unchecked")
public class ExerelinCoreCampaignPlugin extends CoreCampaignPluginImpl {

	@Override
	public PluginPick<ReputationActionResponsePlugin> pickReputationActionResponsePlugin(Object action, String factionId) {
		if (action instanceof RepActions || action instanceof RepActionEnvelope) {
			return new PluginPick<ReputationActionResponsePlugin>(
				new ExerelinReputationPlugin(),
				PickPriority.MOD_GENERAL
			);
	}
	return null;
	}
	
	@Override
	public void updatePlayerFacts(MemoryAPI memory) {
		super.updatePlayerFacts(memory);
		String associatedFactionId = PlayerFactionStore.getPlayerFactionId();
		FactionAPI associatedFaction = Global.getSector().getFaction(associatedFactionId);
		memory.set("$faction", associatedFaction, 0);
		memory.set("$factionId", associatedFactionId, 0);
	}
	
	@Override
	public void updateMarketFacts(MarketAPI market, MemoryAPI memory) {
		super.updateMarketFacts(market, memory);
		memory.set("$reserveSize", ResponseFleetManager.getReserveSize(market), 0);
	}
	
	@Override
	public void updateFactionFacts(FactionAPI faction, MemoryAPI memory) {
		super.updateFactionFacts(faction, memory);
		memory.set("$warWeariness", DiplomacyManager.getWarWeariness(faction.getId()), 0);
		memory.set("$numWars", DiplomacyManager.getFactionsAtWarWithFaction(faction, false).size(), 0);
	}
}