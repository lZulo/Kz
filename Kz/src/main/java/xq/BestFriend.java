package xq;

import com.github.manolo8.darkbot.Main;
import com.github.manolo8.darkbot.config.PlayerTag;
import com.github.manolo8.darkbot.config.types.*;
import com.github.manolo8.darkbot.core.entities.Npc;
import com.github.manolo8.darkbot.core.entities.Ship;
import com.github.manolo8.darkbot.core.itf.Configurable;
import com.github.manolo8.darkbot.core.itf.InstructionProvider;
import com.github.manolo8.darkbot.core.itf.Module;
import com.github.manolo8.darkbot.core.objects.group.GroupMember;
import com.github.manolo8.darkbot.core.utils.Drive;
import com.github.manolo8.darkbot.core.utils.Location;
import com.github.manolo8.darkbot.extensions.features.Feature;
import com.github.manolo8.darkbot.modules.MapModule;
import com.github.manolo8.darkbot.modules.utils.NpcAttacker;
import com.github.manolo8.darkbot.modules.utils.SafetyFinder;


import java.util.List;
import java.util.Random;

@Feature(name = "BestFriend", description = "Follow you and attack the npcs you shoot")
public class BestFriend implements Module, Configurable<BestFriend.BFConfig>, InstructionProvider {
    private BFConfig BFConfig;
    private Ship ship;
    private Main main;
    private List<Ship> ships;
    private NpcAttacker attack;
    private List<Npc> npcs;
    private Drive drive;
    private Random rnd;
    private SafetyFinder safety;
    private long refreshing;


    @Override
    public void install(Main main) {
        this.main = main;
        this.ships = main.mapManager.entities.ships;
        this.npcs = main.mapManager.entities.npcs;
        this.attack = new NpcAttacker(main);
        this.drive = main.hero.drive;
        this.rnd = new Random();
        this.safety = new SafetyFinder(main);

    }

    @Override
    public void uninstall() {

        safety.uninstall();
    }

    @Override
    public boolean canRefresh() {

        if (!attack.hasTarget()) refreshing = System.currentTimeMillis() + 10000;
        return !attack.hasTarget() && safety.state() == SafetyFinder.Escaping.WAITING;


    }

    @Override
    public void setConfig(BFConfig BFConfig) {

        this.BFConfig = BFConfig;
    }

    @Override
    public String instructions() {

        return "If you want your best friend run when is in danger check \n" +
                "the first box and configure safety settings in general. \n" +
                "You need to stay in group with your good friend.  \n" +
                "If a \"Tag\" is not defined, it will follow the group leader.";
    }

    public static class BFConfig  {

        @Option (value = "Ship Tag", description = "Put your main ship tag here")
        @Tag(TagDefault.ALL)
        public PlayerTag SENTINEL_TAG = null;

        @Option("Check to enable the darkbot security settings")
        public boolean OPTSEC = false;

        @Option("Random movements when attacking npc")
        public boolean RANDOM = false;

        @Option("Follow")
        public boolean JFOLLOW = false ;



    }


    public void tick() {

        if (BFConfig.OPTSEC && checkDangerous()) {
            if (BFConfig.RANDOM) {
                RANDOM_IN();
            } else if (BFConfig.JFOLLOW) {
                JFOLLOW_IN();
            }
        }
    }


    private boolean checkDangerous() {

        return safety.tick() ;
    }

    private boolean isAttacking() {
        if ((attack.target = this.npcs.stream()
                .filter(s -> ship.isAttacking(s))
                .findAny().orElse(null)) == null) {
            return false;
        }
        main.hero.attackMode(attack.target);
        attack.doKillTargetTick();

        return (attack.target != null);
    }

    private boolean shipAround() {
        ship = this.ships.stream()
                .filter(ship -> (BFConfig.SENTINEL_TAG.has(main.config.PLAYER_INFOS.get(ship.id))))
                .findAny().orElse(null);
        return ship != null;
    }

    private void goToLeader() {
        for (GroupMember m : main.guiManager.group.group.members) {
            if ((m.isLeader || BFConfig.SENTINEL_TAG.has(main.config.PLAYER_INFOS.get(m.id))) && m.id != main.hero.id) {
                if (m.mapId == main.hero.map.id) {
                    drive.move(m.location);
                } else {
                    main.setModule(new MapModule()).setTarget(main.starManager.byId(m.mapId));
                }
                return;
            }
        }
    }

    private void acceptGroup() {
        if (!main.guiManager.group.invites.isEmpty() && !main.guiManager.group.visible) {
            main.guiManager.group.show(true);
        }

        main.guiManager.group.invites.stream()
                .filter(in -> in.incomming && (BFConfig.SENTINEL_TAG == null ||
                        BFConfig.SENTINEL_TAG.has(main.config.PLAYER_INFOS.get(in.inviter.id))))
                .findFirst()
                .ifPresent(inv -> main.guiManager.group.acceptInvite(inv));
    }

    private void RANDOM_IN() {
        if (main.guiManager.group.group != null && main.guiManager.group.group.isValid()) {
            if (shipAround()) {

                if (!isAttacking() && main.hero.target != ship) {
                    main.hero.roamMode();
                    drive.move(ship);
                } else {
                    drive.move(Location.of(attack.target.locationInfo.now, rnd.nextInt(360), attack.target.npcInfo.radius));
                }
            } else {
                goToLeader();
            }
        } else {
            acceptGroup();
        }

    }

    private void JFOLLOW_IN() {
        if (main.guiManager.group.group != null && main.guiManager.group.group.isValid()) {
            if (shipAround()) {
                if (!isAttacking() && main.hero.target != ship) {
                    main.hero.roamMode();
                }
                drive.move(ship);
            } else {
                goToLeader();
            }
        } else {
            acceptGroup();
        }
    }
}
