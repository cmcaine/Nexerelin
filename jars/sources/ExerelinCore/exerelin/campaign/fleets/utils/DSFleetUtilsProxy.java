package exerelin.campaign.fleets.utils;

import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV2;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParams;
import data.scripts.campaign.DS_FleetFactory;
import data.scripts.campaign.fleets.DS_FleetInjector;
import static data.scripts.campaign.fleets.DS_FleetInjector.randomizeVariants;
import data.scripts.util.DS_Defs;
import data.scripts.util.DS_Util;
import static data.scripts.util.DS_Util.getArchetypeWeights;
import java.util.List;
import java.util.Random;

public class DSFleetUtilsProxy {
	
	public static CampaignFleetAPI enhancedCreateFleet(FactionAPI faction, FleetParams params, int total) {
		final FleetParams params2 = params;
		return DS_FleetFactory.enhancedCreateFleet(faction, total, new DS_FleetFactory.FleetFactoryDelegate() {
			@Override
			public CampaignFleetAPI createFleet() {
				return FleetFactoryV2.createFleet(params2);
			}
		});
	}
	
	public static void injectFleet(CampaignFleetAPI fleet, MarketAPI market, Float stability, Float qualityFactor, String type) {
		String factionId = fleet.getFaction().getId();
		MemoryAPI memory = fleet.getMemoryWithoutUpdate();
		Random r;
		if (memory.contains(DS_Defs.MEMORY_KEY_RANDOM_SEED)) {
			long seed = memory.getLong(DS_Defs.MEMORY_KEY_RANDOM_SEED);
			r = new Random(seed);
		} else {
			r = new Random();
		}

		DS_Defs.Archetype theme = DS_FleetInjector.pickTheme(factionId, r);
		DS_Util.setThemeName(fleet, theme);
		List<String> extendedTheme = DS_Util.pickExtendedTheme(factionId, market, r);
		DS_Util.setExtendedThemeName(fleet, extendedTheme);

		switch (type)
		{
			case "exerelinInvasionFleet":
			case "exerelinRespawnFleet":
				randomizeVariants(fleet, factionId, extendedTheme, qualityFactor, 0f, theme, 
						getArchetypeWeights(DS_Defs.FleetStyle.ELITE, factionId), false, r);
				break;
			case "exerelinInvasionSupportFleet":
			case "exerelinDefenceFleet":
				randomizeVariants(fleet, factionId, extendedTheme, qualityFactor, 0f, theme, 
						getArchetypeWeights(DS_Defs.FleetStyle.MILITARY, factionId), false, r);
				break;
			case "exerelinResponseFleet":
				randomizeVariants(fleet, factionId, extendedTheme, qualityFactor, 0f, theme, 
						getArchetypeWeights(DS_Defs.FleetStyle.MILITARY, factionId), false, r);
				break;  
			case "exerelinMiningFleet":
				randomizeVariants(fleet, factionId, extendedTheme, qualityFactor, 0f, theme, 
						getArchetypeWeights(DS_Defs.FleetStyle.CIVILIAN, factionId), true, r);
				break;
			case "vengeanceFleet":
				randomizeVariants(fleet, factionId, extendedTheme, qualityFactor, 0f, theme, 
						getArchetypeWeights(DS_Defs.FleetStyle.ELITE, factionId), false, r);
				break;
				
			default:	// fallback taken from SS+
				randomizeVariants(fleet, factionId, null, qualityFactor, 0f, theme, 
						getArchetypeWeights(DS_Defs.FleetStyle.STANDARD, factionId), false, r);
		}
		DS_FleetFactory.finishFleetNonIntrusive(fleet, factionId, false, r);
	}
}
