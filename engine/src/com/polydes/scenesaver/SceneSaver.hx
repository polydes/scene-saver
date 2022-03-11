package com.polydes.scenesaver;

import com.stencyl.models.scene.Layer;
import com.stencyl.Engine;

import openfl.utils.ByteArray;

typedef TileData = {
	var tilesetID:Int;
	var tileID:Int;
	var autotileIndex:Int;
	var explicitAutotileIndex:Bool;
}

class SceneSaver
{
	public static function saveModifiedTilesForCurrentScene():Void
	{
		#if stencyltools
		com.stencyl.utils.ToolsetInterface.instance.sendBinaryData(
			["Content-Type" => "com.polydes.scenesaver.scn",
			"Scene-Id" => "" + Engine.engine.scene.ID],
			layersToByte()
		);
		#else
		trace("Error: saving modified tiles requires the game controller feature to be enabled.");
		#end
	}

	private static function layersToByte():ByteArray
	{
		var numLayers = Engine.engine.interactiveLayers.length;

		//Transform each layer and find out the byte count for each
		var layerLengths:Array<Int> = [];
		var layerData:Array<ByteArray> = [];
		var totalLayerByteCount = 0;
		
		var tilelayers = Engine.engine.interactiveLayers.copy();
		tilelayers.reverse();

		for(layer in tilelayers)
		{
			var layerBytes = layerToByte(layer);
			var layerSize = layerBytes.length;

			layerData.push(layerBytes);
			layerLengths.push(layerSize);
			totalLayerByteCount += layerSize;
		}
		
		//Put together
		var finalBuffer = new ByteArray(totalLayerByteCount + (4 * numLayers) + 1);
		
		//Write Headers
		for(length in layerLengths)
		{
			finalBuffer.writeInt(length);
		}
		
		//Write Layers
		for(data in layerData)
		{
			finalBuffer.writeBytes(data, 0, data.length);
		}
		
		return finalBuffer;
	}

	private static function areTilesEqual(tile1:TileData, tile2:TileData):Bool
	{
		if(tile1 == null) return tile2 == null;
		if(tile2 == null) return false;

		return
			tile1.tilesetID == tile2.tilesetID &&
			tile1.tileID == tile2.tileID &&
			tile1.autotileIndex == tile2.autotileIndex &&
			tile1.explicitAutotileIndex == tile2.explicitAutotileIndex;
	}

	static var RLETILE_BYTE_COUNT = 8;
	static var SHORT_MAX_VALUE = 32767;
	
	private static function getTileData(layer:Layer, x:Int, y:Int):TileData
	{
		var tile = layer.tiles.rows[y][x];
		var autotileIndex = layer.tiles.autotileData[y][x];
		if(tile == null)
		{
			return null;
		}
		else
		{
			return {
				tilesetID: tile.parent.ID,
				tileID: tile.tileID,
				autotileIndex: autotileIndex,
				explicitAutotileIndex: false
			};
		}
	}

	private static function layerToByte(layer:Layer):ByteArray
	{
		//Used to determine the last Tile we've seen in the loop
		var lastSeen:TileData = null;
		
		//Keeps track of run length
		var runCounter = 0;
		
		var width = layer.tiles.numCols;
		var height = layer.tiles.numRows;
		
		//8 is to account for layerID and zOrder storage.
		var EXTRA = 8;
		var tileBuffer = new ByteArray(EXTRA + (RLETILE_BYTE_COUNT * width * height));
		
		tileBuffer.writeInt(layer.ID);

		//XXX: this may be different from the value the toolset places here,
		//but it's actually not used for anything.
		tileBuffer.writeInt(layer.order);
		
		lastSeen = getTileData(layer, 0, 0);
		
		for(row in 0...height)
		{
			for(col in 0...width)
			{
				var currentTile = getTileData(layer, col, row);
				
				//Keep counting consecutive repetitions of a blank tile
				if(currentTile == null && lastSeen == null)
				{
					runCounter++;
				}
				
				//Keep counting consecutive repetitions of a tile
				else if(currentTile != null && areTilesEqual(currentTile, lastSeen))
				{
					runCounter++;
					
					//Record the run if it ends here
					if(row + 1 == height && col + 1 == width)
					{
						writeTilesToByte(tileBuffer, currentTile, runCounter);
					}
				}
				
				else
				{
					writeTilesToByte(tileBuffer, lastSeen, runCounter);
					
					runCounter = 1;
					lastSeen = currentTile;
					
					//Need to add the last tile 
					//to the list if it's not part of a run.
					
					if(row + 1 == height && col + 1 == width)
					{
						writeTilesToByte(tileBuffer, currentTile, runCounter);
					}
				}
			}
		}
		
		var finalArray = new ByteArray(tileBuffer.position);
		finalArray.writeBytes(tileBuffer, 0, tileBuffer.position);

		return finalArray;
	}
	
	private static function writeTilesToByte(buffer:ByteArray, tile:TileData, runLength:Int)
	{
		if(runLength <= SHORT_MAX_VALUE)
		{
			writeTilesToByteShort(buffer, tile, runLength);
		}
		else
		{
			while(runLength > 0)
			{
				var tilesInSubrun = Std.int(Math.min(SHORT_MAX_VALUE, runLength));
				writeTilesToByteShort(buffer, tile, tilesInSubrun);
				runLength -= SHORT_MAX_VALUE;
			}
		}
	}
	
	private static function writeTilesToByteShort(buffer:ByteArray, tile:TileData, runLength:Int)
	{
		if(tile == null)
		{
			buffer.writeShort(0);
			buffer.writeShort(-1);
			buffer.writeShort(-1);
		}
		else
		{
			var writeAutotileIndex = tile.explicitAutotileIndex ? (-tile.autotileIndex - 1) : tile.autotileIndex;
			
			buffer.writeShort(writeAutotileIndex);
			buffer.writeShort(tile.tilesetID);
			buffer.writeShort(tile.tileID);
		}
		
		buffer.writeShort(runLength);
	}
}