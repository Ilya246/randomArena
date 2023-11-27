package randomArena;

import arc.Core;
import arc.Events;
import arc.math.Mathf;
import arc.struct.*;
import arc.util.*;
import arc.util.pooling.Pools;
import mindustry.content.*;
import mindustry.core.GameState;
import mindustry.entities.abilities.UnitSpawnAbility;
import mindustry.entities.*;
import mindustry.entities.bullet.*;
import mindustry.entities.units.StatusEntry;
import mindustry.game.EventType.*;
import mindustry.game.Team;
import mindustry.gen.*;
import mindustry.mod.Plugin;
import mindustry.type.*;
import mindustry.world.Tile;

import static mindustry.Vars.*;

public class RandomArena extends Plugin{
	private static final String configPrefix = "randomArena-";
	public String gamemodeInfo = "[scarlet]GAMEMODE INFO:[accent]\n-Kills may not always register properly, this is a limitation of the game and can't be fixed\n-Navals will sometimes spawn as flying, more so on low-water maps\n-Gamma can build, buildings work for free\n-You can always hit both air and ground units even though the enemy may sometimes not flash\n-Use /lb to view the leaderboard\n-Kill others to get score";

	public ObjectIntMap<String> scores = new ObjectIntMap<>();
	public ObjectMap<String, Unit> playerUnits = new ObjectMap<>();
	public IntMap<String> unitPlayers = new IntMap<>();
	public Seq<Unit> killedUnprocessed = new Seq<>();
	
	public Seq<UnitType> availableUnitTypes = new Seq<>();
	public Seq<Seq<UnitType>> availableUnitSets = new Seq<>();
	public ObjectMap<UnitType, Seq<StatusEffect>> boostedUnits = new ObjectMap<>();

