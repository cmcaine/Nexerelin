package exerelin.campaign.intel.specialforces;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.fleets.RouteLocationCalculator;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager.RouteData;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidIntel;
import com.fs.starfarer.api.impl.campaign.rulecmd.Nex_FactionDirectory;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Pair;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.campaign.AllianceManager;
import exerelin.campaign.fleets.InvasionFleetManager;
import exerelin.campaign.intel.colony.ColonyExpeditionIntel;
import exerelin.campaign.intel.fleets.OffensiveFleetIntel;
import exerelin.campaign.intel.invasion.InvasionIntel;
import exerelin.campaign.intel.raid.BaseStrikeIntel;
import exerelin.campaign.intel.raid.NexRaidIntel;
import exerelin.campaign.intel.satbomb.SatBombIntel;
import exerelin.utilities.ExerelinUtilsFaction;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.log4j.Logger;
import org.lazywizard.lazylib.MathUtils;

public class SpecialForcesRouteAI {
	
	public static Logger log = Global.getLogger(SpecialForcesRouteAI.class);
	
	public static final float MAX_RAID_ETA_TO_CARE = 60;
	
	protected SpecialForcesIntel sf;
	protected SpecialForcesTask currentTask;
	
	protected IntervalUtil recheckTaskInterval = new IntervalUtil(7, 13);
	
	public SpecialForcesRouteAI(SpecialForcesIntel sf) {
		this.sf = sf;
	}
	
	protected List<RaidIntel> getActiveRaids() {
		List<RaidIntel> raids = new ArrayList<>();
		for (IntelInfoPlugin intel : Global.getSector().getIntelManager().getIntel()) 
		{			
			if (!(intel instanceof RaidIntel))
				continue;
			
			RaidIntel raid = (RaidIntel)intel;
			
			if (raid.isEnding() || raid.isEnded())
				continue;
			
			raids.add(raid);
		}
		return raids;
	}
	
	/**
	 * Gets active raid-type events by non-hostile factions against hostile factions.
	 * @return
	 */
	protected List<RaidIntel> getActiveRaidsFriendly() {
		List<RaidIntel> raids = getActiveRaids();
		List<RaidIntel> raidsFiltered = new ArrayList<>();
		for (RaidIntel raid : raids) {
			
			if (raid.getFaction().isHostileTo(sf.faction))
				continue;
			
			if (raid instanceof OffensiveFleetIntel) {
				if (raid instanceof BaseStrikeIntel) continue;
				if (raid instanceof ColonyExpeditionIntel) continue;
				
				// Only count the raid if the target is hostile to us
				OffensiveFleetIntel ofi = (OffensiveFleetIntel)raid;
				if (!ofi.getTarget().getFaction().isHostileTo(sf.faction))
					continue;
			}
			else {	// probably a pirate raid
				// Only count the raid if the target is hostile to us
				StarSystemAPI targetSys = raid.getSystem();
				if (targetSys == null) continue;
				FactionAPI owner = ExerelinUtilsFaction.getSystemOwner(targetSys);
				if (owner == null || owner.isHostileTo(sf.faction))
					continue;				
			}
			
			raidsFiltered.add(raid);
		}
		return raidsFiltered;
	}
	
	/**
	 * Gets active raid-type events by hostile factions against us or our allies.
	 * @return
	 */
	protected List<RaidIntel> getActiveRaidsHostile() {
		List<RaidIntel> raids = getActiveRaids();
		List<RaidIntel> raidsFiltered = new ArrayList<>();
		for (RaidIntel raid : raids) {			
			if (!raid.getFaction().isHostileTo(sf.faction))
				continue;
			
			if (raid instanceof OffensiveFleetIntel) {
				if (raid instanceof BaseStrikeIntel) continue;
				
				// Only count the raid if the target is an ally of us
				OffensiveFleetIntel ofi = (OffensiveFleetIntel)raid;
				String targetFaction = ofi.getTarget().getFactionId();
				if (!AllianceManager.areFactionsAllied(targetFaction, sf.faction.getId()))
					continue;
			}
			else {	// probably a pirate raid
				// Only count the raid if the target is an ally of us
				StarSystemAPI targetSys = raid.getSystem();
				if (targetSys == null) continue;
				FactionAPI owner = ExerelinUtilsFaction.getSystemOwner(targetSys);
				if (owner == null || !AllianceManager.areFactionsAllied(owner.getId(), sf.faction.getId()))
					continue;				
			}
			
			raidsFiltered.add(raid);
		}
		return raidsFiltered;
	}
	
