package exerelin.world.industry;

import exerelin.world.ExerelinProcGen.ProcGenEntity;

// this exists so not every market gets a manufacturing industry
public class NullIndustry extends IndustryClassGen {
	
	public NullIndustry() {
		super((String)null);
	}
	
	// light industry, heavy industry and fuel production are generally about 400 weight
	@Override
	public float getWeight(ProcGenEntity entity) {
		int size = entity.market.getSize();
		if (size >= 7) return 0;
		return (8 - size) * 125f;
	}
	
	@Override
	public void apply(ProcGenEntity entity, boolean instant) {
		entity.numProductiveIndustries += 1;
	}
	
	@Override
	public boolean canApply(ProcGenEntity entity) {
		return true;
	}
}