    @Override
    public void init(){
		availableUnitSets.add(Seq.with(
		UnitTypes.crawler, UnitTypes.dagger,   UnitTypes.flare,    UnitTypes.nova,   UnitTypes.risso,                                    UnitTypes.elude,   UnitTypes.stell,    UnitTypes.merui,
		UnitTypes.atrax,   UnitTypes.mace,     UnitTypes.horizon,  UnitTypes.pulsar, UnitTypes.minke, UnitTypes.oxynoe,  UnitTypes.poly, UnitTypes.avert,   UnitTypes.locus,    UnitTypes.cleroi,
		UnitTypes.spiroct, UnitTypes.fortress, UnitTypes.zenith,   UnitTypes.quasar, UnitTypes.bryde, UnitTypes.cyerce,  UnitTypes.mega));
		availableUnitSets.add(Seq.with(
		UnitTypes.crawler, UnitTypes.gamma,
		UnitTypes.arkyid,  UnitTypes.scepter,  UnitTypes.antumbra, UnitTypes.vela,   UnitTypes.sei,                      UnitTypes.quad, UnitTypes.obviate, UnitTypes.vanquish, UnitTypes.anthicus,
		UnitTypes.toxopid, UnitTypes.reign,    UnitTypes.eclipse,  UnitTypes.corvus, UnitTypes.omura, UnitTypes.navanax,                 UnitTypes.quell,   UnitTypes.conquer,  UnitTypes.tecta,
		                                                                                                                                 UnitTypes.disrupt,                     UnitTypes.collaris));
		boostedUnits.put(UnitTypes.crawler, Seq.with(StatusEffects.shielded, StatusEffects.shielded, StatusEffects.overclock, StatusEffects.overclock, StatusEffects.overclock, StatusEffects.overclock, StatusEffects.overclock, StatusEffects.overclock));
		boostedUnits.put(UnitTypes.dagger, Seq.with(StatusEffects.overclock, StatusEffects.overclock, StatusEffects.overclock, StatusEffects.overdrive, StatusEffects.overdrive, StatusEffects.overdrive, StatusEffects.shielded));
		boostedUnits.put(UnitTypes.obviate, Seq.with(StatusEffects.overdrive, StatusEffects.overdrive, StatusEffects.overclock, StatusEffects.shielded));
		boostedUnits.put(UnitTypes.anthicus, Seq.with(StatusEffects.overclock, StatusEffects.overclock, StatusEffects.overclock, StatusEffects.overclock));
		boostedUnits.put(UnitTypes.quell, Seq.with(StatusEffects.overclock));
		boostedUnits.put(UnitTypes.flare, Seq.with(StatusEffects.shielded, StatusEffects.shielded, StatusEffects.shielded));
		// lowtier boosts
		boostedUnits.put(UnitTypes.nova, Seq.with(StatusEffects.overclock, StatusEffects.overdrive));
		boostedUnits.put(UnitTypes.risso, Seq.with(StatusEffects.overclock, StatusEffects.overdrive));
		boostedUnits.put(UnitTypes.stell, Seq.with(StatusEffects.overclock, StatusEffects.overdrive));
		boostedUnits.put(UnitTypes.elude, Seq.with(StatusEffects.overclock, StatusEffects.overdrive));
		boostedUnits.put(UnitTypes.mace, Seq.with(StatusEffects.overclock, StatusEffects.overdrive));
		boostedUnits.put(UnitTypes.vanquish, Seq.with(StatusEffects.overclock, StatusEffects.overdrive));
		for(UnitType type : content.units()){
			type.weapons.each(w -> {
				fixBullet(w.bullet);
			});
			type.armor = 0f;
			type.buildSpeed = -1f;
		}

		UnitTypes.crawler.weapons.get(0).bullet.splashDamage = 666666;
		UnitTypes.omura.abilities.each(ability -> {
			if(ability instanceof UnitSpawnAbility a){
				a.unit = UnitTypes.mono;
			}
		});
		UnitTypes.sei.health = 7500f;
		UnitTypes.omura.health = 18000f;
		UnitTypes.gamma.buildSpeed = 0.8f;
		UnitTypes.quell.weapons.get(0).bullet.spawnUnit.lifetime *= 2.5f;
		UnitTypes.disrupt.weapons.get(0).bullet.spawnUnit.lifetime *= 1.5f;
		UnitTypes.anthicus.weapons.get(0).bullet.spawnUnit.lifetime *= 2f;
		
		Events.on(WorldLoadEvent.class, e -> {
			scores.clear();
			playerUnits.clear();
			unitPlayers.clear();
			availableUnitTypes = availableUnitSets.random();
			Core.app.post(() -> {
				state.rules.canGameOver = false;
				state.rules.pvpAutoPause = false;
				state.rules.coreCapture = false;
				state.rules.pvp = true;
				state.rules.waves = false;
				state.rules.unitCap = Math.max(state.rules.unitCap, 1);
				for(Team t : Team.all){
					var rules = t.rules();
					rules.cheat = true;
					rules.infiniteResources = true;
				}
				Groups.player.each(p -> {
					scores.put(p.uuid(), 0);
					p.sendMessage("[scarlet]Your unit will be assigned soon.");
				});
				state.set(GameState.State.playing);
				Call.setRules(state.rules);
			});
			state.teams.active.each(t -> {
				t.cores.each(core -> {
					Core.app.post(() -> {
						core.kill();
					});
				});
			});
			Timer.schedule(() -> {
				Groups.player.each(p -> {
					initPlayer(p);
				});
			}, 5f);
		});
		Events.on(PlayerJoin.class, e -> {
			e.player.sendMessage(gamemodeInfo);
			e.player.sendMessage(gamemodeInfo); // send it twice so they see it for sure
			initPlayer(e.player);
		});
		netServer.assigner = (player, players) -> {
			ObjectIntMap<Team> teamPlayers = new ObjectIntMap<>();
			players.forEach(p -> {
				teamPlayers.put(p.team(), teamPlayers.get(p.team(), 0) + 1);
			});
			teamPlayers.put(Team.derelict, 999999);
            return Seq.with(Team.all).shuffle().min(t -> {
				return teamPlayers.get(t);
			});
        };
        Events.on(UnitBulletDestroyEvent.class, e -> {
			if(e.bullet.owner instanceof Unit killer){
				String uuid = unitPlayers.get(e.unit.id);
				String killerUuid = unitPlayers.get(killer.id);
				if(uuid != null && killerUuid != null){
					addKill(killerUuid, uuid);
					killedUnprocessed.remove(e.unit);
					unitPlayers.remove(e.unit.id);
					if(e.unit.isAdded()){
						Call.unitDestroy(e.unit.id);
					}
				}
			}
        });
		Events.on(UnitDestroyEvent.class, e -> {
			Player player = e.unit.getPlayer();
			if(player == null){
				String uuid = unitPlayers.get(e.unit.id);
				player = playerByUuid(uuid);
			}
			if(player != null){
				assignUnit(player);
			}
			killedUnprocessed.add(e.unit);
		});
		Events.run(Trigger.update, () -> {
			for(Unit unit : killedUnprocessed){
				Unit killer = Units.closestEnemy(unit.team, unit.x, unit.y, 100 * tilesize, k -> unitPlayers.containsKey(k.id));	
				if(killer != null){
					String uuid = unitPlayers.get(unit.id);
					String killerUuid = unitPlayers.get(killer.id);
					if(uuid != null && killerUuid != null){
						addKill(killerUuid, uuid);
					}
				}
				unitPlayers.remove(unit.id);
				if(unit.isAdded()){
					Call.unitDestroy(unit.id);
				}
			}
			killedUnprocessed.clear();
		});
    }
	