	public void resetRoute(RouteManager.RouteData route) {
		CampaignFleetAPI fleet = route.getActiveFleet();
		if (fleet != null) {
			fleet.clearAssignments();
		}
		route.getSegments().clear();
		route.setCurrent(null);
	}
	
	/**
	 * Set task as current, updating routes and the like.
	 * @param task
	 */
	public void assignTask(SpecialForcesTask task) 
	{
		RouteData route = sf.route;
		currentTask = task;
		sf.debugMsg("Assigning task of type " + task.type + "; priority " 
				+ String.format("%.1f", task.priority), false);
		if (task.market != null) 
			sf.debugMsg("  Target: " + task.market.getName(), true);
		else if (task.system != null)
			sf.debugMsg("  Target: " + task.system.getNameWithLowercaseType(), true);
		
		resetRoute(route);
		
		CampaignFleetAPI fleet = route.getActiveFleet();	
		SectorEntityToken from;
		if (fleet != null) from = fleet.getContainingLocation().createToken(fleet.getLocation());
		else from = Global.getSector().getHyperspace().createToken(route.getInterpolatedHyperLocation());
		
		// get time for assignment, estimate travel time needed
		float travelTime = 0;
		
		// setup a travel segment and an action segment
		RouteManager.RouteSegment actionSeg = null, travelSeg = null;
		SectorEntityToken destination = null;
		switch (task.type) {
			case REBUILD:
			case DEFEND_RAID:
			case ASSIST_RAID:
			case PATROL:
			case RAID:
			case ASSEMBLE:
				destination = task.market == null ? task.system.getCenter() : task.market.getPrimaryEntity();
				
				travelTime = RouteLocationCalculator.getTravelDays(from, destination);
				travelSeg = new RouteManager.RouteSegment(travelTime, from, destination);
				actionSeg = new RouteManager.RouteSegment(task.time, destination);
				break;
			case HUNT_PLAYER:
				// TODO				
			case IDLE:
				// go to nearest star system and just bum around it for a bit
				StarSystemAPI system;
				if (fleet != null) {
					system = route.getActiveFleet().getStarSystem();
					if (system == null)
					{
						system = Misc.getNearestStarSystem(from);
					}
				}
				else {
					system = Misc.getNearestStarSystem(from);
				}
				if (system == null) break;
				destination = system.getCenter();
				
				travelTime = RouteLocationCalculator.getTravelDays(from, destination);
				travelSeg = new RouteManager.RouteSegment(task.time + travelTime, destination);
				actionSeg = new RouteManager.RouteSegment(task.time, destination);
				break;
		}
		
		// if joining a raid, try to make sure we arrive at the same time as them
		// instead of showing up super early and potentially getting whacked
		if (task.type == TaskType.ASSIST_RAID) {
			float delay = task.raid.getETA() - travelTime;
			if (delay > 0) {
				RouteManager.RouteSegment wait = new RouteManager.RouteSegment(task.raid.getETA(), from);
				wait.custom = SpecialForcesAssignmentAI.CUSTOM_DELAY_BEFORE_RAID;
				route.addSegment(wait);
			}
		}
		
		// don't have a travel segment if fleet is already in target system
		//if (destination != null && fleet != null && fleet.getContainingLocation() == destination.getContainingLocation())
		//	travelSeg = null;
		
		if (task.type == TaskType.ASSEMBLE)
			travelSeg = null;
		
		if (travelSeg != null) {
			route.addSegment(travelSeg);
		}
		if (actionSeg != null) {
			route.addSegment(actionSeg);
		}
		
		if (fleet != null) {
			fleet.clearAssignments();
		}
		
		sf.sendUpdateIfPlayerHasIntel(SpecialForcesIntel.NEW_ORDERS_UPDATE, false, false);
	}
	
