package exerelin.campaign.events;

import com.fs.starfarer.api.impl.campaign.events.InvestigationEvent;

public class ExerelinInvestigationEvent extends InvestigationEvent {
    
    protected String factionId = "neutral";
    
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
        factionId = market.getFactionId();
    }
    
    @Override
    public void advance(float amount) {
        if (!isEventStarted()) return;
        if (isDone()) return;

        // market has changed hands since investigation started; kill it
        if (!market.getFactionId().equals(factionId))
        {
            super.endEvent();
            return;
        }
        
        super.advance(amount);
    }
}