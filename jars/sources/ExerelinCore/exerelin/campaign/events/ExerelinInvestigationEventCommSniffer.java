package exerelin.campaign.events;

import com.fs.starfarer.api.impl.campaign.events.InvestigationEventCommSniffer;

public class ExerelinInvestigationEventCommSniffer extends InvestigationEventCommSniffer {
    
    @Override
    public void startEvent() {
        //String alignedFactionId = PlayerFactionStore.getPlayerFactionId();
        if (market != null) {
            String marketFactionId = market.getFactionId();
            if (marketFactionId.equals("player_npc"))
            {
                log.info("Investigation by own faction; aborting");
                endEvent();
                return;
            }
        }
        super.startEvent();
    }
}