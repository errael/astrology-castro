/* Copyright © 2023 Ernie Rael. All rights reserved */

package com.raelity.astrolog.castro;

import java.util.List;

import org.antlr.v4.runtime.misc.Interval;

/**
 *
 * @author err
 */
public class LineMap
{
private final WriteableLineMap wlm;

public LineMap(WriteableLineMap wlm)
{
    this.wlm = wlm;
}


public Interval getInterval(int line)
{
    return wlm.getInterval(line);
}

    public static class WriteableLineMap
    {
    private final List<Interval> lineList;
    private final LineMap lm;
    
    public WriteableLineMap(List<Interval> lineList)
    {
        this.lineList = lineList;
        lm = new LineMap(this);
    }
    
    public LineMap getLineMap()
    {
        return lm;
    }
    
    public Interval getInterval(int line) {
        Interval interval = null;
        if(lineList != null && lineList.size() > line)
            interval = lineList.get(line);
        return interval;
    }
    
    Interval insureLine(int line)
    {
        while(lineList.size() < line + 1)
            lineList.add(null);
        return lineList.get(line);
    }

    public void includeLineStart(int line, int index)
    {
        Interval interval = insureLine(line);
        // If interval is not null, the beginning of line is already set
        if(interval == null)
            lineList.set(line, new Interval(index, index));
        
    }

    public void includeLineStop(int line, int index)
    {
        Interval interval = insureLine(line);
        if(index > interval.b)
            lineList.set(line, new Interval(interval.a, index));
        
    }
    }

}