	public SpecialForcesTask generateRaidDefenseTask(RaidIntel raid, float priority) {
		SpecialForcesTask task = new SpecialForcesTask(TaskType.DEFEND_RAID, priority);
		task.raid = raid;
		task.system = task.raid.getSystem();
		if (task.raid instanceof OffensiveFleetIntel) {
			task.market = ((OffensiveFleetIntel)task.raid).getTarget();
		}
		task.time = 30 + raid.getETA();
		return task;
	}
	
	public SpecialForcesTask generateRaidAssistTask(RaidIntel raid, float priority) {
		SpecialForcesTask task = new SpecialForcesTask(TaskType.ASSIST_RAID, priority);
		task.raid = raid;
		task.system = task.raid.getSystem();
		if (task.raid instanceof OffensiveFleetIntel) {
			task.market = ((OffensiveFleetIntel)task.raid).getTarget();
		}
		task.time = 30;	// don't add ETA here, apply it as a delay instead
		return task;
	}
	
	public SpecialForcesTask generatePatrolTask(MarketAPI market, float priority) {
		SpecialForcesTask task = new SpecialForcesTask(TaskType.PATROL, priority);
		task.system = market.getStarSystem();
		task.market = market;
		return task;
	}
	
	/**
	 * Picks a task for the task force to do.
	 * @param priorityDefenseOnly Only check for any urgent defense tasks that 
	 * should take priority over what we're currently doing.
	 * @return 
	 */
	public SpecialForcesTask pickTask(boolean priorityDefenseOnly) 
	{
		sf.debugMsg("Picking task for " + sf.getFleetNameForDebugging(), false);
		
		// TODO: first of all check if we want resupply
		
		
		// check for priority defense missions
		List<Pair<RaidIntel, Float>> hostileRaids = new ArrayList<>();
		for (RaidIntel raid : getActiveRaidsHostile()) {
			if (raid.getETA() > MAX_RAID_ETA_TO_CARE) continue;
			hostileRaids.add(new Pair<>(raid, getRaidDefendPriority(raid)));
		}
		
		boolean isBusy = currentTask != null && currentTask.type.isBusyTask();
		
		Pair<RaidIntel, Float> priorityDefense = pickPriorityDefendTask(hostileRaids, isBusy);
		if (priorityDefense != null) {
			SpecialForcesTask task = generateRaidDefenseTask(priorityDefense.one, priorityDefense.two);
			return task;
		}
		
		// no high priority defense, look for another task
		if (priorityDefenseOnly)
			return null;
		
		WeightedRandomPicker<SpecialForcesTask> picker = new WeightedRandomPicker<>();
		
		// Defend vs. raid
		for (Pair<RaidIntel, Float> raid : hostileRaids) {
			picker.add(generateRaidDefenseTask(raid.one, raid.two), raid.two);
		}
		
		// Assist raid
		for (RaidIntel raid : getActiveRaidsFriendly()) {
			if (raid.getETA() > MAX_RAID_ETA_TO_CARE) continue;
			float priority = getRaidAttackPriority(raid);
			picker.add(generateRaidAssistTask(raid, priority), priority);
		}
		
		// Patrol
		List<MarketAPI> alliedMarkets = getAlliedMarkets();
		for (MarketAPI market : alliedMarkets) {
			float priority = getPatrolPriority(market);
			picker.add(generatePatrolTask(market, priority), priority);
		}
		
		// idle
		if (picker.isEmpty()) {
			SpecialForcesTask task = new SpecialForcesTask(TaskType.IDLE, 0);
			task.time = 15;
			return task;
		}
		
		return picker.pick();
	}
	