	public void fixBullet(BulletType b){
		b.collidesGround = true;
		b.collidesAir = true;
		if(b.spawnUnit != null){
			b.spawnUnit.targetGround = true;
			b.spawnUnit.targetAir = true;
		}
		if(b.fragBullet != null){
			fixBullet(b.fragBullet);
		}
	}

	public Seq<StatusEntry> getStatusEntries(Unit unit){
		try{
			return Reflect.get(unit, "statuses");
		}catch(Exception e){
			return Reflect.get(UnitEntity.class, unit, "statuses");
		}
	}
	public void applyStatus(Unit unit, float duration, Seq<StatusEffect> effects){
        Seq<StatusEntry> entries = new Seq<>();
		for(StatusEffect effect : effects){
			StatusEntry entry = Pools.obtain(StatusEntry.class, StatusEntry::new);
			entry.set(effect, duration);
			entries.add(entry);
		}
        getStatusEntries(unit).addAll(entries);
    }
	
	public Unit spawnUnit(Team t){
		Tile tile = world.tile((int)Mathf.random(world.width() - 1), (int)Mathf.random(world.height() - 1));
		UnitType type = availableUnitTypes.random();
		boolean fly = false;
		if(!type.flying){
			if(type.naval){
				int i = 0;
				while(tile.solid() || tile.floor().liquidDrop != Liquids.water){
					if(i > Config.navalSpawnLimit.i()){
						fly = true;
						break;
					}
					tile = world.tile((int)Mathf.random(world.width() - 1), (int)Mathf.random(world.height() - 1));
					i += 1;
				}
			}else{
				while(tile.solid() || tile.floor().isDeep()){
					tile = world.tile((int)Mathf.random(world.width() - 1), (int)Mathf.random(world.height() - 1));
				}
			}
		}
		Unit unit = type.spawn(t, tile.getX(), tile.getY());
		if(fly){
			unit.elevation = 1f;
		}
		if(boostedUnits.containsKey(type)){
			applyStatus(unit, 999999999f, boostedUnits.get(type));
		}
		return unit;
	}
	
	public void assignUnit(Player p){
		Unit u = spawnUnit(p.team());
		p.unit(u);
		playerUnits.put(p.uuid(), u);
		unitPlayers.put(u.id, p.uuid());
	}
	
	public void initPlayer(Player p){
		if(!scores.containsKey(p.uuid())){
			scores.put(p.uuid(), 0);
		}
		Unit u = playerUnits.get(p.uuid());
		if(u != null && !u.dead){
			p.unit(u);
		} else {
			assignUnit(p);
		}
	}
	
