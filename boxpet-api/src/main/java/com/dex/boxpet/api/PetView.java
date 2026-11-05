package com.dex.boxpet.api;

import java.util.UUID;

/**
 * Read-only snapshot of a pet at the moment it was taken.
 *
 * @param id          the pet's unique id
 * @param owner       the owner's UUID
 * @param type        pet type id (file name in {@code pets/})
 * @param name        shown name (custom name or the type's name), with colour codes
 * @param rarity      rarity id ({@code common}, {@code rare}, ...)
 * @param role        {@code combat}, {@code support} or {@code boost}
 * @param level       current level
 * @param xp          XP inside the current level
 * @param stage       evolution stage, 0 = first
 * @param bond        thân thiết 0-100
 * @param fullness    độ no 0-100
 * @param ageDays     whole days since it hatched
 * @param active      true if it is the owner's active pet
 */
public record PetView(UUID id, UUID owner, String type, String name, String rarity, String role, int level, double xp,
                      int stage, int bond, int fullness, long ageDays, boolean active) {
}
