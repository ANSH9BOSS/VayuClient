/*
 * Decompiled with CFR 0.152.
 */
package net.fastclient.hud.store;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.fastclient.hud.store.CosmeticCategory;

public class StoreState {
    private int coinBalance = 2500;
    private final List<String> ownedItemIds = new ArrayList<String>();
    private final List<String> equippedItemIds = new ArrayList<String>();
    private String lastSelectedCategory = CosmeticCategory.FEATURED.name();
    private double scrollOffset = 0.0;
    private long lastCoinClaimEpochDay = Long.MIN_VALUE;

    public int getCoinBalance() {
        return this.coinBalance;
    }

    public void setCoinBalance(int coinBalance) {
        this.coinBalance = coinBalance;
    }

    public void addCoins(int amount) {
        this.coinBalance = Math.max(0, this.coinBalance + amount);
    }

    public boolean spendCoins(int amount) {
        if (amount <= 0) {
            return true;
        }
        if (this.coinBalance >= amount) {
            this.coinBalance -= amount;
            return true;
        }
        return false;
    }

    public List<String> getOwnedItemIds() {
        return this.ownedItemIds;
    }

    public void replaceOwned(Collection<String> ids) {
        this.ownedItemIds.clear();
        for (String id : ids) {
            this.addOwned(id);
        }
    }

    public boolean owns(String id) {
        return this.ownedItemIds.contains(id);
    }

    public void addOwned(String id) {
        if (!this.ownedItemIds.contains(id)) {
            this.ownedItemIds.add(id);
        }
    }

    public List<String> getEquippedItemIds() {
        return this.equippedItemIds;
    }

    public void replaceEquipped(Collection<String> ids) {
        this.equippedItemIds.clear();
        for (String id : ids) {
            this.equip(id);
        }
    }

    public boolean isEquipped(String id) {
        return this.equippedItemIds.contains(id);
    }

    public void equip(String id) {
        if (!this.equippedItemIds.contains(id)) {
            this.equippedItemIds.add(id);
        }
    }

    public void unequip(String id) {
        this.equippedItemIds.remove(id);
    }

    public void removeAllEquipped() {
        this.equippedItemIds.clear();
    }

    public String getLastSelectedCategory() {
        return this.lastSelectedCategory;
    }

    public void setLastSelectedCategory(String lastSelectedCategory) {
        this.lastSelectedCategory = lastSelectedCategory;
    }

    public double getScrollOffset() {
        return this.scrollOffset;
    }

    public void setScrollOffset(double scrollOffset) {
        this.scrollOffset = scrollOffset;
    }

    public long getLastCoinClaimEpochDay() {
        return this.lastCoinClaimEpochDay;
    }

    public void setLastCoinClaimEpochDay(long lastCoinClaimEpochDay) {
        this.lastCoinClaimEpochDay = lastCoinClaimEpochDay;
    }

    public boolean canClaimDailyCoins() {
        return this.lastCoinClaimEpochDay != LocalDate.now().toEpochDay();
    }

    public void markDailyCoinsClaimed() {
        this.lastCoinClaimEpochDay = LocalDate.now().toEpochDay();
    }
}