	public List<MarketAPI> getAlliedMarkets() {
		String factionId = sf.faction.getId();
		List<MarketAPI> alliedMarkets;
		if (AllianceManager.getFactionAlliance(factionId) != null) {
			alliedMarkets = AllianceManager.getFactionAlliance(factionId).getAllianceMarkets();
		}
		else
			alliedMarkets = ExerelinUtilsFaction.getFactionMarkets(factionId);
		
		return alliedMarkets;
	}
	
	/**
	 * Picks the highest-priority raid for a priority defense assignment, if any exceed the needed priority threshold.
	 * If no raid is picked, the task force may still randomly pick a raid to defend against.
	 * @param raids
	 * @param isBusy
	 * @return
	 */
	protected Pair<RaidIntel, Float> pickPriorityDefendTask(List<Pair<RaidIntel, Float>> raids, boolean isBusy) 
	{
		Pair<RaidIntel, Float> highest = null;
		float highestScore = currentTask != null ? currentTask.priority : 0;
		if (isBusy) highestScore *= 2;
		float minimum = getPriorityNeededForUrgentDefense(isBusy);
		
		for (Pair<RaidIntel, Float> entry : raids) {
			float score = entry.two;
			if (score < minimum) continue;
			if (score > highestScore) {
				highestScore = score;
				highest = entry;
			}
		}
		
		return highest;
	}
	
	/**
	 * Check if we should be doing something else.
	 */
	public void updateTaskIfNeeded() 
	{
		sf.debugMsg("Checking " + sf.getFleetNameForDebugging() + " for task change", false);
		TaskType taskType = currentTask == null ? TaskType.IDLE : currentTask.type;
		
		if (taskType == TaskType.REBUILD || taskType == TaskType.ASSEMBLE)
			return;
		
		boolean wantNewTask = false;
		
		if (taskType == TaskType.IDLE) {
			wantNewTask = true;
		}
		// We were assigned to help or defend against a raid, but it's already ended
		else if (currentTask != null && (taskType == TaskType.ASSIST_RAID 
				|| taskType == TaskType.DEFEND_RAID)) {
			RaidIntel raid = currentTask.raid;
			
			if (raid != null) 
			{
				if (raid.isEnding() || raid.isEnded())
				{
					wantNewTask = true;
				}
			}
		}
		
		if (!wantNewTask) 
		{
			// check if there's a defend task we should divert to
			if (taskType == TaskType.RAID 
				|| taskType == TaskType.ASSIST_RAID
				|| taskType == TaskType.HUNT_PLAYER
				|| taskType == TaskType.PATROL) 
			{
				SpecialForcesTask task = pickTask(true);
				if (task != null) {
					assignTask(task);
					return;
				}
			}
		}
		else {
			SpecialForcesTask task = pickTask(false);
			if (task != null) {
				assignTask(task);
			}
		}
	}
	
	/**
	 * Are we actually close enough to the target entity to execute the ordered task?
	 * @return
	 */
	public boolean isCloseEnoughForTask() {
		CampaignFleetAPI fleet = sf.route.getActiveFleet();
		if (fleet == null) return true;
		
		SectorEntityToken target = currentTask.market.getPrimaryEntity();
		return MathUtils.getDistance(fleet, target) < 250;	// FIXME
	}
	
