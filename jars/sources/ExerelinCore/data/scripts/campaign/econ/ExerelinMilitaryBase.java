package data.scripts.campaign.econ;

import com.fs.starfarer.api.impl.campaign.econ.ConditionData;
import com.fs.starfarer.api.impl.campaign.econ.MilitaryBase;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;

public class ExerelinMilitaryBase extends MilitaryBase {
	public static final float EXTRA_MARINES_MULT = 1.4f;	// hax
	public static final float FUEL_NON_CONSUMING_FRACTION = 0.5f;	// more hax	// remove in 0.7.1a
	
	public void apply(String id) {
		super.apply(id);
		if (this.market.getFactionId().equals("templars"))
		{
			market.removeSubmarket(Submarkets.GENERIC_MILITARY);
		}
		market.getCommodityData(Commodities.MARINES).getSupply().modifyFlat(id, ConditionData.MILITARY_BASE_MARINES_SUPPLY * EXTRA_MARINES_MULT);
		market.getCommodityData("agent").getSupply().modifyFlat(id, 2);
		market.getCommodityData("saboteur").getSupply().modifyFlat(id, 1);
		
		market.getDemand(Commodities.FUEL).getNonConsumingDemand().modifyFlat(id, ConditionData.MILITARY_BASE_FUEL * FUEL_NON_CONSUMING_FRACTION);
	}

	public void unapply(String id) {
		super.unapply(id);
		market.getCommodityData("agent").getSupply().unmodify(id);
		market.getCommodityData("saboteur").getSupply().unmodify(id);
		market.getDemand(Commodities.FUEL).getNonConsumingDemand().unmodify(id);
	}

}
