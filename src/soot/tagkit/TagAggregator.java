package soot.tagkit;

public interface TagAggregator {
    
    public void aggregateTag(Tag t, Unit u);    
    public Tag produceAggregateTag();
    
}