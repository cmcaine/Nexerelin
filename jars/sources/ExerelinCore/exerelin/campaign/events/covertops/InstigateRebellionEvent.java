package exerelin.campaign.events.covertops;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.util.Misc;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class InstigateRebellionEvent extends CovertOpsEventBase {
	
	@Override
	public Map<String, String> getTokenReplacements() {
		Map<String, String> map = super.getTokenReplacements();
		map.put("$timeFrame", (String)params.get("timeFrame") + "");
		
		return map;
	}
	
	@Override
	public String[] getHighlights(String stageId) {
		List<String> result = new ArrayList<>();
		if (stageId.startsWith("success"))
			addTokensToList(result, "$timeFrame");
		if (stageId.contains("detected"))
		{
			addTokensToList(result, "$repEffectAbs");
			addTokensToList(result, "$newRelationStr");
		}
		
		return result.toArray(new String[0]);
	}
	
	@Override
	public Color[] getHighlightColors(String stageId) {
		List<Color> result = new ArrayList<>();
		if (stageId.startsWith("success"))
			result.add(Misc.getHighlightColor());
		if (stageId.contains("detected"))
		{
			result.add(repEffect > 0 ? Global.getSettings().getColor("textFriendColor") : Global.getSettings().getColor("textEnemyColor"));
			result.add(agentFaction.getRelColor(faction.getId()));
		}
		return result.toArray(new Color[0]);
	}
}