package org.screamingsandals.bedwars.lib.nms.entity;

import org.bukkit.entity.LivingEntity;
import org.screamingsandals.bedwars.lib.nms.accessors.EntityAccessor;
import org.screamingsandals.bedwars.lib.nms.accessors.EntityInsentientAccessor;
import org.screamingsandals.bedwars.lib.nms.accessors.NBTTagCompoundAccessor;
import org.screamingsandals.bedwars.lib.nms.accessors.PathfinderGoalMeleeAttackAccessor;
import org.screamingsandals.bedwars.lib.nms.utils.ClassStorage;

public class EntityUtils {

	/*
	 * @return EntityLivingNMS
	 */
	public static EntityLivingNMS makeMobAttackTarget(LivingEntity mob, double speed, double follow,
		double attackDamage) {
		try {
			Object handler = ClassStorage.getHandle(mob);
			if (!EntityInsentientAccessor.getType().isInstance(handler)) {
				throw new IllegalArgumentException("Entity must be instance of EntityInsentient!!");
			}
			
			EntityLivingNMS entityLiving = new EntityLivingNMS(handler);
			
			GoalSelector selector = entityLiving.getGoalSelector();
			selector.clearSelector();
			selector.registerPathfinder(0, PathfinderGoalMeleeAttackAccessor.getConstructor0().newInstance(handler, 1.0D, false));
			
			entityLiving.setAttribute(Attribute.MOVEMENT_SPEED, speed);
			entityLiving.setAttribute(Attribute.FOLLOW_RANGE, follow);
			entityLiving.setAttribute(Attribute.ATTACK_DAMAGE, attackDamage);
			
			entityLiving.getTargetSelector().clearSelector();
			
			return entityLiving;
		} catch (Throwable ignored) {
			ignored.printStackTrace();
		}
		return null;
	}

	public static void disableEntityAI(LivingEntity entity) {
		try {
			entity.setAI(false);
		} catch (Throwable t) {
			// this is not needed anymore, some 1.8 bullshit
			try {
				Object handler = ClassStorage.getHandle(entity);
				Object tag = ClassStorage.getMethod(handler, "getNBTTag").invoke(); // Can this really work? or it's always creating
																		// new
																		// one?
				if (tag == null) {
					tag = NBTTagCompoundAccessor.getConstructor0().newInstance();
				}
				ClassStorage.getMethod(handler, EntityAccessor.getMethodFunc_184198_c1()).invoke(tag);
				ClassStorage.getMethod(NBTTagCompoundAccessor.getMethodFunc_74768_a1()).invokeInstance(tag, "NoAI", 1);
				ClassStorage.getMethod(handler, EntityAccessor.getMethodFunc_70020_e1()).invoke(tag);
			} catch (Throwable ignored) {
			}
		}
	}
}
