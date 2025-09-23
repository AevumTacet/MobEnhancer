package com.mobenhancer.type.zombie;

import com.mobenhancer.ZombieCustomType;

public class Default implements ZombieCustomType {
    @Override
    public String getId() {
        return "default";
    }

    @Override
    public String getName() {
        return "";
    }
}
