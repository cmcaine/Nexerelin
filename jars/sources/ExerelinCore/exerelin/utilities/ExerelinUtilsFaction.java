package exerelin.utilities;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.util.Misc;

import java.util.ArrayList;
import java.util.List;

public class ExerelinUtilsFaction {

    

    public static boolean doesFactionOwnSystem(String factionId, StarSystemAPI system)
    {
        for(MarketAPI market : Misc.getMarketsInLocation(system))
        {
            if(!market.getFaction().getId().equalsIgnoreCase(factionId)
                    && !market.getFaction().getId().equalsIgnoreCase("neutral")
                    && !market.getFaction().getId().equalsIgnoreCase("independent"))
            {
                return false;
            }
        }

        return true;
    }
    
    public static List<MarketAPI> getFactionMarkets(String factionId)
    {
        List<MarketAPI> allMarkets = Global.getSector().getEconomy().getMarketsCopy();
        List<MarketAPI> ret = new ArrayList<>();
        for (MarketAPI market : allMarkets)
        {
            if (market.getFactionId().equals(factionId))
                ret.add(market);
        }
        return ret;
    }

}