	public void notifyRouteFinished() {
		sf.debugMsg("Route finished, looking for new task", false);
		
		if (currentTask == null) {
			pickTask(false);
			return;
		}
		
		if (currentTask.type == TaskType.REBUILD) 
		{
			// Not close enough, wait a while longer
			if (!isCloseEnoughForTask()) {
				sf.route.getSegments().clear();
				sf.route.setCurrent(null);
				sf.route.addSegment(new RouteManager.RouteSegment(currentTask.time * 0.5f, 
						currentTask.market.getPrimaryEntity()));
				return;
			}
			
			sf.executeRebuildOrder();
			// spend a few days orbiting the planet, to shake down the new members
			SpecialForcesTask task = new SpecialForcesTask(TaskType.ASSEMBLE, 100);
			task.market = currentTask.market;
			task.time = 2;
			assignTask(task);
			return;
		}
		
		currentTask = null;
		pickTask(false);
	}
	
	public void advance(float amount) {
		float days = Global.getSector().getClock().convertToDays(amount);
		recheckTaskInterval.advance(amount);
		if (recheckTaskInterval.intervalElapsed()) {
			updateTaskIfNeeded();
		}
	}
	
	/**
	 * Gets the priority level for defending against the specified raid-type event.
	 * @param raid
	 * @return
	 */
	public float getRaidDefendPriority(RaidIntel raid) {
		List<MarketAPI> targets = new ArrayList<>();
		float mult = 1;
		
		if (raid instanceof OffensiveFleetIntel) {
			OffensiveFleetIntel ofi = (OffensiveFleetIntel)raid;
			
			// raid: assign values for all allied markets contained in system
			if (raid instanceof NexRaidIntel) {
				for (MarketAPI market : Global.getSector().getEconomy().getMarkets(ofi.getTarget().getContainingLocation()))
				{
					if (!AllianceManager.areFactionsAllied(market.getFactionId(), sf.faction.getId()))
						continue;
					targets.add(market);
				}
			}
			else {
				targets.add(ofi.getTarget());
			}
			
			if (raid instanceof InvasionIntel)
				mult = 6;
			else if (raid instanceof SatBombIntel)
				mult = 8;
		}
		
		float priority = 0;
		for (MarketAPI market : targets) {
			priority += getRaidDefendPriority(market);
		}
		priority *= mult;
		
		return priority;
	}
	
	/**
	 * Gets the priority level for assisting the specified raid-type event.
	 * @param raid
	 * @return
	 */
	public float getRaidAttackPriority(RaidIntel raid) {
		List<MarketAPI> targets = new ArrayList<>();
		float mult = 1;
		
		if (raid instanceof OffensiveFleetIntel) {
			OffensiveFleetIntel ofi = (OffensiveFleetIntel)raid;
			
			// raid: assign values for all hostile markets contained in system
			if (raid instanceof NexRaidIntel) {
				for (MarketAPI market : Global.getSector().getEconomy().getMarkets(ofi.getTarget().getContainingLocation()))
				{
					if (market.getFaction().isHostileTo(sf.faction))
						continue;
					targets.add(market);
				}
			}
			else {
				targets.add(ofi.getTarget());
			}
			
			if (raid instanceof InvasionIntel)
				mult = 3;
			else if (raid instanceof SatBombIntel)
				mult = 3;
		}
		
		float priority = 0;
		for (MarketAPI market : targets) {
			priority += getRaidAttackPriority(market);
		}
		priority *= mult;
		
		return priority;
	}
	
	/**
	 * Gets the priority for defending the specified market against a raid-type event.
	 * @param market
	 * @return
	 */
	public float getRaidDefendPriority(MarketAPI market) {
		float priority = market.getSize() * market.getSize();
		if (Nex_FactionDirectory.hasHeavyIndustry(market))
			priority *= 4;
		
		sf.debugMsg("  Defending market " + market.getName() + " has priority " + String.format("%.1f", priority), true);
		return priority;
	}
	
	/**
	 * Gets the priority for attacking the specified market during a raid-type event.
	 * @param market
	 * @return
	 */
	public float getRaidAttackPriority(MarketAPI market) {
		float priority = market.getSize() * market.getSize();
		if (Nex_FactionDirectory.hasHeavyIndustry(market))
			priority *= 3;
		
		sf.debugMsg("  Attacking market " + market.getName() + " has priority " + String.format("%.1f", priority), true);
		return priority;
	}
	
