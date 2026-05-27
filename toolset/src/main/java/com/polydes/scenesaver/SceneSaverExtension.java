package com.polydes.scenesaver;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import stencyl.app.doc.IWorkspace;
import stencyl.core.SWC;
import stencyl.core.ext.GameExtension;
import stencyl.core.ext.engine.ExtensionInstanceManager.FormatUpdateSubmitter;
import stencyl.core.util.ParsingHelper;
import stencyl.sw.app.editors.scene.Designer;
import stencyl.sw.app.editors.scene.EditorSceneModel;
import stencyl.sw.app.editors.scene.SceneTab;
import stencyl.sw.core.gamesession.controller.GCIClient;
import stencyl.sw.core.gamesession.controller.GCIClient.GamePacketListener;
import stencyl.sw.core.gamesession.controller.GameInterfaceServer;
import stencyl.sw.core.gamesession.controller.GameInterfaceServer.ClientConnectionListener;
import stencyl.sw.core.gamesession.controller.SocketDataParser.Packet;
import stencyl.sw.core.lib.game.Game;
import stencyl.sw.core.lib.game.GameLocations;
import stencyl.sw.core.lib.scene.*;

public class SceneSaverExtension extends GameExtension implements ClientConnectionListener, GamePacketListener
{
	private static final Logger log = Logger.getLogger(SceneSaverExtension.class);

	@Override
	protected void onLoad() {
		SWC.get(GameInterfaceServer.class).addConnectionsListener(this);
	}

	@Override
	protected void onUnload() {
		SWC.get(GameInterfaceServer.class).removeConnectionsListener(this);
	}

	@Override
	public void clientConnected(GCIClient client) {
		client.addMessageListener(this);
	}

	@Override
	public void clientDisconnected(GCIClient client) {
		client.removeMessageListener(this);
	}

	@Override
	public void packetReceived(Packet packet)
	{
		if(packet.header.get("Content-Type").equals("com.polydes.scenesaver.scn"))
		{
			int sceneID = ParsingHelper.parseInt(packet.header.get("Scene-Id"), -1);
			SceneModel scene = getProject().getResource(SceneModel.class, sceneID);
			if(scene != null)
			{
				File sceneFile = ((GameLocations) getProject().getFiles()).getSceneDataFile(sceneID);
				
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
					FileUtils.writeByteArrayToFile(sceneFile, packet.data);
				}
				catch (IOException e)
				{
					log.error(e.getMessage(), e);
				}
			}
			
			if(SWC.get(IWorkspace.class).isResourceOpen(scene))
			{
				var editor = SWC.get(IWorkspace.class).getDocumentForResource(scene).getResourceEditor();
				SceneTab sceneTab = (SceneTab) editor.getEditorComponent();
				Designer designer = sceneTab.getCanvas();
				EditorSceneModel editorModel = designer.getModel();
				SceneModel model = editorModel.getModel();
				
				Terrain terrain = null;
				
				try
				{
					Method readData = SceneReader.class.getDeclaredMethod("readData", Game.class, SceneModel.class);
					readData.setAccessible(true);
					terrain = (Terrain) readData.invoke(null, (Game) getProject(), model);
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

	@Override
	public void updateFromVersion(int fromVersion, FormatUpdateSubmitter formatUpdateQueue) {

	}
}