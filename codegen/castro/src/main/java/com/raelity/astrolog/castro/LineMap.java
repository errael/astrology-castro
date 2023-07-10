/* Copyright © 2023 Ernie Rael. All rights reserved */

package com.raelity.astrolog.castro;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.antlr.v4.runtime.misc.Interval;

import com.raelity.astrolog.castro.Castro.CastroLineMaps;


/**
 * A map of line number to Interval for a file;
 * also publishes a map of fileName to LineMap.
 * A LineMap is added when it is created, and populated
 * during the file's pass1.
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

public String getFileName()
{
    return wlm.fileName;
}

    public static class WriteableLineMap
    {
    private final List<Interval> lineList;
    private final LineMap lm;
    private final String fileName;
    private static Map<String,LineMap> lineMaps;
    
    public static WriteableLineMap createLineMap(String fileName)
    {
        return new WriteableLineMap(new ArrayList<>(100), fileName);
    }

    private WriteableLineMap(List<Interval> lineList, String fileName)
    {
        this.lineList = lineList;
        this.fileName = fileName;
        if(lineMaps == null) {
            lineMaps = new HashMap<>();
            Util.addLookup(new CastroLineMaps(Collections.unmodifiableMap(lineMaps)));
        }
        lm = new LineMap(this);
        if(lineMaps.containsKey(fileName))
            throw new IllegalStateException(String.format(
                    "LineMap for %s already created\n", fileName));
        lineMaps.put(fileName, lm);
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
