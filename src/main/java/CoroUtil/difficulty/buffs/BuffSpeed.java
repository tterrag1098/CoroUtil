package CoroUtil.difficulty.buffs;

import CoroUtil.config.ConfigHWMonsters;
import CoroUtil.difficulty.UtilEntityBuffs;
import CoroUtil.difficulty.data.cmods.CmodAttributeHealth;
import CoroUtil.difficulty.data.cmods.CmodAttributeSpeed;
import CoroUtil.util.CoroUtilAttributes;
import CoroUtil.util.EnumAttribModifierType;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;

/**
 * Created by Corosus on 1/9/2017.
 */
public class BuffSpeed extends BuffBase {

    @Override
    public String getTagName() {
        return UtilEntityBuffs.dataEntityBuffed_Speed;
    }

    @Override
    public boolean applyBuff(EntityCreature ent, float difficulty) {

        /**
         * TODO: use unique persistant UUID for all mods that want to apply dangerously overlapping speed buffs
         * - zombie awareness
         * - invasion
         * - monsters
         */

        CmodAttributeSpeed cmod = (CmodAttributeSpeed)UtilEntityBuffs.getCmodData(ent, getTagName());

        if (cmod != null) {
            /**
             * Lets start by keeping it simple
             * - allow setting base health
             * - allow setting extra health ontop of base health using difficulty * a basic multiplier
             *
             * health = base + (base * difficulty * multiplier)
             *
             * lets follow minecraft attrib rules:
             * - set base health as our base here
             * - apply a multiplier that does exactly above, doable by operation 1 aka INCREMENT_MULTIPLY_BASE
             */

            //set base value if we need to
            if (cmod.base_value != -1) {
                ent.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).setBaseValue(cmod.base_value);
            }

            double extraMultiplier = (difficulty * cmod.difficulty_multiplier);
            ent.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).applyModifier(new AttributeModifier(CoroUtilAttributes.SPEED_BOOST_UUID, "speed multiplier boost", extraMultiplier, EnumAttribModifierType.INCREMENT_MULTIPLY_BASE.ordinal()));
        }

        /*double curSpeed = ent.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).getAttributeValue();
        //avoid retardedly fast speeds
        if (curSpeed < UtilEntityBuffs.speedCap) {
            double speedBoost = (Math.min(ConfigHWMonsters.scaleSpeedCap, difficulty * ConfigHWMonsters.scaleSpeed));
            //debug += "speed % " + speedBoost;
            AttributeModifier speedBoostModifier = new AttributeModifier(CoroUtilAttributes.SPEED_BOOST_UUID, "speed multiplier boost", speedBoost, EnumAttribModifierType.INCREMENT_MULTIPLY_BASE.ordinal());
            if (!ent.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).hasModifier(speedBoostModifier)) {
                ent.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).applyModifier(speedBoostModifier);
            }
        }*/

        return super.applyBuff(ent, difficulty);
    }
}