	public void addKill(String killerUuid, String uuid){
		int newScore = scores.get(killerUuid, 0) + 1;
		scores.put(killerUuid, newScore);
		String name = getLastName(uuid);
		String killerName = getLastName(killerUuid);
		StringBuilder killMessage = new StringBuilder();
		killMessage.append(killerName).append(" [white](").append(newScore).append(") ").append((char)Iconc.codes.get("modePvp")).append(" ").append(name);
		Call.sendMessage(killMessage.toString());
		if(!state.gameOver && newScore >= Config.victoryScore.i()){
			Call.sendMessage(killerName + " [accent]has won the game!");
			Events.fire(new GameOverEvent(Team.derelict));
		}
	}

    public enum Config{
        victoryScore("The score needed for a player to win.", 10),
		navalSpawnLimit("How many times to try to spawn navals before spawning them as flying.", 3);

        public static final Config[] all = values();

        public final Object defaultValue;
        public String description;

        Config(String description, Object value){
            this.description = description;
            this.defaultValue = value;
        }
        public String getName(){
            return configPrefix + name();
        }
        public boolean b(){
            return Core.settings.getBool(getName(), (boolean)defaultValue);
        }
        public float f(){
            return Core.settings.getFloat(getName(), (float)defaultValue);
        }
		public int i(){
            return Core.settings.getInt(getName(), (int)defaultValue);
        }
        public String s(){
            return Core.settings.get(getName(), defaultValue).toString();
        }
        public void set(Object value){
            Core.settings.put(getName(), value);
        }
    }
	
	public String getLastName(String uuid){
		Player p = playerByUuid(uuid);
		return p != null ? p.coloredName() : netServer.admins.getInfo(uuid).lastName;
	}
	
	public Player playerByUuid(String uuid){
		return Groups.player.find(p -> {
			return p.uuid().equals(uuid);
		});
	}

    public void registerClientCommands(CommandHandler handler){
        handler.<Player>register("lb", "[players]", "Display leaderboard.", (args, player) -> {
			StringBuilder leaderboard = new StringBuilder("[scarlet]Leaderboard:");
			leaderboard.append("\n[Winning score]").append(Config.victoryScore.i()).append("[accent]\n");
            Seq<ObjectIntMap.Entry<String>> scoreSeq = scores.entries().toArray();
			scoreSeq.sort(s -> {
				return -s.value;
			});
			int entries = Math.min(10, scoreSeq.size);
			if (args.length > 0) {
				try{
                    entries = Math.min(Integer.parseInt(args[0]), scoreSeq.size);
                }catch(NumberFormatException e){
                }
			}
			for(int i = 0; i < scoreSeq.size; i++){
				leaderboard.append("\n").append(getLastName(scoreSeq.get(i).key.toString())).append(" [accent] - ").append(scoreSeq.get(i).value);
			}
			player.sendMessage(leaderboard.toString());
        });
		handler.<Player>register("info", "Show info about the RandomArena gamemode.", (args, player) -> {
			player.sendMessage(gamemodeInfo);
		});
    }

    public void registerServerCommands(CommandHandler handler){
        handler.register("randomarenaconfig", "[name] [value]", "Configure random arena plugin settings. Run with no arguments to list values.", args -> {
            if(args.length == 0){
                Log.info("All config values:");
                for(Config c : Config.all){
                    Log.info("&lk| @: @", c.name(), "&lc&fi" + c.s());
                    Log.info("&lk| | &lw" + c.description);
                    Log.info("&lk|");
                }
                return;
            }
            try{
                Config c = Config.valueOf(args[0]);
                if(args.length == 1){
                    Log.info("'@' is currently @.", c.name(), c.s());
                }else{
                    if(args[1].equals("default")){
                        c.set(c.defaultValue);
                    }else{
                        try{
                            if(c.defaultValue instanceof Float){
                                c.set(Float.parseFloat(args[1]));
                            }else if(c.defaultValue instanceof Integer){
                                c.set(Integer.parseInt(args[1]));
                            }else{
                                c.set(Boolean.parseBoolean(args[1]));
                            }
                        }catch(NumberFormatException e){
                            Log.err("Not a valid number: @", args[1]);
                            return;
                        }
                    }
                    Log.info("@ set to @.", c.name(), c.s());
                    Core.settings.forceSave();
                }
            }catch(IllegalArgumentException e){
                Log.err("Unknown config: '@'. Run the command with no arguments to get a list of valid configs.", args[0]);
            }
        });
    }
}
