/*
    Copyright 2012 The Whitehole team

    This file is part of Whitehole.

    Whitehole is free software: you can redistribute it and/or modify it under
    the terms of the GNU General Public License as published by the Free
    Software Foundation, either version 3 of the License, or (at your option)
    any later version.

    Whitehole is distributed in the hope that it will be useful, but WITHOUT ANY 
    WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS 
    FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.

    You should have received a copy of the GNU General Public License along 
    with Whitehole. If not, see http://www.gnu.org/licenses/.
*/

package whitehole.smg;

import java.io.IOException;
import java.util.LinkedHashMap;
import javax.media.opengl.GL2;
import javax.media.opengl.GLException;
import whitehole.rendering.GLRenderer;
import whitehole.vectors.Color4;
import whitehole.vectors.Vector3;

public class PathObject 
{
    public PathObject(ZoneArchive zone, Bcsv.Entry entry)
    {
        this.zone = zone;
        
        data = entry;
        uniqueID = -1;
        
        index = (int)(short)data.get("no");
        pathID = (int)data.get("l_id");
        
        int npoints = (int)data.get("num_pnt");
        points = new LinkedHashMap<>(npoints);
        
        try
        {
            Bcsv pointsfile = new Bcsv(zone.archive.openFile(String.format("/Stage/jmp/Path/CommonPathPointInfo.%1$d", index)));
            for (Bcsv.Entry pt : pointsfile.entries)
            {
                PathPointObject ptobj = new PathPointObject(this, pt);
                points.put(ptobj.index, ptobj);
            }
            pointsfile.close();
        }
        catch (IOException ex)
        {
            System.out.println(String.format("Failed to load path points for path %1$d: %2$s", index, ex.getMessage()));
            points.clear();
        }
    }
    
    
    public void prerender(GLRenderer.RenderInfo info)
    {
        displayLists = new int[2];
        
        GL2 gl = info.drawable.getGL().getGL2();
        
        // DISPLAY LIST 0 -- path rendered in picking mode (just the points)
        
        displayLists[0] = gl.glGenLists(1);
        gl.glNewList(displayLists[0], GL2.GL_COMPILE);
        
        for (PathPointObject point : points.values())
        {
            gl.glBegin(GL2.GL_POINTS);

            gl.glPointSize(8f);
            int uniqueid = point.uniqueID;
            gl.glColor4ub(
                (byte)(uniqueid >>> 16), 
                (byte)(uniqueid >>> 8), 
                (byte)uniqueid, 
                (byte)0xFF);
            gl.glVertex3f(point.point0.x, point.point0.y, point.point0.z);

            gl.glPointSize(6f);
            uniqueid++;
            gl.glColor4ub(
                (byte)(uniqueid >>> 16), 
                (byte)(uniqueid >>> 8), 
                (byte)uniqueid, 
                (byte)0xFF);
            if (Vector3.roughlyEqual(point.point0, point.point1))
                gl.glVertex3f(point.point1.x+50f, point.point1.y+50f, point.point1.z+50f);
            else
                gl.glVertex3f(point.point1.x, point.point1.y, point.point1.z);
            
            uniqueid++;
            gl.glColor4ub(
                (byte)(uniqueid >>> 16), 
                (byte)(uniqueid >>> 8), 
                (byte)uniqueid, 
                (byte)0xFF);
            if (Vector3.roughlyEqual(point.point0, point.point2))
                gl.glVertex3f(point.point2.x+50f, point.point2.y+50f, point.point2.z+50f);
            else
                gl.glVertex3f(point.point2.x, point.point2.y, point.point2.z);
            
            gl.glEnd();
        }
        
        gl.glEndList();
        
        // DISPLAY LIST 1 -- shows the path fully
        
        displayLists[1] = gl.glGenLists(1);
        gl.glNewList(displayLists[1], GL2.GL_COMPILE);
        
        for (int i = 0; i < 8; i++)
        {
            gl.glActiveTexture(GL2.GL_TEXTURE0 + i);
            gl.glDisable(GL2.GL_TEXTURE_2D);
        }

        gl.glDepthFunc(GL2.GL_LEQUAL);
        gl.glDepthMask(true);
        gl.glDisable(GL2.GL_LIGHTING);
        gl.glEnable(GL2.GL_BLEND);
        gl.glBlendFunc(GL2.GL_SRC_COLOR, GL2.GL_ONE_MINUS_SRC_COLOR);
        gl.glDisable(GL2.GL_COLOR_LOGIC_OP);
        gl.glDisable(GL2.GL_ALPHA_TEST);
        try { gl.glUseProgram(0); } catch (GLException ex) { }
        gl.glEnable(GL2.GL_POINT_SMOOTH);
        gl.glHint(GL2.GL_POINT_SMOOTH_HINT, GL2.GL_NICEST);
        
        Color4 pcolor = pathcolors[index % pathcolors.length];
        gl.glColor4f(pcolor.r, pcolor.g, pcolor.b, 0.8f);
        
        for (PathPointObject point : points.values())
        {
            gl.glBegin(GL2.GL_POINTS);

            gl.glPointSize(8f);
            gl.glVertex3f(point.point0.x, point.point0.y, point.point0.z);

            gl.glPointSize(6f);
            if (Vector3.roughlyEqual(point.point0, point.point1))
                gl.glVertex3f(point.point1.x+50f, point.point1.y+50f, point.point1.z+50f);
            else
                gl.glVertex3f(point.point1.x, point.point1.y, point.point1.z);
            
            if (Vector3.roughlyEqual(point.point0, point.point2))
                gl.glVertex3f(point.point2.x+50f, point.point2.y+50f, point.point2.z+50f);
            else
                gl.glVertex3f(point.point2.x, point.point2.y, point.point2.z);
            
            gl.glEnd();
            
            gl.glLineWidth(1f);
            gl.glBegin(GL2.GL_LINE_STRIP);
            gl.glVertex3f(point.point1.x, point.point1.y, point.point1.z);
            gl.glVertex3f(point.point0.x, point.point0.y, point.point0.z);
            gl.glVertex3f(point.point2.x, point.point2.y, point.point2.z);
            gl.glEnd();
        }
        
        gl.glLineWidth(1.5f);
        gl.glBegin(GL2.GL_LINE_STRIP);
        int numpnt = (int)data.get("num_pnt");
        int end = numpnt;
        if (((String)data.get("closed")).equals("CLOSE")) end++;
        for (int p = 1; p < end; p++)
        {
            int pid = p;
            Vector3 p1 = points.get(pid - 1).point0;
            Vector3 p2 = points.get(pid - 1).point2;
            if (pid >= numpnt) pid -= numpnt;
            Vector3 p3 = points.get(pid).point1;
            Vector3 p4 = points.get(pid).point0;
            
            for (float t = 0f; t < 1f; t += 0.01f)
            {
                float p1t = (1f - t) * (1f - t) * (1f - t);
                float p2t = 3 * t * (1f - t) * (1f - t);
                float p3t = 3 * t * t * (1f - t);
                float p4t = t * t * t;
                
                gl.glVertex3f(
                        p1.x * p1t + p2.x * p2t + p3.x * p3t + p4.x * p4t,
                        p1.y * p1t + p2.y * p2t + p3.y * p3t + p4.y * p4t,
                        p1.z * p1t + p2.z * p2t + p3.z * p3t + p4.z * p4t);
            }
        }
        
        gl.glEnd();
        gl.glEndList();
    }
    
    public void render(GLRenderer.RenderInfo info)
    {
        if (info.renderMode == GLRenderer.RenderMode.TRANSLUCENT) return;
        
        GL2 gl = info.drawable.getGL().getGL2();
        
        int dlid = -1;
        switch (info.renderMode)
        {
            case PICKING: dlid = 0; break;
            case OPAQUE: dlid = 1; break;
        }
        
        gl.glCallList(displayLists[dlid]);
    }
    
    @Override
    public String toString()
    {
        return String.format("[%1$d] %2$s", pathID, data.get("name"));
    }
    
    
    private final Color4[] pathcolors = {
        new Color4(1f, 0.3f, 0.3f, 1f),
        new Color4(0.3f, 1f, 0.3f, 1f),
        new Color4(0.3f, 0.3f, 1f, 1f),
        new Color4(1f, 1f, 0.3f, 1f),
        new Color4(0.3f, 1f, 1f, 1f),
        new Color4(1f, 0.3f, 1f, 1f),
    };
    
    public ZoneArchive zone;
    public Bcsv.Entry data;
    public int[] displayLists;
    
    public int uniqueID;
    
    public int index;
    public int pathID;
    
    public LinkedHashMap<Integer, PathPointObject> points;
}
