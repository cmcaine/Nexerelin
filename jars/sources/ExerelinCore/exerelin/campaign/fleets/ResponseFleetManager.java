package exerelin.campaign.fleets;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCampaignEventListener;
import com.fs.starfarer.api.campaign.CampaignEventListener;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParams;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.util.IntervalUtil;
import exerelin.ExerelinConstants;
import exerelin.utilities.ExerelinConfig;
import exerelin.utilities.ExerelinFactionConfig;
import exerelin.utilities.ExerelinUtils;
import exerelin.utilities.ExerelinUtilsFleet;
import exerelin.utilities.StringHelper;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.apache.log4j.Logger;

/**
 * When someone tries to invade our market, spawn a big freaking fleet to eat them
 */
public class ResponseFleetManager extends BaseCampaignEventListener implements EveryFrameScript
{
	public static final String MANAGER_MAP_KEY = "exerelin_responseFleetManager";
	private static final float RESERVE_INCREMENT_PER_DAY = 0.08f;
	private static final float RESERVE_MARKET_STABILITY_DIVISOR = 5f;
	private static final float INITIAL_RESERVE_SIZE_MULT = 0.75f;
	public static final float MIN_FP_TO_SPAWN = 5f;
	
	protected Map<String, Float> revengeStrength = new HashMap<>();
	protected Map<String, Float> reserves = new HashMap<>();
	protected Random random = new Random();
	
	public static Logger log = Global.getLogger(ResponseFleetManager.class);
	
	private final List<ResponseFleetData> activeFleets = new LinkedList();
	private final IntervalUtil tracker;
	
	private static ResponseFleetManager responseFleetManager;
	
	public ResponseFleetManager()
	{
		super(true);
	
		float interval = Global.getSettings().getFloat("averagePatrolSpawnInterval");
		//interval = 2;   // debug
		this.tracker = new IntervalUtil(interval * 0.75F, interval * 1.25F);
		
		reserves = new HashMap<>();
		List<MarketAPI> markets = Global.getSector().getEconomy().getMarketsCopy();
		for(MarketAPI market:markets)
			reserves.put(market.getId(), getMaxReserveSize(market, false)*INITIAL_RESERVE_SIZE_MULT);
	}
	
