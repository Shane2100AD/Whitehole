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

import java.io.*;
import java.util.*;
import whitehole.fileio.*;

public class ZoneArchive 
{
    public ZoneArchive(GalaxyArchive arc, String name) throws IOException
    {
        galaxy = arc;
        game = arc.game;
        filesystem = game.filesystem;
        
        zoneName = name;
        
        // try SMG2-style first, then SMG1
        if (filesystem.fileExists("/StageData/" + zoneName + "/" + zoneName + "Map.arc"))
        {
            // SMG2-style zone
            // * /StageData/<zoneName>/<zoneName>Design.arc -> ???
            // * /StageData/<zoneName>/<zoneName>Map.arc -> holds map objects
            // * /StageData/<zoneName>/<zoneName>Sound.arc -> seems to hold sound-related objects
            
            gameMask = 2;
            zonefile = "/StageData/" + zoneName + "/" + zoneName + "Map.arc";
        }
        else
        {
            // SMG1-style zone
            // * /StageData/<zoneName>.arc -> holds all map objects
            
            gameMask = 1;
            zonefile = "/StageData/" + zoneName + ".arc";
        }
        
        loadZone();
    }
    
    public ZoneArchive(GameArchive game, String name) throws IOException
    {
        galaxy = null;
        this.game = game;
        filesystem = game.filesystem;
        
        zoneName = name;
        
        // try SMG2-style first, then SMG1
        if (filesystem.fileExists("/StageData/" + zoneName + "/" + zoneName + "Map.arc"))
        {
            // SMG2-style zone
            // * /StageData/<zoneName>/<zoneName>Design.arc -> ???
            // * /StageData/<zoneName>/<zoneName>Map.arc -> holds map objects
            // * /StageData/<zoneName>/<zoneName>Sound.arc -> seems to hold sound-related objects
            
            gameMask = 2;
            zonefile = "/StageData/" + zoneName + "/" + zoneName + "Map.arc";
        }
        else
        {
            // SMG1-style zone
            // * /StageData/<zoneName>.arc -> holds all map objects
            
            gameMask = 1;
            zonefile = "/StageData/" + zoneName + ".arc";
        }
        
        loadZone();
    }
    
    public void save() throws IOException
    {
        archive = new RarcFilesystem(filesystem.openFile(zonefile));
        
        saveObjects("MapParts", "MapPartsInfo");
        saveObjects("Placement", "ObjInfo");
        
        archive.close();
    }
    
    public void close()
    {
    }
    
    
    private void loadZone() throws IOException
    {
        objects = new HashMap<>();
        subZones = new HashMap<>();
        archive = new RarcFilesystem(filesystem.openFile(zonefile));
        
        loadObjects("MapParts", "MapPartsInfo");
        loadObjects("Placement", "ObjInfo");
        
        loadPaths();
        
        loadSubZones();
        
        archive.close();
    }
    
    private void loadObjects(String dir, String file)
    {
        List<String> layers = archive.getDirectories("/Stage/Jmp/" + dir);
        for (String layer : layers)
            addObjectsToList(dir + "/" + layer + "/" + file);
    }
    
    private void saveObjects(String dir, String file)
    {
        List<String> layers = archive.getDirectories("/Stage/Jmp/" + dir);
        for (String layer : layers)
            saveObjectList(dir + "/" + layer + "/" + file);
    }
    
    private void addObjectsToList(String filepath)
    {
        String layer = filepath.split("/")[1].toLowerCase();
        
        if (!objects.containsKey(layer))
            objects.put(layer, new ArrayList<LevelObject>());
        
        try
        {
            Bcsv bcsv = new Bcsv(archive.openFile("/Stage/Jmp/" + filepath));
            for (Bcsv.Entry entry : bcsv.entries)
                objects.get(layer).add(new LevelObject(this, filepath, entry));
            bcsv.close();
        }
        catch (IOException ex)
        {
            // TODO better error handling, really
            System.out.println(ex.getMessage());
            ex.printStackTrace();
        }
    }
    
    private void saveObjectList(String filepath)
    {
        String[] stuff = filepath.split("/");
        String dir = stuff[0], file = stuff[2];
        String layer = stuff[1].toLowerCase();
        if (!objects.containsKey(layer))
            return;
        
        try
        {
            Bcsv bcsv = new Bcsv(archive.openFile("/Stage/Jmp/" + filepath));
            bcsv.entries.clear();
            for (LevelObject obj : objects.get(layer))
            {
                if (!dir.equals(obj.directory) || !file.equals(obj.file))
                    continue;
                
                obj.save();
                bcsv.entries.add(obj.data);
            }
            bcsv.save();
            bcsv.close();
        }
        catch (IOException ex)
        {
            // TODO better error handling, really
            System.out.println(ex.getMessage());
            ex.printStackTrace();
        }
    }
    
    private void loadPaths()
    {
        try
        {
            Bcsv bcsv = new Bcsv(archive.openFile("/Stage/jmp/Path/CommonPathInfo"));
            paths = new ArrayList<>(bcsv.entries.size());
            for (Bcsv.Entry entry : bcsv.entries)
                paths.add(new PathObject(this, entry));
            bcsv.close();
        }
        catch (IOException ex)
        {
            System.out.println(zoneName+": Failed to load paths: "+ex.getMessage());
        }
    }
    
    private void loadSubZones()
    {
        List<String> layers = archive.getDirectories("/Stage/Jmp/Placement");
        for (String layer : layers)
        {
            try
            {
                Bcsv bcsv = new Bcsv(archive.openFile("/Stage/Jmp/Placement/" + layer + "/StageObjInfo"));
                subZones.put(layer.toLowerCase(), bcsv.entries); // lazy lol
                bcsv.close();
            }
            catch (IOException ex)
            {
                // TODO better error handling, really
                System.out.println(ex.getMessage());
                ex.printStackTrace();
            }
        }
    }
    
    
    public GalaxyArchive galaxy;
    public GameArchive game;
    public FilesystemBase filesystem;
    public String zonefile;
    public RarcFilesystem archive;
    
    public String zoneName;
    
    public int gameMask;
    public HashMap<String, List<LevelObject>> objects;
    public List<PathObject> paths;
    public HashMap<String, List<Bcsv.Entry>> subZones;
}