	/**
	 * Gets the priority for patrolling this market in the absence of raiding activity.
	 * @param market
	 * @return
	 */
	public float getPatrolPriority(MarketAPI market) {
		float priority = market.getSize() * market.getSize();
		if (Nex_FactionDirectory.hasHeavyIndustry(market))
			priority *= 4;
		if (market.getFaction() != sf.faction)	// lower priority for allies' markets
			priority *= 0.75f;
		
		// TODO: include term for distance
		
		// pirate, Stormhawk, etc. activity
		if (market.hasCondition(Conditions.PIRATE_ACTIVITY))
			priority *= 2;
		if (market.hasCondition("vayra_raider_activity"))
			priority *= 2;
		
		// high interest in patrolling locations where a hostile player is
		if (Global.getSector().getPlayerFaction().isHostileTo(sf.faction) 
				&& Global.getSector().getPlayerFleet().getContainingLocation() == market.getContainingLocation())
			priority *= 3;
		
		float def = InvasionFleetManager.estimatePatrolStrength(market, 0.5f) 
				+ InvasionFleetManager.estimateStationStrength(market);
		priority *= 100/def;
		sf.debugMsg("  Patrolling market " + market.getName() + " has priority " 
				+ String.format("%.1f", priority) + "; defensive rating " + String.format("%.1f", def), true);
		return priority;
	}
	
	/**
	 * A raid must have at least this much defend priority for the special forces unit to be tasked
	 * to defend against it. Required priority will be increased if we already have another task.
	 * @param isBusy True if we're checking whether to cancel an existing busy-type assignment.
	 * @return
	 */
	public float getPriorityNeededForUrgentDefense(boolean isBusy) {
		int aggro = sf.faction.getDoctrine().getAggression();
		switch (aggro) {
			case 5:
				if (isBusy) return 9999999;
				return 50;
			default:
				return 8 * aggro * (isBusy ? 2 : 1);
		}
	}
	
	public void addInitialTask() {
		float orbitDays = 1 + sf.startingFP * 0.02f * (0.75f + (float) Math.random() * 0.5f);
		
		SpecialForcesTask task = new SpecialForcesTask(TaskType.ASSEMBLE, 100);
		task.market = sf.origin;
		task.time = orbitDays;
		assignTask(task);
	}
	
	public enum TaskType {
		RAID, PATROL, ASSIST_RAID, DEFEND_RAID, REBUILD, HUNT_PLAYER, ASSEMBLE, IDLE;
		
		/**
		 * Returns true for tasks we don't like to "put down", 
		 * i.e. reassignment would be considered inconvenient.
		 * @return
		 */
		public boolean isBusyTask() {
			return this == RAID || this == ASSIST_RAID || this == DEFEND_RAID;
		}
	}
	
	public static class SpecialForcesTask {
		public TaskType type;
		public float priority;
		public RaidIntel raid;
		public float time = 45;	// controls how long the action segment lasts
		public MarketAPI market;
		public StarSystemAPI system;
		public Map<String, Object> params = new HashMap<>();
		
		public SpecialForcesTask(TaskType type, float priority) {
			this.type = type;
			this.priority = priority;
		}
		
		/**
		 * Returns a string describing the task.
		 * @return
		 */
		public String getText() {
			switch (type) {
				case RAID:
					return "raiding " + market.getName();
				case ASSIST_RAID:
					return "assisting raid " + raid.getName();
				case DEFEND_RAID:
					return "defending vs. raid " + raid.getName();
				case PATROL:
					return "patrolling " + (market != null? market.getName() : system.getNameWithLowercaseType());
				case REBUILD:
					return "reconstituting fleet at " + market.getName();
				case ASSEMBLE:
					return "assembling at " + market.getName();
				case IDLE:
					return "idle";
				default:
					return "unknown";
			}
		}
	}
}