	public CampaignFleetAPI getResponseFleet(MarketAPI origin, int points)
	{
		//float qf = origin.getShipQualityFactor();
		//qf = Math.max(qf, 0.7f);
		
		String factionId = origin.getFactionId();
		String fleetFactionId = factionId;
		ExerelinFactionConfig factionConfig = ExerelinConfig.getExerelinFactionConfig(factionId);
		ExerelinFactionConfig fleetFactionConfig = null;
		
		if (factionConfig.factionIdForHqResponse != null && origin.hasCondition(Conditions.HEADQUARTERS))
		{
			fleetFactionId = factionConfig.factionIdForHqResponse;
			fleetFactionConfig = ExerelinConfig.getExerelinFactionConfig(fleetFactionId);
		}
		
		String name = "";
		
		if (fleetFactionConfig != null)
			name = fleetFactionConfig.responseFleetName;
		else
			name = factionConfig.responseFleetName;
		
		if (points <= 18) name = StringHelper.getString("exerelin_fleets", "responseFleetPrefixSmall") + " " + name;
		else if (points >= 54) name = StringHelper.getString("exerelin_fleets", "responseFleetPrefixLarge") + " " + name;
		
		//int marketSize = origin.getSize();
		//if (origin.getId().equals(ExerelinConstants.AVESTA_ID)) marketSize += 2;
		//CampaignFleetAPI fleet = FleetFactory.createGenericFleet(origin.getFactionId(), name, qf, maxFP);
		FleetParams fleetParams = new FleetParams(null, origin, fleetFactionId, null, "exerelinResponseFleet", 
				points, // combat
				0,	//maxFP*0.1f, // freighters
				0,		// tankers
				0,		// personnel transports
				0,		// liners
				0,		// civilian
				0,	//maxFP*0.1f,	// utility
				0.15f, -1, 1.25f, 1);	// quality bonus, quality override, officer num mult, officer level bonus
		fleetParams.random = getRandom();
		
		CampaignFleetAPI fleet = ExerelinUtilsFleet.customCreateFleet(Global.getSector().getFaction(fleetFactionId), fleetParams);
		if (fleet == null) return null;
		
		fleet.setFaction(factionId, true);
		fleet.setName(name);
		fleet.setAIMode(true);
		fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_PATROL_FLEET, true);
		if (origin.getFaction().isHostileTo(Factions.PLAYER))
		{
			fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_AGGRESSIVE, true, 5);
			fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_HOSTILE, true, 5);
			fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_HOSTILE_WHILE_TOFF, true, 5);
		}
		
		return fleet;
	}
	
	/**
	 * Adds the response fleet to the list of active fleets and assigns its AI script.
	 * @param fleet
	 * @param origin
	 * @param target The enemy fleet to intercept.
	 */
	public void registerResponseFleetAndSetAI(CampaignFleetAPI fleet, MarketAPI origin, SectorEntityToken target)
	{
		ResponseFleetData data = new ResponseFleetData(fleet);
		data.startingFleetPoints = fleet.getFleetPoints();
		data.sourceMarket = origin;
		data.source = origin.getPrimaryEntity();
		data.target = target;
		this.activeFleets.add(data);
		
		ResponseFleetAI ai = new ResponseFleetAI(fleet, data);
		fleet.addScript(ai);
		
		
		if (target == Global.getSector().getPlayerFleet())
		{
			data.fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_SAW_PLAYER_WITH_TRANSPONDER_ON, true, 5);
		}
	}
	
	public void spawnResponseFleet(MarketAPI origin, SectorEntityToken target, SectorEntityToken spawnEntity)
	{
		float reserveSize = getReserveSize(origin);
		int maxFP = (int)reserveSize;
		if (maxFP < MIN_FP_TO_SPAWN)
		{
			log.info(origin.getName() + " has insufficient FP for response fleet: " + maxFP);
			return;
		}
		int enemyFP = ((CampaignFleetAPI)target).getFleetPoints();
		if (enemyFP > maxFP * 8)
		{
			// disable: no reason not to at least try, especially now that multi-fleet battles are a thing
			//log.info(target.getName() + " is too big to handle: " + enemyFP);
			//return;
		}
		
		CampaignFleetAPI fleet = getResponseFleet(origin, maxFP);
		if (fleet == null) return;
		
		registerResponseFleetAndSetAI(fleet, origin, target);
		
		if (spawnEntity == null) spawnEntity = origin.getPrimaryEntity();
		spawnEntity.getContainingLocation().addEntity(fleet);
		fleet.setLocation(spawnEntity.getLocation().x, spawnEntity.getLocation().y);
		
		log.info("\tSpawned " + fleet.getNameWithFaction() + " of size " + maxFP);
		reserves.put(origin.getId(), 0f);
		
		// clear any stored interaction dialog response fleets
		MemoryAPI marketMem = origin.getMemoryWithoutUpdate();
		marketMem.unset("$nex_invasionResponseFleet");
		marketMem.unset("$nex_raidResponseFleet");
	}
	
	public static float getMaxReserveSize(MarketAPI market, boolean raw)
	{
		int marketSize = market.getSize();
		if (market.getId().equals(ExerelinConstants.AVESTA_ID)) marketSize += 2;
		
		float baseSize = marketSize * 5 + 8;
		float size = baseSize;
		if (raw) return size;
		
		if (market.hasCondition(Conditions.MILITARY_BASE)) size += baseSize * 0.1;
		if (market.hasCondition(Conditions.ORBITAL_STATION)) size += baseSize * 0.05;
		if (market.hasCondition(Conditions.SPACEPORT)) size += baseSize * 0.05;
		if (market.hasCondition(Conditions.REGIONAL_CAPITAL)) size += baseSize * 0.1;
		if (market.hasCondition(Conditions.HEADQUARTERS)) size += baseSize * 0.2;
		
		ExerelinFactionConfig factionConfig = ExerelinConfig.getExerelinFactionConfig(market.getFactionId());
		if (factionConfig != null)
		{
			size += baseSize * factionConfig.responseFleetSizeMod;
		}
		
		if (DefenceStationManager.getManager() != null)
			size -= DefenceStationManager.getManager().getDefenceFleetPenaltyFromStations(market);
		
		size = size + ExerelinUtilsFleet.getPlayerLevelFPBonus();
		
		return size;
	}
	
	public void updateReserves(float days)
	{
		// prevents NPE of unknown origin
		if (Global.getSector() == null || Global.getSector().getEconomy() == null)
			return;
		
		List<MarketAPI> markets = Global.getSector().getEconomy().getMarketsCopy();
		for(MarketAPI market:markets)
		{
			if (!reserves.containsKey(market.getId()))
				reserves.put(market.getId(), getMaxReserveSize(market, false)*INITIAL_RESERVE_SIZE_MULT);
			
			int marketSize = market.getSize();
			if (market.getId().equals(ExerelinConstants.AVESTA_ID)) marketSize++;
			
			float baseIncrement = marketSize * (0.5f + (market.getStabilityValue()/RESERVE_MARKET_STABILITY_DIVISOR));
			float increment = baseIncrement;
			//if (market.hasCondition(Conditions.REGIONAL_CAPITAL)) increment += baseIncrement * 0.1f;
			if (market.hasCondition(Conditions.HEADQUARTERS)) increment += baseIncrement * 0.25f;
			
			ExerelinFactionConfig factionConfig = ExerelinConfig.getExerelinFactionConfig(market.getFactionId());
			if (factionConfig != null)
			{
				increment += baseIncrement * factionConfig.responseFleetSizeMod;
			}
			
			increment = increment * RESERVE_INCREMENT_PER_DAY * days;
			float newValue = Math.min(getReserveSize(market) + increment, getMaxReserveSize(market, false));
			
			reserves.put(market.getId(), newValue);
		}
	}
	
	public Random getRandom() 
	{
		// reverse compatibility
		if (random == null)
		{
			random = new Random(ExerelinUtils.getStartingSeed());
		}
		return random;
	}
	
	public static void requestResponseFleet(MarketAPI market, SectorEntityToken attacker)
	{
		requestResponseFleet(market, attacker, null);
	}
	
	public static void requestResponseFleet(MarketAPI market, SectorEntityToken attacker, SectorEntityToken spawnEntity)
	{
		if (responseFleetManager == null) return;
		responseFleetManager.spawnResponseFleet(market, attacker, spawnEntity);
	}
	
	public static float modifyReserveSize(MarketAPI market, float delta)
	{
		if (responseFleetManager == null) return 0f;
		String marketId = market.getId();
		if (!responseFleetManager.reserves.containsKey(marketId)) return 0f;
		float current = getReserveSize(market);
		float newValue = current + delta;
		float max = getMaxReserveSize(market, false);
		if (newValue < 0) newValue = 0;
		else if (newValue > max) newValue = max;
		responseFleetManager.reserves.put(marketId, newValue);
		return newValue - current;
	}
	
	public static float getReserveSize(MarketAPI market)
	{
		if (responseFleetManager == null) return -1f;
		if (market == null) return -1f;
		String marketId = market.getId();
		Map<String, Float> reserves = responseFleetManager.reserves;
		if (!reserves.containsKey(marketId))
		{
			// probably a fake market, don't save reserves
			if (!market.isInEconomy()) return -1f;
			
			reserves.put(marketId, getMaxReserveSize(market, false)*INITIAL_RESERVE_SIZE_MULT);
		}
		return reserves.get(marketId);
	}
  
	
	float lastReserveUpdateAge = 0f;
	@Override
	public void advance(float amount)
	{
		float days = Global.getSector().getClock().convertToDays(amount);
		lastReserveUpdateAge += days;
		if (lastReserveUpdateAge >= 1)
		{
			lastReserveUpdateAge -= 1;
			updateReserves(1);
		}
		
		this.tracker.advance(days);
		if (this.tracker.intervalElapsed()) {
			return;
		}
		List<ResponseFleetData> remove = new LinkedList();
		for (ResponseFleetData data : this.activeFleets) {
			if ((data.fleet.getContainingLocation() == null) || (!data.fleet.getContainingLocation().getFleets().contains(data.fleet)) || (!data.fleet.isAlive())) {
				remove.add(data);
			}
		}
		this.activeFleets.removeAll(remove);
	}
	
	@Override
	public boolean isDone()
	{
		return false;
	}
	
	@Override
	public void reportFleetDespawned(CampaignFleetAPI fleet, CampaignEventListener.FleetDespawnReason reason, Object param)
	{
		super.reportFleetDespawned(fleet, reason, param);
		for (ResponseFleetData data : this.activeFleets) {
			if (data.fleet == fleet)
			{
				this.activeFleets.remove(data);
				break;
			}
		}
	}
	
	@Override
	public boolean runWhilePaused()
	{
		return false;
	}
	
	public static ResponseFleetManager getManager()
	{
		Map<String, Object> data = Global.getSector().getPersistentData();
		responseFleetManager = (ResponseFleetManager)data.get(MANAGER_MAP_KEY);
		if (responseFleetManager != null)
			return responseFleetManager;
		return null;
	}
	
	public static ResponseFleetManager create()
	{
		ResponseFleetManager saved = getManager();
		if (saved != null) return saved;
		
		Map<String, Object> data = Global.getSector().getPersistentData();
		responseFleetManager = new ResponseFleetManager();
		data.put(MANAGER_MAP_KEY, responseFleetManager);
		return responseFleetManager;
	}
	
	public static class ResponseFleetData
	{
		public CampaignFleetAPI fleet;
		public SectorEntityToken source;
		public SectorEntityToken target;
		public MarketAPI sourceMarket;
		public float startingFleetPoints = 0.0F;
	
		public ResponseFleetData(CampaignFleetAPI fleet)
		{
			this.fleet = fleet;
		}
	}
}