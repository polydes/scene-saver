package com.polydes.scenesaver;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;

import stencyl.core.lib.Game;
import stencyl.core.lib.io.read.SceneReader;
import stencyl.core.lib.scene.SceneModel;
import stencyl.core.lib.scene.Terrain;
import stencyl.core.lib.scene.TerrainLayer;
import stencyl.core.lib.scene.TileData;
import stencyl.sw.SW;
import stencyl.sw.app.gamecontroller.GameInterfaceServer.ClientConnectionListener;
import stencyl.sw.app.gamecontroller.GameInterfaceServer.GamePacketListener;
import stencyl.sw.app.gamecontroller.SocketDataParser.Packet;
import stencyl.sw.editors.scene.Designer;
import stencyl.sw.editors.scene.EditorSceneModel;
import stencyl.sw.editors.scene.SceneTab;
import stencyl.sw.ext.BaseExtension;
import stencyl.sw.ext.OptionsPanel;
import stencyl.sw.prefs.runconfigs.BuildConfig;
import stencyl.sw.util.Locations;
import stencyl.sw.util.Util;

public class SceneSaverExtension extends BaseExtension implements ClientConnectionListener, GamePacketListener
{
	private static final Logger log = Logger.getLogger(SceneSaverExtension.class);
	
	@Override
	public void onStartup()
	{
		super.onStartup();
		
//		isInMenu = false;
//		menuName = "Engine Scene Saver";
//		
//		isInGameCenter = false;
//		gameCenterName = "Engine Scene Saver";
	}

	@Override
	public void onActivate()
	{
		
	}

	@Override
	public void onGameOpened(Game game)
	{
		SW.get().getGameInterfaceServer().addConnectionsListener(this);
	}
	
	@Override
	public void onGameClosed(Game game)
	{
		SW.get().getGameInterfaceServer().removeConnectionsListener(this);
	}

	@Override
	public void onGameSave(Game game)
	{
		
	}

	@Override
	public void onInstall()
	{
		
	}

	@Override
	public void onUninstall()
	{
		
	}
	
	@Override
	protected boolean hasOptions()
	{
		return false;
	}

	@Override
	public OptionsPanel onOptions()
	{
		return null;
	}
	
	@Override
	public void clientConnected(BuildConfig cfg)
	{
		SW.get().getGameInterfaceServer().addMessagesListener(cfg, this);
	}
	
	@Override
	public void clientDisconnected(BuildConfig cfg)
	{
		SW.get().getGameInterfaceServer().removeMessagesListener(cfg, this);
	}
	
	@Override
	public void packetReceived(Packet packet)
	{
		if(packet.header.get("Content-Type").equals("com.polydes.scenesaver.scn"))
		{
			int sceneID = Util.parseInt(packet.header.get("Scene-Id"), -1);
			SceneModel scene = Game.getGame().getScene(sceneID);
			if(scene != null)
			{
				String url = Locations.getSceneDataLocation(sceneID);
				String sceneURL = Locations.getGameLocation(Game.getGame()) + url;
				
//				try
//				{
//					var firstWrongByte = Arrays.compare(
//						Files.readAllBytes(Path.of(sceneURL)),
//						packet.data
//					);
//					log.info("Bad bytes? " + firstWrongByte);
//					Files.write(Path.of(sceneURL+"2"), packet.data);
//				}
//				catch (IOException e1)
//				{
//					log.error(e1.getMessage(), e1);
//				}
				
				try
				{
					FileUtils.writeByteArrayToFile(new File(sceneURL), packet.data);
				}
				catch (IOException e)
				{
					log.error(e.getMessage(), e);
				}
			}
			
			if(SW.get().getWorkspace().isResourceOpen(scene))
			{
				var editor = SW.get().getWorkspace().getDocumentForResource(scene).getResourceEditor();
				SceneTab sceneTab = (SceneTab) editor.getEditorComponent();
				Designer designer = sceneTab.getCanvas();
				EditorSceneModel editorModel = designer.getModel();
				SceneModel model = editorModel.getModel();
				
				Terrain terrain = null;
				
				try
				{
					Method readData = SceneReader.class.getDeclaredMethod("readData", Game.class, SceneModel.class);
					readData.setAccessible(true);
					terrain = (Terrain) readData.invoke(null, Game.getGame(), model);
				}
				catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e)
				{
					log.error(e.getMessage(), e);
				}
				
				if(terrain != null)
				{
					for(TerrainLayer l : terrain.getAllLayers())
					{
						for(int x = 0; x < model.getWidthInTiles(); x++)
						{
							for(int y = 0; y < model.getHeightInTiles(); y++)
							{
								TileData td = l.getTileDataAt(x, y);
								
								if(td != null)
								{
									editorModel.setTileAt(l.getLayerID(), x, y, td.tile, td.autotileIndex, td.explicitAutotileIndex);
								}
								else
								{
									editorModel.removeTileAt(l.getLayerID(), x, y);
								}
							}
						}
					}
				}
				
				designer.repaint();
			}
		}
	}
}