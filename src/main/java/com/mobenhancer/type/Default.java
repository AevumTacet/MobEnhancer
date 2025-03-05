package com.mobenhancer.type;

import com.mobenhancer.CustomType;

public class Default implements CustomType {
    @Override
    public String getId() {
        return "default";
    }

    @Override
    public String getName() {
        return "";
    }
}
